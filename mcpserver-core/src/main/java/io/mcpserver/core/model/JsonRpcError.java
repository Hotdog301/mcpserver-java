package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A JSON-RPC 2.0 Error object.
 *
 * <p>Represents an error that occurred while processing a request.
 * Contains a numeric error code, a human-readable message, and optional
 * additional data.</p>
 *
 * <p>Standard JSON-RPC error codes (from the specification):</p>
 * <ul>
 *   <li>{@code -32700} – Parse error</li>
 *   <li>{@code -32600} – Invalid Request</li>
 *   <li>{@code -32601} – Method not found</li>
 *   <li>{@code -32602} – Invalid params</li>
 *   <li>{@code -32603} – Internal error</li>
 *   <li>{@code -32000} to {@code -32099} – Server error</li>
 * </ul>
 *
 * @param code    A number that indicates the error type that occurred.
 * @param message A short description of the error.
 * @param data    Optional primitive or structured value carrying additional
 *                error information (nullable).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(
    int code,
    String message,
    JsonNode data
) {

    // ---------- Standard JSON-RPC error factory methods ----------

    /** Parse error code (-32700). */
    public static final int PARSE_ERROR = -32700;
    /** Invalid Request code (-32600). */
    public static final int INVALID_REQUEST = -32600;
    /** Method not found code (-32601). */
    public static final int METHOD_NOT_FOUND = -32601;
    /** Invalid params code (-32602). */
    public static final int INVALID_PARAMS = -32602;
    /** Internal error code (-32603). */
    public static final int INTERNAL_ERROR = -32603;

    /**
     * Creates a parse error with the default message.
     *
     * @return a new {@link JsonRpcError} with code -32700
     */
    public static JsonRpcError parseError() {
        return new JsonRpcError(PARSE_ERROR, "Parse error", null);
    }

    /**
     * Creates an invalid request error with the default message.
     *
     * @return a new {@link JsonRpcError} with code -32600
     */
    public static JsonRpcError invalidRequest() {
        return new JsonRpcError(INVALID_REQUEST, "Invalid Request", null);
    }

    /**
     * Creates a method-not-found error with the default message.
     *
     * @return a new {@link JsonRpcError} with code -32601
     */
    public static JsonRpcError methodNotFound() {
        return new JsonRpcError(METHOD_NOT_FOUND, "Method not found", null);
    }

    /**
     * Creates an invalid-params error with the default message.
     *
     * @return a new {@link JsonRpcError} with code -32602
     */
    public static JsonRpcError invalidParams() {
        return new JsonRpcError(INVALID_PARAMS, "Invalid params", null);
    }

    /**
     * Creates an internal error with the default message.
     *
     * @return a new {@link JsonRpcError} with code -32603
     */
    public static JsonRpcError internalError() {
        return new JsonRpcError(INTERNAL_ERROR, "Internal error", null);
    }

    /**
     * Creates an internal error with a custom message and optional data.
     *
     * @param message the error description
     * @param data    optional additional data (may be {@code null})
     * @return a new {@link JsonRpcError} with code -32603
     */
    public static JsonRpcError internalError(String message, JsonNode data) {
        return new JsonRpcError(INTERNAL_ERROR, message, data);
    }
}
