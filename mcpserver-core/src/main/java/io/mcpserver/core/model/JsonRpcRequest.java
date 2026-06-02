package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A JSON-RPC 2.0 Request object.
 *
 * <p>Represents a call made to a remote system. Contains an identifier for
 * correlating responses, a method name, and optional parameters.</p>
 *
 * @param id     The request identifier. Must be a non-zero integer.
 * @param method The name of the method to be invoked.
 * @param params The parameter values (nullable). Can be an array or object.
 * @param jsonrpc The JSON-RPC version string, always {@code "2.0"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
    long id,
    String method,
    JsonNode params,
    @JsonProperty("jsonrpc") String jsonrpc
) implements JsonRpcMessage {

    /**
     * Creates a JSON-RPC 2.0 Request with the default protocol version.
     *
     * @param id     the request identifier
     * @param method the method name
     * @param params the parameters (may be {@code null})
     */
    public JsonRpcRequest(long id, String method, JsonNode params) {
        this(id, method, params, "2.0");
    }
}
