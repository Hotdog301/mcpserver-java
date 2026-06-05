package io.mcpserver.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the MCP Server.
 *
 * <p>All properties are prefixed with {@code mcpserver} in
 * {@code application.properties} or {@code application.yml}.</p>
 *
 * <p><b>Example application.properties:</b></p>
 * <pre>{@code
 * mcpserver.name=my-custom-server
 * mcpserver.version=1.0.0
 * }</pre>
 */
@ConfigurationProperties(prefix = McpServerProperties.PREFIX)
public class McpServerProperties {

    /**
     * The configuration prefix used for these properties.
     */
    public static final String PREFIX = "mcpserver";

    /** Whether to enable the MCP server. */
    private boolean enabled = true;

    /** Default server name. */
    private String name = "mcpserver-spring-boot";

    /** Default server version. */
    private String version = "0.1.0";

    /** Transport type: "stdio" (default) or "sse". */
    private String transportType = "stdio";

    /** Port for SSE transport (ignored when transportType=stdio). */
    private int ssePort = 3001;

    /** CORS origin for SSE transport (only used when transportType=sse). */
    private String corsOrigin = "*";

    /**
     * Returns the server name sent to MCP clients during initialization.
     *
     * @return the server name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the server name sent to MCP clients during initialization.
     *
     * @param name the server name (default: {@code "mcpserver-spring-boot"})
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the server version sent to MCP clients during initialization.
     *
     * @return the server version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the server version sent to MCP clients during initialization.
     *
     * @param version the server version (default: {@code "0.1.0"})
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns whether the MCP server is enabled.
     *
     * @return {@code true} if enabled (default: true)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the MCP server is enabled.
     *
     * @param enabled {@code false} to disable the MCP server
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the transport type.
     *
     * @return "stdio" or "sse"
     */
    public String getTransportType() {
        return transportType;
    }

    /**
     * Sets the transport type.
     *
     * @param transportType "stdio" (default) or "sse"
     */
    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    /**
     * Returns the port for SSE transport.
     *
     * @return the SSE port (default: 3001)
     */
    public int getSsePort() {
        return ssePort;
    }

    /**
     * Sets the port for SSE transport.
     *
     * @param ssePort the SSE port
     */
    public void setSsePort(int ssePort) {
        this.ssePort = ssePort;
    }

    /**
     * Returns the CORS origin for SSE transport.
     *
     * @return the CORS origin (default: "*")
     */
    public String getCorsOrigin() {
        return corsOrigin;
    }

    /**
     * Sets the CORS origin for SSE transport.
     *
     * @param corsOrigin the CORS origin value
     */
    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = corsOrigin;
    }
}
