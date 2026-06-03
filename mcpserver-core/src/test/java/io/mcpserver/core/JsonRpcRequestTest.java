package io.mcpserver.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.JsonRpcRequest;
import io.mcpserver.core.transport.JsonRpcSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link JsonRpcRequest}.
 *
 * <p>Covers construction, default values, serialization, and record behavior.</p>
 */
@DisplayName("JsonRpcRequest Unit Tests")
class JsonRpcRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("Constructor with Default jsonrpc Version")
    class DefaultVersionConstructor {

        @Test
        @DisplayName("Should default jsonrpc to 2.0")
        void shouldDefaultJsonRpcTo2_0() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", null);

            assertThat(request.jsonrpc()).isEqualTo("2.0");
            assertThat(request.id()).isEqualTo(1);
            assertThat(request.method()).isEqualTo("initialize");
            assertThat(request.params()).isNull();
        }

        @Test
        @DisplayName("Should accept all parameters including custom jsonrpc")
        void shouldAcceptCustomJsonRpcVersion() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("key", "value");

            JsonRpcRequest request = new JsonRpcRequest(
                    42,
                    "tools/call",
                    params,
                    "2.0"
            );

            assertThat(request.id()).isEqualTo(42);
            assertThat(request.method()).isEqualTo("tools/call");
            assertThat(request.params()).isEqualTo(params);
            assertThat(request.jsonrpc()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("Should handle zero id")
        void shouldHandleZeroId() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(0, "method", null);
            assertThat(request.id()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle negative id")
        void shouldHandleNegativeId() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(-1, "method", null);
            assertThat(request.id()).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should handle large id")
        void shouldHandleLargeId() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(Long.MAX_VALUE, "method", null);
            assertThat(request.id()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("Component Accessors")
    class ComponentAccessors {

        @Test
        @DisplayName("Should return correct id")
        void shouldReturnId() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(123, "method", null);
            assertThat(request.id()).isEqualTo(123);
        }

        @Test
        @DisplayName("Should return correct method")
        void shouldReturnMethod() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "tools/list", null);
            assertThat(request.method()).isEqualTo("tools/list");
        }

        @Test
        @DisplayName("Should return correct params")
        void shouldReturnParams() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "my-tool");
            JsonRpcRequest request = new JsonRpcRequest(1, "tools/call", params);
            assertThat(request.params()).isEqualTo(params);
        }

        @Test
        @DisplayName("Should return null params when null provided")
        void shouldReturnNullParams() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", null);
            assertThat(request.params()).isNull();
        }

        @Test
        @DisplayName("Should return jsonrpc version")
        void shouldReturnJsonRpc() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "method", null);
            assertThat(request.jsonrpc()).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        private final JsonRpcSerializer serializer = JsonRpcSerializer.INSTANCE;

        @Test
        @DisplayName("Should serialize request with params to JSON")
        void shouldSerializeRequestWithParams() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "search");
            params.put("query", "test");

            JsonRpcRequest request = new JsonRpcRequest(1, "tools/call", params);
            String json = serializer.serialize(request);

            JsonNode parsed = mapper.readTree(json);
            assertThat(parsed.get("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(parsed.get("id").asLong()).isEqualTo(1);
            assertThat(parsed.get("method").asText()).isEqualTo("tools/call");
            assertThat(parsed.get("params").get("name").asText()).isEqualTo("search");
            assertThat(parsed.get("params").get("query").asText()).isEqualTo("test");
        }

        @Test
        @DisplayName("Should serialize request without params (null excluded)")
        void shouldSerializeRequestWithoutParams() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", null);
            String json = serializer.serialize(request);

            JsonNode parsed = mapper.readTree(json);
            assertThat(parsed.get("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(parsed.get("id").asLong()).isEqualTo(1);
            assertThat(parsed.get("method").asText()).isEqualTo("initialize");
            assertThat(parsed.has("params")).isFalse();
        }

        @Test
        @DisplayName("Should serialize request with complex params")
        void shouldSerializeRequestWithComplexParams() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            ObjectNode nested = params.putObject("nested");
            nested.put("key", "value");
            params.putArray("items").add("one").add("two");

            JsonRpcRequest request = new JsonRpcRequest(42, "complex/method", params);
            String json = serializer.serialize(request);

            JsonNode parsed = mapper.readTree(json);
            assertThat(parsed.get("params").get("nested").get("key").asText()).isEqualTo("value");
            assertThat(parsed.get("params").get("items").size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should include all required JSON-RPC fields")
        void shouldIncludeAllRequiredFields() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "method", null);
            String json = serializer.serialize(request);

            assertThat(json).contains("\"jsonrpc\"");
            assertThat(json).contains("\"id\"");
            assertThat(json).contains("\"method\"");
        }

        @Test
        @DisplayName("Should produce valid JSON-RPC 2.0 structure")
        void shouldProduceValidJsonRpcStructure() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("protocolVersion", "2025-03-26");

            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", params);
            String json = serializer.serialize(request);

            // Parse and validate structure
            JsonNode parsed = mapper.readTree(json);
            assertThat(parsed.isObject()).isTrue();
            assertThat(parsed.has("jsonrpc")).isTrue();
            assertThat(parsed.has("id")).isTrue();
            assertThat(parsed.has("method")).isTrue();
            assertThat(parsed.get("jsonrpc").asText()).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("Record Contract")
    class RecordContractTests {

        @Test
        @DisplayName("Should be equal to request with same components")
        void shouldBeEqualToEquivalentRequest() throws java.io.IOException {
            ObjectNode params1 = JsonNodeFactory.instance.objectNode();
            params1.put("key", "value");
            ObjectNode params2 = JsonNodeFactory.instance.objectNode();
            params2.put("key", "value");

            JsonRpcRequest req1 = new JsonRpcRequest(1, "method", params1);
            JsonRpcRequest req2 = new JsonRpcRequest(1, "method", params2);

            assertThat(req1).isEqualTo(req2);
        }

        @Test
        @DisplayName("Should not be equal to request with different id")
        void shouldNotBeEqualWithDifferentId() throws java.io.IOException {
            JsonRpcRequest req1 = new JsonRpcRequest(1, "method", null);
            JsonRpcRequest req2 = new JsonRpcRequest(2, "method", null);

            assertThat(req1).isNotEqualTo(req2);
        }

        @Test
        @DisplayName("Should not be equal to request with different method")
        void shouldNotBeEqualWithDifferentMethod() throws java.io.IOException {
            JsonRpcRequest req1 = new JsonRpcRequest(1, "method1", null);
            JsonRpcRequest req2 = new JsonRpcRequest(1, "method2", null);

            assertThat(req1).isNotEqualTo(req2);
        }

        @Test
        @DisplayName("Should not be equal to request with different params")
        void shouldNotBeEqualWithDifferentParams() throws java.io.IOException {
            ObjectNode params1 = JsonNodeFactory.instance.objectNode();
            params1.put("key", "value1");
            ObjectNode params2 = JsonNodeFactory.instance.objectNode();
            params2.put("key", "value2");

            JsonRpcRequest req1 = new JsonRpcRequest(1, "method", params1);
            JsonRpcRequest req2 = new JsonRpcRequest(1, "method", params2);

            assertThat(req1).isNotEqualTo(req2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "method", null);
            assertThat(request).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should have consistent hashCode")
        void shouldHaveConsistentHashCode() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("key", "value");

            JsonRpcRequest req1 = new JsonRpcRequest(1, "method", params);
            JsonRpcRequest req2 = new JsonRpcRequest(1, "method", params);

            assertThat(req1.hashCode()).isEqualTo(req2.hashCode());
            assertThat(req1.hashCode()).isEqualTo(req1.hashCode()); // Consistency
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(42, "test-method", null);
            String str = request.toString();

            assertThat(str).contains("42");
            assertThat(str).contains("test-method");
            assertThat(str).contains("jsonrpc");
        }
    }

    @Nested
    @DisplayName("Various MCP Methods")
    class McpMethodTests {

        @Test
        @DisplayName("Should support initialize method")
        void shouldSupportInitializeMethod() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(1, "initialize", null);
            assertThat(request.method()).isEqualTo("initialize");
        }

        @Test
        @DisplayName("Should support tools/list method")
        void shouldSupportToolsListMethod() throws java.io.IOException {
            JsonRpcRequest request = new JsonRpcRequest(2, "tools/list", null);
            assertThat(request.method()).isEqualTo("tools/list");
        }

        @Test
        @DisplayName("Should support tools/call method")
        void shouldSupportToolsCallMethod() throws java.io.IOException {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "my-tool");
            JsonRpcRequest request = new JsonRpcRequest(3, "tools/call", params);
            assertThat(request.method()).isEqualTo("tools/call");
            assertThat(request.params().get("name").asText()).isEqualTo("my-tool");
        }
    }
}
