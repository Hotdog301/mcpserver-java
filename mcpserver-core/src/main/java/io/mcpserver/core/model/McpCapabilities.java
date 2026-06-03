package io.mcpserver.core.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Capabilities advertised by an MCP server or client during the initialization
 * handshake.
 *
 * <p>Each capability type (tools, resources, prompts) is represented as a
 * set of supported feature identifiers. An empty or null set indicates that
 * the corresponding capability is not supported.</p>
 *
 * <p>Instances are created via the {@link Builder}.</p>
 */
@JsonDeserialize(builder = McpCapabilities.Builder.class)
public final class McpCapabilities {

    private final Set<String> tools;
    private final Set<String> resources;
    private final Set<String> prompts;

    private McpCapabilities(Builder builder) {
        this.tools = builder.tools == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(builder.tools));
        this.resources = builder.resources == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(builder.resources));
        this.prompts = builder.prompts == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(builder.prompts));
    }

    /**
     * Returns an unmodifiable set of supported tool identifiers.
     *
     * @return supported tools, never {@code null}
     */
    public Set<String> getTools() {
        return tools;
    }

    /**
     * Returns an unmodifiable set of supported resource identifiers.
     *
     * @return supported resources, never {@code null}
     */
    public Set<String> getResources() {
        return resources;
    }

    /**
     * Returns an unmodifiable set of supported prompt identifiers.
     *
     * @return supported prompts, never {@code null}
     */
    public Set<String> getPrompts() {
        return prompts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof McpCapabilities that)) return false;
        return tools.equals(that.tools)
            && resources.equals(that.resources)
            && prompts.equals(that.prompts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tools, resources, prompts);
    }

    @Override
    public String toString() {
        return "McpCapabilities{"
            + "tools=" + tools
            + ", resources=" + resources
            + ", prompts=" + prompts
            + '}';
    }

    /**
     * Creates a new {@link Builder} instance.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    /**
     * Builder for creating immutable {@link McpCapabilities} instances.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Set<String> tools;
        private Set<String> resources;
        private Set<String> prompts;

        private Builder() {
        }

        /**
         * Sets the supported tool identifiers.
         *
         * @param tools the tool set (may be {@code null} or empty)
         * @return this builder for chaining
         */
        public Builder tools(Set<String> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Adds a single tool identifier.
         *
         * @param tool the tool name
         * @return this builder for chaining
         */
        public Builder addTool(String tool) {
            if (this.tools == null) {
                this.tools = new LinkedHashSet<>();
            }
            this.tools.add(tool);
            return this;
        }

        /**
         * Sets the supported resource identifiers.
         *
         * @param resources the resource set (may be {@code null} or empty)
         * @return this builder for chaining
         */
        public Builder resources(Set<String> resources) {
            this.resources = resources;
            return this;
        }

        /**
         * Adds a single resource identifier.
         *
         * @param resource the resource URI or name
         * @return this builder for chaining
         */
        public Builder addResource(String resource) {
            if (this.resources == null) {
                this.resources = new LinkedHashSet<>();
            }
            this.resources.add(resource);
            return this;
        }

        /**
         * Sets the supported prompt identifiers.
         *
         * @param prompts the prompt set (may be {@code null} or empty)
         * @return this builder for chaining
         */
        public Builder prompts(Set<String> prompts) {
            this.prompts = prompts;
            return this;
        }

        /**
         * Adds a single prompt identifier.
         *
         * @param prompt the prompt name
         * @return this builder for chaining
         */
        public Builder addPrompt(String prompt) {
            if (this.prompts == null) {
                this.prompts = new LinkedHashSet<>();
            }
            this.prompts.add(prompt);
            return this;
        }

        /**
         * Builds the immutable {@link McpCapabilities} instance.
         *
         * @return a new capabilities object
         */
        public McpCapabilities build() {
            return new McpCapabilities(this);
        }
    }
}
