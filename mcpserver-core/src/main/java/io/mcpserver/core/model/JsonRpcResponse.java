package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A JSON-RPC 2.0 Response object.
 *
 * <p>Represents a reply to a previously issued {@link JsonRpcRequest}.
 * Contains either a {@code result} (on success) or an {@code error}
 * (on failure), but never both.</p>
 *
 * @param id      The request identifier this response is correlated with.
 * @param result  The result value (nullable). Present only on success.
 * @param error   The error detail (nullable). Present only on failure.
 * @param jsonrpc The JSON-RPC version string, always {@code "2.0"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
    long id,
    JsonNode result,
    JsonRpcError error,
    @JsonProperty("jsonrpc") String jsonrpc
) implements JsonRpcMessage {

    /**
     * Creates a successful JSON-RPC 2.0 Response.
     *
     * @param id     the request identifier
     * @param result the result value
     * @return a response with the result populated
     */
    public static JsonRpcResponse success(long id, JsonNode result) {
        return new JsonRpcResponse(id, result, null, "2.0");
    }

    /**
     * Creates an error JSON-RPC 2.0 Response.
     *
     * @param id    the request identifier
     * @param error the error detail
     * @return a response with the error populated
     */
    public static JsonRpcResponse error(long id, JsonRpcError error) {
        return new JsonRpcResponse(id, null, error, "2.0");
    }

    /**
     * Convenience constructor defaulting the JSON-RPC version to {@code "2.0"}.
     *
     * @param id     the request identifier
     * @param result the result value (may be {@code null})
     * @param error  the error detail (may be {@code null})
     */
    public JsonRpcResponse(long id, JsonNode result, JsonRpcError error) {
        this(id, result, error, "2.0");
    }
}
