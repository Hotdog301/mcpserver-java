<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/MCP-2025--03--26-purple" alt="MCP Protocol">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="License">
  <img src="https://img.shields.io/github/stars/Hotdog301/mcpserver-java?style=social" alt="Stars">
</p>

<h1 align="center">MCP Server for Java</h1>
<h3 align="center">🔌 零代码将 Spring Boot 应用变为 MCP Server</h3>

<p align="center">
  <b>一个依赖。零配置。你的 Bean 就是 AI 工具。</b>
</p>

<p align="center">
  中文 | <a href="README_EN.md">English</a>
</p>

---

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
- **🌐 双传输模式** — 支持 **stdio**（标准子进程模式）和 **HTTP+SSE**（远程连接模式）
- **🏓 Ping 心跳** — 支持 MCP `ping` 健康检查，保持客户端长连接
- **📦 全类型支持** — String、数字、JSON、POJO — 自动转换
- **🏃‍♂️ 开发模式** — `mvn mcpserver:dev-run` 快速测试，无需 Spring Boot
- **🧩 模块化** — Core、Starter、Plugin — 按需使用
- **🔒 标准协议** — JSON-RPC 2.0，完整支持 tools/resources/prompts
- **🧪 可测试** — Core 模块零 Spring 依赖

---

## 📦 模块

| 模块 | 说明 | 需要 Spring |
|------|------|:---:|
| **mcpserver-core** | 核心协议实现：JSON-RPC 2.0、stdio & SSE 传输、工具/资源/提示注册表 | ❌ |
| **mcpserver-spring-boot-starter** | Spring Boot 自动配置、`@McpTool` 注解扫描、`@McpComponent` 支持 | ✅ |
| **mcpserver-maven-plugin** | Maven 插件：开发服务器（`mvn mcpserver:dev-run`）和依赖添加（`mvn mcpserver:add-mcp`） | ❌ |

---

## 🏗️ 架构

