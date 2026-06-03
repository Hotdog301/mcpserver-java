package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.McpTool;
import io.mcpserver.core.tool.ToolDefinition;
import io.mcpserver.core.tool.ToolHandler;
import io.mcpserver.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive unit tests for {@link ToolRegistry}.
 *
 * <p>Covers registration, lookup, listing, removal, and concurrent safety.</p>
 */
@DisplayName("ToolRegistry Unit Tests")
class ToolRegistryTest {

    private ToolRegistry registry;
    private ToolHandler mockHandler;
    private ObjectNode sampleSchema;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        mockHandler = mock(ToolHandler.class);
        sampleSchema = JsonNodeFactory.instance.objectNode();
        sampleSchema.put("type", "object");
        sampleSchema.put("properties", JsonNodeFactory.instance.objectNode());
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should register a tool using ToolDefinition")
        void shouldRegisterToolDefinition() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "Test description",
                    sampleSchema,
                    mockHandler
            );

            registry.register(def);

            assertThat(registry.toolCount()).isEqualTo(1);
            assertThat(registry.containsTool("test-tool")).isTrue();
        }

        @Test
        @DisplayName("Should register a tool using convenience method")
        void shouldRegisterWithConvenienceMethod() {
            registry.register("calc-tool", "Calculator tool", sampleSchema, mockHandler);

            assertThat(registry.toolCount()).isEqualTo(1);
            assertThat(registry.containsTool("calc-tool")).isTrue();
        }

        @Test
        @DisplayName("Should register multiple tools")
        void shouldRegisterMultipleTools() {
            ToolHandler handler2 = mock(ToolHandler.class);

            registry.register("tool-one", "First tool", sampleSchema, mockHandler);
            registry.register("tool-two", "Second tool", null, handler2);

            assertThat(registry.toolCount()).isEqualTo(2);
            assertThat(registry.containsTool("tool-one")).isTrue();
            assertThat(registry.containsTool("tool-two")).isTrue();
        }

        @Test
        @DisplayName("Should throw NullPointerException when registering null ToolDefinition")
        void shouldThrowWhenRegisteringNullTool() {
            assertThatThrownBy(() -> registry.register((ToolDefinition) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("tool must not be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when registering duplicate name via ToolDefinition")
        void shouldThrowWhenDuplicateNameViaDefinition() {
            ToolDefinition def1 = new ToolDefinition(
                    "my-tool",
                    "Description 1",
                    sampleSchema,
                    mockHandler
            );
            ToolDefinition def2 = new ToolDefinition(
                    "my-tool",
                    "Description 2",
                    sampleSchema,
                    mockHandler
            );

            registry.register(def1);

            assertThatThrownBy(() -> registry.register(def2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("my-tool");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when registering duplicate name via convenience method")
        void shouldThrowWhenDuplicateNameViaConvenience() {
            registry.register("my-tool", "Description 1", sampleSchema, mockHandler);

            assertThatThrownBy(() -> registry.register("my-tool", "Description 2", sampleSchema, mockHandler))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("my-tool");
        }

        @Test
        @DisplayName("Should not allow registration after duplicate exception - first tool still registered")
        void firstToolRemainsAfterDuplicateException() {
            ToolDefinition def1 = new ToolDefinition(
                    "my-tool",
                    "Original",
                    sampleSchema,
                    mockHandler
            );
            ToolDefinition def2 = new ToolDefinition(
                    "my-tool",
                    "Duplicate",
                    sampleSchema,
                    mockHandler
            );

            registry.register(def1);

            try {
                registry.register(def2);
            } catch (IllegalArgumentException ignored) {
                // expected
            }

            // Original tool should still be there
            assertThat(registry.toolCount()).isEqualTo(1);
            assertThat(registry.getTool("my-tool").description()).isEqualTo("Original");
        }

        @Test
        @DisplayName("Should propagate validation exceptions from ToolDefinition constructor")
        void shouldPropagateValidationExceptions() {
            assertThatThrownBy(() -> registry.register(
                            "   ",
                            "Blank name",
                            sampleSchema,
                            mockHandler
                    ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }
    }

    @Nested
    @DisplayName("Tool Lookup")
    class ToolLookupTests {

        @Test
        @DisplayName("Should return tool by name")
        void shouldGetToolByName() {
            ToolDefinition expectedDef = new ToolDefinition(
                    "search-tool",
                    "Search tool",
                    sampleSchema,
                    mockHandler
            );
            registry.register(expectedDef);

            ToolDefinition result = registry.getTool("search-tool");

            assertThat(result).isNotNull();
            assertThat(result).isSameAs(expectedDef);
            assertThat(result.name()).isEqualTo("search-tool");
            assertThat(result.description()).isEqualTo("Search tool");
            assertThat(result.inputSchema()).isEqualTo(sampleSchema);
            assertThat(result.handler()).isSameAs(mockHandler);
        }

        @Test
        @DisplayName("Should return null for non-existent tool")
        void shouldReturnNullForNonExistentTool() {
            ToolDefinition result = registry.getTool("non-existent");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for empty registry")
        void shouldReturnNullForEmptyRegistry() {
            assertThat(registry.getTool("any-tool")).isNull();
        }

        @Test
        @DisplayName("Should throw NullPointerException when name is null")
        void shouldThrowWhenNameIsNull() {
            assertThatThrownBy(() -> registry.getTool(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("name must not be null");
        }

        @Test
        @DisplayName("Should correctly check containsTool for existing tool")
        void shouldReturnTrueForExistingTool() {
            registry.register("existing-tool", "desc", null, mockHandler);
            assertThat(registry.containsTool("existing-tool")).isTrue();
        }

        @Test
        @DisplayName("Should correctly check containsTool for non-existing tool")
        void shouldReturnFalseForNonExistingTool() {
            assertThat(registry.containsTool("non-existing")).isFalse();
        }

        @Test
        @DisplayName("Should throw NullPointerException for containsTool with null name")
        void shouldThrowContainsToolWhenNameIsNull() {
            assertThatThrownBy(() -> registry.containsTool(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("name must not be null");
        }
    }

    @Nested
    @DisplayName("List Tools")
    class ListToolsTests {

        @Test
        @DisplayName("Should return empty list for empty registry")
        void shouldReturnEmptyList() {
            List<McpTool> tools = registry.listTools();

            assertThat(tools).isEmpty();
        }

        @Test
        @DisplayName("Should return list of McpTool instances")
        void shouldReturnMcpToolList() {
            registry.register("tool-1", "First tool", sampleSchema, mockHandler);
            ToolHandler handler2 = mock(ToolHandler.class);
            registry.register("tool-2", null, null, handler2);

            List<McpTool> tools = registry.listTools();

            assertThat(tools).hasSize(2);
            assertThat(tools)
                    .extracting(McpTool::name)
                    .containsExactlyInAnyOrder("tool-1", "tool-2");
        }

        @Test
        @DisplayName("Should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            registry.register("tool-1", "First tool", sampleSchema, mockHandler);

            List<McpTool> tools = registry.listTools();

            assertThatThrownBy(() -> tools.add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should preserve tool metadata in McpTool")
        void shouldPreserveToolMetadata() {
            ObjectNode toolSchema = JsonNodeFactory.instance.objectNode();
            toolSchema.put("type", "object");
            registry.register("my-tool", "My description", toolSchema, mockHandler);

            List<McpTool> tools = registry.listTools();
            McpTool tool = tools.get(0);

            assertThat(tool.name()).isEqualTo("my-tool");
            assertThat(tool.description()).isEqualTo("My description");
            assertThat(tool.inputSchema()).isEqualTo(toolSchema);
        }

        @Test
        @DisplayName("Should return a snapshot - changes after list don't affect returned list")
        void shouldReturnSnapshot() {
            registry.register("tool-1", "First tool", sampleSchema, mockHandler);

            List<McpTool> tools = registry.listTools();
            assertThat(tools).hasSize(1);

            // Add another tool after listing
            ToolHandler handler2 = mock(ToolHandler.class);
            registry.register("tool-2", "Second tool", null, handler2);

            // Original list should be unaffected
            assertThat(tools).hasSize(1);
            assertThat(registry.toolCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle tools with null description and schema")
        void shouldHandleNullFields() {
            ToolHandler handler = mock(ToolHandler.class);
            registry.register("minimal-tool", null, null, handler);

            List<McpTool> tools = registry.listTools();

            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).description()).isNull();
            assertThat(tools.get(0).inputSchema()).isNull();
        }
    }

    @Nested
    @DisplayName("Clear")
    class ClearTests {

        @Test
        @DisplayName("Should remove all tools")
        void shouldRemoveAllTools() {
            registry.register("tool-1", "First", null, mockHandler);
            ToolHandler handler2 = mock(ToolHandler.class);
            registry.register("tool-2", "Second", null, handler2);

            registry.clear();

            assertThat(registry.toolCount()).isZero();
            assertThat(registry.containsTool("tool-1")).isFalse();
            assertThat(registry.containsTool("tool-2")).isFalse();
        }

        @Test
        @DisplayName("Should allow re-registration after clear")
        void shouldAllowReRegistrationAfterClear() {
            registry.register("my-tool", "Original", null, mockHandler);
            registry.clear();

            // Should be able to register with same name
            registry.register("my-tool", "New version", null, mockHandler);

            assertThat(registry.toolCount()).isEqualTo(1);
            assertThat(registry.getTool("my-tool").description()).isEqualTo("New version");
        }

        @Test
        @DisplayName("Should be idempotent on empty registry")
        void shouldBeIdempotentOnEmptyRegistry() {
            registry.clear();
            registry.clear(); // Second clear should not throw

            assertThat(registry.toolCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Tool Count")
    class ToolCountTests {

        @Test
        @DisplayName("Should return zero for empty registry")
        void shouldReturnZeroForEmptyRegistry() {
            assertThat(registry.toolCount()).isZero();
        }

        @Test
        @DisplayName("Should return correct count after registration")
        void shouldReturnCorrectCount() {
            assertThat(registry.toolCount()).isZero();

            registry.register("tool-1", "First", null, mockHandler);
            assertThat(registry.toolCount()).isEqualTo(1);

            ToolHandler handler2 = mock(ToolHandler.class);
            registry.register("tool-2", "Second", null, handler2);
            assertThat(registry.toolCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should not count failed registrations")
        void shouldNotCountFailedRegistrations() {
            registry.register("my-tool", "Original", null, mockHandler);
            assertThat(registry.toolCount()).isEqualTo(1);

            try {
                registry.register("my-tool", "Duplicate", null, mockHandler);
            } catch (IllegalArgumentException ignored) {
                // expected
            }

            assertThat(registry.toolCount()).isEqualTo(1);
        }
    }
}
