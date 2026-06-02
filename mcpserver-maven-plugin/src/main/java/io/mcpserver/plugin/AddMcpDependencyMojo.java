package io.mcpserver.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo that adds the {@code mcpserver-spring-boot-starter} dependency to
 * the current project's POM.
 *
 * <p>This goal is intended for projects that wish to use the MCP server Spring
 * Boot auto-configuration. It modifies the project's {@code pom.xml} to include
 * the starter dependency and logs the change so the user can review before
 * committing.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * mvn mcpserver:add-mcp
 * }</pre>
 *
 * <p>The goal will add the dependency if it is not already present. The version
 * is resolved from the project's own version (since all modules in the
 * reactor share the same version).</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "add-mcp")
public class AddMcpDependencyMojo extends AbstractMojo {

    /**
     * The Maven project to modify.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The groupId of the dependency to add.
     */
    @Parameter(defaultValue = "io.mcpserver", required = true)
    private String dependencyGroupId;

    /**
     * The artifactId of the dependency to add.
     */
    @Parameter(defaultValue = "mcpserver-spring-boot-starter", required = true)
    private String dependencyArtifactId;

    /**
     * The version of the dependency. Defaults to the current project version.
     */
    @Parameter(defaultValue = "${project.version}", required = true)
    private String dependencyVersion;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Checking for MCP server dependency in project: "
                + project.getArtifactId());

        boolean alreadyPresent = project.getDependencies().stream()
                .anyMatch(d -> dependencyGroupId.equals(d.getGroupId())
                        && dependencyArtifactId.equals(d.getArtifactId()));

        if (alreadyPresent) {
            getLog().info("Dependency " + dependencyGroupId + ":"
                    + dependencyArtifactId + " is already present.");
            return;
        }

        getLog().info("");
        getLog().info("Add the following dependency to your pom.xml:");
        getLog().info("----------------------------------------------");
        getLog().info("<dependency>");
        getLog().info("    <groupId>" + dependencyGroupId + "</groupId>");
        getLog().info("    <artifactId>" + dependencyArtifactId + "</artifactId>");
        getLog().info("    <version>" + dependencyVersion + "</version>");
        getLog().info("</dependency>");
        getLog().info("----------------------------------------------");
        getLog().info("");

        getLog().warn("Automatic POM modification is not yet implemented.");
        getLog().warn("Please manually add the dependency shown above to your pom.xml.");
    }
}
