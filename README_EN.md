<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/MCP-2025--03--26-purple" alt="MCP Protocol">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="License">
  <img src="https://img.shields.io/github/stars/Hotdog301/mcpserver-java?style=social" alt="Stars">
</p>

<h1 align="center">MCP Server for Java</h1>
<h3 align="center">🔌 Turn Any Spring Boot Application Into an MCP Server — Zero Code Changes</h3>

<p align="center">
  <b>One dependency. Zero config. Your beans become AI tools.</b>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

---

## 🚀 What is this?

**MCP Server for Java** lets you expose any Spring Boot bean method as an [MCP (Model Context Protocol)](https://modelcontextprotocol.io) tool — with **zero boilerplate**.

Add one dependency, annotate a method with `@McpTool`, and it's immediately discoverable by Claude Desktop, Cursor, VS Code Copilot, and any MCP client.

```xml
<dependency>
    <groupId>io.mcpserver</groupId>
    <artifactId>mcpserver-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@McpTool(description = "Get user profile by ID")
public UserInfo getUserInfo(Long userId) {
    return userService.findById(userId);
}
```

**That's it.** Run your app and connect — the tool is live.

---

## ✨ Features

- **⚡ Zero code** — Auto-discovers `@McpTool` beans at startup, no wiring
- **🔌 Plug-and-play** — Works with Claude Desktop, Cursor, Copilot, any MCP client
- **🌐 Dual transport** — Supports **stdio** (subprocess mode) and **HTTP+SSE** (remote mode)
- **🏓 Ping support** — Implements MCP `ping` health-check for client keep-alive
- **📦 Full type support** — String, numbers, JSON, POJOs — auto-converted
- **🏃‍♂️ Dev mode** — `mvn mcpserver:dev-run` for quick testing without Spring Boot
- **🧩 Modular** — Core, Starter, Plugin. Use what you need
- **🔒 JSON-RPC 2.0** — Full support for tools, resources, and prompts
- **🧪 Testable** — Core module has zero Spring dependencies

---

## 📦 Modules

| Module | Description | Spring Required |
|--------|------------|:---:|
| **mcpserver-core** | Core protocol: JSON-RPC 2.0, stdio & SSE transports, tool/resource/prompt registry | ❌ |
| **mcpserver-spring-boot-starter** | Spring Boot auto-config, `@McpTool` scanning, `@McpComponent` support | ✅ |
| **mcpserver-maven-plugin** | Dev server (`mvn mcpserver:dev-run`) and dependency injector (`mvn mcpserver:add-mcp`) | ❌ |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────┐
│             MCP Client                   │
│   (Claude Desktop / Cursor / Copilot)    │
└────────┬─────────────┬───────────────────┘
   stdin/stdio    │   HTTP+SSE
   stdout         │
         ▼        ▼
┌──────────────────────────────────────────┐
│    Transport (interface)                 │
│  ┌──────────────────┐ ┌───────────────┐  │
│  │ StdioTransport   │ │ SseTransport  │  │
│  │ (stdin/stdout)   │ │ (HTTP+SSE)    │  │
│  └────────┬─────────┘ └──────┬────────┘  │
│           │                  │           │
└───────────┼──────────────────┼───────────┘
            ▼                  ▼
┌──────────────────────────────────────────┐
│              McpServer                   │
│  ┌─────────────┐  ┌───────────────────┐  │
│  │  Protocol   │  │  ToolRegistry     │  │
│  │  Handlers   │  │  (ConcurrentHashMap)│  │
│  └─────────────┘  └───────────────────┘  │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│     Spring Boot (AutoConfiguration)      │
│  ┌──────────────┐  ┌──────────────────┐  │
│  │ @McpTool     │  │ McpToolRegistrar │  │
│  │ @McpComponent│  │ (Reflection)     │  │
│  └──────────────┘  └──────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 📦 Getting the Library

> **⚠️ Note:** This project has not been published to Maven Central yet. You need to build it locally first.

```bash
git clone https://github.com/Hotdog301/mcpserver-java.git
cd mcpserver-java
mvn install -DskipTests
```

Then add the dependency to your project (use the actual version from `pom.xml`):

## 🔧 Quick Start

### 1️⃣ Add the dependency

```xml
<dependency>
    <groupId>io.mcpserver</groupId>
    <artifactId>mcpserver-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2️⃣ Annotate your beans

```java
import io.mcpserver.starter.annotation.McpTool;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class WeatherService {

    @McpTool(description = "Get weather for a city")
    public String getWeather(String city) {
        return "Sunny, 25°C";
    }

    @McpTool(description = "Calculate days between two dates")
    public long daysBetween(@JsonProperty("start") String start,
                            @JsonProperty("end") String end) {
        return ChronoUnit.DAYS.between(
            LocalDate.parse(start), LocalDate.parse(end));
    }
}
```

**Supported method signatures:**

| Parameter type | Behavior |
|----------------|----------|
| No parameters | Invoked directly |
| `String` / `int` / `long` / `double` / `boolean` | Auto-converted from JSON |
| `JsonNode` | Receives raw arguments as JSON tree |
| `Map<String, Object>` | Arguments deserialized to Map |
| Multiple parameters | Each resolved by name from arguments object |
| `@JsonProperty("name")` | Overrides parameter name |

### 3️⃣ Configure your MCP client

**stdio mode** (local clients like Claude Desktop / Cursor):

```json
{
  "mcpServers": {
    "my-app": {
      "command": "java",
      "args": ["-jar", "my-app.jar"]
    }
  }
}
```

**HTTP+SSE mode** (remote services):

```json
{
  "mcpServers": {
    "my-app": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### 4️⃣ Run

```bash
mvn spring-boot:run
```

Connect with your MCP client — your tools are ready.

---

## 🌐 Using SSE Transport

In addition to the default stdio transport, `McpServer` supports HTTP+SSE transport for remote deployment and containerized environments.

### Standalone SSE Server (No Spring Boot)

```java
public class SseMcpServer {
    public static void main(String[] args) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register("echo", "Echo", null,
            args -> JsonNodeFactory.instance.textNode("Hello SSE!"));

        SseServerTransport transport = new SseServerTransport(8080, System.out::println);
        McpServer server = new McpServer("sse-server", "1.0.0", registry, transport);
        McpServer.registerShutdownHook(server);
        server.start();
    }
}
```

The SSE transport supports both Pull mode (`McpServer`-driven event loop) and Push mode (via `messageHandler` callback).

### SSE Protocol Flow

1. Client `GET /sse` → Server keeps connection open, sends events
2. Server sends `event: endpoint` with POST URI `data: /message?sessionId=xxx`
3. Client `POST /message` with JSON-RPC messages
4. Server pushes responses through the SSE connection

---

## 🧪 Dev Mode (No Spring Boot)

For non-Spring-Boot projects or quick testing:

```bash
mvn mcpserver:dev-run
```

This starts a bare stdio MCP server — perfect for testing with `npx @modelcontextprotocol/inspector`.

### Custom Dev Tools

Extend `DevMcpMojo` and override `registerDevTools()` to add tooling:

```java
public class MyDevMojo extends DevMcpMojo {
    @Override
    protected void registerDevTools(ToolRegistry registry) {
        registry.register("ping", "Health check", null,
            args -> JsonNodeFactory.instance.textNode("pong"));
    }
}
```

---

## ⚙️ Configuration

### Application Properties

```properties
# Enable/disable the MCP server (default: true)
mcpserver.enabled=true

# Server identity (shown during MCP initialization)
mcpserver.name=my-custom-server
mcpserver.version=1.0.0

# Transport type: stdio (subprocess mode) or sse (HTTP+SSE remote mode)
mcpserver.transport-type=stdio

# SSE transport port (only used when transport-type=sse, default: 3001)
mcpserver.sse-port=3001

# SSE CORS origin (only used when transport-type=sse, default: *)
mcpserver.cors-origin=*
```

### Custom Server Bean

```java
@Configuration
public class McpConfig {

    @Bean
    public McpServer mcpServer(ToolRegistry registry) {
        McpServer server = new McpServer("custom-server", "1.0.0", registry);
        // Programmatic tool registration
        server.registerTool("ping", "Health check", null,
            args -> JsonNodeFactory.instance.textNode("pong"));
        // Resource registration
        server.registerResource("file:///docs/readme", "README",
            "Project documentation", "text/markdown",
            args -> JsonNodeFactory.instance.textNode("# MCP Server"));
        // Prompt registration
        server.registerPrompt("greeting", "Generate greeting",
            args -> {
                ArrayNode messages = JsonNodeFactory.instance.arrayNode();
                ObjectNode msg = messages.addObject();
                msg.put("role", "assistant");
                msg.put("content", "Hello! I am an MCP server.");
                return messages;
            });
        return server;
    }
}
```

### Optional: @McpComponent

`@McpComponent` is a class-level annotation that optionally marks which beans to scan. When omitted, all beans are scanned:

```java
@McpComponent
@Service
public class MyTools {
    @McpTool(description = "My tool description")
    public String myTool(String input) {
        return "Processed: " + input;
    }
}
```

---

## 🧩 Maven Plugin Usage

```bash
# Add MCP dependency to your project's pom.xml
mvn mcpserver:add-mcp

# Start dev MCP server
mvn mcpserver:dev-run -Dmcpserver.name=my-server
```

---

## 📋 Requirements

- **Java 17+**
- **Maven 3.6+**
- **Spring Boot 3.x** (for starter module)

---

## 🔬 How It Works

1. **Startup**: Spring Boot auto-configuration scans all beans for `@McpTool` methods
2. **Registration**: Each annotated method becomes a `ToolDefinition` with a reflection-based handler and auto-generated JSON Schema
3. **Transport**: `McpServer` drives the message loop through the `Transport` interface (stdio or SSE)
4. **Initialize**: MCP client sends `initialize` → server responds with protocol version and capabilities
5. **Discover**: Client calls `tools/list`, `resources/list`, `prompts/list` to get capability listings
6. **Invoke**: Client calls `tools/call` → arguments auto-converted to Java types → method executed via reflection
7. **Respond**: Return value serialized to JSON, sent back as MCP tool result (with `content` array and `isError` flag)

All JSON-RPC 2.0 communication is handled by `JsonRpcSerializer` based on Jackson.

You can customize the underlying `ObjectMapper` via `JsonRpcSerializer.getMapper()`:

```java
// Register a custom Jackson module at startup
JsonRpcSerializer.getMapper()
    .registerModule(new JavaTimeModule());
```

---

## 📄 License

Apache License 2.0

---

## 🤝 Contributing

PRs welcome! This project is in early development. Check [issues](https://github.com/Hotdog301/mcpserver-java/issues) for what's needed.

---

<p align="center">
  <i>Built for Java developers who want their apps to talk to AI.</i><br>
  <b>One annotation. Zero ceremony. Infinite tools.</b>
</p>
