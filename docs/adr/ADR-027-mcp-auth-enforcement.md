# ADR-027: MCP Surface Authentication Enforcement (SEC-MCP-01 / SEC-MCP-02)

**Status:** Accepted
**Date:** 2026-11-02
**Issues:** #153 (SEC-MCP-01), #154 (SEC-MCP-02)
**Branch:** fix/mcp-tokenless-auth-bypass

---

## Context

The iZinga MCP surface (`/mcp/**`) exposes Spring AI `@Tool`-annotated methods to AI agents. Prior to this ADR, two security gaps existed:

1. **SEC-MCP-01 — Tokenless bypass:** A null-audience request (no scope token, or an invalid/expired token) reached `@Tool` methods without any hard rejection. Tool methods relied on soft enforcement that returned null or empty results, which was insufficient — an unauthenticated caller could enumerate orders, stores, users, and driver locations.

2. **SEC-MCP-02 — Missing per-method scope gates:** Four restricted tools (`find_orders_by_messenger_id`, `find_stores_by_owner`, `find_users`, `start_order`) did not enforce caller-scope gates. A CUSTOMER-audience token could invoke `find_orders_by_messenger_id` to enumerate any messenger's order history (a cross-role data leak).

---

## Decision

### SEC-MCP-01: IS_MCP_REQUEST ThreadLocal Sentinel

A `ThreadLocal<Boolean>` flag (`IS_MCP_REQUEST`) was added to `CallerIdentityContext` (izinga-commons). This flag is set unconditionally to `true` by `StoreScopeValidationFilter` at the beginning of every `/mcp/**` request — **before** token parsing — and is cleared unconditionally in the `finally` block.

**Security invariant:** `IS_MCP_REQUEST` is never settable from a JWT claim or request parameter. Only `StoreScopeValidationFilter` may call `setMcpRequest(true)`.

When `IS_MCP_REQUEST == true` and `audience == null`, the following methods throw `CallerIdentityViolationException` with the `IDENTITY_REFUSAL:` prefix:
- `CallerIdentityValidator.validateCallerPhone()`
- `CallerIdentityValidator.validateCallerOwnsId()`
- `CallerIdentityValidator.validateMcpAudience()`
- `OrderServiceImpl.findOrder()`, `findOrderForMessenger()`, `findOrderByMessengerId()`
- `McpOrderService.startOrder()`

Non-MCP paths (REST, scheduler, internal callers) remain unaffected because `IS_MCP_REQUEST` defaults to `false`.

### SEC-MCP-02: Per-Method Audience Gates

