package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Parameters for the MCP {@code initialize} result.
 *
 * <p>Returned by the server to the client as the response to the
 * {@code initialize} request. Contains the server's protocol version,
 * capabilities, metadata, and optional instructions.</p>
 *
 * @param protocolVersion The MCP protocol version the server supports.
 * @param capabilities    The capabilities advertised by the server.
 * @param serverInfo      Metadata about the server (e.g., name, version).
 * @param instructions    Optional human-readable instructions for the client
 *                        (nullable).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeResultParams(
    @JsonProperty("protocolVersion") String protocolVersion,
    McpCapabilities capabilities,
    @JsonProperty("serverInfo") Map<String, String> serverInfo,
    String instructions
) {
}
