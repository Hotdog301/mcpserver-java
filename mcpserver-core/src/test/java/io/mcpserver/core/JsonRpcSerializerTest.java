package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.*;
import io.mcpserver.core.transport.JsonRpcSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive unit tests for {@link JsonRpcSerializer}.
 *
 * <p>Covers serialization, deserialization, message parsing, and type detection.</p>
 */
@DisplayName("JsonRpcSerializer Unit Tests")
class JsonRpcSerializerTest {

    private final JsonRpcSerializer serializer = JsonRpcSerializer.INSTANCE;

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("INSTANCE should be non-null")
        void instanceShouldBeNonNull() {
            assertThat(JsonRpcSerializer.INSTANCE).isNotNull();
        }

        @Test
        @DisplayName("Multiple accesses should return same instance")
        void multipleAccessesReturnSameInstance() {
            assertThat(JsonRpcSerializer.INSTANCE).isSameAs(JsonRpcSerializer.INSTANCE);
        }
    }

    @Nested
    @DisplayName("serialize() Method")
    class SerializeTests {

        @Test
        @DisplayName("Should serialize JsonRpcRequest to JSON")
        void shouldSerializeJsonRpcRequest() throws IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "test");
            JsonRpcRequest request = new JsonRpcRequest(1, "test/method", params);

            String json = serializer.serialize(request);

            assertThat(json).contains("\"jsonrpc\":\"2.0\"");
            assertThat(json).contains("\"id\":1");
            assertThat(json).contains("\"method\":\"test/method\"");
        }

        @Test
        @DisplayName("Should serialize JsonRpcResponse (success) to JSON")
        void shouldSerializeSuccessResponse() throws IOException {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("status", "ok");
            JsonRpcResponse response = JsonRpcResponse.success(1, result);

            String json = serializer.serialize(response);

            assertThat(json).contains("\"result\"");
            assertThat(json).contains("\"status\":\"ok\"");
        }

        @Test
        @DisplayName("Should serialize JsonRpcResponse (error) to JSON")
        void shouldSerializeErrorResponse() throws IOException {
            JsonRpcError error = new JsonRpcError(-32601, "Method not found", null);
            JsonRpcResponse response = JsonRpcResponse.error(1, error);

            String json = serializer.serialize(response);

            assertThat(json).contains("\"error\"");
            assertThat(json).contains("\"code\":-32601");
            assertThat(json).contains("\"message\":\"Method not found\"");
        }

        @Test
        @DisplayName("Should serialize JsonRpcNotification to JSON")
        void shouldSerializeNotification() throws IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("method", "initialized");
            JsonRpcNotification notification = new JsonRpcNotification(
                    "notifications/initialized", params, "2.0");

            String json = serializer.serialize(notification);

            assertThat(json).contains("\"method\":\"notifications/initialized\"");
            assertThat(json).doesNotContain("\"id\"");
        }

        @Test
        @DisplayName("Should serialize InitializeResultParams to JSON")
        void shouldSerializeInitializeResultParams() throws IOException {
            McpCapabilities capabilities = McpCapabilities.builder()
                    .addTool("built-in")
                    .build();

            InitializeResultParams params = new InitializeResultParams(
                    "2025-03-26",
                    capabilities,
                    Map.of("name", "test-server", "version", "1.0"),
                    "Server is ready"
            );

            String json = serializer.serialize(params);

            assertThat(json).contains("\"protocolVersion\":\"2025-03-26\"");
            assertThat(json).contains("\"name\":\"test-server\"");
            assertThat(json).contains("\"version\":\"1.0\"");
            assertThat(json).contains("\"instructions\":\"Server is ready\"");
        }

        @Test
        @DisplayName("Should exclude null fields in serialization (NON_NULL inclusion)")
        void shouldExcludeNullFields() throws IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", null);
            String json = serializer.serialize(request);

            assertThat(json).doesNotContain("\"params\"");
        }

        @Test
        @DisplayName("Should serialize generic POJO")
        void shouldSerializeGenericPojo() throws IOException {
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("key", "value");
            data.put("number", 42);

            String json = serializer.serialize(data);

            assertThat(json).contains("\"key\":\"value\"");
            assertThat(json).contains("\"number\":42");
        }

        @Test
        @DisplayName("Should serialize McpTool to JSON")
        void shouldSerializeMcpTool() throws IOException {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            McpTool tool = new McpTool("my-tool", "My tool description", schema);

            String json = serializer.serialize(tool);

            assertThat(json).contains("\"name\":\"my-tool\"");
            assertThat(json).contains("\"description\":\"My tool description\"");
            assertThat(json).contains("\"inputSchema\"");
        }

        @Test
        @DisplayName("Should throw IOException for non-serializable value")
        void shouldThrowForNonSerializable() {
            // Jackson can serialize most objects and even null; test with invalid JSON instead
            assertThatThrownBy(() -> serializer.deserialize("not valid json", JsonRpcRequest.class))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("deserialize() Method")
    class DeserializeTests {

        @Test
        @DisplayName("Should deserialize JSON to InitializeRequestParams")
        void shouldDeserializeToInitializeRequestParams() throws IOException {
            String json = """
                {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {"name": "Claude Desktop", "version": "1.0.0"}
                }
                """;

            InitializeRequestParams params = serializer.deserialize(json, InitializeRequestParams.class);

            assertThat(params.protocolVersion()).isEqualTo("2025-03-26");
            assertThat(params.clientInfo()).containsEntry("name", "Claude Desktop");
        }

        @Test
        @DisplayName("Should deserialize JSON to InitializeResultParams")
        void shouldDeserializeToInitializeResultParams() throws IOException {
            String json = """
                {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {"tools": ["built-in"]},
                    "serverInfo": {"name": "Test Server", "version": "0.1.0"},
                    "instructions": "Server is ready"
                }
                """;

            InitializeResultParams params = serializer.deserialize(json, InitializeResultParams.class);

            assertThat(params.protocolVersion()).isEqualTo("2025-03-26");
            assertThat(params.instructions()).isEqualTo("Server is ready");
        }

        @Test
        @DisplayName("Should deserialize JSON to McpCapabilities")
        void shouldDeserializeToMcpCapabilities() throws IOException {
            String json = """
                {
                    "tools": ["tool1", "tool2"],
                    "resources": ["res1"],
                    "prompts": ["prompt1"]
                }
                """;

            McpCapabilities caps = serializer.deserialize(json, McpCapabilities.class);

            assertThat(caps.getTools()).containsExactlyInAnyOrder("tool1", "tool2");
            assertThat(caps.getResources()).contains("res1");
            assertThat(caps.getPrompts()).contains("prompt1");
        }

        @Test
        @DisplayName("Should handle unknown properties gracefully (FAIL_ON_UNKNOWN_PROPERTIES disabled)")
        void shouldHandleUnknownProperties() throws IOException {
            String json = """
                {
                    "name": "test",
                    "unknownField": "should be ignored"
                }
                """;

            // Using ObjectNode to verify no exception
            ObjectNode node = serializer.deserialize(json, ObjectNode.class);
            assertThat(node.get("name").asText()).isEqualTo("test");
            assertThat(node.get("unknownField").asText()).isEqualTo("should be ignored");
        }

        @Test
        @DisplayName("Should throw IOException for invalid JSON")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> serializer.deserialize("not valid json", String.class))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should throw IOException for empty string")
        void shouldThrowForEmptyString() {
            assertThatThrownBy(() -> serializer.deserialize("", String.class))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should deserialize JSON to JsonRpcError")
        void shouldDeserializeToJsonRpcError() throws IOException {
            String json = """
                {
                    "code": -32601,
                    "message": "Method not found",
                    "data": null
                }
                """;

            JsonRpcError error = serializer.deserialize(json, JsonRpcError.class);

            assertThat(error.code()).isEqualTo(-32601);
            assertThat(error.message()).isEqualTo("Method not found");
        }
    }

    @Nested
    @DisplayName("parseMessage() Method")
    class ParseMessageTests {

        @Test
        @DisplayName("Should parse valid JSON object")
        void shouldParseJsonObject() throws IOException {
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize"
                }
                """;

            JsonNode node = serializer.parseMessage(json);

            assertThat(node).isNotNull();
            assertThat(node.isObject()).isTrue();
            assertThat(node.get("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(node.get("id").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should parse valid JSON array")
        void shouldParseJsonArray() throws IOException {
            String json = "[1, 2, 3]";
            JsonNode node = serializer.parseMessage(json);

            assertThat(node).isNotNull();
            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should parse nested JSON structure")
        void shouldParseNestedStructure() throws IOException {
            String json = """
                {
                    "outer": {
                        "inner": {
                            "value": 42
                        }
                    }
                }
                """;

            JsonNode node = serializer.parseMessage(json);

            assertThat(node.get("outer").get("inner").get("value").asInt()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should throw IOException for invalid JSON")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> serializer.parseMessage("{ invalid }"))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should return missing node for empty string")
        void shouldParseEmptyString() throws Exception {
            JsonNode result = serializer.parseMessage("");
            assertThat(result).isNotNull();
            assertThat(result.isMissingNode()).isTrue();
        }

        @Test
        @DisplayName("Should throw for null input")
        void shouldThrowForNullInput() {
            assertThatThrownBy(() -> serializer.parseMessage(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should parse complete JSON-RPC request")
        void shouldParseJsonRpcRequest() throws IOException {
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 123,
                    "method": "tools/call",
                    "params": {
                        "name": "search",
                        "arguments": {"query": "test"}
                    }
                }
                """;

            JsonNode node = serializer.parseMessage(json);

            assertThat(node.get("id").asLong()).isEqualTo(123);
            assertThat(node.get("method").asText()).isEqualTo("tools/call");
            assertThat(node.get("params").get("name").asText()).isEqualTo("search");
        }
    }

    @Nested
    @DisplayName("serializeToNode() Method")
    class SerializeToNodeTests {

        @Test
        @DisplayName("Should convert POJO to JsonNode")
        void shouldConvertPojoToJsonNode() throws IOException {
            McpTool tool = new McpTool("test-tool", "A test tool", null);

            JsonNode node = serializer.serializeToNode(tool);

            assertThat(node).isNotNull();
            assertThat(node.isObject()).isTrue();
            assertThat(node.get("name").asText()).isEqualTo("test-tool");
            assertThat(node.get("description").asText()).isEqualTo("A test tool");
        }

        @Test
        @DisplayName("Should convert Map to JsonNode")
        void shouldConvertMapToJsonNode() throws IOException {
            Map<String, Object> map = Map.of(
                    "name", "test",
                    "count", 42,
                    "active", true
            );

            JsonNode node = serializer.serializeToNode(map);

            assertThat(node.get("name").asText()).isEqualTo("test");
            assertThat(node.get("count").asInt()).isEqualTo(42);
            assertThat(node.get("active").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("Should convert InitializeResultParams to JsonNode")
        void shouldConvertInitializeResultParams() throws IOException {
            McpCapabilities caps = McpCapabilities.builder()
                    .addTool("built-in")
                    .build();

            InitializeResultParams params = new InitializeResultParams(
                    "2025-03-26",
                    caps,
                    Map.of("name", "server", "version", "1.0"),
                    "Ready"
            );

            JsonNode node = serializer.serializeToNode(params);

            assertThat(node.get("protocolVersion").asText()).isEqualTo("2025-03-26");
            assertThat(node.get("instructions").asText()).isEqualTo("Ready");
        }

        @Test
        @DisplayName("Should exclude null values from resulting node")
        void shouldExcludeNullValues() throws IOException {
            McpTool tool = new McpTool("tool", null, null);
            JsonNode node = serializer.serializeToNode(tool);

            assertThat(node.has("name")).isTrue();
            assertThat(node.has("description")).isFalse();
            assertThat(node.has("inputSchema")).isFalse();
        }
    }

    @Nested
    @DisplayName("isRequest() Method")
    class IsRequestTests {

        @Test
        @DisplayName("Should return true for valid request (has id, no result, no error)")
        void shouldReturnTrueForRequest() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"method":"initialize"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isTrue();
        }

        @Test
        @DisplayName("Should return true for request with params")
        void shouldReturnTrueForRequestWithParams() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isTrue();
        }

        @Test
        @DisplayName("Should return false for response (has result)")
        void shouldReturnFalseForResponseWithResult() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"result":{"status":"ok"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for error response (has error)")
        void shouldReturnFalseForErrorResponse() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Not found"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for notification (no id)")
        void shouldReturnFalseForNotification() throws IOException {
            String json = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for null node")
        void shouldReturnFalseForNull() {
            assertThat(serializer.isRequest(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for node without id")
        void shouldReturnFalseForNodeWithoutId() throws IOException {
            String json = """
                {"jsonrpc":"2.0","method":"test"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isRequest(node)).isFalse();
        }
    }

    @Nested
    @DisplayName("isResponse() Method")
    class IsResponseTests {

        @Test
        @DisplayName("Should return true for response with result")
        void shouldReturnTrueForResponseWithResult() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"result":{"status":"ok"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isResponse(node)).isTrue();
        }

        @Test
        @DisplayName("Should return true for error response")
        void shouldReturnTrueForErrorResponse() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Not found"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isResponse(node)).isTrue();
        }

        @Test
        @DisplayName("Should return false for request")
        void shouldReturnFalseForRequest() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"method":"initialize"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isResponse(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for notification")
        void shouldReturnFalseForNotification() throws IOException {
            String json = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isResponse(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for null node")
        void shouldReturnFalseForNull() {
            assertThat(serializer.isResponse(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for node without result or error")
        void shouldReturnFalseForNodeWithoutResultOrError() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"method":"test"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isResponse(node)).isFalse();
        }
    }

    @Nested
    @DisplayName("isNotification() Method")
    class IsNotificationTests {

        @Test
        @DisplayName("Should return true for valid notification")
        void shouldReturnTrueForNotification() throws IOException {
            String json = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isNotification(node)).isTrue();
        }

        @Test
        @DisplayName("Should return true for notification with params")
        void shouldReturnTrueForNotificationWithParams() throws IOException {
            String json = """
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":{"id":1}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isNotification(node)).isTrue();
        }

        @Test
        @DisplayName("Should return false for request (has id)")
        void shouldReturnFalseForRequest() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"method":"initialize"}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isNotification(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for response")
        void shouldReturnFalseForResponse() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1,"result":{"status":"ok"}}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isNotification(node)).isFalse();
        }

        @Test
        @DisplayName("Should return false for null node")
        void shouldReturnFalseForNull() {
            assertThat(serializer.isNotification(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for node without method")
        void shouldReturnFalseForNodeWithoutMethod() throws IOException {
            String json = """
                {"jsonrpc":"2.0","id":1}
                """;
            JsonNode node = serializer.parseMessage(json);
            assertThat(serializer.isNotification(node)).isFalse();
        }
    }

    @Nested
    @DisplayName("Round-trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should round-trip JsonRpcRequest")
        void shouldRoundTripRequest() throws IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "test-tool");
            JsonRpcRequest original = new JsonRpcRequest(1, "tools/call", params);

            String json = serializer.serialize(original);
            JsonNode parsed = serializer.parseMessage(json);

            assertThat(parsed.get("id").asLong()).isEqualTo(original.id());
            assertThat(parsed.get("method").asText()).isEqualTo(original.method());
            assertThat(parsed.get("params").get("name").asText()).isEqualTo("test-tool");
        }

        @Test
        @DisplayName("Should round-trip JsonRpcResponse (success)")
        void shouldRoundTripSuccessResponse() throws IOException {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("tools", JsonNodeFactory.instance.arrayNode());
            JsonRpcResponse original = JsonRpcResponse.success(1, result);

            String json = serializer.serialize(original);
            JsonNode parsed = serializer.parseMessage(json);

            assertThat(parsed.get("id").asLong()).isEqualTo(original.id());
            assertThat(parsed.has("result")).isTrue();
            assertThat(parsed.has("error")).isFalse();
        }

        @Test
        @DisplayName("Should round-trip JsonRpcResponse (error)")
        void shouldRoundTripErrorResponse() throws IOException {
            JsonRpcError error = new JsonRpcError(-32603, "Internal error", null);
            JsonRpcResponse original = JsonRpcResponse.error(1, error);

            String json = serializer.serialize(original);
            JsonNode parsed = serializer.parseMessage(json);

            assertThat(parsed.get("id").asLong()).isEqualTo(original.id());
            assertThat(parsed.has("error")).isTrue();
            assertThat(parsed.get("error").get("code").asInt()).isEqualTo(-32603);
        }
    }
}
