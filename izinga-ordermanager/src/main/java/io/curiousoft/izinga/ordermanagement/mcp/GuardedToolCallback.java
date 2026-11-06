package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * ADR-026 D2: Decorator around a {@link ToolCallback} that asserts the caller is on
 * an MCP request thread before delegating.
 *
 * <h3>Phase 0 / OQ-4-deferred behavior</h3>
 * <p>In Phase 0 and during the migration, the decorator asserts
 * {@link CallerIdentityContext#isMcpRequest()} ONLY and fails closed if false.
 * It does NOT perform audience gating, audit writes, or refusal shaping — those
 * remain in each service/controller method body, byte-equivalent to today's
 * implementations, to preserve the three current refusal shapes:</p>
 * <ul>
 *   <li>{@code start_order}: success-shaped {@code StartOrderResponse.rejected(...)}</li>
 *   <li>{@code find_stores} / {@code find_store_or_shops_by_id}: prefix-less
 *       {@code AudienceViolationException}</li>
 *   <li>All other 15 tools: {@code IDENTITY_REFUSAL:}-prefixed
 *       {@code CallerIdentityViolationException}</li>
 * </ul>
 *
 * <h3>Why the isMcpRequest assert cannot fire on /mcp paths</h3>
 * <p>{@code StoreScopeValidationFilter.doFilterInternal()} calls
 * {@link CallerIdentityContext#setMcpRequest(boolean) setMcpRequest(true)} unconditionally
 * before token parsing (line 76 in the filter, before the scope-param branch). So for every
 * {@code /mcp/**} request — including tokenless ones — {@code isMcpRequest()} is {@code true}.
 * The fail-closed assert therefore cannot fire on any current {@code /mcp} path. It fires
 * only if a {@code GuardedToolCallback} is invoked outside the MCP servlet path: a scheduler,
 * an internal caller, or a future transport where ThreadLocals do not propagate.</p>
 *
 * <h3>When OQ-4 is decided</h3>
 * <p>The wrapper is extended to take over steps 2–5: null-audience reject, allowlist reject,
 * audit write, standard {@code IDENTITY_REFUSAL:} throw. Controller bodies shrink.
 * {@code McpAccessMatrixTest} is updated deliberately to assert the unified shapes.</p>
 *
 * <h3>Tool contract preservation</h3>
 * <p>The delegate's {@link ToolDefinition} (name, description, inputSchema) is passed
 * through byte-identical. {@link ToolMetadata} is also delegated. The
 * {@code McpToolContractSnapshotTest} enforces this.</p>
 *
 * <h3>No AOP</h3>
 * <p>This class is a plain decorator — no Spring AOP, no aspect, no
 * {@code spring-boot-starter-aop}. The guard runs only when the MCP server dispatches
 * a call; an internal Java call can never accidentally pass through it.</p>
 */
public final class GuardedToolCallback implements ToolCallback {

    private static final Logger LOG = LoggerFactory.getLogger(GuardedToolCallback.class);

    private final ToolCallback delegate;

    /**
     * Create a new {@code GuardedToolCallback} wrapping the given delegate.
     *
     * @param delegate the underlying {@link ToolCallback} whose
     *                 {@link ToolDefinition} and call logic are preserved
     * @throws IllegalArgumentException if delegate is null
     */
    public GuardedToolCallback(ToolCallback delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("GuardedToolCallback delegate must not be null");
        }
        this.delegate = delegate;
    }

    /**
     * Returns the delegate's {@link ToolDefinition} byte-identical.
     * The contract (name, description, inputSchema) is never changed by this decorator.
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /**
     * Returns the delegate's {@link ToolMetadata} byte-identical.
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * Phase 0 guard: assert that the calling thread is on an MCP request path
     * (i.e. {@link CallerIdentityContext#isMcpRequest()} is {@code true}), then
     * delegate to the wrapped {@link ToolCallback}.
     *
     * <p>If the assert fires, a {@link CallerIdentityViolationException} is thrown
     * with {@link McpRefusal#NOT_MCP_REQUEST} as the message. Spring AI's
     * {@code McpToolUtils} will convert any exception from this method into a
     * {@code CallToolResult(text = e.getMessage(), isError = true)}.</p>
     *
     * <p>This guard CANNOT fire on any current {@code /mcp} path — see class Javadoc.</p>
     *
     * @param toolInput JSON-serialised input arguments
     * @return the delegate's result
     * @throws CallerIdentityViolationException if called outside an MCP request thread
     */
    @Override
    public String call(String toolInput) {
        assertMcpRequest();
        return delegate.call(toolInput);
    }

    /**
     * Phase 0 guard with ToolContext: same isMcpRequest assert, then delegate.
     *
     * @param toolInput JSON-serialised input arguments
     * @param toolContext Spring AI tool execution context
     * @return the delegate's result
     * @throws CallerIdentityViolationException if called outside an MCP request thread
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        assertMcpRequest();
        return delegate.call(toolInput, toolContext);
    }

    /**
     * Tier-1 guard: fail closed if not on an MCP request thread.
     *
     * <p>See class Javadoc for why this cannot fire on any current {@code /mcp} path.</p>
     */
    private void assertMcpRequest() {
        if (!CallerIdentityContext.isMcpRequest()) {
            String toolName = delegate.getToolDefinition() != null
                    ? delegate.getToolDefinition().name() : "<unknown>";
            LOG.error("[MCP-GUARD] FAIL-CLOSED: GuardedToolCallback invoked outside MCP request path for tool={}. " +
                      "This is a programming error — internal callers must never invoke GuardedToolCallback.", toolName);
            throw new CallerIdentityViolationException(McpRefusal.NOT_MCP_REQUEST);
        }
    }

    /**
     * Returns the wrapped delegate. Package-private for testing only.
     */
    ToolCallback getDelegate() {
        return delegate;
    }
}
