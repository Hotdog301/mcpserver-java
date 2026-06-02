package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes a tool that the MCP server exposes to clients.
 *
 * <p>A tool has a name, an optional human-readable description, and an
 * optional JSON Schema definition ({@code inputSchema}) that describes the
 * expected parameters.</p>
 *
 * @param name        The name of the tool. Must be unique within the server.
 * @param description An optional description of what the tool does.
 * @param inputSchema An optional JSON Schema object describing the expected
 *                    parameters for this tool. Serialized as {@code "inputSchema"}
 *                    in the JSON wire format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpTool(
    String name,
    String description,
    @JsonProperty("inputSchema") JsonNode inputSchema
) {
}
