package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A JSON-RPC 2.0 Notification object.
 *
 * <p>Notifications are similar to {@link JsonRpcRequest requests} but
 * lack an {@code id} field. The receiver must not reply to a notification.
 * This makes them suitable for one-way messages such as event
 * notifications or logging.</p>
 *
 * @param method  The name of the method to be invoked.
 * @param params  The parameter values (nullable).
 * @param jsonrpc The JSON-RPC version string, always {@code "2.0"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcNotification(
    String method,
    JsonNode params,
    @JsonProperty("jsonrpc") String jsonrpc
) implements JsonRpcMessage {

    /**
     * Creates a JSON-RPC 2.0 Notification with the default protocol version.
     *
     * @param method the method name
     * @param params the parameters (may be {@code null})
     */
    public JsonRpcNotification(String method, JsonNode params) {
        this(method, params, "2.0");
    }
}
