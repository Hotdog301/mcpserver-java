package io.mcpserver.core.transport;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages JSON-RPC communication over standard input and standard output streams.
 *
 * <p>This transport reads JSON-RPC messages (one per line) from {@link System#in}
 * and writes serialized JSON responses to {@link System#out}. It is intended for
 * use in subprocess-based MCP server deployments where the parent process
 * communicates via stdin/stdout (e.g., VS Code extensions, CLI launchers).</p>
 *
 * <p>The transport is not thread-safe; calls to {@link #readLine()} and
 * {@link #sendMessage(Object)} should be coordinated externally.</p>
 */
public class StdioServerTransport implements Transport {

    private final BufferedReader reader;
    private volatile boolean running;

    /**
     * Creates a new stdio transport that reads from {@link System#in} and
     * writes to {@link System#out}.
     */
    public StdioServerTransport() {
        this.reader = new BufferedReader(
                new InputStreamReader(noCloseStdin(), StandardCharsets.UTF_8));
    }

    /**
     * Starts the transport, marking it as running and ready to process messages.
     */
    @Override
    public void start() {
        this.running = true;
    }

    /**
     * Stops the transport and releases the underlying reader.
     *
     * <p>After calling this method the transport is considered shut down and
     * should not be used for further communication.</p>
     */
    @Override
    public void stop() {
        this.running = false;
        try {
            reader.close();
        } catch (IOException e) {
            System.err.println("Error closing stdin reader: " + e.getMessage());
        }
    }

    /**
     * Returns a wrapper around {@link System#in} that ignores close requests,
     * so that stopping the transport does not permanently close stdin for the
     * entire JVM (which would break tests, hot-restart, etc.).
     */
    private static InputStream noCloseStdin() {
        return new FilterInputStream(System.in) {
            @Override
            public void close() {
                // Intentionally empty — do not close System.in.
            }
        };
    }

    /**
     * Reads one JSON-RPC message line from standard input.
     *
     * @return the JSON string, or {@code null} if the stream has reached EOF
     * @throws IOException if an I/O error occurs
     */
    @Override
    public String readLine() throws IOException {
        try {
            return reader.readLine();
        } catch (IOException e) {
            if (!running) {
                // transport has been stopped; treat as graceful EOF
                return null;
            }
            throw e;
        }
    }

    /**
     * Serializes the given message to JSON and writes it to standard output,
     * followed by a newline.
     *
     * @param message the message to send (will be serialized via
     *                {@link JsonRpcSerializer})
     * @throws IOException if serialization or write fails
     */
    @Override
    public void sendMessage(Object message) throws IOException {
        if (!running) {
            throw new IOException("Transport not running");
        }
        String json = JsonRpcSerializer.INSTANCE.serialize(message);
        System.out.println(json);
        System.out.flush();
    }

    /**
     * Returns whether the transport is currently running.
     *
     * @return {@code true} if the transport has been started and not yet stopped
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
