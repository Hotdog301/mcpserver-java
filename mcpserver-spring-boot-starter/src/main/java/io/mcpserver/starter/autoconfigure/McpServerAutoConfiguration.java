package io.mcpserver.starter.autoconfigure;

import io.mcpserver.core.McpServer;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.core.transport.SseServerTransport;
import io.mcpserver.core.transport.StdioServerTransport;
import io.mcpserver.core.transport.Transport;
import io.mcpserver.starter.runner.McpServerRunner;
import io.mcpserver.starter.tool.McpToolRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the MCP Server.
 *
 * <p>This configuration class sets up the core MCP infrastructure:</p>
 * <ul>
 *   <li>A shared {@link ToolRegistry} bean</li>
 *   <li>A {@link McpToolRegistrar} that scans the application context for
 *       {@link io.mcpserver.starter.annotation.McpTool @McpTool}-annotated methods</li>
 *   <li>A {@link McpServer} bean (can be overridden by user-provided beans)</li>
 *   <li>A {@link McpServerRunner} that starts the MCP server when the
 *       application is ready</li>
 * </ul>
 *
 * <p>Configuration properties are read from the {@code mcpserver.*} prefix
 * via {@link McpServerProperties}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = "mcpserver", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpServerAutoConfiguration.class);

    /**
     * Creates the shared {@link ToolRegistry} bean that holds all registered
     * MCP tools.
     *
     * @return a new, empty tool registry
     */
    @Bean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    /**
     * Creates and runs the {@link McpToolRegistrar} that scans all beans in
     * the application context for {@code @McpTool} methods and registers them.
     *
     * @param context  the Spring application context
     * @param registry the shared tool registry
     * @return the registrar (kept as a bean for lifecycle management)
     */
    @Bean
    public McpToolRegistrar mcpToolRegistrar(ApplicationContext context, ToolRegistry registry) {
        McpToolRegistrar registrar = new McpToolRegistrar(context, registry);
        registrar.scanAndRegister();
        return registrar;
    }

    /**
     * Creates the {@link McpServer} bean that uses the shared {@link ToolRegistry}
     * for tool discovery and invocation.
     *
     * <p>This bean is conditional &mdash; if the user provides their own
     * {@link McpServer} bean, this one will not be created.</p>
     *
     * @param registry   the shared tool registry containing discovered tools
     * @param properties the server configuration properties
     * @return a configured MCP server instance
     */
    @Bean
    @ConditionalOnMissingBean(McpServer.class)
    public McpServer mcpServer(ToolRegistry registry, McpServerProperties properties) throws Exception {
        Transport transport = createTransport(properties);
        McpServer server = new McpServer(properties.getName(), properties.getVersion(), registry, transport);

        log.info("Created McpServer '{}' v{} with {} tool(s) [transport={}]",
                properties.getName(), properties.getVersion(), registry.toolCount(), properties.getTransportType());
        return server;
    }

    private static Transport createTransport(McpServerProperties properties) throws Exception {
        if ("sse".equalsIgnoreCase(properties.getTransportType())) {
            SseServerTransport sse = new SseServerTransport(properties.getSsePort());
            sse.setCorsOrigin(properties.getCorsOrigin());
            log.info("MCP server configured with SSE transport on port {}, corsOrigin={}",
                    properties.getSsePort(), properties.getCorsOrigin());
            return sse;
        }
        log.info("MCP server configured with stdio transport");
        return new StdioServerTransport();
    }

    /**
     * Creates the {@link McpServerRunner} that starts the MCP server on a
     * separate thread after the Spring Boot application has started.
     *
     * @param server the MCP server to start
     * @return a new runner instance
     */
    @Bean
    public McpServerRunner mcpServerRunner(McpServer server) {
        return new McpServerRunner(server);
    }
}
