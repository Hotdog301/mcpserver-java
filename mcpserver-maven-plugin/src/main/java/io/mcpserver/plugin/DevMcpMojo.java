package io.mcpserver.plugin;

import io.mcpserver.core.McpServer;
import io.mcpserver.core.tool.ToolRegistry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;

/**
 * Maven Mojo that starts an MCP server directly from the build lifecycle for
 * rapid development and testing.
 *
 * <p>Unlike the Spring Boot starter (which auto-configures the server from
 * annotated beans), this goal provides a lightweight {@link McpServer}
 * instance with no Spring Boot dependencies. It is intended for non-Spring-Boot
 * projects or for developers who want to test their service-layer tools without
 * bringing up the full application context.</p>
 *
 * <p>Tools are registered programmatically before the server starts. Add
 * {@code @Component} or plain Java classes that extend the server setup via
 * the {@link #getToolRegistry()} method, or subclass this Mojo to provide
 * custom tool registration logic.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * mvn mcpserver:dev-run -Dmcpserver.name=my-server
 * }</pre>
 *
 * <p>This goal resolves runtime dependencies ({@code requiresDependencyResolution = RUNTIME})
 * so that the project's own classes are available on the classpath. The server
 * runs in the foreground and blocks until stdin closes (the MCP client
 * disconnects) or the process is terminated.</p>
 *
 * @since 0.1.0
 */
@Mojo(
        name = "dev-run",
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        threadSafe = true
)
public class DevMcpMojo extends AbstractMojo {

    /**
     * The Maven project that this plugin runs against.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The name advertised by the MCP server during initialization.
     */
    @Parameter(
            property = "mcpserver.name",
            defaultValue = "mcpserver-dev",
            required = true
    )
    private String serverName;

    /**
     * The version advertised by the MCP server during initialization.
     */
    @Parameter(
            property = "mcpserver.version",
            defaultValue = "0.1.0",
            required = true
    )
    private String serverVersion;

    /**
     * The internal {@link McpServer} instance created during execution.
     */
    private McpServer server;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("===========================================");
        getLog().info("  MCP Dev Server");
        getLog().info("  Name:    " + serverName);
        getLog().info("  Version: " + serverVersion);
        getLog().info("  Project: " + project.getArtifactId()
                + ":" + project.getVersion());
        getLog().info("===========================================");

        server = new McpServer(serverName, serverVersion);

        // Allow subclasses to register tools before starting
        registerDevTools(server.getToolRegistry());

        int registered = server.getToolRegistry().toolCount();
        getLog().info("Registered " + registered + " tool(s)");

        McpServer.registerShutdownHook(server);

        try {
            getLog().info("Starting MCP server (waiting for client on stdin)...");
            getLog().info("Press Ctrl+C to stop.");
            server.start();
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "MCP Server failed to start or encountered an I/O error", e);
        } finally {
            getLog().info("MCP Server stopped.");
        }
    }

    /**
     * Registers development tools with the server.
     *
     * <p>Override this method in a subclass to register project-specific tools
     * before the server starts. The default implementation logs a message
     * indicating that no tools were registered programmatically.</p>
     *
     * @param registry the server's tool registry (never {@code null})
     */
    protected void registerDevTools(ToolRegistry registry) {
        getLog().info("No custom tools registered. "
                + "Override registerDevTools() to add tools.");
    }

    /**
     * Returns the {@link McpServer} instance used by this Mojo, or
     * {@code null} if {@link #execute()} has not been called yet.
     *
     * @return the server instance, or {@code null}
     */
    protected final McpServer getServer() {
        return server;
    }
}
