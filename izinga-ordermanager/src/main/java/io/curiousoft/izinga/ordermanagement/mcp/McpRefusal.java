package io.curiousoft.izinga.ordermanagement.mcp;

/**
 * ADR-026 D2: Constants for MCP boundary refusal messages.
 *
 * <p>Phase 0 defines only the {@link #NOT_MCP_REQUEST} constant used by
 * {@link GuardedToolCallback}'s isMcpRequest assert. Additional refusal constants
 * (identity violation, audience violation) will be added when OQ-4 is decided and
 * the wrapper takes over audience gating and audit writes.</p>
 *
 * <p>These constants are intentionally separate from the message text in
 * {@code CallerIdentityValidator} in izinga-commons. When OQ-4 ships, the duplicate
 * literals in service classes will be removed and this class will be the single
 * source of truth for all MCP boundary refusal text.</p>
 */
public final class McpRefusal {

    private McpRefusal() { /* constants class */ }

    /**
     * Refusal message emitted when {@link GuardedToolCallback} detects that the tool
     * is being invoked outside an MCP request thread (isMcpRequest() == false).
     *
     * <p>This guard fires ONLY if a {@link GuardedToolCallback} is somehow invoked
     * outside the MCP servlet path: a scheduler, an internal caller, or a future
     * transport where ThreadLocals do not propagate. It CANNOT fire on any current
     * {@code /mcp} path because {@code StoreScopeValidationFilter} sets
     * {@code IS_MCP_REQUEST=true} unconditionally for every {@code /mcp/**} request.</p>
     */
    public static final String NOT_MCP_REQUEST =
            "IDENTITY_REFUSAL: This tool callback was invoked outside the MCP request path. " +
            "Direct internal invocation of an MCP controller is not permitted. " +
            "Please contact iZinga support if you need further help.";
}
