package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.recon.ReconServiceImpl;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <h3>SA-021-18: Store-modification tool rule</h3>
 * <p>Any future {@code @Tool} method that modifies store data (hours, stock, settings, etc.)
 * MUST be annotated with
 * {@code @io.curiousoft.izinga.ordermanagement.security.RequiresAudience(io.curiousoft.izinga.messaging.whatsapp.lines.Audience.STORE)}
 * AND MUST call
 * {@code io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator.validate(storeId)}
 * as its FIRST statement before any data access or mutation.
 * Omitting either guard on a store-modifying tool is a security defect that MUST be caught
 * at code-review and rejected before merge.</p>
 *
 * <h3>SA-022-01: Order-creation and order-modification tool rule</h3>
 * <p>Any future {@code @Tool} method that creates or modifies an order document MUST:</p>
 * <ol>
 *   <li><strong>Validate customer identity:</strong> confirm that {@code customerId} resolves to an
 *       existing {@link io.curiousoft.izinga.commons.model.UserProfile} in the repository before
 *       passing anything to {@code OrderServiceImpl}. Reject immediately with a structured error
 *       response if the profile does not exist — never reach domain logic without confirmed
 *       identity binding.</li>
 *   <li><strong>Require explicit customer consent:</strong> enforce a hard server-side
 *       {@code customerConfirmed=true} gate at the MCP layer. Reject with a structured error
 *       if the flag is absent, {@code null}, or {@code false}. This flag is a mandatory consent
 *       signal from the AI agent, not an optional hint.</li>
 *   <li><strong>Carry a non-null idempotency key:</strong> reject immediately if
 *       {@code idempotencyKey} is null or blank. Perform a deduplication lookup against recent
 *       orders bearing the same key for the same customer before persisting. Return the existing
 *       order if a duplicate is detected rather than throwing.</li>
 * </ol>
 * <p>Omitting any one of these three guards on an order-creating or order-modifying
 * {@code @Tool} method is a security defect that MUST be caught at code review and
 * rejected before merge.</p>
 * <p><strong>Known gap, tracked as follow-up (not blocking this feature):</strong> guard (1) currently
 * validates only that {@code customerId} exists as a UserProfile record — it is a bare tool
 * parameter, not yet cryptographically bound to the caller's scope token the way {@code storeId}
 * and {@code audience} already are for store-scoped tools (see {@link io.curiousoft.izinga.ordermanagement.security.RequiresAudience} /
 * {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator}). Binding {@code customerId} into the scope token for CUSTOMER-audience
 * calls would close the gap where an AI agent could pass a customerId that exists in the database
 * but isn't the customer actually in the current conversation. Lindani deferred this strengthening
 * to a follow-up rather than blocking this feature on it (2026-10-20) — raise a backlog ticket.</p>
 */
@Configuration
public class McpConfig {

    /**
     * Exposes all MCP tools registered with this application.
     *
     * <p>The authoritative tool list (names, descriptions, parameter schemas) is maintained in
     * {@code src/test/resources/mcp/tool-contract-snapshot.json}.
     * {@link io.curiousoft.izinga.ordermanagement.mcp.McpToolContractSnapshotTest} verifies that
     * the live tool set matches the snapshot on every build.</p>
     *
     * <p>During the ADR-026 migration, legacy tool objects are listed in {@code legacyObjects}
     * and new {@link io.curiousoft.izinga.ordermanagement.mcp.McpToolController} beans are
     * collected from the {@link ApplicationContext} and wrapped in
     * {@link io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback}.</p>
     */
    @Bean
    public ToolCallbackProvider userProfileToolCallbackProvider(UserProfileService userProfileService,
                                                                OrderServiceImpl orderService,
                                                                ReconService reconService,
                                                                StoreService storeService,
                                                                ApplicationContext applicationContext) {
        // Collect legacy service tool objects (Phase 1 baseline — 13 tools; 5 moved to controllers)
        Object[] legacyObjects = {userProfileService, orderService, reconService, storeService};

        // Collect @McpToolController beans and wrap their callbacks in GuardedToolCallback
        Map<String, Object> controllerBeans =
                applicationContext.getBeansWithAnnotation(McpToolController.class);

        // Combine legacy + controller objects into a single MethodToolCallbackProvider
        Object[] allToolObjects = Stream
                .concat(Arrays.stream(legacyObjects), controllerBeans.values().stream())
                .toArray();
        MethodToolCallbackProvider combined = MethodToolCallbackProvider.builder()
                .toolObjects(allToolObjects)
                .build();

        // No controller beans → return provider as-is (no GuardedToolCallback wrapping needed)
        if (controllerBeans.isEmpty()) {
            return combined;
        }

        // Phase 1+: identify which tool names came from controller beans, wrap only those
        Set<String> controllerToolNames = controllerBeans.values().stream()
                .flatMap(bean -> {
                    ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                            .toolObjects(bean)
                            .build()
                            .getToolCallbacks();
                    return Arrays.stream(callbacks).map(tc -> tc.getToolDefinition().name());
                })
                .collect(Collectors.toSet());

        ToolCallback[] guarded = Arrays.stream(combined.getToolCallbacks())
                .map(tc -> controllerToolNames.contains(tc.getToolDefinition().name())
                        ? new GuardedToolCallback(tc)
                        : tc)
                .toArray(ToolCallback[]::new);

        return () -> guarded;
    }
}
