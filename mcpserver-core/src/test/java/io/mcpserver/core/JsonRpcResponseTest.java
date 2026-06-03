package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.JsonRpcError;
import io.mcpserver.core.model.JsonRpcResponse;
import io.mcpserver.core.transport.JsonRpcSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link JsonRpcResponse}.
 *
 * <p>Covers construction, factory methods, serialization, and record behavior.</p>
 */
@DisplayName("JsonRpcResponse Unit Tests")
class JsonRpcResponseTest {

    private final JsonRpcSerializer serializer = JsonRpcSerializer.INSTANCE;

    @Nested
    @DisplayName("Factory Method: success()")
    class SuccessFactoryMethod {

        @Test
        @DisplayName("Should create success response with result")
        void shouldCreateSuccessResponse() {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("status", "ok");

            JsonRpcResponse response = JsonRpcResponse.success(1, result);

            assertThat(response.id()).isEqualTo(1);
            assertThat(response.result()).isEqualTo(result);
            assertThat(response.error()).isNull();
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("Should create success response with null result")
        void shouldCreateSuccessResponseWithNullResult() {
            JsonRpcResponse response = JsonRpcResponse.success(1, null);

            assertThat(response.id()).isEqualTo(1);
            assertThat(response.result()).isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("Should set jsonrpc to 2.0")
        void shouldSetJsonRpcVersion() {
            JsonRpcResponse response = JsonRpcResponse.success(1, null);
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("Factory Method: error()")
    class ErrorFactoryMethod {

        @Test
        @DisplayName("Should create error response with error details")
        void shouldCreateErrorResponse() {
            JsonRpcError error = new JsonRpcError(-32601, "Method not found", null);

            JsonRpcResponse response = JsonRpcResponse.error(1, error);

            assertThat(response.id()).isEqualTo(1);
            assertThat(response.result()).isNull();
            assertThat(response.error()).isEqualTo(error);
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("Should create error response with standard error codes")
        void shouldCreateResponseWithStandardErrorCodes() {
            JsonRpcResponse response1 = JsonRpcResponse.error(1, JsonRpcError.parseError());
            assertThat(response1.error().code()).isEqualTo(-32700);

            JsonRpcResponse response2 = JsonRpcResponse.error(2, JsonRpcError.invalidRequest());
            assertThat(response2.error().code()).isEqualTo(-32600);

            JsonRpcResponse response3 = JsonRpcResponse.error(3, JsonRpcError.invalidParams());
            assertThat(response3.error().code()).isEqualTo(-32602);

            JsonRpcResponse response4 = JsonRpcResponse.error(4, JsonRpcError.internalError());
            assertThat(response4.error().code()).isEqualTo(-32603);
        }

        @Test
        @DisplayName("Should create error response with custom error")
        void shouldCreateResponseWithCustomError() {
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("detail", "Some detail");

            JsonRpcError error = JsonRpcError.internalError("Custom error message", data);
            JsonRpcResponse response = JsonRpcResponse.error(1, error);

            assertThat(response.error().code()).isEqualTo(JsonRpcError.INTERNAL_ERROR);
            assertThat(response.error().message()).isEqualTo("Custom error message");
            assertThat(response.error().data()).isEqualTo(data);
        }
    }

    @Nested
    @DisplayName("Direct Constructor")
    class DirectConstructor {

        @Test
        @DisplayName("Should create response with default jsonrpc version")
        void shouldCreateWithDefaultJsonRpc() {
            JsonNode result = JsonNodeFactory.instance.textNode("hello");

            JsonRpcResponse response = new JsonRpcResponse(1, result, null);

            assertThat(response.id()).isEqualTo(1);
            assertThat(response.result()).isEqualTo(result);
            assertThat(response.error()).isNull();
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("Should accept custom jsonrpc version")
        void shouldAcceptCustomJsonRpcVersion() {
            JsonRpcResponse response = new JsonRpcResponse(1, null, null, "2.0");
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("Should allow both result and error to be non-null (malformed)")
        void shouldAllowBothResultAndError() {
            JsonNode result = JsonNodeFactory.instance.textNode("result");
            JsonRpcError error = JsonRpcError.parseError();

            // The record doesn't enforce mutual exclusivity
            JsonRpcResponse response = new JsonRpcResponse(1, result, error, "2.0");

            assertThat(response.result()).isEqualTo(result);
            assertThat(response.error()).isEqualTo(error);
        }

        @Test
        @DisplayName("Should allow both result and error to be null")
        void shouldAllowBothNull() {
            JsonRpcResponse response = new JsonRpcResponse(1, null, null);

            assertThat(response.result()).isNull();
            assertThat(response.error()).isNull();
        }
    }

    @Nested
    @DisplayName("Component Accessors")
    class ComponentAccessors {

        @Test
        @DisplayName("Should return correct id")
        void shouldReturnId() {
            JsonRpcResponse response = JsonRpcResponse.success(42, null);
            assertThat(response.id()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should return correct result")
        void shouldReturnResult() {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("key", "value");
            JsonRpcResponse response = JsonRpcResponse.success(1, result);
            assertThat(response.result()).isEqualTo(result);
        }

        @Test
        @DisplayName("Should return correct error")
        void shouldReturnError() {
            JsonRpcError error = JsonRpcError.parseError();
            JsonRpcResponse response = JsonRpcResponse.error(1, error);
            assertThat(response.error()).isEqualTo(error);
        }

        @Test
        @DisplayName("Should return jsonrpc version")
        void shouldReturnJsonRpc() {
            JsonRpcResponse response = JsonRpcResponse.success(1, null);
            assertThat(response.jsonrpc()).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize success response to JSON")
        void shouldSerializeSuccessResponse() throws Exception {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("tools", JsonNodeFactory.instance.arrayNode());

            JsonRpcResponse response = JsonRpcResponse.success(1, result);
            String json = serializer.serialize(response);

            assertThat(json).contains("\"jsonrpc\":\"2.0\"");
            assertThat(json).contains("\"id\":1");
            assertThat(json).contains("\"result\"");
            assertThat(json).doesNotContain("\"error\"");
        }

        @Test
        @DisplayName("Should serialize error response to JSON")
        void shouldSerializeErrorResponse() throws Exception {
            JsonRpcError error = new JsonRpcError(-32601, "Method not found", null);
            JsonRpcResponse response = JsonRpcResponse.error(1, error);
            String json = serializer.serialize(response);

            assertThat(json).contains("\"jsonrpc\":\"2.0\"");
            assertThat(json).contains("\"id\":1");
            assertThat(json).contains("\"error\"");
            assertThat(json).contains("\"code\":-32601");
            assertThat(json).contains("\"message\":\"Method not found\"");
        }

        @Test
        @DisplayName("Should exclude null result in serialization")
        void shouldExcludeNullResult() throws Exception {
            JsonRpcError error = JsonRpcError.parseError();
            JsonRpcResponse response = new JsonRpcResponse(1, null, error);
            String json = serializer.serialize(response);

            // "result" should not appear since it's null and we use NON_NULL inclusion
            assertThat(json).doesNotContain("\"result\"");
        }

        @Test
        @DisplayName("Should exclude null error in serialization")
        void shouldExcludeNullError() throws Exception {
            JsonNode result = JsonNodeFactory.instance.textNode("ok");
            JsonRpcResponse response = JsonRpcResponse.success(1, result);
            String json = serializer.serialize(response);

            assertThat(json).doesNotContain("\"error\"");
        }

        @Test
        @DisplayName("Should serialize with error data")
        void shouldSerializeWithErrorData() throws Exception {
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("trace", "stack trace here");

            JsonRpcError error = JsonRpcError.internalError("Something failed", data);
            JsonRpcResponse response = JsonRpcResponse.error(1, error);
            String json = serializer.serialize(response);

            assertThat(json).contains("\"data\"");
            assertThat(json).contains("\"trace\":\"stack trace here\"");
        }
    }

    @Nested
    @DisplayName("Record Contract")
    class RecordContractTests {

        @Test
        @DisplayName("Should be equal to response with same components")
        void shouldBeEqualToEquivalentResponse() {
            JsonNode result1 = JsonNodeFactory.instance.textNode("result");
            JsonNode result2 = JsonNodeFactory.instance.textNode("result");

            JsonRpcResponse resp1 = JsonRpcResponse.success(1, result1);
            JsonRpcResponse resp2 = JsonRpcResponse.success(1, result2);

            assertThat(resp1).isEqualTo(resp2);
        }

        @Test
        @DisplayName("Should not be equal to response with different id")
        void shouldNotBeEqualWithDifferentId() {
            JsonRpcResponse resp1 = JsonRpcResponse.success(1, null);
            JsonRpcResponse resp2 = JsonRpcResponse.success(2, null);

            assertThat(resp1).isNotEqualTo(resp2);
        }

        @Test
        @DisplayName("Should not be equal to response with different result")
        void shouldNotBeEqualWithDifferentResult() {
            JsonNode result1 = JsonNodeFactory.instance.textNode("result1");
            JsonNode result2 = JsonNodeFactory.instance.textNode("result2");

            JsonRpcResponse resp1 = JsonRpcResponse.success(1, result1);
            JsonRpcResponse resp2 = JsonRpcResponse.success(1, result2);

            assertThat(resp1).isNotEqualTo(resp2);
        }

        @Test
        @DisplayName("Should not be equal to response with different error")
        void shouldNotBeEqualWithDifferentError() {
            JsonRpcError error1 = JsonRpcError.parseError();
            JsonRpcError error2 = JsonRpcError.invalidRequest();

            JsonRpcResponse resp1 = JsonRpcResponse.error(1, error1);
            JsonRpcResponse resp2 = JsonRpcResponse.error(1, error2);

            assertThat(resp1).isNotEqualTo(resp2);
        }

        @Test
        @DisplayName("Success and error responses should not be equal")
        void successAndErrorShouldNotBeEqual() {
            JsonNode result = JsonNodeFactory.instance.textNode("ok");
            JsonRpcResponse success = JsonRpcResponse.success(1, result);
            JsonRpcResponse error = JsonRpcResponse.error(1, JsonRpcError.parseError());

            assertThat(success).isNotEqualTo(error);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            JsonRpcResponse response = JsonRpcResponse.success(1, null);
            assertThat(response).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should have consistent hashCode")
        void shouldHaveConsistentHashCode() {
            JsonNode result = JsonNodeFactory.instance.textNode("result");

            JsonRpcResponse resp1 = JsonRpcResponse.success(1, result);
            JsonRpcResponse resp2 = JsonRpcResponse.success(1, result);

            assertThat(resp1.hashCode()).isEqualTo(resp2.hashCode());
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            JsonRpcResponse response = JsonRpcResponse.success(42, null);
            String str = response.toString();

            assertThat(str).contains("42");
            assertThat(str).contains("jsonrpc");
        }
    }

    @Nested
    @DisplayName("Implements JsonRpcMessage")
    class JsonRpcMessageTests {

        @Test
        @DisplayName("Should implement JsonRpcMessage interface")
        void shouldImplementJsonRpcMessage() {
            JsonRpcResponse response = JsonRpcResponse.success(1, null);
            assertThat(response).isInstanceOf(io.mcpserver.core.model.JsonRpcMessage.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle zero id")
        void shouldHandleZeroId() {
            JsonRpcResponse response = JsonRpcResponse.success(0, null);
            assertThat(response.id()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle negative id")
        void shouldHandleNegativeId() {
            JsonRpcResponse response = JsonRpcResponse.success(-1, null);
            assertThat(response.id()).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should handle large id")
        void shouldHandleLargeId() {
            JsonRpcResponse response = JsonRpcResponse.success(Long.MAX_VALUE, null);
            assertThat(response.id()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("Should handle complex result structures")
        void shouldHandleComplexResult() {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            ObjectNode nested = result.putObject("nested");
            nested.put("key", "value");
            result.putArray("items").add("one").add("two");

            JsonRpcResponse response = JsonRpcResponse.success(1, result);
            assertThat(response.result()).isEqualTo(result);
            assertThat(response.result().get("nested").get("key").asText()).isEqualTo("value");
            assertThat(response.result().get("items").size()).isEqualTo(2);
        }
    }
}
