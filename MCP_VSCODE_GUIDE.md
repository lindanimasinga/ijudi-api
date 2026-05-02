# MCP Server – VS Code Connection Guide

The Izinga API exposes a **Model Context Protocol (MCP)** server using Spring AI.  
AI assistants (GitHub Copilot Chat, Claude, etc.) can discover and invoke the registered tools over HTTP/SSE — without any custom client code.

---

## How it works

```
VS Code / AI Client
      │
      │  GET /sse          (Server-Sent Events – tool discovery stream)
      ├──────────────────► Spring Boot (port 80)
      │
      │  POST /mcp/message (JSON-RPC 2.0 – tool invocations)
      └──────────────────► Spring Boot (port 80)
```

The server auto-discovers every Spring bean whose methods carry `@Tool` (from `org.springframework.ai.tool.annotation`).  
The `McpConfig` class wires those beans into a `ToolCallbackProvider`, which `McpServerAutoConfiguration` picks up automatically.

### Registered tools

| Tool name | Description |
|-----------|-------------|
| `find_user_by_phone` | Look up a user profile by phone number (tries 0, +27, 27 prefixes automatically). |
| `create_user` | Create a new user profile – customer, driver, or store owner – based on the `role` field. |

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Java 17+ | Set `JAVA_HOME` appropriately. |
| Maven 3.8+ | Used to build and run the project. |
| MongoDB | The app reads/writes to MongoDB. Use the URI in `application.yml` or point it at a local instance. |
| VS Code | Any recent version. |
| GitHub Copilot extension (≥ v1.220) **or** any MCP-compatible extension | Copilot Chat supports MCP natively since early 2025. |

---

## Step 1 – Start the server

```bash
# From the repository root
mvn clean install -DskipTests          # build once
cd izinga-ordermanager
mvn spring-boot:run                    # starts on port 80
```

Verify the MCP endpoint is reachable (you should see an SSE stream open and stay alive):

```bash
curl -N http://localhost:80/sse
# expected: event:endpoint\ndata:/mcp/message?sessionId=...
```

---

## Step 2 – Connect VS Code (GitHub Copilot extension)

### Option A – Command Palette (recommended)

1. Open the Command Palette: **Ctrl+Shift+P** (Windows/Linux) or **Cmd+Shift+P** (macOS).
2. Run: **MCP: Add Server**.
3. Choose transport type: **HTTP (SSE)**.
4. Enter the server URL:
   ```
   http://localhost:80/sse
   ```
5. Enter a display name, e.g. `izinga-api`.
6. Click **Connect**.
7. Open **Copilot Chat** (Ctrl+Alt+I / Cmd+Alt+I). You should see a tool icon 🔧 listing the available tools.

### Option B – Workspace `.vscode/mcp.json`

Create the file `.vscode/mcp.json` in the root of your VS Code workspace:

```json
{
  "servers": {
    "izinga-api": {
      "type": "sse",
      "url": "http://localhost:80/sse"
    }
  }
}
```

Reload the window (**Developer: Reload Window**). Copilot Chat will pick up the server automatically.

### Option C – User-level `settings.json`

Open **File → Preferences → Settings** (or press Ctrl+,), switch to the JSON view, and add:

```json
{
  "mcp": {
    "servers": {
      "izinga-api": {
        "type": "sse",
        "url": "http://localhost:80/sse"
      }
    }
  }
}
```

---

## Step 3 – Use the tools in Copilot Chat

Once connected, talk to Copilot Chat naturally:

```
> Find the user with phone number 0812345678
```
Copilot will invoke `find_user_by_phone` and show the result.

```
> Create a new customer named Alice with phone 0721234567 at address "10 Long St, Cape Town"
```
Copilot will invoke `create_user` with the appropriate `UserProfile` payload.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `curl /sse` returns 401 Unauthorized | Security config not updated | Confirm `SecurityConfig.java` permits `/sse` and `/mcp/message` |
| `NoSuchBeanDefinitionException: McpSyncServer` | No `ToolCallbackProvider` bean | Ensure `McpConfig` is on the component-scan path (`io.curiousoft.izinga.ordermanagement`) |
| VS Code shows "Connection refused" | Server not running | Run `mvn spring-boot:run` in `izinga-ordermanager` |
| `NoClassDefFoundError: AnnotationHelper` | Version conflict in `jsonschema-generator` | Check that `pom.xml` excludes the old 4.32.0 jar and forces 4.37.0 |
| Tools appear but invocation fails | JSON schema mismatch | Check tool method signatures match `@Tool` description; use `@ToolParam` to clarify parameters |

---

## Configuration reference

All MCP server settings live under `spring.ai.mcp.server` in `application.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        name: izinga-mcp-server      # shown in Copilot tool list
        version: 1.0.0
        type: sync                   # sync | async
        sse-endpoint: /sse           # SSE discovery endpoint
        sse-message-endpoint: /mcp/message   # JSON-RPC endpoint
        instructions: >
          Izinga marketplace API. Use the available tools to look up users
          and stores, create orders, and manage deliveries.
```

To add a new tool:

1. Add `@Tool(name = "my_tool", description = "…")` to a Spring service method.
2. If the service is **not** already added to `McpConfig`, add it:

```java
@Bean
public ToolCallbackProvider myServiceToolCallbackProvider(MyService svc) {
    return MethodToolCallbackProvider.builder().toolObjects(svc).build();
}
```

3. Restart the server — Copilot Chat will see the new tool immediately.
