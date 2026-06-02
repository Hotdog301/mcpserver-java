package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a client request to invoke a tool on the MCP server.
 *
 * <p>Contains the tool name and the arguments to pass to the tool.
 * The absence of the {@code id} field distinguishes this from a
 * full {@link JsonRpcRequest}; it is embedded as the {@code params}
 * portion of a tool call request.</p>
 *
 * @param name      The name of the tool to invoke.
 * @param arguments The arguments to pass to the tool (nullable).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolCall(
    String name,
    JsonNode arguments
) {
}
