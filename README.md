
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

# 📖 中文文档

<p align="center">
  <b>一个依赖。零配置。你的 Bean 就是 AI 工具。</b>
</p>

## 🚀 这是什么？

**MCP Server for Java** 让你可以将任何 Spring Boot Bean 方法暴露为 [MCP（Model Context Protocol）](https://modelcontextprotocol.io) 工具——**无需任何样板代码**。

添加一个依赖，用 `@McpTool` 注解方法，即可被 Claude Desktop、Cursor、VS Code Copilot 等 MCP 客户端发现。

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

**就这么简单。** 运行应用，连接客户端，工具即可用。

---

## ✨ 特性

- **⚡ 零代码** — 启动时自动发现 `@McpTool` Bean，无需手动装配
- **🔌 即插即用** — 兼容 Claude Desktop、Cursor、Copilot 等任何 MCP 客户端
- **📦 全类型支持** — String、数字、JSON、POJO — 自动转换
- **🏃‍♂️ 开发模式** — `mvn mcpserver:dev-run` 快速测试，无需 Spring Boot
- **🧩 模块化** — Core、Starter、Plugin — 按需使用
- **🔒 标准协议** — JSON-RPC 2.0，无自定义传输层
- **🧪 可测试** — Core 模块零 Spring 依赖

---

## 📦 模块

| 模块 | 说明 | 需要 Spring |
|------|------|:---:|
| **mcpserver-core** | JSON-RPC 2.0 stdio 传输、工具注册表、协议处理 | ❌ |
| **mcpserver-spring-boot-starter** | 自动配置、`@McpTool` 注解、Bean 发现 | ✅ |
| **mcpserver-maven-plugin** | 开发服务器（`mvn mcpserver:dev-run`） | ❌ |

---

## 🏗️ 架构

```
┌──────────────────────────────────────────┐
│             MCP 客户端                    │
│   (Claude Desktop / Cursor / Copilot)    │
└──────────────┬──────────┬────────────────┘
               │ stdin    │ stdout
               ▼          ▼
┌──────────────────────────────────────────┐
│           StdioServerTransport           │
│       (JSON-RPC 2.0 行分隔传输)           │
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
│     Spring Boot (自动配置)                │
│  ┌──────────────┐  ┌──────────────────┐  │
│  │ @McpTool     │  │ McpToolRegistrar │  │
│  │ annotations  │  │ (反射注册)        │  │
│  └──────────────┘  └──────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 🔧 快速开始

### 1️⃣ 添加依赖

```xml
<dependency>
    <groupId>io.mcpserver</groupId>
    <artifactId>mcpserver-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2️⃣ 注解你的 Bean

```java
import io.mcpserver.starter.annotation.McpTool;

@Service
public class WeatherService {

    @McpTool(description = "获取指定城市的天气信息")
    public String getWeather(String city) {
        // 你的业务逻辑
        return "晴天, 25°C";
    }

    @McpTool(description = "计算两个日期之间的天数")
    public long daysBetween(@JsonProperty("start") String start,
                            @JsonProperty("end") String end) {
        return ChronoUnit.DAYS.between(
            LocalDate.parse(start), LocalDate.parse(end));
    }
}
```

### 3️⃣ 配置 MCP 客户端

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

**Cursor / VS Code Copilot**: 指向你的 Spring Boot jar 包。

### 4️⃣ 运行

```bash
mvn spring-boot:run
```

用 MCP 客户端连接，工具即可用。

---

## 🧪 开发模式（无需 Spring Boot）

```bash
mvn mcpserver:dev-run
```

这会启动一个裸 MCP 服务器，从 stdin 读取，向 stdout 写入——非常适合用 `npx @modelcontextprotocol/inspector` 测试。

---

## ⚙️ 配置

### 应用配置

```properties
# 服务器身份（MCP 初始化时显示）
mcpserver.name=my-custom-server
mcpserver.version=1.0.0
```

### 自定义服务器

```java
@Configuration
public class McpConfig {

    @Bean
    public McpServer mcpServer(ToolRegistry registry) {
        // 覆盖默认服务器，添加自定义工具
        McpServer server = new McpServer("custom-server", "1.0.0");
        server.registerTool("ping", "健康检查", null,
            args -> JsonNodeFactory.instance.textNode("pong"));
        return server;
    }
}
```

---

## 🧩 Maven 插件

```bash
# 添加 MCP 依赖到项目
mvn mcpserver:add-mcp

# 启动开发 MCP 服务器
mvn mcpserver:dev-run -Dmcpserver.name=my-server
```

---

## 📋 要求

- **Java 17+**
- **Maven 3.6+**
- **Spring Boot 3.x**（starter 模块）

---

## 🔬 工作原理

1. **启动**：Spring Boot 自动配置扫描所有 Bean 的 `@McpTool` 方法
2. **注册**：每个注解方法变为 `ToolDefinition`，带反射处理器
3. **监听**：`McpServer` 打开 stdin/stdout 传输层，使用 JSON-RPC 2.0
4. **初始化**：MCP 客户端发送 `initialize` → 服务器响应能力声明
5. **发现**：客户端调用 `tools/list` → 获取所有注解工具列表
6. **调用**：客户端调用 `tools/call` 带 `{name, arguments}` → 处理器执行
7. **响应**：返回值序列化为 JSON，作为工具结果返回

所有通信通过 stdio 行分隔 JSON 进行——标准 MCP 传输方式。

---

## 📄 许可证

Apache License 2.0

---

## 🤝 贡献

欢迎 PR！项目处于早期开发阶段。查看 [issues](https://github.com/Hotdog301/mcpserver-java/issues) 了解需求。

---

<p align="center">
  <i>为想让应用与 AI 对话的 Java 开发者打造。</i><br>
  <b>一个注解。零仪式。无限工具。</b>
</p>
