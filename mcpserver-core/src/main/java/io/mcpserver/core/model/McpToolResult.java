package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;

/**
 * The result of a tool invocation returned from the MCP server to the client.
 *
 * <p>Contains a list of content items (each represented as a flexible
 * {@link JsonNode}) and an optional error indicator.</p>
 *
 * @param content A list of result content items. Each item is a JSON node
 *                whose structure depends on the content type (e.g., text,
 *                image, embedded resource).
 * @param isError Whether the tool execution resulted in an error.
 *                Defaults to {@code false}.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record McpToolResult(
    List<JsonNode> content,
    @JsonProperty("isError") boolean isError
) {

    /**
     * Creates a successful tool result with the given content items.
     *
     * @param content the content items
     */
    public McpToolResult(List<JsonNode> content) {
        this(content, false);
    }

    /**
     * Returns an unmodifiable view of the content list.
     *
     * @return the content list, never {@code null}
     */
    @Override
    public List<JsonNode> content() {
        return content == null ? List.of() : Collections.unmodifiableList(content);
    }
}
