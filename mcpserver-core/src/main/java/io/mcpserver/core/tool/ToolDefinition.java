package io.mcpserver.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.mcpserver.core.model.McpTool;

import java.util.Objects;

/**
 * A registered tool definition that pairs metadata with an executable handler.
 *
 * <p>Instances of this class describe a tool that the MCP server exposes to
 * clients. Each definition carries the tool's name, an optional description,
 * an optional JSON Schema for its input parameters, and a {@link ToolHandler}
 * that performs the actual work when the tool is invoked.</p>
 *
 * <p>Use {@link #toMcpTool()} to obtain the protocol-level representation
 * ({@link McpTool}) for inclusion in capability negotiation messages.</p>
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final ToolHandler handler;

    /**
     * Creates a new tool definition.
     *
     * @param name        the tool name (must not be blank)
     * @param description an optional human-readable description; may be {@code null}
     * @param inputSchema an optional JSON Schema describing the expected input;
     *                    may be {@code null}
     * @param handler     the handler that executes this tool (must not be {@code null})
     * @throws NullPointerException     if {@code name} or {@code handler} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public ToolDefinition(String name, String description, JsonNode inputSchema, ToolHandler handler) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    /**
     * Returns the tool name.
     *
     * @return the tool name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the optional human-readable description.
     *
     * @return the description, or {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Returns the optional JSON Schema describing the expected input parameters.
     *
     * @return the input schema, or {@code null}
     */
    public JsonNode inputSchema() {
        return inputSchema;
    }

    /**
     * Returns the handler that executes this tool.
     *
     * @return the handler (never {@code null})
     */
    public ToolHandler handler() {
        return handler;
    }

    /**
     * Converts this definition into a protocol-level {@link McpTool} for use
     * in MCP capability announcements.
     *
     * @return a new {@link McpTool} instance derived from this definition
     */
    public McpTool toMcpTool() {
        return new McpTool(name, description, inputSchema);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolDefinition that)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "ToolDefinition{name='" + name + "'}";
    }
}
