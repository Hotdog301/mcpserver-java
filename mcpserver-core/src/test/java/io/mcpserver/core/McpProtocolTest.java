package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.McpConstants;
import io.mcpserver.core.tool.ToolHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MCP Protocol Functional Tests")
class McpProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private McpServer server;
    private ByteArrayOutputStream capturedOutput;
    private final PrintStream originalOut = System.out;
    private Method processMessageMethod;

    @BeforeEach
    void setUp() {
        capturedOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOutput));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        if (server != null) {
            server.stop();
        }
    }

    private static void enableTransport(McpServer s) {
        try {
            Field t = McpServer.class.getDeclaredField("transport");
            t.setAccessible(true);
            Object tr = t.get(s);
            Field r = tr.getClass().getDeclaredField("running");
            r.setAccessible(true);
            r.setBoolean(tr, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Method getProcessMessageMethod() throws Exception {
        Method m = McpServer.class.getDeclaredMethod("processMessage", String.class);
        m.setAccessible(true);
        return m;
    }

    private void send(String json) throws Exception {
        capturedOutput.reset();
        processMessageMethod.invoke(server, json);
    }

    private JsonNode readResponse() throws Exception {
        String output = capturedOutput.toString().trim();
        return output.isEmpty() ? null : mapper.readTree(output);
    }

    private String jsonRpcRequest(long id, String method) {
        return jsonRpcRequest(id, method, null);
    }

    private String jsonRpcRequest(long id, String method, ObjectNode params) {
        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("jsonrpc", "2.0");
            node.put("id", id);
            node.put("method", method);
            if (params != null) {
                node.set("params", params);
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String jsonRpcNotification(String method) {
        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("jsonrpc", "2.0");
            node.put("method", method);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initServer(String name, String version) throws Exception {
        server = new McpServer(name, version);
        enableTransport(server);
        processMessageMethod = getProcessMessageMethod();
    }

    // ── initialize ──

    @Nested
    @DisplayName("initialize")
    class InitializeTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("TestServer", "1.0.0");
        }

        @Test
        @DisplayName("Should respond with protocol version and server info")
        void shouldInitializeWithFullParams() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("protocolVersion", McpConstants.MCP_PROTOCOL_VERSION);
            params.set("capabilities", JsonNodeFactory.instance.objectNode());
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "TestClient");
            clientInfo.put("version", "2.0.0");

            send(jsonRpcRequest(1, McpConstants.METHOD_INITIALIZE, params));
            JsonNode response = readResponse();

            assertThat(response).isNotNull();
            assertThat(response.get("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(response.get("id").asLong()).isEqualTo(1);
            assertThat(response.has("result")).isTrue();

            JsonNode result = response.get("result");
            assertThat(result.get("protocolVersion").asText()).isEqualTo(McpConstants.MCP_PROTOCOL_VERSION);
            assertThat(result.get("serverInfo").get("name").asText()).isEqualTo("TestServer");
            assertThat(result.get("serverInfo").get("version").asText()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("Should handle initialize with null params")
        void shouldInitializeWithNullParams() throws Exception {
            send(jsonRpcRequest(2, McpConstants.METHOD_INITIALIZE));
            JsonNode response = readResponse();

            assertThat(response).isNotNull();
            assertThat(response.get("id").asLong()).isEqualTo(2);
            assertThat(response.has("result")).isTrue();
        }

        @Test
        @DisplayName("Should include capabilities in initialize response")
        void shouldIncludeCapabilities() throws Exception {
            send(jsonRpcRequest(3, McpConstants.METHOD_INITIALIZE));
            JsonNode response = readResponse();

            JsonNode result = response.get("result");
            assertThat(result.has("capabilities")).isTrue();
            assertThat(result.get("capabilities").has("tools")).isTrue();
        }
    }

    // ── notifications/initialized ──

    @Nested
    @DisplayName("notifications/initialized")
    class NotificationsTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("TestServer", "1.0.0");
        }

        @Test
        @DisplayName("Should not produce a response for initialized notification")
        void shouldNotRespondToInitializedNotification() throws Exception {
            send(jsonRpcNotification(McpConstants.METHOD_NOTIFICATIONS_INITIALIZED));
            JsonNode response = readResponse();
            assertThat(response).isNull();
        }
    }

    // ── tools/list ──

    @Nested
    @DisplayName("tools/list")
    class ToolsListTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("TestServer", "1.0.0");
        }

        @Test
        @DisplayName("Should return empty tools list")
        void shouldReturnEmptyList() throws Exception {
            send(jsonRpcRequest(10, McpConstants.METHOD_TOOLS_LIST));
            JsonNode response = readResponse();

            assertThat(response).isNotNull();
            assertThat(response.get("id").asLong()).isEqualTo(10);
            JsonNode tools = response.get("result").get("tools");
            assertThat(tools.isArray()).isTrue();
            assertThat(tools.size()).isZero();
        }

        @Test
        @DisplayName("Should return registered tools with metadata")
        void shouldReturnRegisteredTools() throws Exception {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            schema.putObject("properties").putObject("query").put("type", "string");

            ToolHandler handler = args -> JsonNodeFactory.instance.textNode("ok");
            server.registerTool("search", "Search the web", schema, handler);

            send(jsonRpcRequest(11, McpConstants.METHOD_TOOLS_LIST));
            JsonNode response = readResponse();

            JsonNode tools = response.get("result").get("tools");
            assertThat(tools.size()).isEqualTo(1);
            assertThat(tools.get(0).get("name").asText()).isEqualTo("search");
            assertThat(tools.get(0).get("description").asText()).isEqualTo("Search the web");
            assertThat(tools.get(0).has("inputSchema")).isTrue();
        }

        @Test
        @DisplayName("Should handle tool with null description")
        void shouldHandleToolWithNullDescription() throws Exception {
            ToolHandler handler = args -> JsonNodeFactory.instance.textNode("ok");
            server.registerTool("minimal", null, null, handler);

            send(jsonRpcRequest(12, McpConstants.METHOD_TOOLS_LIST));
            JsonNode response = readResponse();

            JsonNode tools = response.get("result").get("tools");
            assertThat(tools.size()).isEqualTo(1);
            assertThat(tools.get(0).get("name").asText()).isEqualTo("minimal");
            assertThat(tools.get(0).has("description")).isFalse();
        }
    }

    // ── tools/call ──

    @Nested
    @DisplayName("tools/call")
    class ToolsCallTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("TestServer", "1.0.0");
        }

        @Test
        @DisplayName("Should invoke tool and return result")
        void shouldInvokeToolSuccessfully() throws Exception {
            ToolHandler handler = args -> {
                ObjectNode r = JsonNodeFactory.instance.objectNode();
                r.put("status", "success");
                if (args != null && args.has("query")) {
                    r.put("query", args.get("query").asText());
                }
                return r;
            };
            server.registerTool("search", "Search", null, handler);

            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "search");
            ObjectNode args = params.putObject("arguments");
            args.put("query", "hello");

            send(jsonRpcRequest(20, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.get("id").asLong()).isEqualTo(20);
            assertThat(response.has("result")).isTrue();
            JsonNode result = response.get("result");
            assertThat(result.get("content").isArray()).isTrue();
            assertThat(result.get("content").size()).isEqualTo(1);
            assertThat(result.get("content").get(0).get("type").asText()).isEqualTo("text");
            assertThat(result.get("isError").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Should return error for unknown tool")
        void shouldReturnErrorForUnknownTool() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "nonexistent");

            send(jsonRpcRequest(21, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.get("id").asLong()).isEqualTo(21);
            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
            assertThat(response.get("error").get("message").asText()).contains("Unknown tool");
        }

        @Test
        @DisplayName("Should return error when name missing")
        void shouldReturnErrorWhenNameMissing() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();

            send(jsonRpcRequest(22, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
            assertThat(response.get("error").get("message").asText()).contains("name");
        }

        @Test
        @DisplayName("Should return error when params null")
        void shouldReturnErrorWhenParamsNull() throws Exception {
            send(jsonRpcRequest(23, McpConstants.METHOD_TOOLS_CALL));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }

        @Test
        @DisplayName("Should wrap tool exception as error")
        void shouldWrapToolExceptionAsError() throws Exception {
            ToolHandler handler = args -> { throw new RuntimeException("fail"); };
            server.registerTool("fail", "Fails", null, handler);

            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "fail");

            send(jsonRpcRequest(24, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32603);
        }
    }

    // ── Error handling ──

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("TestServer", "1.0.0");
        }

        @Test
        @DisplayName("Should return Method not found for unknown method")
        void shouldReturnMethodNotFound() throws Exception {
            send(jsonRpcRequest(99, "unknown/method"));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
            assertThat(response.get("error").get("message").asText()).contains("Method not found");
        }

        @Test
        @DisplayName("Should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() throws Exception {
            processMessageMethod.invoke(server, "{ invalid json }");
        }

        @Test
        @DisplayName("Should handle empty message gracefully")
        void shouldHandleEmptyMessage() throws Exception {
            processMessageMethod.invoke(server, "");
        }
    }

    // ── Full session ──

    // ── Resources ──

    @Nested
    @DisplayName("resources")
    class ResourceTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("ResourceServer", "1.0.0");
        }

        @Test
        @DisplayName("Should list registered resources")
        void shouldListResources() throws Exception {
            server.registerResource("file:///docs/readme", "README",
                    "Project readme", "text/markdown",
                    args -> JsonNodeFactory.instance.textNode("# MCP Server"));

            send(jsonRpcRequest(1, McpConstants.METHOD_RESOURCES_LIST));
            JsonNode response = readResponse();

            assertThat(response.has("result")).isTrue();
            JsonNode resources = response.get("result").get("resources");
            assertThat(resources).isNotNull();
            assertThat(resources.size()).isEqualTo(1);
            assertThat(resources.get(0).get("uri").asText()).isEqualTo("file:///docs/readme");
            assertThat(resources.get(0).get("name").asText()).isEqualTo("README");
            assertThat(resources.get(0).get("description").asText()).isEqualTo("Project readme");
            assertThat(resources.get(0).get("mimeType").asText()).isEqualTo("text/markdown");
        }

        @Test
        @DisplayName("Should read registered resource")
        void shouldReadResource() throws Exception {
            server.registerResource("file:///docs/readme", "README",
                    null, null,
                    args -> JsonNodeFactory.instance.textNode("Hello Resource!"));

            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("uri", "file:///docs/readme");
            send(jsonRpcRequest(2, McpConstants.METHOD_RESOURCES_READ, params));
            JsonNode response = readResponse();

            assertThat(response.has("result")).isTrue();
            assertThat(response.get("result").get("contents").isArray()).isTrue();
        }

        @Test
        @DisplayName("Should return error for unknown resource")
        void shouldReturnErrorForUnknownResource() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("uri", "file:///unknown");
            send(jsonRpcRequest(3, McpConstants.METHOD_RESOURCES_READ, params));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }

        @Test
        @DisplayName("Should return error when uri is missing")
        void shouldReturnErrorWhenUriMissing() throws Exception {
            send(jsonRpcRequest(4, McpConstants.METHOD_RESOURCES_READ));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }
    }

    // ── Prompts ──

    @Nested
    @DisplayName("prompts")
    class PromptTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("PromptServer", "1.0.0");
        }

        @Test
        @DisplayName("Should list registered prompts")
        void shouldListPrompts() throws Exception {
            server.registerPrompt("greeting", "Generates a greeting",
                    args -> {
                        ArrayNode messages = JsonNodeFactory.instance.arrayNode();
                        ObjectNode msg = messages.addObject();
                        msg.put("role", "assistant");
                        msg.put("content", "Hello!");
                        return messages;
                    });

            send(jsonRpcRequest(1, McpConstants.METHOD_PROMPTS_LIST));
            JsonNode response = readResponse();

            assertThat(response.has("result")).isTrue();
            JsonNode prompts = response.get("result").get("prompts");
            assertThat(prompts).isNotNull();
            assertThat(prompts.size()).isEqualTo(1);
            assertThat(prompts.get(0).get("name").asText()).isEqualTo("greeting");
        }

        @Test
        @DisplayName("Should get registered prompt")
        void shouldGetPrompt() throws Exception {
            server.registerPrompt("greeting", null,
                    args -> {
                        ArrayNode messages = JsonNodeFactory.instance.arrayNode();
                        ObjectNode msg = messages.addObject();
                        msg.put("role", "assistant");
                        msg.put("content", "Hello!");
                        return messages;
                    });

            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "greeting");
            send(jsonRpcRequest(2, McpConstants.METHOD_PROMPTS_GET, params));
            JsonNode response = readResponse();

            assertThat(response.has("result")).isTrue();
            assertThat(response.get("result").get("messages").isArray()).isTrue();
        }

        @Test
        @DisplayName("Should return error for unknown prompt")
        void shouldReturnErrorForUnknownPrompt() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "nonexistent");
            send(jsonRpcRequest(3, McpConstants.METHOD_PROMPTS_GET, params));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }

        @Test
        @DisplayName("Should return error when name is missing")
        void shouldReturnErrorWhenNameMissing() throws Exception {
            send(jsonRpcRequest(4, McpConstants.METHOD_PROMPTS_GET));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }
    }

    // ── Ping ──

    @Nested
    @DisplayName("ping")
    class PingTests {

        @BeforeEach
        void setUpServer() throws Exception {
            initServer("PingServer", "1.0.0");
        }

        @Test
        @DisplayName("Should respond to ping with success")
        void shouldRespondToPing() throws Exception {
            send(jsonRpcRequest(1, McpConstants.METHOD_PING));
            JsonNode response = readResponse();

            assertThat(response.has("result")).isTrue();
            assertThat(response.get("id").asLong()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Full Session Flow")
    class FullSessionTests {

        @Test
        @DisplayName("Complete session: init -> list -> call -> error")
        void completeSessionFlow() throws Exception {
            initServer("FullFlowServer", "1.0.0");

            ToolHandler echo = args -> {
                if (args != null && args.has("message")) {
                    return JsonNodeFactory.instance.textNode("echo: " + args.get("message").asText());
                }
                return JsonNodeFactory.instance.textNode("echo: (empty)");
            };
            server.registerTool("echo", "Echo", null, echo);

            // 1. Initialize
            ObjectNode initParams = JsonNodeFactory.instance.objectNode();
            initParams.put("protocolVersion", McpConstants.MCP_PROTOCOL_VERSION);
            initParams.set("capabilities", JsonNodeFactory.instance.objectNode());
            initParams.set("clientInfo", JsonNodeFactory.instance.objectNode());
            send(jsonRpcRequest(1, McpConstants.METHOD_INITIALIZE, initParams));
            assertThat(readResponse().get("result").get("serverInfo").get("name").asText())
                    .isEqualTo("FullFlowServer");

            // 2. Notification
            send(jsonRpcNotification(McpConstants.METHOD_NOTIFICATIONS_INITIALIZED));

            // 3. List tools
            send(jsonRpcRequest(2, McpConstants.METHOD_TOOLS_LIST));
            assertThat(readResponse().get("result").get("tools").size()).isEqualTo(1);

            // 4. Call tool
            ObjectNode callParams = JsonNodeFactory.instance.objectNode();
            callParams.put("name", "echo");
            ObjectNode callArgs = callParams.putObject("arguments");
            callArgs.put("message", "Hello");
            send(jsonRpcRequest(3, McpConstants.METHOD_TOOLS_CALL, callParams));
            assertThat(readResponse().get("result").get("isError").asBoolean()).isFalse();

            // 5. Error
            ObjectNode badParams = JsonNodeFactory.instance.objectNode();
            badParams.put("name", "nope");
            send(jsonRpcRequest(4, McpConstants.METHOD_TOOLS_CALL, badParams));
            assertThat(readResponse().has("error")).isTrue();
        }
    }
}
