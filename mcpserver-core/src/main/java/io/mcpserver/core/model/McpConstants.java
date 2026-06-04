package io.mcpserver.core.model;

/**
 * Constants for the MCP (Model Context Protocol) and JSON-RPC 2.0 integration.
 *
 * <p>Defines protocol version strings and standard method names used
 * throughout the MCP lifecycle.</p>
 */
public interface McpConstants {

    /**
     * The current MCP protocol version string.
     *
     * @see <a href="https://spec.modelcontextprotocol.io">MCP Specification</a>
     */
    String MCP_PROTOCOL_VERSION = "2025-03-26";

    /**
     * The JSON-RPC version string required by the specification.
     */
    String JSON_RPC_VERSION = "2.0";

    // ---------------------------------------------------------------
    // Standard method names
    // ---------------------------------------------------------------

    /** Method name for the initialization handshake. */
    String METHOD_INITIALIZE = "initialize";

    /** Method name for listing available tools. */
    String METHOD_TOOLS_LIST = "tools/list";

    /** Method name for invoking a tool. */
    String METHOD_TOOLS_CALL = "tools/call";

    /** Method name for listing available resources. */
    String METHOD_RESOURCES_LIST = "resources/list";

    /** Method name for reading a resource by URI. */
    String METHOD_RESOURCES_READ = "resources/read";

    /** Method name for listing available prompts. */
    String METHOD_PROMPTS_LIST = "prompts/list";

    /** Method name for retrieving a prompt by name. */
    String METHOD_PROMPTS_GET = "prompts/get";

    /** Method name for the initialized notification sent by the client. */
    String METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized";
}
