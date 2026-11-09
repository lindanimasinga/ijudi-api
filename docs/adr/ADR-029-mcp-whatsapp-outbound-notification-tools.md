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
  `GuardedToolCallback` does NOT read this annotation at runtime; the public behaviour comes from the
  deliberate absence of an inline identity gate in the method body. `@McpPublicTool` is a documentation
  marker and ArchUnit R7 exemption only.
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

Added `sendShopOptions(String mobileNumber, String name)` (returns `boolean`) to
`WhatsappNotificationService` in `izinga-messaging`. Phone normalization (`0` → `+27`), null/blank
guard (returns `false` early), JSON template string construction (template name `izinga_shop_options`,
language `en`, single BODY TEXT parameter #name), `mapper.readValue`,
`whatsAppService.sendMessage().execute()`. Returns `true` on success. Catches `Exception` (not just
`IOException`) to absorb unchecked `DataAccessException` from `WhatsappSenderResolver.resolve()`,
logs at ERROR, and returns `false` (AC-09 graceful degradation). The controller uses the boolean
return to report an honest outcome to the agent.

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

---

## Deferred Follow-ups

The items below were identified during security review and QA but are explicitly OUT OF SCOPE for
this ADR's implementation tranche. Each must be resolved — or accepted with a documented risk
decision — before `send_shop_options_notification` (Tool 2) is enabled in production.

### SEC-04 (MEDIUM) — Rate limit and per-number cooldown for Tool 2

**Status**: Deferred — not implemented.

**Risk**: `send_shop_options_notification` has no rate limit, cooldown, or per-number deduplication.
An AI agent loop, misconfigured flow, or malicious caller (Tool 2 is public — no token required)
could spam a user's WhatsApp number with repeated `izinga_shop_options` messages. Meta's WhatsApp
Business Policy prohibits unsolicited repeated messages; violations risk phone-number suspension.

**Recommendation**: Before enabling Tool 2 in production, add a per-number cooldown of at least
24 hours, reusing the `acquireCooldown` / `PayoutNotificationCooldownRepository` pattern introduced
in ADR-028 (feature/payout-disbursement-fixes). The cooldown key should be
`SHOP_OPTIONS:<normalised-phone>`. If the key exists, `sendShopOptions` should log at WARN and
return `false` immediately without calling the WhatsApp API.

**Gate**: Tool 2 must not be enabled in production until SEC-04 is implemented and verified.
Escalate to Lindani Masinga for production gate decision.

---

### POPIA-01 (OPEN) — Arbitrary mobile number, no opt-in check for Tool 2

**Status**: Open question — requires escalation to Lindani Masinga before production enablement.

**Risk**: Tool 2 (`send_shop_options_notification`) accepts an arbitrary mobile number from any
unauthenticated caller (no scope token required). The tool sends an `izinga_shop_options` WhatsApp
template to that number. There is no check that the number's owner has opted in to receive messages
from iZinga on WhatsApp.

South Africa's Protection of Personal Information Act (POPIA), Section 69 and Section 11, restricts
direct marketing communications and requires consent or an existing customer relationship. Meta's
WhatsApp Business Policy additionally requires that messages sent outside the 24-hour customer
service window use pre-approved templates AND that recipients have opted in.

The original design premise is that Tool 2 is called only within an ongoing WhatsApp conversation
(i.e. the user has already initiated contact — implicit consent). However, this premise is not
enforced at the tool layer: any unauthenticated caller can invoke the tool with any number.

**Question for Lindani**: Is the "user initiated the WhatsApp conversation" premise sufficient for
POPIA and Meta policy compliance? If yes, should this be documented as an accepted risk? If no,
what opt-in signal should be checked before sending?

**Gate**: Tool 2 must not be enabled in production until POPIA-01 is answered and the decision
is recorded (either as a code-level guard or as an accepted-risk ADR amendment signed off by
Lindani).
