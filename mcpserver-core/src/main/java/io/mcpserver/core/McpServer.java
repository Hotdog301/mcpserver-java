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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main MCP server class that orchestrates JSON-RPC communication over stdio transport.
 *
 * <p>This server implements the Model Context Protocol (MCP), enabling LLM clients
 * (such as Claude Desktop) to discover and invoke tools exposed by this application.
 * Communication follows JSON-RPC 2.0 over stdin/stdout.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Client starts this process as a subprocess</li>
 *   <li>Client sends {@code initialize} request</li>
 *   <li>Server responds with capabilities and server info</li>
 *   <li>Client sends {@code notifications/initialized}</li>
 *   <li>Client may query {@code tools/list} and invoke {@code tools/call}</li>
 *   <li>Connection ends when stdin closes or server shuts down</li>
 * </ol>
 */
public class McpServer {

    private final StdioServerTransport transport;
    private final ToolRegistry toolRegistry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final JsonRpcSerializer serializer = JsonRpcSerializer.INSTANCE;
    private ExecutorService executor;

    /** Server metadata sent during initialization. */
    private final String serverName;
    private final String serverVersion;

    /**
     * Creates a new MCP server with the given name and version.
     *
     * @param serverName   the name of this server
     * @param serverVersion the version of this server
     */
    public McpServer(String serverName, String serverVersion) {
        this.transport = new StdioServerTransport();
        this.toolRegistry = new ToolRegistry();
        this.serverName = serverName;
        this.serverVersion = serverVersion;
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
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mcp-server-worker");
            t.setDaemon(true);
            return t;
        });

        System.err.println("[MCP Server] Started: " + serverName + " v" + serverVersion);
        System.err.println("[MCP Server] Registered tools: " + toolRegistry.toolCount());

        // Main message loop
        while (running.get()) {
            String line = transport.readLine();
            if (line == null || line.isEmpty()) {
                // EOF — client disconnected
                break;
            }

            final String requestLine = line;
            executor.submit(() -> processMessage(requestLine));
        }

        shutdown();
    }

    /**
     * Gracefully stops the server.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            transport.stop();
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
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
        String method = requestNode.get("method").asText();
        long id = requestNode.get("id").asLong();
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
            default:
                sendErrorResponse(id, -32601, "Method not found: " + method, null);
                break;
        }
    }

    private void handleNotification(JsonNode notificationNode) {
        String method = notificationNode.get("method").asText();
        if (McpConstants.METHOD_NOTIFICATIONS_INITIALIZED.equals(method)) {
            System.err.println("[MCP Server] Client initialization complete");
        }
    }

    private void handleInitialize(long id, JsonNode params) throws IOException {
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

        McpCapabilities capabilities = McpCapabilities.builder()
                .addTool("built-in")
                .build();

        InitializeResultParams resultParams = new InitializeResultParams(
                McpConstants.MCP_PROTOCOL_VERSION,
                capabilities,
                Map.of("name", serverName, "version", serverVersion),
                "MCP Server is ready. Use tools/list to discover available tools."
        );

        sendResultResponse(id, resultParams);
        System.err.println("[MCP Server] Initialized with protocol " + initParams.protocolVersion());
    }

    private void handleToolsList(long id) throws IOException {
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

    private void handleToolsCall(long id, JsonNode params) throws IOException {
        if (params == null || !params.has("name")) {
            sendErrorResponse(id, -32602, "Missing required parameter: name", null);
            return;
        }

        String toolName = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : null;

        ToolDefinition toolDef = toolRegistry.getTool(toolName);
        if (toolDef == null) {
            sendErrorResponse(id, -32602, "Unknown tool: " + toolName, null);
            return;
        }

        try {
            JsonNode result = toolDef.handler().execute(arguments);

            // Wrap in MCP tool result format: content array with text items
            ObjectNode response = JsonNodeFactory.instance.objectNode();
            ArrayNode content = response.putArray("content");

            ObjectNode textItem = content.addObject();
            textItem.put("type", "text");
            textItem.set("text", result != null ? result : JsonNodeFactory.instance.textNode(""));

            response.put("isError", false);

            sendResultResponse(id, response);
        } catch (Exception e) {
            sendErrorResponse(id, -32603, "Tool execution failed: " + e.getMessage(), null);
        }
    }

    private void sendResultResponse(long id, Object result) throws IOException {
        JsonNode resultNode = serializer.serializeToNode(result);
        JsonRpcResponse response = JsonRpcResponse.success(id, resultNode);
        transport.sendMessage(response);
    }

    private void sendErrorResponse(long id, int code, String message, JsonNode data) throws IOException {
        JsonRpcError error = new JsonRpcError(code, message, data);
        JsonRpcResponse response = JsonRpcResponse.error(id, error);
        transport.sendMessage(response);
    }

    private void shutdown() {
        running.set(false);
        transport.stop();

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

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
            server.stop();
        }));
    }
}
