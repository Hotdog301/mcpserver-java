package io.mcpserver.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.model.*;
import io.mcpserver.core.tool.ToolDefinition;
import io.mcpserver.core.tool.ToolHandler;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.core.transport.JsonRpcSerializer;
import io.mcpserver.core.transport.StdioServerTransport;
import io.mcpserver.core.transport.Transport;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main MCP server class that orchestrates JSON-RPC communication over stdio transport.
 *
 * <p>This server implements the Model Context Protocol (MCP), enabling LLM clients
 * (such as Claude Desktop) to discover and invoke tools, resources, and prompts exposed
 * by this application. Communication follows JSON-RPC 2.0 over stdin/stdout.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Client starts this process as a subprocess</li>
 *   <li>Client sends {@code initialize} request</li>
 *   <li>Server responds with capabilities and server info</li>
 *   <li>Client sends {@code notifications/initialized}</li>
 *   <li>Client may query {@code tools/list}, {@code resources/list}, {@code prompts/list}
 *       and invoke {@code tools/call}, {@code resources/read}, {@code prompts/get}</li>
 *   <li>Connection ends when stdin closes or server shuts down</li>
 * </ol>
 */
public class McpServer {

    private final Transport transport;
    private final ToolRegistry toolRegistry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final JsonRpcSerializer serializer = JsonRpcSerializer.INSTANCE;
    private final ConcurrentMap<String, ResourceDefinition> resources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PromptDefinition> prompts = new ConcurrentHashMap<>();

    /** Server metadata sent during initialization. */
    private final String serverName;
    private final String serverVersion;

    /**
     * Creates a new MCP server with the given name and version.
     *
     * @param serverName   the name of this server (must not be null or blank)
     * @param serverVersion the version of this server (must not be null or blank)
     * @throws NullPointerException     if serverName or serverVersion is null
     * @throws IllegalArgumentException if serverName or serverVersion is blank
     */
    public McpServer(String serverName, String serverVersion) {
        this(serverName, serverVersion, new ToolRegistry());
    }

    /**
     * Creates a new MCP server with the given name, version, and shared tool registry.
     * Uses a default {@link StdioServerTransport} for communication.
     *
     * @param serverName   the name of this server (must not be null or blank)
     * @param serverVersion the version of this server (must not be null or blank)
     * @param toolRegistry the shared tool registry to use
     * @throws NullPointerException     if serverName, serverVersion, or toolRegistry is null
     * @throws IllegalArgumentException if serverName or serverVersion is blank
     */
    public McpServer(String serverName, String serverVersion, ToolRegistry toolRegistry) {
        this(serverName, serverVersion, toolRegistry, new StdioServerTransport());
    }

    /**
     * Creates a new MCP server with a custom transport layer.
     *
     * @param serverName   the name of this server (must not be null or blank)
     * @param serverVersion the version of this server (must not be null or blank)
     * @param toolRegistry the shared tool registry to use
     * @param transport    the transport layer (e.g., {@link StdioServerTransport} or
     *                     {@link io.mcpserver.core.transport.SseServerTransport})
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if serverName or serverVersion is blank
     */
    public McpServer(String serverName, String serverVersion, ToolRegistry toolRegistry, Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.serverName = Objects.requireNonNull(serverName, "serverName must not be null");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion must not be null");
        if (serverName.isBlank()) {
            throw new IllegalArgumentException("serverName must not be blank");
        }
        if (serverVersion.isBlank()) {
            throw new IllegalArgumentException("serverVersion must not be blank");
        }
    }

    /**
     * Returns the tool registry for registering tools before the server starts.
     *
     * @return the tool registry
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Registers a tool with the server.
     *
     * @param name        the tool name
     * @param description the tool description (may be null)
     * @param inputSchema the JSON Schema for tool arguments (may be null)
     * @param handler     the handler function
     */
    public void registerTool(String name, String description, JsonNode inputSchema, ToolHandler handler) {
        toolRegistry.register(name, description, inputSchema, handler);
    }

