
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
@McpTool(description = "查询当前用户信息")
public UserInfo getUserInfo(Long userId) {
    return userService.findById(userId);
}
```

**That's it.** Run your app and connect — the tool is live.

---

## ✨ Features

- **⚡ Zero code** — Auto-discovers `@McpTool` beans at startup, no wiring
- **🔌 Plug-and-play** — Works with Claude Desktop, Cursor, Copilot, any MCP client
- **📦 Supports all params** — String, numbers, JSON, POJOs — auto-converted
- **🏃‍♂️ Dev mode** — `mvn mcpserver:dev-run` for quick testing without Spring Boot
- **🧩 Modular** — Core, Starter, Plugin. Use what you need
- **🔒 JSON-RPC 2.0** — Standard protocol, no custom transports
- **🧪 Testable** — Core module has zero Spring dependencies

---

## 📦 Modules

| Module | Description | Spring Required |
|--------|------------|:---:|
| **mcpserver-core** | JSON-RPC 2.0 over stdio, tool registry, protocol handlers | ❌ |
| **mcpserver-spring-boot-starter** | Auto-configuration, `@McpTool` annotation, bean discovery | ✅ |
| **mcpserver-maven-plugin** | Dev server runner (`mvn mcpserver:dev-run`) | ❌ |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────┐
│             MCP Client                   │
│   (Claude Desktop / Cursor / Copilot)    │
└──────────────┬──────────┬────────────────┘
               │ stdin    │ stdout
               ▼          ▼
┌──────────────────────────────────────────┐
│           StdioServerTransport           │
│       (JSON-RPC 2.0 line-delimited)      │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
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
│  │ annotations  │  │ (Reflection)     │  │
│  └──────────────┘  └──────────────────┘  │
└──────────────────────────────────────────┘
```

---

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

@Service
public class WeatherService {

    @McpTool(description = "获取指定城市的天气信息")
    public String getWeather(String city) {
        // your logic here
        return "Sunny, 25°C";
    }

    @McpTool(description = "计算两个日期之间的天数")
    public long daysBetween(@JsonProperty("start") String start,
                            @JsonProperty("end") String end) {
        return ChronoUnit.DAYS.between(
            LocalDate.parse(start), LocalDate.parse(end));
    }
}
```

### 3️⃣ Configure your MCP client

**Claude Desktop** (`claude_desktop_config.json`):
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

**Cursor / VS Code Copilot**: Point to your compiled Spring Boot jar.

### 4️⃣ Run

```bash
mvn spring-boot:run
```

Connect with your MCP client — your tools are ready.

---

## 🧪 Dev Mode (No Spring Boot)

For non-Spring-Boot projects or quick testing:

```bash
mvn mcpserver:dev-run
```

This starts a bare MCP server that reads from stdin and writes to stdout — perfect for testing with `npx @modelcontextprotocol/inspector`.

---

## ⚙️ Configuration

### Application Properties

```properties
# Server identity (shown during MCP initialization)
mcpserver.name=my-custom-server
mcpserver.version=1.0.0
```

### Customize the Server

```java
@Configuration
public class McpConfig {

    @Bean
    public McpServer mcpServer(ToolRegistry registry) {
        // Override the default server with custom tools
        McpServer server = new McpServer("custom-server", "1.0.0");
        // Register tools programmatically
        server.registerTool("ping", "Health check", null,
            args -> JsonNodeFactory.instance.textNode("pong"));
        return server;
    }
}
```

---

## 🧩 Maven Plugin Usage

```bash
# Add MCP dependency to your project
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
2. **Registration**: Each annotated method becomes a `ToolDefinition` with a reflection-based handler
3. **Listen**: `McpServer` opens stdin/stdout transport for JSON-RPC 2.0 communication
4. **Initialize**: MCP client sends `initialize` → server responds with capabilities
5. **Discover**: Client calls `tools/list` → gets the list of all annotated tools
6. **Invoke**: Client calls `tools/call` with `{name, arguments}` → handler method executes
7. **Respond**: Return value serialized to JSON and sent back as tool result

All communication is line-delimited JSON over stdio — the standard MCP transport.

---

## 📄 License

Apache License 2.0

---

## 🤝 Contributing

PRs welcome! This project is in early development. Check [issues](https://github.com/Hotdog301/mcpserver-java/issues) for what's needed.

---

<p align="center">
  <i>Made for Java developers who want their apps to talk to AI.</i><br>
  <b>One annotation. Zero ceremony. Infinite tools.</b>
</p>
