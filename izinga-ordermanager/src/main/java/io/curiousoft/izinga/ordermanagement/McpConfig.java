package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
 *
 * <h3>SA-021-18: Store-modification tool rule (ADR-026 Phase 6 updated)</h3>
 * <p>Any future {@code @Tool} method that modifies store data (hours, stock, settings, etc.)
 * MUST live on a class annotated {@link McpToolController}, carry
 * {@code @io.curiousoft.izinga.ordermanagement.mcp.McpAudience} including
 * {@code io.curiousoft.izinga.messaging.whatsapp.lines.Audience.STORE}, and call
 * {@code io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator.validate(storeId)}
 * as the FIRST statement in the controller method body before any data access or mutation.
 * Omitting any of these on a store-modifying tool is a security defect that MUST be caught
 * at code-review and rejected before merge.</p>
 *
 * <h3>SA-022-01: Order-creation and order-modification tool rule (ADR-026 Phase 6 updated)</h3>
 * <p>Any future {@code @Tool} method that creates or modifies an order document MUST:</p>
 * <ol>
 *   <li><strong>Validate customer identity (controller layer):</strong> confirm that {@code customerId}
 *       resolves to an existing {@link io.curiousoft.izinga.commons.model.UserProfile} in the
 *       repository before passing anything to {@code OrderServiceImpl}. Reject immediately with a
 *       structured error response if the profile does not exist.</li>
 *   <li><strong>Require explicit customer consent (controller layer):</strong> enforce a hard server-side
 *       {@code customerConfirmed=true} gate. Reject with a structured error if the flag is absent,
 *       {@code null}, or {@code false}.</li>
 *   <li><strong>Carry a non-null idempotency key (controller layer):</strong> reject immediately if
 *       {@code idempotencyKey} is null or blank. Deduplicate before persisting.</li>
 * </ol>
 * <p>Omitting any of these three guards is a security defect that MUST be caught at code review
 * and rejected before merge.</p>
 * <p><strong>Known gap (SA-022-01 follow-up, not blocking):</strong> guard (1) currently validates
 * only that {@code customerId} exists as a UserProfile record — it is a bare tool parameter, not yet
 * cryptographically bound to the caller's scope token the way {@code storeId} and {@code audience}
 * are for store-scoped tools. Binding {@code customerId} into the scope token for CUSTOMER-audience
 * calls would close the gap where an AI agent could pass a customerId that exists in the database
 * but isn't the customer actually in the current conversation (deferred 2026-10-20).</p>
 */
@Configuration
public class McpConfig {

    /**
     * ADR-026 Phase 6 (T-07 / D6): Exposes all MCP tools registered with this application.
     *
     * <p>The authoritative tool list (names, descriptions, parameter schemas) is maintained in
     * {@code src/test/resources/mcp/tool-contract-snapshot.json}.
     * {@link io.curiousoft.izinga.ordermanagement.mcp.McpToolContractSnapshotTest} verifies that
     * the live tool set matches the snapshot on every build.</p>
     *
     * <p>ADR-026 Phase 6 lock-down: the legacy tool-object list is gone. ALL 17 tools now live
     * on {@link McpToolController} beans (create_user removed from the MCP surface in Phase 2b,
     * Lindani 2026-11-05). This method collects those beans from the
     * {@link ApplicationContext}, assembles a single {@link MethodToolCallbackProvider}, and wraps
     * every callback in a {@link GuardedToolCallback}.</p>
     *
     * <h3>Fail-fast duplicate-name guard</h3>
     * <p>If two controllers register the same tool name (a configuration error), this method
     * throws {@link IllegalStateException} at startup, preventing silent tool shadowing.</p>
     */
    @Bean
    public ToolCallbackProvider userProfileToolCallbackProvider(ApplicationContext applicationContext) {
        // ADR-026 Phase 6: collect ALL tool objects from @McpToolController beans only.
        // No legacy service objects remain — every @Tool method now lives on a controller bean.
        Map<String, Object> controllerBeans =
                applicationContext.getBeansWithAnnotation(McpToolController.class);

        // Fail-fast: detect duplicate tool names across controllers.
        // MethodToolCallbackProvider itself throws on duplicates, but we produce a
        // clearer diagnostic here before reaching the provider build.
        Set<String> seen = new HashSet<>();
        for (Object bean : controllerBeans.values()) {
            ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(bean)
                    .build()
                    .getToolCallbacks();
            for (ToolCallback tc : callbacks) {
                String name = tc.getToolDefinition().name();
                if (!seen.add(name)) {
                    throw new IllegalStateException(
                            "Duplicate MCP tool name '" + name + "' registered across @McpToolController beans. " +
                            "Each tool name must be unique. Offending bean: " + bean.getClass().getSimpleName());
                }
            }
        }

        // Build the combined provider from all controller beans.
        MethodToolCallbackProvider combined = MethodToolCallbackProvider.builder()
                .toolObjects(controllerBeans.values().toArray())
                .build();

        // Wrap every callback in GuardedToolCallback (Phase 1+ behaviour, now unconditional).
        ToolCallback[] guarded = Arrays.stream(combined.getToolCallbacks())
                .map(GuardedToolCallback::new)
                .toArray(ToolCallback[]::new);

        return () -> guarded;
    }
}
