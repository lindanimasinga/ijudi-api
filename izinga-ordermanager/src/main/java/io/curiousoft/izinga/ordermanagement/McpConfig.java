package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Spring AI MCP (Model Context Protocol) server.
 *
 * <p>The MCP server exposes this application's capabilities as tools that can be
 * consumed by AI assistants (e.g. GitHub Copilot, Claude, GPT) through the
 * SSE transport at {@code GET /sse} (event stream) and
 * {@code POST /mcp/message} (JSON-RPC messages).</p>
 *
 * <p>Each bean of type {@link ToolCallbackProvider} is automatically picked up by
 * {@code McpServerAutoConfiguration} and registered as a set of MCP tools.</p>
 */
@Configuration
public class McpConfig {

    /**
     * Exposes {@link UserProfileService} methods annotated with
     * {@code @Tool} as MCP tools.
     *
     * <p>Currently registered tools:
     * <ul>
     *   <li>{@code find_user_by_phone} – look up a user profile by phone number.</li>
     *   <li>{@code create_user} – create a new user profile (customer, driver, or store owner).</li>
     * </ul>
     */
    @Bean
    public ToolCallbackProvider userProfileToolCallbackProvider(UserProfileService userProfileService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(userProfileService)
                .build();
    }
}
