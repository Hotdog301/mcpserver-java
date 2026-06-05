package io.mcpserver.core.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * HTTP+SSE transport for MCP server communication.
 *
 * <p>This transport implements the MCP HTTP + Server-Sent Events (SSE) specification,
 * allowing MCP clients to communicate with the server over HTTP rather than stdio.
 * It uses Java's built-in {@link HttpServer} and requires no external dependencies.</p>
 *
 * <p>Protocol flow:</p>
 * <ol>
 *   <li>Client opens a GET connection to {@code /sse} — server keeps it open and
 *       sends events as SSE messages</li>
 *   <li>Server sends an {@code endpoint} event containing the session URI where
 *       the client should POST messages</li>
 *   <li>Client sends JSON-RPC messages via POST to {@code /message} with the
 *       session ID obtained from the SSE connection</li>
 *   <li>Server pushes responses/events back through the SSE connection</li>
 * </ol>
 *
 * <p>This transport supports two usage modes:</p>
 * <ul>
 *   <li><b>Push mode</b>: incoming POST messages are forwarded to the
 *       {@code messageHandler} callback (useful for standalone integration)</li>
 *   <li><b>Pull mode</b>: {@link #readLine()} blocks until the next message is
 *       available, allowing {@link io.mcpserver.core.McpServer} to drive the loop</li>
 * </ul>
 */
public class SseServerTransport implements Transport {

    private static final String SSE_ENDPOINT = "/sse";
    private static final String MESSAGE_ENDPOINT = "/message";

    private final HttpServer server;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Consumer<String> messageHandler;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2), r -> {
        Thread t = new Thread(r, "mcp-sse-worker");
        t.setDaemon(true);
        return t;
    });

    /** Queue used for pull-based reading via {@link #readLine()}. */
    private final LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1024);

    /** Maximum request body size for POST messages (1 MB). */
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;

    /** CORS origin header value. Defaults to "*". */
    private volatile String corsOrigin = "*";

    private volatile SseConnection currentConnection;
    private volatile String currentSessionId;

    /**
     * Callback invoked when a new session is established.
     * Used by tests to synchronize on session state.
     */
    private volatile Runnable onSessionEstablished;

    public void setOnSessionEstablished(Runnable callback) {
        this.onSessionEstablished = callback;
    }

    /**
     * Sets the CORS origin header value for both SSE and message endpoints.
     *
     * @param corsOrigin the origin value (e.g., "*", "http://example.com");
     *                   must not be null
     */
    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = Objects.requireNonNull(corsOrigin, "corsOrigin must not be null");
    }

    /**
     * Creates a new SSE server transport.
     *
     * @param port           the HTTP port to bind to
     * @param messageHandler callback invoked for each incoming JSON-RPC message line
     * @throws IOException if the HTTP server cannot be created
     */
    public SseServerTransport(int port, Consumer<String> messageHandler) throws IOException {
        this.port = port;
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(executor);

        this.server.createContext(SSE_ENDPOINT, this::handleSseConnection);
        this.server.createContext(MESSAGE_ENDPOINT, this::handleMessagePost);
    }

    /**
     * Creates a new SSE server transport without a push-mode message handler.
     * Use this constructor when the transport is driven by {@link #readLine()}
     * (e.g., paired with {@link io.mcpserver.core.McpServer}).
     *
     * @param port the HTTP port to bind to (0 for random)
     * @throws IOException if the HTTP server cannot be created
     */
    public SseServerTransport(int port) throws IOException {
        this(port, msg -> {});
    }

    /**
     * Starts the HTTP server and begins accepting connections.
     */
    @Override
    public void start() {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server is already running");
        }
        server.start();
        System.err.println("[MCP SSE Transport] Listening on http://0.0.0.0:" + port);
    }

    /**
     * Stops the HTTP server and releases all resources.
     *
     * <p>Closes all active connections (both input and output) to unblock any
     * pending reads before stopping the HTTP server.</p>
     */
    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        // Close any active SSE connection (both input and output)
        SseConnection conn = currentConnection;
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException e) {
                System.err.println("[MCP SSE Transport] Error closing SSE connection: " + e.getMessage());
            }
            currentConnection = null;
        }

        server.stop(1);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.err.println("[MCP SSE Transport] Shutdown complete");
    }

    /**
     * Sends a JSON event to the connected SSE client.
     *
     * @param data the JSON string to send as an SSE event
     * @throws IOException if the connection is closed or write fails
     */
    public void sendEvent(String data) throws IOException {
        SseConnection conn = currentConnection;
        if (conn == null) {
            throw new IOException("No connected SSE client");
        }
        conn.send(data);
    }

    /**
     * Returns whether the transport is currently running.
     *
     * @return {@code true} if running
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the port the HTTP server is bound to.
     * If started with port 0, returns the actual OS-assigned port.
     *
     * @return the port number
     */
    public int getPort() {
        return server.getAddress().getPort();
    }

    /**
     * Returns the current session ID, or {@code null} if no client is connected.
     *
     * @return the session ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * Reads the next JSON-RPC message from the internal queue.
     *
     * <p>Blocks until a message is available or the transport is stopped.
     * Returns {@code null} when the transport has been stopped, allowing
     * the caller to break out of a read loop.</p>
     *
     * @return the JSON string, or {@code null} if stopped
     * @throws IOException if an unexpected error occurs
     */
    @Override
    public String readLine() throws IOException {
        try {
            while (running.get()) {
                String message = messageQueue.poll(500, TimeUnit.MILLISECONDS);
                if (message != null) {
                    return message;
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Sends a JSON-RPC response through the SSE connection.
     *
     * @param message the message to send (will be serialized by {@link JsonRpcSerializer})
     * @throws IOException if serialization or send fails
     */
    @Override
    public void sendMessage(Object message) throws IOException {
        String json = JsonRpcSerializer.INSTANCE.serialize(message);
        sendEvent("event: message\ndata: " + json + "\n\n");
    }

    // ---------------------------------------------------------------
    // HTTP handlers
    // ---------------------------------------------------------------

    /**
     * Handles a new SSE connection request (GET /sse).
     *
     * <p>Keeps the connection open and streams events to the client.
     * Sends an initial {@code endpoint} event with the message POST URI.</p>
     */
    private void handleSseConnection(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String messageUri = "/message?sessionId=" + sessionId;

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigin);

        OutputStream out = exchange.getResponseBody();
        InputStream in = exchange.getRequestBody();
        SseConnection conn = new SseConnection(sessionId, out, in);

        // Replace any previous connection
        SseConnection previous = currentConnection;
        if (previous != null) {
            try {
                previous.close();
            } catch (IOException ignored) {
                // Ignore
            }
        }

        currentConnection = conn;
        currentSessionId = sessionId;

        Runnable listener = onSessionEstablished;
        if (listener != null) {
            try {
                listener.run();
            } catch (Exception e) {
                System.err.println("[MCP SSE Transport] onSessionEstablished callback error: " + e.getMessage());
            }
        }

        System.err.println("[MCP SSE Transport] Client connected (session=" + sessionId + ")");

        // Send 200 OK and the endpoint event
        exchange.sendResponseHeaders(200, 0);

        conn.send("event: endpoint\ndata: " + messageUri + "\n\n");

        // Block until the connection closes.
        // When the client disconnects, read() returns -1 and the loop exits.
        // During shutdown, stop() closes the connection's input stream which
        // causes read() to throw IOException, caught below.
        try {
            byte[] buf = new byte[4096];
            while (running.get()) {
                int n = in.read(buf);
                if (n <= 0) {
                    break; // Client closed or stream interrupted
                }
            }
        } catch (IOException ignored) {
            // Client disconnected or server stopped
        } finally {
            try {
                conn.close();
            } catch (IOException ignored) {
                // Ignore
            }
            if (currentConnection == conn) {
                currentConnection = null;
                currentSessionId = null;
            }
            exchange.close();
            System.err.println("[MCP SSE Transport] Client disconnected (session=" + sessionId + ")");
        }
    }

    /**
     * Handles a POST message to the message endpoint (POST /message).
     */
    private void handleMessagePost(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        // Session validation: if an SSE session is active, require sessionId match
        String expectedSessionId = currentSessionId;
        if (expectedSessionId != null) {
            String query = exchange.getRequestURI().getQuery();
            String requestSessionId = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if ("sessionId".equals(pair[0]) && pair.length > 1) {
                        requestSessionId = pair[1];
                        break;
                    }
                }
            }
            if (!expectedSessionId.equals(requestSessionId)) {
                System.err.println("[MCP SSE Transport] Rejected POST from invalid session: "
                        + requestSessionId + " (expected: " + expectedSessionId + ")");
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
        }

        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigin);
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        // Read the request body with size limit
        java.io.InputStream is = exchange.getRequestBody();
        byte[] buf = new byte[MAX_MESSAGE_SIZE + 1];
        int offset = 0;
        int bytesRead;
        try {
            while (offset <= MAX_MESSAGE_SIZE
                    && (bytesRead = is.read(buf, offset, buf.length - offset)) != -1) {
                offset += bytesRead;
            }
        } finally {
            is.close();
        }
        if (offset > MAX_MESSAGE_SIZE) {
            System.err.println("[MCP SSE Transport] Request body too large (exceeded " + MAX_MESSAGE_SIZE + " bytes)");
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return;
        }
        byte[] bodyBytes = Arrays.copyOf(buf, offset);
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        if (body.isEmpty()) {
            exchange.sendResponseHeaders(202, 0);
            exchange.close();
            return;
        }

        // Acknowledge receipt
        String response = "accepted";
        exchange.sendResponseHeaders(202, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }

        exchange.close();

        // Add to queue for pull-based reading (McpServer.readLine())
        if (!messageQueue.offer(body)) {
            System.err.println("[MCP SSE Transport] Message queue full (capacity=1024), dropping message");
        }

        // Also invoke the callback for push-based integration
        if (messageHandler != null) {
            messageHandler.accept(body);
        }
    }

    // ---------------------------------------------------------------
    // SSE connection helper
    // ---------------------------------------------------------------

    /**
     * Wraps an SSE connection's input and output streams.
     */
    private static class SseConnection {
        private final String sessionId;
        private final OutputStream output;
        private final InputStream input;
        private volatile boolean closed;

        SseConnection(String sessionId, OutputStream output, InputStream input) {
            this.sessionId = sessionId;
            this.output = output;
            this.input = input;
        }

        synchronized void send(String data) throws IOException {
            if (closed) {
                throw new IOException("SSE connection closed");
            }
            output.write(data.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                input.close();
            } catch (IOException ignored) {
            }
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }

        String sessionId() {
            return sessionId;
        }
    }
}
