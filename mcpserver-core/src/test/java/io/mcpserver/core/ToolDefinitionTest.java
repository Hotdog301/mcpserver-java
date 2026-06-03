package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.McpTool;
import io.mcpserver.core.tool.ToolDefinition;
import io.mcpserver.core.tool.ToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for {@link ToolDefinition}.
 *
 * <p>Covers constructor validation, getter methods, {@code toMcpTool()},
 * and {@code equals}/hashCode contract.</p>
 */
@DisplayName("ToolDefinition Unit Tests")
class ToolDefinitionTest {

    private ToolHandler mockHandler;
    private ObjectNode sampleSchema;

    @BeforeEach
    void setUp() {
        mockHandler = mock(ToolHandler.class);
        sampleSchema = JsonNodeFactory.instance.objectNode();
        sampleSchema.put("type", "object");
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should create ToolDefinition with all parameters")
        void shouldCreateWithAllParameters() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "A test tool description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def.name()).isEqualTo("test-tool");
            assertThat(def.description()).isEqualTo("A test tool description");
            assertThat(def.inputSchema()).isEqualTo(sampleSchema);
            assertThat(def.handler()).isSameAs(mockHandler);
        }

        @Test
        @DisplayName("Should create ToolDefinition with null description")
        void shouldCreateWithNullDescription() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    null,
                    sampleSchema,
                    mockHandler
            );

            assertThat(def.name()).isEqualTo("test-tool");
            assertThat(def.description()).isNull();
            assertThat(def.inputSchema()).isEqualTo(sampleSchema);
        }

        @Test
        @DisplayName("Should create ToolDefinition with null inputSchema")
        void shouldCreateWithNullInputSchema() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "A test tool",
                    null,
                    mockHandler
            );

            assertThat(def.name()).isEqualTo("test-tool");
            assertThat(def.description()).isEqualTo("A test tool");
            assertThat(def.inputSchema()).isNull();
        }

        @Test
        @DisplayName("Should create ToolDefinition with both optional parameters null")
        void shouldCreateWithBothOptionalNull() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    null,
                    null,
                    mockHandler
            );

            assertThat(def.name()).isEqualTo("test-tool");
            assertThat(def.description()).isNull();
            assertThat(def.inputSchema()).isNull();
        }

        @Test
        @DisplayName("Should throw NullPointerException when name is null")
        void shouldThrowWhenNameIsNull() {
            assertThatThrownBy(() -> new ToolDefinition(
                            null,
                            "description",
                    sampleSchema,
                    mockHandler
                    ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("name must not be null");
        }

        @Test
        @DisplayName("Should throw NullPointerException when handler is null")
        void shouldThrowWhenHandlerIsNull() {
            assertThatThrownBy(() -> new ToolDefinition(
                            "test-tool",
                            "description",
                    sampleSchema,
                    null
                    ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("handler must not be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is blank")
        void shouldThrowWhenNameIsBlank() {
            assertThatThrownBy(() -> new ToolDefinition(
                            "   ",
                            "description",
                    sampleSchema,
                    mockHandler
                    ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is empty string")
        void shouldThrowWhenNameIsEmpty() {
            assertThatThrownBy(() -> new ToolDefinition(
                            "",
                            "description",
                    sampleSchema,
                    mockHandler
                    ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is only whitespace characters")
        void shouldThrowWhenNameIsWhitespace() {
            assertThatThrownBy(() -> new ToolDefinition(
                            "\t\n\r",
                            "description",
                    sampleSchema,
                    mockHandler
                    ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }
    }

    @Nested
    @DisplayName("toMcpTool() Method")
    class ToMcpToolMethod {

        @Test
        @DisplayName("Should convert to McpTool with all fields")
        void shouldConvertToMcpToolWithAllFields() {
            ToolDefinition def = new ToolDefinition(
                    "my-tool",
                    "My tool description",
                    sampleSchema,
                    mockHandler
            );

            McpTool tool = def.toMcpTool();

            assertThat(tool.name()).isEqualTo("my-tool");
            assertThat(tool.description()).isEqualTo("My tool description");
            assertThat(tool.inputSchema()).isEqualTo(sampleSchema);
        }

        @Test
        @DisplayName("Should convert to McpTool with null description")
        void shouldConvertToMcpToolWithNullDescription() {
            ToolDefinition def = new ToolDefinition(
                    "my-tool",
                    null,
                    sampleSchema,
                    mockHandler
            );

            McpTool tool = def.toMcpTool();

            assertThat(tool.name()).isEqualTo("my-tool");
            assertThat(tool.description()).isNull();
            assertThat(tool.inputSchema()).isEqualTo(sampleSchema);
        }

        @Test
        @DisplayName("Should convert to McpTool with null inputSchema")
        void shouldConvertToMcpToolWithNullInputSchema() {
            ToolDefinition def = new ToolDefinition(
                    "my-tool",
                    "A description",
                    null,
                    mockHandler
            );

            McpTool tool = def.toMcpTool();

            assertThat(tool.name()).isEqualTo("my-tool");
            assertThat(tool.description()).isEqualTo("A description");
            assertThat(tool.inputSchema()).isNull();
        }

        @Test
        @DisplayName("Should return a new McpTool instance each time")
        void shouldReturnNewInstanceEachTime() {
            ToolDefinition def = new ToolDefinition(
                    "my-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            McpTool tool1 = def.toMcpTool();
            McpTool tool2 = def.toMcpTool();

            assertThat(tool1).isNotSameAs(tool2);
            assertThat(tool1).isEqualTo(tool2);
        }
    }

    @Nested
    @DisplayName("equals() and hashCode() Contract")
    class EqualsAndHashCodeContract {

        @Test
        @DisplayName("Should be equal to self")
        void shouldEqualSelf() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def).isEqualTo(def);
        }

        @Test
        @DisplayName("Should be equal when names match (ignoring other fields)")
        void shouldBeEqualWhenNamesMatch() {
            ToolHandler handler2 = mock(ToolHandler.class);
            ObjectNode schema2 = JsonNodeFactory.instance.objectNode();
            schema2.put("type", "array"); // Different schema

            ToolDefinition def1 = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );
            ToolDefinition def2 = new ToolDefinition(
                    "test-tool",
                    "different description",
                    schema2,
                    handler2
            );

            assertThat(def1).isEqualTo(def2);
            assertThat(def1.hashCode()).isEqualTo(def2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when names differ")
        void shouldNotBeEqualWhenNamesDiffer() {
            ToolDefinition def1 = new ToolDefinition(
                    "tool-one",
                    "description",
                    sampleSchema,
                    mockHandler
            );
            ToolDefinition def2 = new ToolDefinition(
                    "tool-two",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def1).isNotEqualTo(def2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def).isNotEqualTo("test-tool");
            assertThat(def).isNotEqualTo(new Object());
        }

        @Test
        @DisplayName("Should have consistent hashCode for equal objects")
        void shouldHaveConsistentHashCode() {
            ToolHandler handler2 = mock(ToolHandler.class);

            ToolDefinition def1 = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );
            ToolDefinition def2 = new ToolDefinition(
                    "test-tool",
                    "different description",
                    null,
                    handler2
            );

            int hashCode1 = def1.hashCode();
            int hashCode2 = def2.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
            // Call hashCode multiple times to verify consistency
            assertThat(def1.hashCode()).isEqualTo(hashCode1);
            assertThat(def2.hashCode()).isEqualTo(hashCode2);
        }
    }

    @Nested
    @DisplayName("toString() Method")
    class ToStringMethod {

        @Test
        @DisplayName("Should return formatted string with tool name")
        void shouldReturnFormattedString() {
            ToolDefinition def = new ToolDefinition(
                    "my-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            String result = def.toString();

            assertThat(result).isEqualTo("ToolDefinition{name='my-tool'}");
        }

        @Test
        @DisplayName("Should include name in string representation")
        void shouldIncludeNameInString() {
            ToolDefinition def = new ToolDefinition(
                    "search-tool",
                    "A search tool",
                    null,
                    mockHandler
            );

            assertThat(def.toString()).contains("search-tool");
        }
    }

    @Nested
    @DisplayName("Handler Execution Delegation")
    class HandlerDelegation {

        @Test
        @DisplayName("Should return the same handler reference")
        void shouldReturnSameHandlerReference() {
            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            assertThat(def.handler()).isSameAs(mockHandler);
            assertThat(def.handler()).isSameAs(mockHandler);
        }

        @Test
        @DisplayName("Handler should be callable through ToolDefinition")
        void handlerShouldBeCallable() {
            JsonNode expectedResult = JsonNodeFactory.instance.textNode("result");
            JsonNode inputArgs = JsonNodeFactory.instance.objectNode();

            when(mockHandler.execute(inputArgs)).thenReturn(expectedResult);

            ToolDefinition def = new ToolDefinition(
                    "test-tool",
                    "description",
                    sampleSchema,
                    mockHandler
            );

            JsonNode result = def.handler().execute(inputArgs);

            assertThat(result).isEqualTo(expectedResult);
            verify(mockHandler).execute(inputArgs);
        }
    }
}
