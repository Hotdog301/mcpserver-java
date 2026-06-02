package io.mcpserver.starter.runner;

import io.mcpserver.core.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * {@link ApplicationRunner} that starts the MCP server on a separate daemon
 * thread after the Spring Boot application has fully started.
 *
 * <p>This runner performs the following actions:</p>
 * <ol>
 *   <li>Registers a JVM shutdown hook for graceful termination via
 *       {@link McpServer#registerShutdownHook(McpServer)}</li>
 *   <li>Starts the MCP server in a dedicated daemon thread named
 *       {@code "mcp-server"}</li>
 * </ol>
 *
 * <p>The server runs in a daemon thread so that it does not prevent the JVM
 * from exiting when the main application thread completes.</p>
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
            }
        }, "mcp-server");

        serverThread.setDaemon(true);
        serverThread.start();

        log.info("MCP server thread started (daemon: {})", serverThread.isDaemon());
    }
}
