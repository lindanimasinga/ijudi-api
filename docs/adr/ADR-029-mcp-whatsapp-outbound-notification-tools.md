# ADR-029: MCP WhatsApp Outbound Notification Tools

**Status**: Accepted  
**Date**: 2026-11-07  
**Authors**: iZinga Backend Developer  
**Reviewers**: iZinga Solution Architect, iZinga Security & Compliance

---

## Context

The MCP surface currently exposes tools for data lookup, order management, and document handling.
AI agents interacting with iZinga users on WhatsApp need to send outbound WhatsApp template messages
as part of conversational flows — specifically to present landing options (service categories) and
shop options (available stores) to users who have initiated contact.

Two new tools are required:

1. `send_landing_options_notification` — sends the `izinga_landing_options` template, presenting
   iZinga service categories. This tool is called by authenticated agents that already have a
   validated scope token (audience + optional store scope), and must route via the caller's branded
   WhatsApp line.

2. `send_shop_options_notification` — sends the `izinga_shop_options` template, presenting
   available iZinga shops. This message contains no caller-specific or account-sensitive information
   and may be initiated by unauthenticated entry-point agents (e.g. the initial WhatsApp onboarding
   flow before a scope token is established).

---

## Decision

### Tool 1: `send_landing_options_notification`

Implemented in `NotificationMcpController.sendLandingOptionsNotification()`.

- **Gate**: SEC-MCP-01 null-audience hard reject. If `CallerIdentityContext.isMcpRequest()` is true
  and `CallerIdentityContext.getAudience()` is null (no scope token), the method:
  1. Calls `agentIdentityAuditService.recordViolation(callerPhone, mobileNumber, AUDIENCE_VIOLATION,
     "send_landing_options_notification", "UNKNOWN", null, null)`.
  2. Throws `CallerIdentityViolationException` with the standard `IDENTITY_REFUSAL:` message.
- **Routing**: After the gate, delegates to the 5-argument overload
  `WhatsappNotificationService.sendLandingOptions(mobileNumber, name, null,
   mcpCaller.getAudience(), mcpCaller.getPermittedStoreId())`. This routes via
  `WhatsappSenderResolver.resolve(audience, storeId)` so the correct branded WhatsApp phone ID
  is used for multi-line deployments.
- **Audiences**: `@McpAudience({CUSTOMER, DRIVER, STORE, ADMIN, OTP})` — all five audiences allowed
  post-gate.

### Tool 2: `send_shop_options_notification`

Implemented in `NotificationMcpController.sendShopOptionsNotification()`.

- **Access**: Annotated `@McpPublicTool` — explicitly public. No identity gate is applied.
  `GuardedToolCallback` skips the SEC-MCP-01 null-audience check for methods carrying this annotation.
- **Routing**: Delegates to `WhatsappNotificationService.sendShopOptions(mobileNumber, name)`,
  which always routes via `senderResolver.resolve(Audience.CUSTOMER, null)` — the main customer
  WhatsApp line.
- **Audiences**: `@McpAudience({CUSTOMER, DRIVER, STORE, ADMIN, OTP})` — all five audiences.
  Tokenless callers also pass through (public tool).
- **Rationale for public access**: The `izinga_shop_options` template contains only a list of
  available shop categories — no personal data, no account references, no caller-specific content.
  Allowing unauthenticated access carries minimal risk and enables the entry-point onboarding flow.

### New annotation: `@McpPublicTool`

A new `@Target(METHOD) @Retention(RUNTIME)` annotation `McpPublicTool` is introduced in
`izinga-ordermanager`. It is the ONLY mechanism by which a tool method opts out of the
SEC-MCP-01 null-audience gate. It must NOT be added to any existing tool (doing so would be a
security regression). The ArchUnit R7 rule (see below) enforces this invariant structurally.

### New service method: `WhatsappNotificationService.sendShopOptions`

