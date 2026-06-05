package io.mcpserver.core.transport;

import java.io.IOException;

/**
 * Contract for MCP server transport layers.
 *
 * <p>A transport handles the bidirectional flow of JSON-RPC messages between
 * the MCP server and its client. Implementations may use stdio, HTTP+SSE,
 * WebSockets, or any other channel.</p>
 */
public interface Transport {

    /**
     * Starts the transport and begins accepting messages.
     */
    void start();

    /**
     * Stops the transport and releases all resources.
     */
    void stop();

    /**
     * Returns whether the transport is currently running.
     */
    boolean isRunning();

    /**
     * Reads the next JSON-RPC message from the transport.
     *
     * <p>This method blocks until a message is available, the transport is
     * stopped, or the underlying channel is closed.</p>
     *
     * @return the JSON string, or {@code null} if the transport has shut down
     * @throws IOException if an I/O error occurs
     */
    String readLine() throws IOException;

    /**
     * Sends a JSON-RPC response through the transport.
     *
     * @param message the message to send (will be serialized by {@link JsonRpcSerializer})
     * @throws IOException if the send fails
     */
    void sendMessage(Object message) throws IOException;
}
