package io.mcpserver.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of a tool invocation returned from the MCP server to the client.
 *
 * <p>Contains a list of content items (each represented as a flexible
 * {@link JsonNode}) and an error indicator.</p>
 *
 * @param content A list of result content items. Each item is a JSON node
 *                whose structure depends on the content type (e.g., text,
 *                image, embedded resource).
 * @param isError Whether the tool execution resulted in an error.
 *                Defaults to {@code false}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolResult(
    List<JsonNode> content,
    @JsonProperty("isError") Boolean isError
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

    /**
     * Creates a successful tool result containing a single text content item.
     *
     * @param text the text content
     * @return a tool result with one text item and isError=false
     */
    public static McpToolResult text(String text) {
        ObjectNode textItem = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        textItem.put("type", "text");
        textItem.put("text", text);
        List<JsonNode> content = new ArrayList<>(1);
        content.add(textItem);
        return new McpToolResult(content, false);
    }

    /**
     * Creates a successful tool result with the given content node wrapped
     * as a text item.
     *
     * @param contentItem the content node
     * @return a tool result with one text item and isError=false
     */
    public static McpToolResult text(JsonNode contentItem) {
        List<JsonNode> content = new ArrayList<>(1);
        ObjectNode textItem = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        textItem.put("type", "text");
        textItem.put("text", contentItem != null ? contentItem.asText() : "");
        content.add(textItem);
        return new McpToolResult(content, false);
    }
}
