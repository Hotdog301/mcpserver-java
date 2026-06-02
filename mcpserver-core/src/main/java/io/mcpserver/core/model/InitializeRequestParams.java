package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Parameters for the MCP {@code initialize} request.
 *
 * <p>Sent by the client to the server at the start of a session to
 * negotiate protocol version and exchange capabilities.</p>
 *
 * @param protocolVersion The MCP protocol version the client supports.
 * @param capabilities    The capabilities advertised by the client.
 * @param clientInfo      Metadata about the client (e.g., name, version).
 * @param serverInfo      Metadata about the server (nullable). In the
 *                        <em>client-to-server</em> direction this is
 *                        typically {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeRequestParams(
    @JsonProperty("protocolVersion") String protocolVersion,
    McpCapabilities capabilities,
    @JsonProperty("clientInfo") Map<String, String> clientInfo,
    @JsonProperty("serverInfo") Map<String, String> serverInfo
) {
}
