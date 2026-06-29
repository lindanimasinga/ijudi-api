package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke-test for the MCP server SSE endpoint.
 *
 * <p>This test starts the full Spring Boot application and verifies that the
 * MCP SSE endpoint is accessible without authentication.  It is marked
 * {@link Disabled} so it does not run in CI (requires network-accessible
 * MongoDB); remove the annotation to run locally.</p>
 *
 * <h2>VS Code MCP Configuration</h2>
 * <p>When the server is running (e.g. {@code mvn spring-boot:run}), configure
 * VSCode as follows:</p>
 *
 * <h3>Option 1 – GitHub Copilot extension (v1.220+)</h3>
 * <pre>
 * 1. Open Command Palette (Ctrl+Shift+P / Cmd+Shift+P).
 * 2. Run: "MCP: Add Server".
 * 3. Choose "HTTP (SSE)" transport.
 * 4. Enter URL: http://localhost:80/sse
 * 5. Give the server a name (e.g. "izinga-api").
 * 6. Click "Connect" – you will see the tools listed under Copilot Chat.
 * </pre>
 *
 * <h3>Option 2 – settings.json / mcp.json (global or workspace)</h3>
 * <pre>
 * // .vscode/mcp.json (workspace-level)
 * {
 *   "servers": {
 *     "izinga-api": {
 *       "type": "sse",
 *       "url": "http://localhost:80/sse"
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>Once connected, AI assistants can invoke tools like:</p>
 * <ul>
 *   <li>{@code find_user_by_phone} – "Find the user with phone 0812345678"</li>
 *   <li>{@code create_user} – "Create a new customer named Alice with phone 0712345678"</li>
 * </ul>
 */
@Disabled("Requires a running MongoDB instance – run manually for local smoke testing")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class McpSseEndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Verifies that the SSE endpoint responds without requiring authentication.
     * The endpoint streams text/event-stream so the response will be 200 OK
     * (possibly with no body content in a short-lived HTTP request).
     */
    @Test
    void sseEndpoint_isAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/sse", String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "/sse must not require authentication");
        assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "/sse must not be forbidden for unauthenticated clients");
    }

    /**
     * Verifies that the MCP message endpoint returns a non-5xx response
     * when called with a valid JSON-RPC initialize request.
     */
    @Test
    void mcpMessageEndpoint_acceptsInitializeRequest() {
        String initRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": { "roots": { "listChanged": true }, "sampling": {} },
                    "clientInfo": { "name": "vscode-test-client", "version": "1.0.0" }
                  }
                }
                """;

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Content-Type", "application/json");
        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(initRequest, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/mcp/message", entity, String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "/mcp/message must not require authentication");
        assertTrue(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is4xxClientError(),
                "Expected non-5xx response but got: " + response.getStatusCode());
    }
}
