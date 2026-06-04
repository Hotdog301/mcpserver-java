package io.mcpserver.core.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * <p>This transport is not thread-safe for reads; callers should coordinate access.</p>
 */
public class SseServerTransport {

    private static final String SSE_ENDPOINT = "/sse";
    private static final String MESSAGE_ENDPOINT = "/message";

    private final HttpServer server;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Consumer<String> messageHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mcp-sse-worker");
        t.setDaemon(true);
        return t;
    });

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
     * Starts the HTTP server and begins accepting connections.
     */
    public void start() {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server is already running");
        }
        server.start();
        System.err.println("[MCP SSE Transport] Listening on http://0.0.0.0:" + port);
    }

    /**
     * Stops the HTTP server and releases all resources.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        // Close any active SSE connection
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        OutputStream out = exchange.getResponseBody();
        SseConnection conn = new SseConnection(sessionId, out);

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
            listener.run();
        }

        System.err.println("[MCP SSE Transport] Client connected (session=" + sessionId + ")");

        // Send 200 OK and the endpoint event
        exchange.sendResponseHeaders(200, 0);

        conn.send("event: endpoint\ndata: " + messageUri + "\n\n");

        // Block until the connection closes
        try {
            // Keep the connection alive by reading from the input stream
            // (The client closing its end will cause read() to return -1)
            var input = exchange.getRequestBody();
            byte[] buf = new byte[4096];
            while (running.get()) {
                int n = input.read(buf);
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
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
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

        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        // Read the request body
        byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        // Acknowledge receipt
        String response = "accepted";
        exchange.sendResponseHeaders(202, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }

        exchange.close();

        // Forward the message to the handler
        if (messageHandler != null && !body.isEmpty()) {
            messageHandler.accept(body);
        }
    }

    // ---------------------------------------------------------------
    // SSE connection helper
    // ---------------------------------------------------------------

    /**
     * Wraps an SSE connection's output stream.
     */
    private static class SseConnection {
        private final String sessionId;
        private final OutputStream output;
        private volatile boolean closed;

        SseConnection(String sessionId, OutputStream output) {
            this.sessionId = sessionId;
            this.output = output;
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
            output.close();
        }

        String sessionId() {
            return sessionId;
        }
    }
}
