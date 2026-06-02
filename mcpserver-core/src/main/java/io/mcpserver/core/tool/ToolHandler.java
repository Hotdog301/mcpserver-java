package io.mcpserver.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Functional interface for handling a tool invocation.
 *
 * <p>Implementations receive the arguments provided by the client (as a
 * {@link JsonNode}) and must return a {@link JsonNode} representing the
 * result. The result structure is up to the implementor but should be
 * compatible with the MCP tool result contract (e.g., wrapped into an
 * {@code McpToolResult} by the calling infrastructure).</p>
 *
 * <p>This is a {@link FunctionalInterface} and may be used as a lambda or
 * method reference.</p>
 */
@FunctionalInterface
public interface ToolHandler {

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the arguments provided by the client; may be
     *                  {@code null} or a JSON object/array depending on
     *                  the tool's input schema
     * @return a {@link JsonNode} representing the result of the execution
     */
    JsonNode execute(JsonNode arguments);
}
