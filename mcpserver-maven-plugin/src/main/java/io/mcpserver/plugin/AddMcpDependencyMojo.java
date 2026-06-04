package io.mcpserver.plugin;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

        // Check if the dependency is already present
        boolean alreadyPresent = project.getDependencies().stream()
                .anyMatch(d -> dependencyGroupId.equals(d.getGroupId())
                        && dependencyArtifactId.equals(d.getArtifactId()));

        if (alreadyPresent) {
            getLog().info("Dependency " + dependencyGroupId + ":"
                    + dependencyArtifactId + " is already present.");
            return;
        }

        // Locate the POM file
        File pomFile = project.getFile();
        if (pomFile == null || !pomFile.exists()) {
            throw new MojoExecutionException("Cannot locate pom.xml for project: "
                    + project.getArtifactId());
        }

        // Read the POM model
        Model model;
        try (FileReader reader = new FileReader(pomFile)) {
            MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
            model = xpp3Reader.read(reader);
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Failed to read pom.xml: " + e.getMessage(), e);
        }

        // Create and add the dependency
        Dependency dependency = new Dependency();
        dependency.setGroupId(dependencyGroupId);
        dependency.setArtifactId(dependencyArtifactId);
        dependency.setVersion(dependencyVersion);
        model.addDependency(dependency);

        // Write the modified POM back
        try (FileWriter writer = new FileWriter(pomFile, StandardCharsets.UTF_8)) {
            MavenXpp3Writer xpp3Writer = new MavenXpp3Writer();
            xpp3Writer.write(writer, model);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write pom.xml: " + e.getMessage(), e);
        }

        getLog().info("Successfully added dependency " + dependencyGroupId + ":"
                + dependencyArtifactId + ":" + dependencyVersion + " to " + pomFile.getName());
        getLog().info("Please review the changes before committing.");
    }
}