| Tool | Permitted audiences | Enforcement method |
|---|---|---|
| `find_orders_by_messenger_id` | DRIVER (own id only), STORE (with permittedStoreId, filtered to that store) | `CallerIdentityContext.isMcpRequest()` + audience switch in `OrderServiceImpl.findOrderByMessengerId()` |
| `find_stores_by_owner` | STORE (permitted store's ownerId must match), CUSTOMER/DRIVER (callerPhone resolves to userId, must match ownerId) | `StoreService.findByOwner()` |
| `find_users` | STORE only | `CallerIdentityValidator.validateMcpAudience("STORE")` in `UserProfileService.findByLocation()` |
| `start_order` | CUSTOMER, STORE | Audience gate in `McpOrderService.startOrder()` Step 0 |

### SEC-MCP-07: Bank Account Masking

`StoreService.find()`, `findByOwner()`, and `findStores()` mask the bank `accountId` field with `mainPayAccount` when `CallerIdentityContext.isMcpRequest() == true`. Masking is applied **after** any `profileRepo.save()` call so the masked value is never persisted to MongoDB.

---

## Consequences

**Positive:**
- Tokenless MCP callers receive a machine-parseable `IDENTITY_REFUSAL:` rejection that AI agents can detect and report to the user.
- All four SEC-MCP-02 restricted tools now enforce caller-scope gates without disrupting REST consumers.
- Bank account IDs are never returned to MCP callers, preventing financial data exposure through the AI surface.
- ThreadLocal lifecycle is correct: the filter sets and clears the flag on each request, and `StoreScopeContext.clear()` cascades to `CallerIdentityContext.clear()`.

**Neutral:**
- Audit log entries are written asynchronously (`@Async`) on violations. The audience is captured on the servlet thread before the async dispatch (FIX-04 pattern).
- REST endpoints are unaffected — all guards are conditioned on `isMcpRequest()`.

**Risks and follow-ups:**
- The `start_order` tool is permitted for CUSTOMER and STORE audiences. If a new AI agent with a different audience is wired to `start_order`, this gate must be updated.
- Bank masking relies on `mainPayAccount` being set on the `StoreProfile`. If `mainPayAccount` is null, a null value is written to the returned DTO's bank field. This is an acceptable degradation (null is safer than a real account number).
- `@Scheduled` methods (e.g. `notifyUnregisteredWhatsappUsers`) run on scheduler threads where `IS_MCP_REQUEST` is always false — they are unaffected by this change. Verified via `CallerIdentityValidatorTest`.

---

## Alternatives Considered

**Alternative A — Spring Security expression on @Tool methods:** Rejected because Spring AI tool calls run on the MVC servlet thread inside the filter chain, not as separate security contexts. A `@PreAuthorize` expression would require a custom `GrantedAuthority` population that would tightly couple Spring AI to Spring Security internals.

**Alternative B — Unconditional audience gate (no IS_MCP_REQUEST flag):** Rejected because it would break REST endpoints that do not carry a scope token (e.g. admin console, SchedulerService). The IS_MCP_REQUEST sentinel ensures REST callers are never subject to MCP-specific enforcement.

**Alternative C — Separate controller layer for MCP tools:** Noted in this ADR as a future improvement. The current approach intercepts at the service layer via ThreadLocal, which is testable and safe but mixes tool-surface concerns into domain services. This is tracked as reserved `ADR-026-mcp-tools-controller-layer-separation.md` (not yet written). A future author should evaluate moving all `@Tool` methods to a dedicated MCP controller layer, keeping domain services clean.

---

## Amendment AMD-01: ADMIN Audience (2026-11-03)

**Status:** Accepted
**Approved by:** Lindani Masinga (co-founder, CEO)
**Branch:** fix/mcp-tokenless-auth-bypass

### Context

PR #156 adds ADMIN-level MCP tool access for iZinga admin users calling via WhatsApp. The ADMIN audience is minted at runtime when the caller's phone resolves to a `ProfileRoles.ADMIN` user profile AND webhook signature verification is active.

### Changes

#### REQ-A1: New Audience value
`Audience.ADMIN` added to `io.curiousoft.izinga.messaging.whatsapp.lines.Audience`.

#### REQ-A2/A3: ADMIN minting site
The only minting site is `AiCustomerServiceAgent.buildScopedMcpTools()`. When `WhatsappConfig.isSignatureVerificationEnabled()` is `true` (appSecret non-blank), the caller phone is normalised and looked up against `UserProfileRepo`. If the resolved profile has `role == ProfileRoles.ADMIN`, the JWT audience is set to `ADMIN` and `storeId` is set to `null`. This override is JWT-only — LineContext, outbound routing, and WhatsappSenderResolver never see ADMIN.

**Fail-closed (REQ-A3):** When signature verification is disabled (production bypass state, appSecret not configured), the DB lookup is skipped entirely. The configured audience is used unchanged. The moment appSecret is configured and the app restarts, ADMIN minting activates automatically with zero code change.

#### REQ-A4: ADMIN never from AiAgentConfig
If an AiAgentConfig or WhatsappLine is misconfigured with audience=ADMIN, `buildScopedMcpTools()` demotes it to CUSTOMER and logs a WARN before any minting. ADMIN is exclusively a runtime elevation.

#### REQ-A5: Non-admin senders unchanged
Phone lookup failure or a non-ADMIN role result in the configured audience being used — same as before AMD-01.

#### REQ-A10: CallerIdentityValidator ADMIN pass-through
`validateCallerPhone()` and `validateCallerOwnsId()` in `CallerIdentityValidator` short-circuit to a no-op for `audience == "ADMIN"`. The null-audience hard-reject is preserved as the first check — ADMIN pass-through comes strictly AFTER it.

#### Tool gate before/after table

| Tool | Before AMD-01 | After AMD-01 |
|---|---|---|
| `find_users` | STORE only | **ADMIN only** (REQ-A6; Lindani's explicit decision) |
| `find_orders_by_messenger_id` | DRIVER (own), STORE (with permittedStoreId, filtered) | **DRIVER (own), ADMIN (broad, unfiltered)** (REQ-A7) |
| `find_stores_by_owner` | STORE (owner-match), CUSTOMER/DRIVER (caller-phone resolves to ownerId) | **STORE (owner-match), ADMIN (broad)** — any other audience is a hard reject with audit (REQ-A8) |
| `start_order` | CUSTOMER, STORE | **CUSTOMER, STORE, ADMIN** (REQ-A9) |
| `find_store_or_shops_by_id` | STORE, DRIVER, CUSTOMER | **STORE, DRIVER, CUSTOMER, ADMIN** — bank masking still applies (REQ-A10) |
| `find_stores` | CUSTOMER, DRIVER, STORE | **CUSTOMER, DRIVER, STORE, ADMIN** — bank masking still applies (REQ-A10) |
| All other 12 tools | As per SEC-MCP-01/02 | Unchanged (REQ-A11 tokenless reject preserved) |

#### find_users STORE removal rationale
The STORE audience is removed from `find_users` by Lindani's explicit decision. Evidence that no production agent breaks: the store template's `allowedTools` list contains only `find_store_or_shops_by_id`; nothing in `izinga-messaging` references `find_users`. Production-edited agent configs in MongoDB are unverified but the store template does not list this tool.

#### WhatsappConfig shared predicate
`WhatsappConfig.isSignatureVerificationEnabled()` is the single predicate used by both `AiCustomerServiceAgent` (for ADMIN minting) and `WhatsAppWebhookController.verifyHmacSignature()` (indirectly — it checks `appSecret` directly). The predicate may be used in `verifyHmacSignature` in a future refactor without any behaviour change.
