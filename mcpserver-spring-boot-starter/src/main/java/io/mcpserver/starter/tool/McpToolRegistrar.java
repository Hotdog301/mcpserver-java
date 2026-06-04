package io.mcpserver.starter.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpserver.core.tool.ToolDefinition;
import io.mcpserver.core.tool.ToolHandler;
import io.mcpserver.core.tool.ToolRegistry;
import io.mcpserver.starter.annotation.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
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
 * <p>Tools with multiple parameters are supported &mdash; each parameter is
 * resolved by name from the {@code arguments} object. Parameter names can be
 * customized via {@link JsonProperty @JsonProperty}. A JSON Schema is
 * automatically inferred from the method signature and included in the
 * tool definition, enabling MCP clients to validate arguments and render
 * input forms.</p>
 *
 * <p>Supported method signatures:</p>
 * <ul>
 *   <li>No parameters &mdash; invoked without arguments</li>
 *   <li>One or more parameters &mdash; each extracted by name from the
 *       arguments object; {@code @JsonProperty} overrides the parameter name</li>
 *   <li>Single {@link JsonNode} parameter &mdash; receives the full arguments
 *       object directly</li>
 *   <li>Single {@link Map Map&lt;String, Object&gt;} parameter &mdash; arguments
 *       are deserialized using Jackson</li>
 * </ul>
 */
public class McpToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistrar.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;

    /**
     * Creates a new registrar.
     *
     * @param applicationContext the Spring application context to scan
     * @param toolRegistry       the registry to populate with discovered tools
     */
    public McpToolRegistrar(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
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

            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                // Skip beans that are not yet fully initialized (e.g. this registrar itself)
                log.debug("Skipping bean '{}' during tool scan: {}.", beanName, e.getMessage());
                continue;
            }

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

            String toolName = resolveToolName(method, annotation);
            String description = annotation.description().isEmpty()
                    ? null : annotation.description();

            JsonNode inputSchema = buildInputSchema(method);
            ToolHandler handler = createHandler(bean, method);

            ToolDefinition definition = new ToolDefinition(toolName, description, inputSchema, handler);

            try {
                toolRegistry.register(definition);
                log.debug("Registered MCP tool '{}' from method {}.{}",
                        toolName, beanClass.getSimpleName(), method.getName());
            } catch (IllegalArgumentException e) {
                log.warn("Duplicate MCP tool '{}' from method {}.{}: {}",
                        toolName, beanClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }

    /**
     * Builds a JSON Schema object for the given method's parameters.
     *
     * <p>Each parameter is mapped to a JSON Schema property with the appropriate
     * type (string, integer, number, boolean, array, object). The {@code @JsonProperty}
     * annotation can be used to customize the property name.</p>
     *
     * @param method the method to analyze
     * @return a JSON Schema {@link ObjectNode}, or {@code null} if the method has no parameters
     */
    private JsonNode buildInputSchema(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return null;
        }

        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Parameter param : parameters) {
            String paramName = resolveParameterName(param);
            Class<?> paramType = param.getType();

            ObjectNode propSchema = properties.putObject(paramName);
            propSchema.put("type", mapTypeToJsonSchema(paramType));

            required.add(paramName);
        }

        return schema;
    }

    /**
     * Resolves the parameter name for schema and argument lookup, using the
     * value of {@link JsonProperty @JsonProperty} if present.
     */
    private String resolveParameterName(Parameter param) {
        if (param.isAnnotationPresent(JsonProperty.class)) {
            String name = param.getAnnotation(JsonProperty.class).value();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return param.getName();
    }

    /**
     * Maps a Java type to its corresponding JSON Schema type string.
     */
    private String mapTypeToJsonSchema(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) return "integer";
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || Collection.class.isAssignableFrom(type)) return "array";
        if (type == JsonNode.class || type == Object.class
                || Map.class.isAssignableFrom(type)) return "object";
        return "object";
    }

    /**
     * Resolves the tool name from the annotation or method name.
     */
    private String resolveToolName(Method method, McpTool annotation) {
        return annotation.name().isEmpty() ? method.getName() : annotation.name();
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
        method.setAccessible(true);
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
     * <p>Each parameter is extracted by name from the arguments object. Parameter
     * name customization via {@link JsonProperty @JsonProperty} is respected.</p>
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

        // For a single parameter that is JsonNode or Map, pass the raw arguments
        if (parameters.length == 1) {
            Parameter param = parameters[0];
            if (param.getType() == JsonNode.class || param.getType() == Map.class) {
                return new Object[]{resolveSingleValue(param.getType(), arguments, null)};
            }
        }

        // For all other cases, extract by parameter name
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = resolveParameterName(parameters[i]);
            Class<?> paramType = parameters[i].getType();

            JsonNode value;
            if (arguments != null && arguments.isObject()) {
                value = arguments.get(paramName);
            } else if (arguments != null && !arguments.isObject() && parameters.length == 1) {
                // Non-object arguments with single param: pass the raw value
                value = arguments;
            } else {
                value = null;
            }

            args[i] = resolveSingleValue(paramType, value, paramName);
        }

        return args;
    }

    /**
     * Converts a single {@link JsonNode} value to the expected parameter type.
     *
     * @param paramType the expected Java type
     * @param value     the JSON value (may be {@code null})
     * @param paramName the parameter name for debug / extraction (may be {@code null})
     * @return the converted value
     */
    private Object resolveSingleValue(Class<?> paramType, JsonNode value, String paramName) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (paramType == JsonNode.class) {
            return value;
        }

        if (paramType == String.class) {
            return value.asText();
        }

        if (paramType == Map.class) {
            return MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {});
        }

        // For primitive / wrapper numeric types, extract the scalar value
        if (paramType == int.class || paramType == Integer.class) {
            return value.isIntegralNumber() ? value.asInt() : MAPPER.convertValue(value, Integer.class);
        }
        if (paramType == long.class || paramType == Long.class) {
            return value.isIntegralNumber() ? value.asLong() : MAPPER.convertValue(value, Long.class);
        }
        if (paramType == double.class || paramType == Double.class) {
            return value.isDouble() ? value.asDouble() : MAPPER.convertValue(value, Double.class);
        }
        if (paramType == float.class || paramType == Float.class) {
            return (float) (value.isDouble() ? value.asDouble() : MAPPER.convertValue(value, Double.class));
        }
        if (paramType == boolean.class || paramType == Boolean.class) {
            return value.isBoolean() ? value.asBoolean() : MAPPER.convertValue(value, Boolean.class);
        }

        // Fall back to Jackson deserialization for complex types (POJOs, etc.)
        return MAPPER.convertValue(value, paramType);
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
        return MAPPER.valueToTree(result);
    }
}
