package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * ADR-029: MCP controller for WhatsApp outbound notification tools.
 *
 * <p>Owns two tools:
 * <ul>
 *   <li>{@code send_landing_options_notification} — requires an authenticated scope token;
 *       routes via the caller's audience and permitted store ID so the correct branded
 *       WhatsApp line is used (multi-line support, REQ-23).</li>
 *   <li>{@code send_shop_options_notification} — explicitly public ({@link McpPublicTool});
 *       accepts tokenless calls and always routes via the main customer WhatsApp line.</li>
 * </ul>
 * </p>
 *
 * <h3>Gate ordering</h3>
 * <ul>
 *   <li>send_landing_options_notification: SEC-MCP-01 null-audience hard reject (with audit + throw)
 *       before any downstream call. Tokenless callers cannot trigger outbound messages.</li>
 *   <li>send_shop_options_notification: {@link McpPublicTool} — no identity gate applied;
 *       the GuardedToolCallback explicitly permits tokenless access for this method.</li>
 * </ul>
 *
 * <h3>Security note</h3>
 * <p>{@code send_shop_options_notification} is a deliberate public endpoint: it only
 * sends a generic shop-listing template and carries no caller-specific or account-sensitive
 * information. Any change that makes other tools public must go through a security review.</p>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends on {@link WhatsappNotificationService}, {@link McpCaller}, and
 * {@link AgentIdentityAuditService} (services/components), never on any {@code *Repo} or
 * {@code *Repository} type directly.</p>
 */
@McpToolController
public class NotificationMcpController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationMcpController.class);

    // ---- Exact refusal message literals (must match CallerIdentityValidator.kt) -----------

    static final String UNAUTHENTICATED_MESSAGE =
        "IDENTITY_REFUSAL: This tool requires an authenticated scope token. " +
        "Unauthenticated or tokenless requests are not permitted on the MCP surface. " +
        "Please contact iZinga support if you need further help.";

    // ---- Collaborators ------------------------------------------------------------------

    private final WhatsappNotificationService whatsappNotificationService;
    private final McpCaller mcpCaller;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public NotificationMcpController(WhatsappNotificationService whatsappNotificationService,
                                     McpCaller mcpCaller,
                                     AgentIdentityAuditService agentIdentityAuditService) {
        this.whatsappNotificationService = whatsappNotificationService;
        this.mcpCaller = mcpCaller;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- send_landing_options_notification ----------------------------------------------

    /**
     * Send a landing options WhatsApp notification to a user.
     *
     * <p>Presents available iZinga service categories to the caller. Routes the message via the
     * caller's current audience and permitted store ID so the correct branded WhatsApp line
     * and template are used (multi-line support, REQ-23 / REQ-24).</p>
     *
     * <p>Gate: SEC-MCP-01 — null audience means no scope token is present. Hard reject before
     * any downstream call so tokenless callers cannot trigger outbound WhatsApp messages.
     * Writes an async audit record then throws.</p>
     *
     * @param mobileNumber recipient's mobile number in international format, e.g. +27821234567
     * @param name         display name of the recipient
     */
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "send_landing_options_notification",
          description = "Send a landing options WhatsApp notification to a user. " +
                        "Presents available iZinga service categories to the caller. " +
                        "Requires an authenticated scope token.")
    public String sendLandingOptionsNotification(
            @ToolParam(description = "Mobile number of the recipient in international format, e.g. +27821234567")
            String mobileNumber,
            @ToolParam(description = "Display name of the recipient")
            String name) {
        // Gate: SEC-MCP-01 — null audience means no scope token. Hard reject before any downstream
        // call so tokenless callers cannot trigger outbound WhatsApp messages.
        if (CallerIdentityContext.isMcpRequest() && CallerIdentityContext.getAudience() == null) {
            agentIdentityAuditService.recordViolation(
                    StoreScopeContext.getCallerPhone(), mobileNumber,
                    AgentIdentityAuditLog.ViolationType.AUDIENCE_VIOLATION,
                    "send_landing_options_notification", "UNKNOWN", null, null);
            throw new CallerIdentityViolationException(UNAUTHENTICATED_MESSAGE);
        }
        // Route via the caller's audience and permitted storeId so the correct branded WhatsApp
        // line and template are selected (WhatsappSenderResolver.resolve / multi-line, REQ-23).
        whatsappNotificationService.sendLandingOptions(
                mobileNumber, name, null,
                mcpCaller.getAudience(), mcpCaller.getPermittedStoreId());
        LOG.debug("[NotificationMcpController] send_landing_options_notification dispatched to {}", mobileNumber);
        return "Landing options notification sent to " + mobileNumber + ".";
    }

    // ---- send_shop_options_notification -------------------------------------------------

    /**
     * Send a shop options WhatsApp notification to a user.
     *
     * <p>Presents available iZinga shops to the caller using the {@code izinga_shop_options}
     * template on the main customer WhatsApp line. This tool is intentionally public
     * ({@link McpPublicTool}) and accepts tokenless requests because it carries no caller-specific
     * or account-sensitive information — only a generic shop-listing template.</p>
     *
     * <p><strong>Security note:</strong> No identity gate is applied here. The
     * {@code GuardedToolCallback} in {@link io.curiousoft.izinga.ordermanagement.McpConfig}
     * passes through requests to methods carrying {@link McpPublicTool}.</p>
     *
     * @param mobileNumber recipient's mobile number in international format, e.g. +27821234567
     * @param name         display name of the recipient
     */
    @McpPublicTool
    @McpAudience({Audience.CUSTOMER, Audience.DRIVER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(name = "send_shop_options_notification",
          description = "Send a shop options WhatsApp notification to a user. " +
                        "Presents available iZinga shops to the caller. " +
                        "This tool is publicly accessible and does not require an authenticated scope token.")
    public String sendShopOptionsNotification(
            @ToolParam(description = "Mobile number of the recipient in international format, e.g. +27821234567")
            String mobileNumber,
            @ToolParam(description = "Display name of the recipient")
            String name) {
        // @McpPublicTool: intentionally accepts unauthenticated requests.
        // No identity gate. Routes via the main customer line (Audience.CUSTOMER, no store scope).
        whatsappNotificationService.sendShopOptions(mobileNumber, name);
        LOG.debug("[NotificationMcpController] send_shop_options_notification dispatched to {}", mobileNumber);
        return "Shop options notification sent to " + mobileNumber + ".";
    }
}
