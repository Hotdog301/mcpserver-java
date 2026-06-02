package io.mcpserver.starter.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mcpserver.core.tool.ToolHandler;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.starter.annotation.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Scans Spring beans for methods annotated with {@link McpTool} and registers
 * them as tools in the {@link ToolRegistry}.
 *
 * <p>This registrar iterates over all beans in the Spring {@link ApplicationContext},
 * inspects their methods for the {@code @McpTool} annotation, and creates
 * a {@link ToolHandler} that invokes the annotated method via reflection.
 * The handler is responsible for converting incoming {@link JsonNode} arguments
 * to the appropriate Java types expected by the method, and converting the
 * method's return value back to a {@link JsonNode}.</p>
 *
 * <p>Supported method signatures:</p>
 * <ul>
 *   <li>No parameters &mdash; invoked without arguments</li>
 *   <li>Single {@link String} parameter &mdash; arguments are converted via
 *       {@link JsonNode#asText()}</li>
 *   <li>Single {@link JsonNode} parameter &mdash; arguments are passed through
 *       directly</li>
 *   <li>Single {@link Map Map&lt;String, Object&gt;} parameter &mdash; arguments
 *       are deserialized using Jackson</li>
 *   <li>Any other single parameter type &mdash; deserialized using Jackson</li>
 * </ul>
 *
 * <p>Tool names are derived from the {@code @McpTool} annotation's {@code name}
 * attribute, falling back to the Java method name if the attribute is blank.</p>
 */
public class McpToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistrar.class);

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new registrar.
     *
     * @param applicationContext the Spring application context to scan
     * @param toolRegistry       the registry to populate with discovered tools
     */
    public McpToolRegistrar(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Scans all beans in the application context and registers methods annotated
     * with {@code @McpTool} in the {@link ToolRegistry}.
     *
     * <p>This method is idempotent and safe to call multiple times, though in
     * normal usage it is invoked once during application startup.</p>
     */
    public void scanAndRegister() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Class<?> beanClass = applicationContext.getType(beanName);
            if (beanClass == null) {
                continue;
            }

            Object bean = applicationContext.getBean(beanName);
            registerToolsFromBean(bean, beanClass);
        }

        log.info("MCP tool scanning complete. Registered {} tools.", toolRegistry.toolCount());
    }

    /**
     * Registers tools from a single bean instance by inspecting its methods.
     *
     * @param bean       the bean instance
     * @param beanClass  the class of the bean
     */
    private void registerToolsFromBean(Object bean, Class<?> beanClass) {
        for (Method method : beanClass.getMethods()) {
            McpTool annotation = method.getAnnotation(McpTool.class);
            if (annotation == null) {
                continue;
            }

            String toolName = resolveToolName(annotation, method);
            String description = annotation.description();

            ToolHandler handler = createHandler(bean, method);

            try {
                toolRegistry.register(toolName, description, null, handler);
                log.debug("Registered MCP tool '{}' from bean '{}'.{}()",
                        toolName, beanClass.getSimpleName(), method.getName());
            } catch (Exception e) {
                log.error("Failed to register MCP tool '{}' from {}.{}(): {}",
                        toolName, beanClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }

    /**
     * Resolves the tool name from the annotation, falling back to the method name.
     */
    private static String resolveToolName(McpTool annotation, Method method) {
        String name = annotation.name();
        return (name == null || name.isBlank()) ? method.getName() : name;
    }

    /**
     * Creates a {@link ToolHandler} that invokes the given method on the given
     * bean instance when a tool call is received.
     *
     * <p>The handler handles argument conversion and return value serialization
     * as described in the class-level documentation.</p>
     *
     * @param bean   the bean instance on which to invoke the method
     * @param method the method to invoke
     * @return a handler that wraps the reflective invocation
     */
    private ToolHandler createHandler(Object bean, Method method) {
        return arguments -> {
            try {
                Object[] args = resolveArguments(method, arguments);
                Object result = method.invoke(bean, args);
                return serializeResult(result);
            } catch (Exception e) {
                // Unwrap InvocationTargetException to get the real cause
                Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                log.error("Error executing MCP tool '{}': {}", method.getName(), cause.getMessage());
                throw new RuntimeException("Tool execution failed: " + cause.getMessage(), cause);
            }
        };
    }

    /**
     * Resolves the arguments array for the given method from the provided
     * {@link JsonNode} arguments.
     *
     * @param method    the method being invoked
     * @param arguments the JSON arguments from the MCP client
     * @return an array of arguments suitable for {@link Method#invoke}
     */
    private Object[] resolveArguments(Method method, JsonNode arguments) {
        Parameter[] parameters = method.getParameters();

        if (parameters.length == 0) {
            return new Object[0];
        }

        if (parameters.length > 1) {
            log.warn("Method '{}' has {} parameters; MCP tools support at most one parameter. " +
                     "Only the first parameter will be used.", method.getName(), parameters.length);
        }

        Parameter param = parameters[0];
        Class<?> paramType = param.getType();

        Object resolved = resolveSingleArgument(paramType, arguments);
        return new Object[]{resolved};
    }

    /**
     * Converts a {@link JsonNode} to the expected parameter type.
     */
    private Object resolveSingleArgument(Class<?> paramType, JsonNode arguments) {
        if (paramType == JsonNode.class) {
            return arguments;
        }

        if (paramType == String.class) {
            return (arguments == null) ? null : arguments.asText();
        }

        if (paramType == Map.class) {
            if (arguments == null) {
                return null;
            }
            return objectMapper.convertValue(arguments, new TypeReference<Map<String, Object>>() {});
        }

        // For any other type, attempt Jackson deserialization
        if (arguments == null) {
            return null;
        }
        return objectMapper.convertValue(arguments, paramType);
    }

    /**
     * Serializes the return value of a tool method into a {@link JsonNode}.
     *
     * @param result the return value from the method; may be {@code null}
     * @return a {@link JsonNode} representing the result
     */
    private JsonNode serializeResult(Object result) {
        if (result == null) {
            return JsonNodeFactory.instance.nullNode();
        }

        if (result instanceof JsonNode jsonNode) {
            return jsonNode;
        }

        if (result instanceof String text) {
            return JsonNodeFactory.instance.textNode(text);
        }

        if (result instanceof Boolean bool) {
            return JsonNodeFactory.instance.booleanNode(bool);
        }

        if (result instanceof Number number) {
            if (result instanceof Integer i) {
                return JsonNodeFactory.instance.numberNode(i);
            } else if (result instanceof Long l) {
                return JsonNodeFactory.instance.numberNode(l);
            } else if (result instanceof Double d) {
                return JsonNodeFactory.instance.numberNode(d);
            } else if (result instanceof Float f) {
                return JsonNodeFactory.instance.numberNode(f);
            }
            return JsonNodeFactory.instance.numberNode(number.doubleValue());
        }

        // Fall back to Jackson serialization for complex types
        return objectMapper.valueToTree(result);
    }
}