    /**
     * Registers a resource with the server.
     *
     * @param uri         the resource URI
     * @param name        the human-readable name
     * @param description an optional description (may be null)
     * @param mimeType    the MIME type (may be null, defaults to text/plain)
     * @param handler     the handler that returns the resource content as text
     */
    public void registerResource(String uri, String name, String description, String mimeType, ToolHandler handler) {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        resources.put(uri, new ResourceDefinition(uri, name, description,
                mimeType != null ? mimeType : "text/plain", handler));
    }

    /**
     * Registers a prompt with the server.
     *
     * @param name        the prompt name
     * @param description an optional description (may be null)
     * @param handler     the handler that returns the prompt messages
     */
    public void registerPrompt(String name, String description, ToolHandler handler) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        prompts.put(name, new PromptDefinition(name, description, handler));
    }

    /**
     * Starts the MCP server and begins processing messages.
     *
     * <p>This method blocks until the server stops (stdin closes or stop() is called).
     * It should be called on a dedicated thread.</p>
     *
     * @throws IOException if transport fails
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server is already running");
        }

        transport.start();

        System.err.println("[MCP Server] " + serverName + " v" + serverVersion + " started");
        System.err.println("[MCP Server] Registered " + toolRegistry.toolCount() + " tools, "
                + resources.size() + " resources, " + prompts.size() + " prompts");

        try {
            String line;
            while (running.get() && (line = transport.readLine()) != null) {
                if (!running.get()) {
                    break;
                }

                processMessage(line);
            }
        } finally {
            shutdown();
        }
    }

    /**
     * Gracefully stops the server.
     * Idempotent — safe to call multiple times or while {@link #start()} is executing.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            transport.stop();
        }
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    // ──────────────────────────────────────────────────
    // Private implementation
    // ──────────────────────────────────────────────────

    private void processMessage(String rawMessage) {
        try {
            JsonNode parsed = serializer.parseMessage(rawMessage);
            if (parsed == null) {
                return;
            }

            if (serializer.isRequest(parsed)) {
                handleRequest(parsed);
            } else if (serializer.isNotification(parsed)) {
                handleNotification(parsed);
            }
            // Responses from client are ignored
        } catch (Exception e) {
            System.err.println("[MCP Server] Error processing message: " + e.getMessage());
        }
    }

    private void handleRequest(JsonNode requestNode) throws IOException {
        // Validate jsonrpc version per JSON-RPC 2.0 spec
        JsonNode versionNode = requestNode.get("jsonrpc");
        if (versionNode == null || !"2.0".equals(versionNode.asText())) {
            Object id = extractId(requestNode.get("id"));
            sendErrorResponse(id, -32600, "Invalid Request: jsonrpc must be '2.0'", null);
            return;
        }
        JsonNode methodNode = requestNode.get("method");
        if (methodNode == null) {
            Object id = extractId(requestNode.get("id"));
            sendErrorResponse(id, -32600, "Invalid Request: missing 'method'", null);
            return;
        }
        String method = methodNode.asText();
        Object id = extractId(requestNode.get("id"));
        JsonNode params = requestNode.has("params") ? requestNode.get("params") : null;

        switch (method) {
            case McpConstants.METHOD_INITIALIZE:
                handleInitialize(id, params);
                break;
            case McpConstants.METHOD_TOOLS_LIST:
                handleToolsList(id);
                break;
            case McpConstants.METHOD_TOOLS_CALL:
                handleToolsCall(id, params);
                break;
            case McpConstants.METHOD_RESOURCES_LIST:
                handleResourcesList(id);
                break;
            case McpConstants.METHOD_RESOURCES_READ:
                handleResourcesRead(id, params);
                break;
            case McpConstants.METHOD_PROMPTS_LIST:
                handlePromptsList(id);
                break;
            case McpConstants.METHOD_PROMPTS_GET:
                handlePromptsGet(id, params);
                break;
            case McpConstants.METHOD_PING:
                sendResultResponse(id, JsonNodeFactory.instance.objectNode());
                break;
            default:
                sendErrorResponse(id, -32601, "Method not found: " + method, null);
                break;
        }
    }

    private void handleNotification(JsonNode notificationNode) {
        JsonNode methodNode = notificationNode.get("method");
        if (methodNode == null) {
            return;
        }
        String method = methodNode.asText();
        if (McpConstants.METHOD_NOTIFICATIONS_INITIALIZED.equals(method)) {
            System.err.println("[MCP Server] Client initialization complete");
        }
    }

    private void handleInitialize(Object id, JsonNode params) throws IOException {
        InitializeRequestParams initParams;
        if (params != null) {
            initParams = serializer.deserialize(params.toString(), InitializeRequestParams.class);
        } else {
            initParams = new InitializeRequestParams(
                    McpConstants.MCP_PROTOCOL_VERSION,
                    McpCapabilities.builder().build(),
                    Map.of("name", serverName, "version", serverVersion),
                    null
            );
        }

        McpCapabilities.Builder capabilitiesBuilder = McpCapabilities.builder();
        if (toolRegistry.toolCount() > 0) {
            capabilitiesBuilder.addTool("built-in");
        }
        if (!resources.isEmpty()) {
            capabilitiesBuilder.addResource("static");
        }
        if (!prompts.isEmpty()) {
            capabilitiesBuilder.addPrompt("static");
        }
        McpCapabilities capabilities = capabilitiesBuilder.build();

        String clientVersion = initParams.protocolVersion();
        if (!McpConstants.MCP_PROTOCOL_VERSION.equals(clientVersion)) {
            System.err.println("[MCP Server] Protocol version mismatch: client="
                    + clientVersion + ", server=" + McpConstants.MCP_PROTOCOL_VERSION);
        }

        InitializeResultParams resultParams = new InitializeResultParams(
                McpConstants.MCP_PROTOCOL_VERSION,
                capabilities,
                Map.of("name", serverName, "version", serverVersion),
                "MCP Server is ready. Use tools/list, resources/list, or prompts/list to discover capabilities."
        );

        sendResultResponse(id, resultParams);
        System.err.println("[MCP Server] Initialized with protocol " + clientVersion);
    }

    private void handleToolsList(Object id) throws IOException {
        List<McpTool> tools = toolRegistry.listTools();
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode toolsArray = result.putArray("tools");

        for (McpTool tool : tools) {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", tool.name());
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            if (tool.inputSchema() != null) {
                toolNode.set("inputSchema", tool.inputSchema());
            }
        }

        sendResultResponse(id, result);
        System.err.println("[MCP Server] Listed " + tools.size() + " tools");
    }

    private void handleToolsCall(Object id, JsonNode params) throws IOException {
        if (params == null) {
            sendErrorResponse(id, -32602, "Missing required parameter: name", null);
            return;
        }

        McpToolCall toolCall = serializer.deserialize(params.toString(), McpToolCall.class);
        if (toolCall.name() == null || toolCall.name().isBlank()) {
            sendErrorResponse(id, -32602, "Missing required parameter: name", null);
            return;
        }

        ToolDefinition toolDef = toolRegistry.getTool(toolCall.name());
        if (toolDef == null) {
            sendErrorResponse(id, -32602, "Unknown tool: " + toolCall.name(), null);
            return;
        }

        try {
            JsonNode result = toolDef.handler().execute(toolCall.arguments());
            McpToolResult toolResult = McpToolResult.text(
                    result != null ? result : JsonNodeFactory.instance.textNode("")
            );
            sendResultResponse(id, toolResult);
        } catch (Exception e) {
            sendErrorResponse(id, -32603, "Tool execution failed",
                    JsonNodeFactory.instance.textNode(e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void handleResourcesList(Object id) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode resourcesArray = result.putArray("resources");

        for (ResourceDefinition res : resources.values()) {
            ObjectNode resNode = resourcesArray.addObject();
            resNode.put("uri", res.uri());
            resNode.put("name", res.name());
            if (res.description() != null) {
                resNode.put("description", res.description());
            }
            resNode.put("mimeType", res.mimeType());
        }

        sendResultResponse(id, result);
        System.err.println("[MCP Server] Listed " + resources.size() + " resources");
    }

    private void handleResourcesRead(Object id, JsonNode params) throws IOException {
        if (params == null || !params.has("uri")) {
            sendErrorResponse(id, -32602, "Missing required parameter: uri", null);
            return;
        }

        String uri = params.get("uri").asText();
        ResourceDefinition resDef = resources.get(uri);
        if (resDef == null) {
            sendErrorResponse(id, -32602, "Unknown resource: " + uri, null);
            return;
        }

        try {
            JsonNode contentData = resDef.handler().execute(params);

            ObjectNode result = JsonNodeFactory.instance.objectNode();
            ArrayNode contents = result.putArray("contents");
            ObjectNode contentItem = contents.addObject();
            contentItem.put("uri", uri);
            contentItem.put("mimeType", resDef.mimeType());
            contentItem.set("text", contentData != null
                    ? contentData : JsonNodeFactory.instance.textNode(""));

            sendResultResponse(id, result);
        } catch (Exception e) {
            sendErrorResponse(id, -32603, "Resource read failed",
                    JsonNodeFactory.instance.textNode(e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void handlePromptsList(Object id) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode promptsArray = result.putArray("prompts");

        for (PromptDefinition prompt : prompts.values()) {
            ObjectNode promptNode = promptsArray.addObject();
            promptNode.put("name", prompt.name());
            if (prompt.description() != null) {
                promptNode.put("description", prompt.description());
            }
            // Arguments can be added later when the MCP prompt argument spec is fully supported
        }

        sendResultResponse(id, result);
        System.err.println("[MCP Server] Listed " + prompts.size() + " prompts");
    }

    private void handlePromptsGet(Object id, JsonNode params) throws IOException {
        if (params == null || !params.has("name")) {
            sendErrorResponse(id, -32602, "Missing required parameter: name", null);
            return;
        }

        String promptName = params.get("name").asText();
        PromptDefinition promptDef = prompts.get(promptName);
        if (promptDef == null) {
            sendErrorResponse(id, -32602, "Unknown prompt: " + promptName, null);
            return;
        }

        try {
            JsonNode messages = promptDef.handler().execute(params);

            ObjectNode result = JsonNodeFactory.instance.objectNode();
            if (messages != null && messages.isArray()) {
                result.set("messages", messages);
            } else {
                ArrayNode messagesArray = result.putArray("messages");
                ObjectNode messageItem = messagesArray.addObject();
                messageItem.put("role", "assistant");
                messageItem.set("content", messages != null
                        ? messages : JsonNodeFactory.instance.textNode(""));
            }

            sendResultResponse(id, result);
        } catch (Exception e) {
            sendErrorResponse(id, -32603, "Prompt get failed",
                    JsonNodeFactory.instance.textNode(e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void sendResultResponse(Object id, Object result) throws IOException {
        JsonNode resultNode = serializer.serializeToNode(result);
        JsonRpcResponse response = JsonRpcResponse.success(id, resultNode);
        transport.sendMessage(response);
    }

    private void sendErrorResponse(Object id, int code, String message, JsonNode data) throws IOException {
        JsonRpcError error = new JsonRpcError(code, message, data);
        JsonRpcResponse response = JsonRpcResponse.error(id, error);
        transport.sendMessage(response);
    }

    /**
     * Extracts the request id from a JSON-RPC request node.
     * Per JSON-RPC 2.0, id can be a number, string, or null.
     * For numeric IDs, prefers long representation to avoid precision loss.
     */
    private static Object extractId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isNumber()) {
            // Use asLong() for integral numbers to avoid precision loss;
            // fall back to decimalValue() for floats.
            if (idNode.isIntegralNumber()) {
                return idNode.asLong();
            }
            return idNode.decimalValue();
        }
        return idNode.asText();
    }

    private void shutdown() {
        transport.stop();
        System.err.println("[MCP Server] Shutdown complete");
    }

    /**
     * Registers a shutdown hook for graceful termination on SIGTERM/SIGINT.
     *
     * @param server the server to stop on shutdown
     */
    public static void registerShutdownHook(McpServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[MCP Server] Received shutdown signal");
            if (server != null) {
                server.stop();
            }
        }));
    }

    // ---------------------------------------------------------------
    // Internal definition records
    // ---------------------------------------------------------------

    private record ResourceDefinition(
            String uri,
            String name,
            String description,
            String mimeType,
            ToolHandler handler
    ) {}

    private record PromptDefinition(
            String name,
            String description,
            ToolHandler handler
    ) {}
}
