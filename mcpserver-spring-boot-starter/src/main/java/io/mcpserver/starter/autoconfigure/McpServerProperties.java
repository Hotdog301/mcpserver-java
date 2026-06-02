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

    /** Default server name. */
    private String name = "mcpserver-spring-boot";

    /** Default server version. */
    private String version = "0.1.0";

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
}
