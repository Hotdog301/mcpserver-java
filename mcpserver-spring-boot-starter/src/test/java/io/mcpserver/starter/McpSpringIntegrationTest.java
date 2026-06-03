package io.mcpserver.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.McpServer;
import io.mcpserver.core.model.McpConstants;
import io.mcpserver.core.tool.ToolDefinition;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.starter.annotation.McpComponent;
import io.mcpserver.starter.annotation.McpTool;
import io.mcpserver.starter.autoconfigure.McpServerProperties;
import io.mcpserver.starter.tool.McpToolRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Spring Boot starter.
 *
 * <p>Tests: auto-config, @McpTool discovery, tool registration, MCP protocol interaction.</p>
 * <p>Uses spring.main.allow-circular-references=true to handle the ApplicationContext
 * circular dependency in McpToolRegistrar during startup.</p>
 */
@SpringBootTest(classes = McpSpringIntegrationTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "mcpserver.name=IntegrationTestServer",
        "mcpserver.version=1.0.0",
        "spring.main.allow-circular-references=true"
    })
@DisplayName("Spring Boot Starter Integration Tests")
class McpSpringIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private McpServer server;

    @Autowired
    private McpServerProperties properties;

    private ByteArrayOutputStream capturedOutput;
    private final PrintStream originalOut = System.out;
    private Method processMessageMethod;

    @BeforeEach
    void setUpServer() throws Exception {
        capturedOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOutput));

        // Cache reflection
        processMessageMethod = McpServer.class.getDeclaredMethod("processMessage", String.class);
        processMessageMethod.setAccessible(true);

        // Enable transport for stdio testing
        enableTransport(server);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
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

    private void send(String json) throws Exception {
        capturedOutput.reset();
        processMessageMethod.invoke(server, json);
    }

    private JsonNode readResponse() throws Exception {
        String output = capturedOutput.toString().trim();
        // Filter out SLF4J log lines (which go to stdout via console appender)
        // and extract only the JSON-RPC response line
        String jsonLine = output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{") && !line.startsWith("2"))
                .findFirst()
                .orElse(output);
        return jsonLine.isEmpty() ? null : mapper.readTree(jsonLine);
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

    // ── Auto-discovery tests ──

    @Nested
    @DisplayName("Auto-Configuration")
    class AutoConfigTests {

        @Test
        @DisplayName("Should create McpServer bean")
        void shouldCreateMcpServer() {
            assertThat(server).isNotNull();
        }

        @Test
        @DisplayName("Should apply properties from config")
        void shouldApplyProperties() {
            assertThat(properties.getName()).isEqualTo("IntegrationTestServer");
            assertThat(properties.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("Should discover and register all @McpTool methods")
        void shouldDiscoverAllTools() {
            // echo, greet (from EchoTools)
            // add, compute (from MathTools)
            assertThat(server.getToolRegistry().toolCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should register echo tool with correct description")
        void shouldRegisterEchoTool() {
            ToolDefinition def = server.getToolRegistry().getTool("echo");
            assertThat(def).isNotNull();
            assertThat(def.description()).isEqualTo("Echo back the input message");
        }

        @Test
        @DisplayName("Should register compute tool with Map parameter")
        void shouldRegisterComputeTool() {
            assertThat(server.getToolRegistry().getTool("compute")).isNotNull();
        }

        @Test
        @DisplayName("Should register add tool with primitive parameters")
        void shouldRegisterAddTool() {
            assertThat(server.getToolRegistry().getTool("add")).isNotNull();
        }
    }

    // ── Tool invocation via MCP protocol ──

    @Nested
    @DisplayName("Tool Invocation via MCP Protocol")
    class InvocationTests {

        @Test
        @DisplayName("Should call echo tool via tools/call")
        void shouldCallEchoTool() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "echo");
            params.putObject("arguments").put("message", "Hello MCP");

            send(jsonRpcRequest(1, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.get("result").get("isError").asBoolean()).isFalse();
            JsonNode content = response.get("result").get("content").get(0);
            assertThat(content.get("type").asText()).isEqualTo("text");
            assertThat(content.get("text").asText()).isEqualTo("echo: Hello MCP");
        }

        @Test
        @DisplayName("Should call greet tool")
        void shouldCallGreetTool() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "greet");
            params.putObject("arguments").put("name", "Alice");

            send(jsonRpcRequest(2, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            JsonNode content = response.get("result").get("content").get(0);
            assertThat(content.get("text").asText()).isEqualTo("Hello, Alice!");
        }

        @Test
        @DisplayName("Should call compute tool with Map parameter")
        void shouldCallComputeTool() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "compute");
            ObjectNode args = params.putObject("arguments");
            args.put("x", 10);
            args.put("y", 20);

            send(jsonRpcRequest(3, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.get("result").get("isError").asBoolean()).isFalse();
            JsonNode content = response.get("result").get("content").get(0);
            assertThat(content.get("type").asText()).isEqualTo("text");
            JsonNode result = content.get("text");
            assertThat(result.has("sum")).isTrue();
            assertThat(result.get("sum").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should return error for unknown tool")
        void shouldReturnErrorForUnknownTool() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("name", "nonexistent");

            send(jsonRpcRequest(4, McpConstants.METHOD_TOOLS_CALL, params));
            JsonNode response = readResponse();

            assertThat(response.has("error")).isTrue();
            assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
        }
    }

    // ── tools/list ──

    @Nested
    @DisplayName("tools/list")
    class ToolsListTests {

        @Test
        @DisplayName("Should list all discovered tools")
        void shouldListAllTools() throws Exception {
            send(jsonRpcRequest(10, McpConstants.METHOD_TOOLS_LIST, null));
            JsonNode response = readResponse();

            JsonNode tools = response.get("result").get("tools");
            assertThat(tools.size()).isEqualTo(4);
        }

        @Test
        @DisplayName("Tool list should include descriptions")
        void shouldIncludeDescriptions() throws Exception {
            send(jsonRpcRequest(11, McpConstants.METHOD_TOOLS_LIST, null));
            JsonNode response = readResponse();

            JsonNode tools = response.get("result").get("tools");
            JsonNode echoTool = null;
            for (JsonNode tool : tools) {
                if ("echo".equals(tool.get("name").asText())) {
                    echoTool = tool;
                    break;
                }
            }
            assertThat(echoTool).isNotNull();
            assertThat(echoTool.get("description").asText()).isEqualTo("Echo back the input message");
        }
    }

    // ── Initialize ──

    @Nested
    @DisplayName("Initialize handshake")
    class InitializeTests {

        @Test
        @DisplayName("Should return configured server info")
        void shouldReturnConfiguredServerInfo() throws Exception {
            ObjectNode params = JsonNodeFactory.instance.objectNode();
            params.put("protocolVersion", McpConstants.MCP_PROTOCOL_VERSION);
            params.set("capabilities", JsonNodeFactory.instance.objectNode());
            params.set("clientInfo", JsonNodeFactory.instance.objectNode());

            send(jsonRpcRequest(1, McpConstants.METHOD_INITIALIZE, params));
            JsonNode response = readResponse();

            JsonNode result = response.get("result");
            assertThat(result.get("serverInfo").get("name").asText())
                .isEqualTo("IntegrationTestServer");
            assertThat(result.get("serverInfo").get("version").asText())
                .isEqualTo("1.0.0");
        }
    }

    // ── Test application ──

    @org.springframework.boot.autoconfigure.SpringBootApplication
    static class Application {
        // Auto-config handles everything, just define test tool beans
    }

    // ── Test tool beans ──

    @org.springframework.stereotype.Service
    @McpComponent
    static class EchoTools {

        @McpTool(name = "echo", description = "Echo back the input message")
        public String echo(String message) {
            return "echo: " + message;
        }

        @McpTool(name = "greet", description = "Greet someone by name")
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    @org.springframework.stereotype.Service
    @McpComponent
    static class MathTools {

        @McpTool(name = "add", description = "Add two numbers")
        public int add(int a, int b) {
            return a + b;
        }

        @McpTool(name = "compute", description = "Compute from JSON params")
        public Map<String, Object> compute(Map<String, Object> params) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("input", params);
            result.put("sum", ((Number) params.get("x")).intValue()
                + ((Number) params.get("y")).intValue());
            return result;
        }
    }
}
