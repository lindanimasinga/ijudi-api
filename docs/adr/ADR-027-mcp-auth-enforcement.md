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