Added `sendShopOptions(String mobileNumber, String name)` to `WhatsappNotificationService` in
`izinga-messaging`. Follows the same pattern as `sendLandingOptions`: phone normalization
(`0` → `+27`), JSON template string construction (template name `izinga_shop_options`, language `en`,
single BODY TEXT parameter #name), `mapper.readValue`, `whatsAppService.sendMessage().execute()`,
`catch (IOException e)` with error logging. Failure is non-fatal — the message is logged and
the tool continues.

---

## ArchUnit Rule R7

A new ArchUnit rule `R7_toolMethodsMustHaveIdentityGateOrBePublicTool` is added to
`McpArchitectureTest`. The rule applies to all `@Tool`-annotated methods in `@McpToolController`
classes that are NOT annotated `@McpPublicTool`, and asserts each such method either:

- Directly calls a gate method in `CallerIdentityValidator`, `StoreScopeValidator`, or
  `CallerIdentityContext.isMcpRequest`, OR
- Delegates to a private helper in the same class that calls such a gate method (two-level
  traversal, required for `DocumentUploadMcpController.enforceCallerPhoneOrAudit()`).

Non-vacuity is proven by `R7ViolatorFixture` — a deliberately misconfigured `@McpToolController`
in package `fixtures.izinga.violations` (outside the production component scan) whose `@Tool`
method has no gate and no `@McpPublicTool`. The fixture test asserts that R7 raises an
`AssertionError` containing "R7 violation" on this fixture.

---

## Consequences

### Additive changes only
- One new annotation (`McpPublicTool`) added to `izinga-ordermanager`.
- One new controller (`NotificationMcpController`) added to `izinga-ordermanager`.
- One new service method (`sendShopOptions`) added to `WhatsappNotificationService` in
  `izinga-messaging`.
- No existing API paths, HTTP methods, or tool names are changed.
- No existing tools are reclassified as public.

### Tool count
- Previous: 17 tools across 6 `@McpToolController` classes.
- After ADR-029: 19 tools across 7 `@McpToolController` classes.

### Security invariants preserved
- All 17 existing tools continue to reject null/missing audience (tokenless calls) byte-for-byte
  unchanged. The `@McpPublicTool` annotation is not added to any existing tool.
- `GuardedToolCallback`, `CallerIdentityValidator`, `StoreScopeValidationFilter`, and `McpConfig`
  are NOT modified. The public-access mechanism is entirely contained in the new annotation and
  the new controller method.

### Test coverage
- `NotificationMcpControllerTest`: 9 unit tests covering null-audience DENY and all 5 audience
  ALLOW paths for Tool 1; null-audience and 2 audience ALLOW paths for Tool 2. 100% branch coverage.
- `McpArchitectureTest`: R7 ArchTest + non-vacuity guard (`R7ViolatorFixture`), updated tool count
  (17→19), updated controller count (6→7), updated tool name list.
- `McpToolContractSnapshotTest`: Updated EXPECTED_TOOL_COUNT (17→19), new tool names added,
  `NotificationMcpController` wired. Golden file regenerated.
- `McpAccessMatrixTest`: 12 new matrix rows (6 for each tool × all audience combinations),
  2 new downstream verifier cases.

### Open questions
None. This ADR supersedes no previous ADR and introduces no cross-module contract changes.

---

## Alternatives Considered

**Add gate directly in `GuardedToolCallback` conditional on tool name**: Rejected — name-based
conditionals in security infrastructure are fragile and hard to audit. The annotation-based approach
is explicit and structurally enforced by ArchUnit R7.

**Use a separate `@PublicMcpToolController` meta-annotation instead of method-level `@McpPublicTool`**:
Rejected — a class-level annotation would make ALL tools in a controller public, which is too coarse.
Method-level annotation allows mixed controllers (authenticated and public tools in the same class)
and is consistent with the existing `@McpAudience` per-method pattern.
