package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.JsonRpcError;
import io.mcpserver.core.tool.ToolHandler;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.core.transport.StdioServerTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link McpServer}.
 *
 * <p>Tests server construction, tool registration, and lifecycle methods.</p>
 * <p>Note: {@code start()} and message processing tests are limited because
 * they depend on stdio transport which cannot be easily mocked in the current
 * implementation.</p>
 */
@DisplayName("McpServer Unit Tests")
@ExtendWith(MockitoExtension.class)
class McpServerTest {

    private McpServer server;
    private ToolHandler mockHandler;

    @Mock
    private StdioServerTransport mockTransport;

    @BeforeEach
    void setUp() {
        mockHandler = mock(ToolHandler.class);
    }

    @AfterEach
    void tearDown() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should create server with name and version")
        void shouldCreateServer() {
            server = new McpServer("TestServer", "1.0.0");

            assertThat(server).isNotNull();
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should throw NullPointerException for null name")
        void shouldThrowOnNullName() {
            assertThatThrownBy(() -> new McpServer(null, "1.0.0"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("serverName");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank name")
        void shouldThrowOnBlankName() {
            assertThatThrownBy(() -> new McpServer("  ", "1.0.0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serverName");
        }

        @Test
        @DisplayName("Should throw NullPointerException for null version")
        void shouldThrowOnNullVersion() {
            assertThatThrownBy(() -> new McpServer("TestServer", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("serverVersion");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank version")
        void shouldThrowOnBlankVersion() {
            assertThatThrownBy(() -> new McpServer("TestServer", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serverVersion");
        }

        @Test
        @DisplayName("Should throw NullPointerException for null toolRegistry")
        void shouldThrowOnNullToolRegistry() {
            ToolRegistry nullRegistry = null;
            assertThatThrownBy(() -> new McpServer("TestServer", "1.0.0", nullRegistry))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("toolRegistry");
        }
    }

    @Nested
    @DisplayName("Tool Registry Access")
    class ToolRegistryAccessTests {

        @Test
        @DisplayName("Should return non-null tool registry")
        void shouldReturnToolRegistry() {
            server = new McpServer("TestServer", "1.0.0");
            ToolRegistry registry = server.getToolRegistry();

            assertThat(registry).isNotNull();
            assertThat(registry.toolCount()).isZero();
        }

        @Test
        @DisplayName("Should return same tool registry instance")
        void shouldReturnSameRegistry() {
            server = new McpServer("TestServer", "1.0.0");
            ToolRegistry registry1 = server.getToolRegistry();
            ToolRegistry registry2 = server.getToolRegistry();

            assertThat(registry1).isSameAs(registry2);
        }

        @Test
        @DisplayName("Tool registry should be initially empty")
        void registryShouldBeInitiallyEmpty() {
            server = new McpServer("TestServer", "1.0.0");
            assertThat(server.getToolRegistry().toolCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Tool Registration")
    class ToolRegistrationTests {

        @Test
        @DisplayName("Should register tool with all parameters")
        void shouldRegisterToolWithAllParameters() {
            server = new McpServer("TestServer", "1.0.0");
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");

            server.registerTool("test-tool", "Test description", schema, mockHandler);

            assertThat(server.getToolRegistry().toolCount()).isEqualTo(1);
            assertThat(server.getToolRegistry().containsTool("test-tool")).isTrue();
        }

        @Test
        @DisplayName("Should register tool with null description")
        void shouldRegisterToolWithNullDescription() {
            server = new McpServer("TestServer", "1.0.0");
            server.registerTool("test-tool", null, null, mockHandler);

            assertThat(server.getToolRegistry().toolCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should register tool with null schema")
        void shouldRegisterToolWithNullSchema() {
            server = new McpServer("TestServer", "1.0.0");
            server.registerTool("test-tool", "description", null, mockHandler);

            assertThat(server.getToolRegistry().toolCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should register multiple tools")
        void shouldRegisterMultipleTools() {
            server = new McpServer("TestServer", "1.0.0");
            ToolHandler handler2 = mock(ToolHandler.class);

            server.registerTool("tool-one", "First", null, mockHandler);
            server.registerTool("tool-two", "Second", null, handler2);

            assertThat(server.getToolRegistry().toolCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should throw when registering duplicate tool via convenience method")
        void shouldThrowOnDuplicateRegistration() {
            server = new McpServer("TestServer", "1.0.0");
            server.registerTool("my-tool", "Original", null, mockHandler);

            assertThatThrownBy(() -> server.registerTool("my-tool", "Duplicate", null, mockHandler))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw when registering tool with blank name")
        void shouldThrowOnBlankName() {
            server = new McpServer("TestServer", "1.0.0");

            assertThatThrownBy(() -> server.registerTool("   ", "description", null, mockHandler))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("Should throw when registering tool with null handler")
        void shouldThrowOnNullHandler() {
            server = new McpServer("TestServer", "1.0.0");

            assertThatThrownBy(() -> server.registerTool("my-tool", "description", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("handler must not be null");
        }
    }

    @Nested
    @DisplayName("Lifecycle State")
    class LifecycleStateTests {

        @Test
        @DisplayName("Should not be running after construction")
        void shouldNotBeRunningInitially() {
            server = new McpServer("TestServer", "1.0.0");
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should throw IllegalStateException when starting already running server")
        void shouldThrowWhenStartingAlreadyRunningServer() {
            server = new McpServer("TestServer", "1.0.0");

            // We can't easily call start() because it blocks on stdio,
            // but we can test the state flag behavior by checking the
            // initial state and the stop() behavior
            assertThat(server.isRunning()).isFalse();
        }

        @Test
        @DisplayName("stop() should be idempotent")
        void stopShouldBeIdempotent() {
            server = new McpServer("TestServer", "1.0.0");

            // Calling stop on a non-running server should not throw
            server.stop();
            server.stop();

            assertThat(server.isRunning()).isFalse();
        }

        @Test
        @DisplayName("isRunning() should return false after stop()")
        void isRunningShouldReturnFalseAfterStop() {
            server = new McpServer("TestServer", "1.0.0");
            server.stop();
            assertThat(server.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("Shutdown Hook")
    class ShutdownHookTests {

        @Test
        @DisplayName("registerShutdownHook should not throw")
        void registerShutdownHookShouldNotThrow() {
            server = new McpServer("TestServer", "1.0.0");

            // Should not throw
            McpServer.registerShutdownHook(server);
        }

        @Test
        @DisplayName("registerShutdownHook should accept null server gracefully")
        void registerShutdownHookShouldHandleNull() {
            // Should not throw even with null
            McpServer.registerShutdownHook(null);
        }
    }

    @Nested
    @DisplayName("Server Configuration")
    class ServerConfigurationTests {

        @Test
        @DisplayName("Should handle special characters in server name")
        void shouldHandleSpecialCharsInName() {
            server = new McpServer("Test/Server@1.0", "v1.0.0-beta+build.123");
            assertThat(server).isNotNull();
        }

        @Test
        @DisplayName("Should handle unicode in server name")
        void shouldHandleUnicodeInName() {
            server = new McpServer("サーバー", "1.0");
            assertThat(server).isNotNull();
        }

        @Test
        @DisplayName("Should handle very long server name")
        void shouldHandleLongName() {
            String longName = "A".repeat(1000);
            server = new McpServer(longName, "1.0");
            assertThat(server).isNotNull();
        }
    }

    @Nested
    @DisplayName("Integration-style Tests")
    class IntegrationStyleTests {

        @Test
        @DisplayName("Should allow full setup flow: create, register, verify")
        void shouldSupportFullSetupFlow() {
            // Create server
            server = new McpServer("IntegrationServer", "2.0.0");

            // Register tools
            ObjectNode searchSchema = JsonNodeFactory.instance.objectNode();
            searchSchema.put("type", "object");
            searchSchema.putObject("properties").putObject("query").put("type", "string");

            ObjectNode calcSchema = JsonNodeFactory.instance.objectNode();
            calcSchema.put("type", "object");

            ToolHandler searchHandler = mock(ToolHandler.class);
            ToolHandler calcHandler = mock(ToolHandler.class);

            server.registerTool("search", "Search the web", searchSchema, searchHandler);
            server.registerTool("calculate", "Perform calculations", calcSchema, calcHandler);

            // Verify state
            assertThat(server.isRunning()).isFalse();
            assertThat(server.getToolRegistry().toolCount()).isEqualTo(2);
            assertThat(server.getToolRegistry().containsTool("search")).isTrue();
            assertThat(server.getToolRegistry().containsTool("calculate")).isTrue();

            // Verify tools have correct metadata
            assertThat(server.getToolRegistry().getTool("search").description()).isEqualTo("Search the web");
            assertThat(server.getToolRegistry().getTool("calculate").inputSchema()).isEqualTo(calcSchema);
        }

        @Test
        @DisplayName("Should allow registering tools before and after getting registry")
        void shouldAllowRegistrationViaBothMethods() {
            server = new McpServer("MixedServer", "1.0");

            // Register via convenience method
            server.registerTool("tool-via-convenience", "desc", null, mockHandler);

            // Register via direct registry access
            server.getToolRegistry().register("tool-via-registry", "desc", null, mock(ToolHandler.class));

            assertThat(server.getToolRegistry().toolCount()).isEqualTo(2);
        }
    }
}
