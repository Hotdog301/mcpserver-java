package io.mcpserver.starter.runner;

import io.mcpserver.core.McpServer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * {@link ApplicationRunner} that starts the MCP server on a separate
 * thread after the Spring Boot application has fully started.
 *
 * <p>This runner performs the following actions:</p>
 * <ol>
 *   <li>Registers a JVM shutdown hook for graceful termination via
 *       {@link McpServer#registerShutdownHook(McpServer)}</li>
 *   <li>Starts the MCP server in a dedicated thread named
 *       {@code "mcp-server"}</li>
 *   <li>Stops the server via {@link jakarta.annotation.PreDestroy @PreDestroy}
 *       when the Spring application context closes</li>
 * </ol>
 *
 * <p>The server thread is a non-daemon thread to keep the JVM alive in
 * non-web applications. Clean shutdown is handled by the {@code @PreDestroy}
 * method and the JVM shutdown hook.</p>
 */
public class McpServerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpServerRunner.class);

    private final McpServer server;

    /**
     * Creates a new runner that will start the given MCP server.
     *
     * @param server the MCP server to start (must not be {@code null})
     */
    public McpServerRunner(McpServer server) {
        this.server = server;
    }

    /**
     * Starts the MCP server on a separate thread after the application is ready.
     *
     * <p>A JVM shutdown hook is registered before starting the server thread
     * to ensure graceful cleanup on SIGTERM/SIGINT.</p>
     *
     * @param args the application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        McpServer.registerShutdownHook(server);

        Thread serverThread = new Thread(() -> {
            try {
                log.info("Starting MCP server...");
                server.start();
            } catch (Exception e) {
                log.error("MCP server terminated with an error", e);
                server.stop();
            }
        }, "mcp-server");

        serverThread.start();
    }

    /**
     * Stops the MCP server when the Spring application context is closed.
     * This ensures clean shutdown in non-web applications where the MCP
     * server thread keeps the JVM alive.
     */
    @PreDestroy
    public void destroy() {
        log.info("Stopping MCP server...");
        server.stop();
    }
}
