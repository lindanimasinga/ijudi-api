# ADR-030: Remove New-Session Pre-Dispatch Shortcut in WhatsApp Inbound Handler

**Date:** 2026-11-10
**Status:** Accepted
**Deciders:** Lindani Masinga (CEO), iZinga Backend Developer

---

## Context

`WhatsappInboundEventHandler.handlePreDispatchFlows()` contained a shortcut block (lines ~194–207 on the branch before this fix):

```java
if (session.isNewSession()) {
    if (lineContext.audience() != Audience.DRIVER) {
        whatsappNotificationService.sendLandingOptions(...);
    }
    return null;  // ← AI dispatch NEVER reached on new sessions
}
```

This block sent a landing-options WhatsApp template directly and returned `null`, preventing the AI agent from ever being invoked on the first message of a session. The result:

- The AI agent was never called on new sessions.
- For existing sessions, the AI agent WAS called but could not invoke MCP tools because `useTools` was `false` on MongoDB documents (see ADR root-cause B, addressed in the same PR).
- Effectively: zero MCP tool calls ever dispatched — the AI verbally described actions but never performed them.

Lindani's instruction: "the issue I was pointing at was to remove that if statement on the line I shared so that AI is always the one deciding on which templates to use. Don't restructure more than that one conditional."

---

## Decision

Remove the `if (session.isNewSession())` block entirely from `handlePreDispatchFlows()`. Add a single DEBUG-level log line before the AI dispatch check to aid observability.

The landing-options notification responsibility is now fully delegated to the AI agent, which invokes the `send_landing_options_notification` MCP tool when appropriate. The AI decides which template to use, based on audience, session context, and any line-specific `landingTemplateName`.

---

## Consequences

### Preserved
- Verification-consent handling (`isVerificationMessage`) runs before AI dispatch — unchanged.
- The `sendLandingOptions` method in `WhatsappNotificationService` is NOT deleted — it is still used by:
  - `NotificationMcpController.java` (MCP tool endpoint)
  - `SchedulerService.java` (re-engagement scheduler)
- SA-5 invariant preserved: DRIVER lines never receive customer landing options. The AI agent respects this via the `send_landing_options_notification` tool's `audience` parameter and its system prompt.
- `WhatsappSession.isAIAgentActive` defaults to `true` on new sessions — confirming the AI is active on first message.

### New behavior
- Every inbound WhatsApp message, including the very first message of a new session, reaches the AI agent (if enabled and `isAIAgentActive=true`).
- The AI agent sends the appropriate landing options template via the MCP tool, allowing it to tailor the template selection per audience and line configuration.

### Risk
- If the AI agent is disabled (`ai.agent.enabled=false`) or the session has `isAIAgentActive=false`, no landing options are sent for new sessions. This is a known gap: a future follow-up task (non-AI fallback for new sessions when AI is disabled) can address this if the product requires it.

---

## Files Changed

- `izinga-messaging/.../whatsapp/webhooks/WhatsappInboundEventHandler.java` — block removed
- `izinga-messaging/.../whatsapp/webhooks/WhatsappInboundEventHandlerTest.java` — test updated
- `izinga-messaging/.../whatsapp/webhooks/StoreLineWebhookIntegrationTest.java` — tests updated