```
┌──────────────────────────────────────────┐
│             MCP 客户端                    │
│   (Claude Desktop / Cursor / Copilot)    │
└────────────┬─────────────┬───────────────┘
       stdin/stdio    │   HTTP+SSE
       stdout         │
             ▼        ▼
┌──────────────────────────────────────────┐
│    Transport （接口）                     │
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
│     Spring Boot (自动配置)                │
│  ┌──────────────┐  ┌──────────────────┐  │
│  │ @McpTool     │  │ McpToolRegistrar │  │
│  │ @McpComponent│  │ (反射注册+类型转换)│  │
│  └──────────────┘  └──────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 📦 获取

> **⚠️ 注意：** 该项目尚未发布到 Maven Central，需要先本地构建。

```bash
git clone https://github.com/Hotdog301/mcpserver-java.git
cd mcpserver-java
mvn install -DskipTests
```

然后在你的项目中添加依赖（版本号以实际 `pom.xml` 为准）：

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
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class WeatherService {

    @McpTool(description = "获取指定城市的天气信息")
    public String getWeather(String city) {
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

**支持的方法签名：**

| 参数类型 | 说明 |
|---------|------|
| 无参数 | 直接调用 |
| `String` / `int` / `long` / `double` / `boolean` | 基础类型自动转换 |
| `JsonNode` | 接收原始 JSON 参数对象 |
| `Map<String, Object>` | 接收反序列化为 Map 的参数 |
| 多个参数 | 按名称从 arguments 对象中提取 |
| `@JsonProperty("name")` | 自定义参数名 |

### 3️⃣ 配置 MCP 客户端

**stdio 模式**（Claude Desktop / Cursor 等本地客户端）：

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

**HTTP+SSE 模式**（远程服务）：

```json
{
  "mcpServers": {
    "my-app": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### 4️⃣ 运行

```bash
mvn spring-boot:run
```

---

## 🌐 使用 SSE 传输

除了默认的 stdio，`McpServer` 支持 HTTP+SSE 传输，适合远程部署和容器化场景。

### 纯 SSE 服务器（无 Spring Boot）

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

SSE 传输自动支持 Pull 模式（`McpServer` 驱动的事件循环）和 Push 模式（`messageHandler` 回调）。

### SSE 传输协议

1. 客户端 `GET /sse` → 服务端保持连接并推送事件
2. 服务端发送 `event: endpoint` 包含 POST 地址 `data: /message?sessionId=xxx`
3. 客户端 `POST /message` 发送 JSON-RPC 消息
4. 服务端通过 SSE 连接推送响应事件

---

## 🧪 开发模式（无需 Spring Boot）

```bash
mvn mcpserver:dev-run
```

这会启动一个裸 MCP 服务器（stdio），非常适合用 `npx @modelcontextprotocol/inspector` 测试。

### 自定义 Dev Server 工具

可以通过继承 `DevMcpMojo` 并重写 `registerDevTools()` 来注册开发工具：

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

## ⚙️ 配置

### 应用配置

```properties
# 启用/禁用 MCP 服务器（默认 true）
mcpserver.enabled=true

# 服务器身份（MCP 初始化时显示）
mcpserver.name=my-custom-server
mcpserver.version=1.0.0

# 传输类型：stdio（标准子进程模式）或 sse（HTTP+SSE 远程模式）
mcpserver.transport-type=stdio

# SSE 传输端口（仅 transport-type=sse 时生效，默认 3001）
mcpserver.sse-port=3001

# SSE CORS 跨域来源（仅 transport-type=sse 时生效，默认 *）
mcpserver.cors-origin=*
```

### 自定义服务器 Bean

```java
@Configuration
public class McpConfig {

    @Bean
    public McpServer mcpServer(ToolRegistry registry) {
        // 覆盖默认服务器
        McpServer server = new McpServer("custom-server", "1.0.0", registry);
        // 注册编程式工具
        server.registerTool("ping", "健康检查", null,
            args -> JsonNodeFactory.instance.textNode("pong"));
        // 注册资源
        server.registerResource("file:///docs/readme", "README文档",
            "项目说明文档", "text/markdown",
            args -> JsonNodeFactory.instance.textNode("# MCP Server"));
        // 注册提示
        server.registerPrompt("greeting", "问候语生成",
            args -> {
                ArrayNode messages = JsonNodeFactory.instance.arrayNode();
                ObjectNode msg = messages.addObject();
                msg.put("role", "assistant");
                msg.put("content", "你好！我是 MCP 服务器。");
                return messages;
            });
        return server;
    }
}
```

### 可选：@McpComponent

`@McpComponent` 是一个类级别注解，可选地标记哪些 Bean 应被扫描。不使用时，所有 Bean 都会被扫描：

```java
@McpComponent
@Service
public class MyTools {
    @McpTool(description = "我的工具")
    public String myTool(String input) {
        return "处理结果: " + input;
    }
}
```

---

## 🧩 Maven 插件

```bash
# 添加 MCP 依赖到项目 pom.xml
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
2. **注册**：每个注解方法变为 `ToolDefinition`，附带反射处理器和自动推断的 JSON Schema
3. **传输**：`McpServer` 通过 `Transport` 接口驱动消息循环（stdio 或 SSE）
4. **初始化**：MCP 客户端发送 `initialize` → 服务器响应协议版本和能力声明
5. **发现**：客户端调用 `tools/list`、`resources/list`、`prompts/list` 获取能力列表
6. **调用**：客户端调用 `tools/call` → 参数自动转换为 Java 类型 → 方法反射执行
7. **响应**：返回值序列化为 JSON，以 MCP 工具结果格式返回（含 `content` 数组和 `isError` 标记）

所有 JSON-RPC 2.0 通信由 `JsonRpcSerializer` 基于 Jackson 处理。

可以通过 `JsonRpcSerializer.getMapper()` 获取底层 `ObjectMapper` 进行自定义：

```java
// 在应用启动时注册自定义 Jackson 模块
JsonRpcSerializer.getMapper()
    .registerModule(new JavaTimeModule());
```

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
