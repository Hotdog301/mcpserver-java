package io.mcpserver.core.model;

/**
 * Base sealed interface for all JSON-RPC 2.0 messages in the MCP protocol.
 *
 * <p>JSON-RPC 2.0 defines three message types: requests, responses, and notifications.
 * This sealed hierarchy ensures type safety when handling protocol messages.</p>
 *
 * @see <a href="https://www.jsonrpc.org/specification">JSON-RPC 2.0 Specification</a>
 */
public sealed interface JsonRpcMessage permits JsonRpcRequest, JsonRpcResponse, JsonRpcNotification {
}
