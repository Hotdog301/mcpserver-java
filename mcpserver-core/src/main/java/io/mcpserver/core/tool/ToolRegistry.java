package io.mcpserver.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.mcpserver.core.model.McpTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry for MCP tool definitions.
 *
 * <p>Tools are identified by name and stored in a {@link ConcurrentHashMap}.
 * Registrations are idempotent in the sense that duplicate names cause an
 * {@link IllegalArgumentException} to be thrown, ensuring that the tool
 * namespace remains unambiguous.</p>
 *
 * <p>All public methods are safe for concurrent use without external
 * synchronization.</p>
 */
public class ToolRegistry {

    private final ConcurrentMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * Registers a tool definition.
     * <p>
     * If a tool with the same name is already registered an
     * {@link IllegalArgumentException} is thrown.
     *
     * @param tool the tool definition to register (must not be {@code null})
     * @throws NullPointerException     if {@code tool} is {@code null}
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        ToolDefinition previous = tools.putIfAbsent(tool.name(), tool);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Tool already registered with name '" + tool.name() + "'");
        }
    }

    /**
     * Convenience method that creates a {@link ToolDefinition} and registers it
     * in a single call.
     *
     * @param name        the tool name (must not be blank)
     * @param description an optional description; may be {@code null}
     * @param inputSchema an optional input JSON Schema; may be {@code null}
     * @param handler     the tool handler (must not be {@code null})
     * @throws NullPointerException     if {@code name} or {@code handler} is {@code null}
     * @throws IllegalArgumentException if a tool with the same name is already registered
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public void register(String name, String description, JsonNode inputSchema, ToolHandler handler) {
        register(new ToolDefinition(name, description, inputSchema, handler));
    }

    /**
     * Retrieves a tool definition by name.
     *
     * @param name the tool name (must not be {@code null})
     * @return the matching {@link ToolDefinition}, or {@code null} if not found
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public ToolDefinition getTool(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return tools.get(name);
    }

    /**
     * Returns whether a tool with the given name has been registered.
     *
     * @param name the tool name (must not be {@code null})
     * @return {@code true} if the tool exists
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public boolean containsTool(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return tools.containsKey(name);
    }

    /**
     * Returns an immutable list of all registered tools as protocol-level
     * {@link McpTool} instances.
     *
     * <p>The returned list is a snapshot at the time of the call; subsequent
     * registrations are not reflected.</p>
     *
     * @return an unmodifiable list of {@link McpTool} instances (never {@code null})
     */
    public List<McpTool> listTools() {
        List<McpTool> result = new ArrayList<>(tools.size());
        for (ToolDefinition def : tools.values()) {
            result.add(def.toMcpTool());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the number of registered tools.
     *
     * @return the tool count
     */
    public int toolCount() {
        return tools.size();
    }

    /**
     * Removes all registered tools from this registry.
     */
    public void clear() {
        tools.clear();
    }
}
