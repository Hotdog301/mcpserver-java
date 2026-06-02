package io.mcpserver.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that marks a Spring bean for MCP tool scanning.
 *
 * <p>This annotation is optional. When present on a Spring bean, it signals
 * the {@link io.mcpserver.starter.tool.McpToolRegistrar} that the bean
 * should be scanned for methods annotated with {@link McpTool}. If this
 * annotation is not used, all beans in the application context are still
 * scanned, but applying {@code @McpComponent} provides explicit intent
 * and may improve startup performance by reducing the scan space.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * @McpComponent
 * public class MyTools {
 *
 *     @McpTool(name = "echo", description = "Echoes back the input")
 *     public String echo(String input) {
 *         return input;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpComponent {
}
