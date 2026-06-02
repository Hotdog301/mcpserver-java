package io.mcpserver.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a Spring bean method as an MCP tool.
 *
 * <p>Methods annotated with {@code @McpTool} are automatically discovered
 * by the {@link io.mcpserver.starter.tool.McpToolRegistrar} and registered
 * in the {@link io.mcpserver.core.tool.ToolRegistry} when the Spring
 * application context is ready.</p>
 *
 * <p>The annotated method may accept zero parameters, a single
 * {@link String} parameter, a single {@link com.fasterxml.jackson.databind.JsonNode}
 * parameter, or a single {@link java.util.Map Map&lt;String, Object&gt;} parameter.
 * The return value is serialized to a {@link com.fasterxml.jackson.databind.JsonNode}
 * using Jackson.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * @Component
 * public class MyTools {
 *
 *     @McpTool(name = "greet", description = "Greets a person by name")
 *     public String greet(String name) {
 *         return "Hello, " + name + "!";
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * The name of the tool. Defaults to the method name if left blank.
     *
     * @return the tool name
     */
    String name() default "";

    /**
     * An optional human-readable description of what the tool does.
     *
     * @return the tool description
     */
    String description() default "";
}
