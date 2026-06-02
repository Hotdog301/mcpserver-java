package io.mcpserver.core.transport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.IOException;

/**
 * Jackson-based JSON-RPC message serializer and deserializer.
 *
 * <p>Provides stateless utility methods for converting between JSON-RPC 2.0
 * domain objects (or arbitrary messages) and their JSON string representation.
 * The underlying {@link ObjectMapper} is pre-configured with sensible defaults
 * for MCP wire-format communication.</p>
 *
 * <p>This class is thread-safe and meant to be used as a singleton via the
 * {@link #INSTANCE} field.</p>
 */
public final class JsonRpcSerializer {

    /** Shared singleton instance. */
    public static final JsonRpcSerializer INSTANCE = new JsonRpcSerializer();

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    // prevent instantiation
    private JsonRpcSerializer() {
    }

    /**
     * Serializes the given message (a JSON-RPC request, response, notification,
     * or any other POJO) to its JSON string representation.
     *
     * @param message the message to serialize
     * @return the JSON string
     * @throws IOException if serialization fails
     */
    public String serialize(Object message) throws IOException {
        return MAPPER.writeValueAsString(message);
    }

    /**
     * Deserializes a JSON string into the specified target type.
     *
     * @param json  the JSON string to deserialize
     * @param clazz the target class
     * @param <T>   the target type
     * @return the deserialized instance
     * @throws IOException if deserialization fails
     */
    public <T> T deserialize(String json, Class<T> clazz) throws IOException {
        return MAPPER.readValue(json, clazz);
    }

    /**
     * Parses a JSON string into a raw {@link JsonNode} tree for dispatch
     * without binding to a specific domain class.
     *
     * @param json the JSON string to parse
     * @return the root {@link JsonNode}
     * @throws IOException if the input is not valid JSON
     */
    public JsonNode parseMessage(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    /**
     * Serializes the given object into a {@link JsonNode} tree.
     *
     * <p>This is useful when you need to convert a POJO to a JsonNode
     * without going through an intermediate JSON string.</p>
     *
     * @param value the object to convert
     * @return the resulting {@link JsonNode}
     * @throws IOException if conversion fails
     */
    public JsonNode serializeToNode(Object value) throws IOException {
        return MAPPER.valueToTree(value);
    }

    /**
     * Determines whether the given JSON node represents a JSON-RPC <em>request</em>.
     *
     * <p>A node is considered a request when it contains an {@code "id"} field
     * and does <strong>not</strong> contain either a {@code "result"} or
     * {@code "error"} field.</p>
     *
     * @param node the parsed JSON node (may be {@code null})
     * @return {@code true} if the node is a JSON-RPC request
     */
    public boolean isRequest(JsonNode node) {
        if (node == null) {
            return false;
        }
        return node.has("id")
                && !node.has("result")
                && !node.has("error");
    }

    /**
     * Determines whether the given JSON node represents a JSON-RPC <em>response</em>.
     *
     * <p>A node is considered a response when it contains either a
     * {@code "result"} or an {@code "error"} field.</p>
     *
     * @param node the parsed JSON node (may be {@code null})
     * @return {@code true} if the node is a JSON-RPC response
     */
    public boolean isResponse(JsonNode node) {
        if (node == null) {
            return false;
        }
        return node.has("result") || node.has("error");
    }

    /**
     * Determines whether the given JSON node represents a JSON-RPC
     * <em>notification</em>.
     *
     * <p>A node is considered a notification when it contains a {@code "method"}
     * field but does <strong>not</strong> contain an {@code "id"} field.</p>
     *
     * @param node the parsed JSON node (may be {@code null})
     * @return {@code true} if the node is a JSON-RPC notification
     */
    public boolean isNotification(JsonNode node) {
        if (node == null) {
            return false;
        }
        return node.has("method") && !node.has("id");
    }
}
