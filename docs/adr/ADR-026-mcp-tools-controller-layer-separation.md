# ADR-026: MCP Tools Controller-Layer Separation

**Status:** Accepted (2026-11-04, Lindani Masinga, via answers to OQ-1 to OQ-7)
**Date:** 2026-11-04
**Requested by:** Lindani Masinga (co-founder), proposal of 2026-11-02
**Baseline:** `develop` @ `d23a595` (after PR #151 ADR-024, PR #152, PR #155, PR #156 ADR-027/AMD-01)
**Supersedes (if approved):** ADR-025 / MCP-SEC-02 (see "MCP-SEC-02 state" below)
**Related:** ADR-027 (MCP auth enforcement + AMD-01 ADMIN audience), issues #138, #153, #154

> This is a from-scratch rewrite. The first ADR-026 draft (2026-11-02) was lost before commit and was never reviewed. Nothing from it is carried over unverified. Every number below comes from reading `develop` at `d23a595`.

---

## Context

All 18 MCP tools are `@Tool` methods that sit directly on domain service classes (or on two thin adapter services). Authorization is spread through those service bodies as imperative checks that branch on ThreadLocal state (`CallerIdentityContext.isMcpRequest()`, audience, callerPhone, permittedStoreId) set by `StoreScopeValidationFilter`.

So the same Java method is two things at once:

- an **external entry point** when Spring AI's MCP server dispatches a tool call, and
- an **internal domain method** when a REST controller, scheduler, event listener, or another service calls it.

Whether a check fires depends on whatever ThreadLocal state the current thread happens to carry. The 2026-11-02 bug (`SchedulerService.updateMissingPayouts()` calling the `get_payouts_for_user` tool method) came from exactly this. PR #155 fixed that call site. The structural cause is still there: 11 of the 18 tool methods still have non-MCP callers (see the inventory).

**Lindani's proposal:** move every `@Tool` method onto a dedicated controller layer. Authentication and authorization happen only at that boundary. Services become pure business logic that any internal caller can use with zero gating.

ADR-027 (Alternative C) already marks this as the intended follow-up and reserved this number for it.

---

## Findings (current `develop`)

### F1. Tool inventory: 18 `@Tool` methods, 6 classes, 3 modules

All 18 are registered through one `MethodToolCallbackProvider` bean in `izinga-ordermanager/.../McpConfig.java`. These 18 names match the tool list of the live izinga MCP connector exactly.

Legend: **own** = only the caller's own record (phone or resolved userId must match the JWT `callerPhone`). **assigned** = only orders where the caller is the assigned messenger. **any** = no ownership or scope restriction. **—** = hard reject. All 18 reject tokenless MCP calls (the rejection shape varies, see F3).

| # | Tool | Current home (module) | Gate mechanism today | DRIVER | CUSTOMER | STORE | ADMIN | Non-MCP callers today |
|---|---|---|---|---|---|---|---|---|
| 1 | `find_order_by_id` | `OrderServiceImpl.findOrder` (ordermanager) | inline null-audience reject + `validateCallerOwnsId` | assigned | own | **any** | any | `OrderController` x2, `YocoPaymentController` via `IzingaOrderManagerClient` |
| 2 | `find_orders_by_user_id` | `OrderServiceImpl.findOrderByUserId` | `validateCallerOwnsId(userId)` | own | own | **any** | any | `OrderController` |
| 3 | `find_order_for_messenger` | `OrderServiceImpl.findOrderForMessenger` | inline null reject + `validateCallerOwnsId` | assigned | own | **any** | any | `OrderController`, `MessengerOrderController` |
| 4 | `find_orders_by_phone_number` | `OrderServiceImpl.findOrderByPhone` | `validateCallerPhone` | own | own | **any** | any | `OrderController` |
| 5 | `find_orders_by_messenger_id` | `OrderServiceImpl.findOrderByMessengerId` | inline audience switch (AMD-01) | own | — | — | any | `OrderController`, `MessengerOrderController` |
| 6 | `start_order` | `McpOrderService.startOrder`, plus a domain-level check in `OrderServiceImpl.startOrder` | inline allowlist (returns `rejected`), plus `validateCallerOwnsId(customerId)` in the domain method | — | own customerId | **any customer, any shop** | any | none for the tool; domain `startOrder` is used by `OrderController` |
| 7 | `find_store_or_shops_by_id` | `StoreService.find` (ordermanager) | `StoreScopeValidator.validate(id)` + `validateAudience(S,D,C,A)` + bank mask | any | any | own store only | any | `EmailNotificationService.notifyOrder` (scheduler and async), `ShoppingListEventHandler` (async) |
| 8 | `find_stores_by_owner` | `StoreService.findByOwner` | inline audience switch + owner match + bank mask | — | — | owner of permitted store | any | `StoreControler` x3 |
| 9 | `find_stores` | `StoreService.findStores` | `validateAudience(C,D,S,A)` + unconditional mask | any | any | any | any | none |
| 10 | `get_payouts_for_user` | `ReconServiceImpl.getAllPayouts` (recon) | `validateCallerOwnsId(toId)` | own | own | **any toId / type** | any | `ReconController` |
| 11 | `find_user_by_phone` | `UserProfileService.findUserByPhone` (usermanagement) | `rejectTokenlessMcp` + `validateCallerPhone` | own | own | **any** | any | `FirebaseJwtAuthenticationConverter` (every Firebase-authenticated REST request), `WhatsAppOtpService`, `UserController`, `DocumentUploadMcpService` x6, `StoreAdminStoreProfileEventHandler` (sync listener, see F2) |
| 12 | `find_users` | `UserProfileService.findByLocation` | `validateMcpAudience("ADMIN")` | — | — | — | any | `UserController` |
| 13 | `create_user` | `UserProfileService.create` | `rejectTokenlessMcp` + `validateCallerPhone` (only when `mobileNumber` is non-blank) | own phone | own phone | **any phone** | any | `UserController` x2, `StoreDriverInviteService`, `create(profile, ref)` |
| 14 | `get_missing_fields_by_phone` | `UserProfileService.getAllMissingFields(String)` | delegates to `findUserByPhone` | own | own | **any** | any | none (only `DocumentUploadMcpService`, which is itself MCP-only) |
| 15 | `get_required_documents_for_user` | `DocumentUploadMcpService` (usermanagement) | `validateCallerPhone` | own | own | **any** | any | none |
| 16 | `create_document_upload_session` | `DocumentUploadMcpService` | `validateCallerPhone` | own | own | **any** | any | none |
| 17 | `attach_uploaded_document_to_user_tag` | `DocumentUploadMcpService` | `validateCallerPhone` | own | own | **any (write)** | any | none |
| 18 | `recheck_required_documents` | `DocumentUploadMcpService` | `validateCallerPhone` | own | own | **any** | any | none |

**Totals:**

- Per class: OrderServiceImpl 5, McpOrderService 1, StoreService 3, ReconServiceImpl 1, UserProfileService 4, DocumentUploadMcpService 4.
- Per module: ordermanager 9, usermanagement 8, recon 1.
- 0 tools are fully ungated. #156 closed the last 4.
- **11 of 18** tool methods are also called internally, outside MCP. These are the dual-use methods this ADR targets.
- **2 classes are already pure adapters**, with no non-MCP callers of their tool methods: `McpOrderService` and `DocumentUploadMcpService`. That makes them the cheapest to move.
- Reachability by caller type:
  - DRIVER: 15 tools
  - CUSTOMER: 15 tools
  - STORE: 16 tools, of which **13 have no ownership or scope restriction** (not counting the public `find_stores` catalogue)
  - ADMIN: all 18, with no restriction anywhere
  - `OTP`, the 5th enum value: never minted by code, but a misconfigured `AiAgentConfig` could carry it. It would behave like STORE on the 12 phone/id-gated tools. AMD-01 REQ-A4 only demotes ADMIN, not OTP.

### F2. Scheduled, async, and event paths that reach tool methods

- **The payout scheduler is fixed.** `SchedulerService.updateMissingPayouts()` now calls `generatePayoutForShopAndOrder` / `generatePayoutForMessengerAndOrder` directly (PR #155, BUG-1). `getAllPayouts` has no caller outside `ReconController` (REST) and MCP registration. `ReconService.kt` now documents both generators as "MUST NEVER be annotated @Tool".
- **Re-audit:** 11 active `@Scheduled` methods in `SchedulerService`, 2 in recon (`reconcilePendingReferralCommissions`, `DailyPayoutEmailNotificationService`), 1 in messaging (`cleanupOldConversations`). `notifyUnregisteredWhatsappUsers` stays disabled (`//@Scheduled`). Two scheduled jobs still reach a tool method, and **neither is broken today**:
  - `notifyUnpaidOrders` → `EmailNotificationService.notifyAdminOrderNotPaid` (not `@Async`) → `notifyOrder` (a self-invocation, so `@Async` is bypassed) → `StoreService.find` (tool #7) runs on the scheduler thread.
  - `checkUnconfirmedOrders` → `notifyAdminNewOrder` (`@Async`) → `notifyOrder` → `StoreService.find` runs on an async thread.
  - Both are safe because off-MCP `isMcpRequest()` is false and `permittedStoreId` is null. `StoreScopeValidator.validate` passes, there's no audience check and no masking. There is no `TaskDecorator` anywhere, so ThreadLocals never propagate to async threads.
  - `newDrivers` calls the `getAllMissingFields(UserProfile)` overload, not the tool's `(String)` overload.
- **Synchronous `@EventListener`s:** 7 non-async domain listeners exist, plus 2 startup lifecycle listeners. One calls a tool method: `StoreAdminStoreProfileEventHandler.handleNewStoreCreatedEvent` → `userProfileService::findUserByPhone` (tool #11) for each admin cell. Nothing in the codebase constructs a `StoreCreatedEvent`, so the listener is dead today. It is latent, though. If store creation ever runs on an MCP thread (a future `create_store` onboarding tool, for example), a CUSTOMER/DRIVER audience would hit `PHONE_MISMATCH` on the admin's phone. That would write a false audit record, and because the listener is synchronous it would also fail the publisher.
- **Request-path internal callers:** `FirebaseJwtAuthenticationConverter` calls tool #11 on every Firebase-authenticated request. On `/mcp` it is safe only because of filter order: the Spring Security chain (order -100) runs before `StoreScopeValidationFilter`, which has default lowest precedence and no `@Order`. The safety therefore rests on an implicit ordering contract.
- **Nothing new from #156:** every null-audience hard reject is conditioned on `isMcpRequest()`, so schedulers and listeners are unaffected. The ADMIN minting in `AiCustomerServiceAgent` uses `userProfileRepo` directly, not the gated `findUserByPhone`. That was a deliberate choice and it is correct.
- **The inverse problem (internal calls inside an MCP thread):**
  - `DocumentUploadMcpService` calls gated `findUserByPhone` 6 times per tool call. This is harmless today because it is the same phone.
  - `StoreService.find()` masks `bank.accountId` by mutating the entity it loaded. That is safe only as long as no MCP-thread caller later saves that instance.
  - Both are "correct by coincidence", which is the same class of risk as the original bug.

### F3. Consistency problems the current design has accumulated

- **7 distinct gate mechanisms:**
  1. inline `isMcpRequest()` / audience branches (9 code sites across `OrderServiceImpl`, `McpOrderService`, `StoreService`, including 2 masking branches)
  2. `validateCallerPhone`
  3. `validateCallerOwnsId`
  4. `validateMcpAudience`
  5. `rejectTokenlessMcp`
  6. `StoreScopeValidator.validate`
  7. `StoreScopeValidator.validateAudience`
- **3 refusal shapes:**
  1. `CallerIdentityViolationException` with an `IDENTITY_REFUSAL:` prefix (15 tools)
  2. `AudienceViolationException` with no prefix and no audit record (`find_stores`, `find_store_or_shops_by_id` when called tokenless or with OTP)
  3. `StartOrderResponse.rejected(...)` returned as a success-shaped payload (`start_order`)
- **Duplicated code:** the `IDENTITY_REFUSAL` message text appears as 12 string literals in main code. `resolveCallerUserId()` (phone → userId by prefix loop) is duplicated in `OrderServiceImpl`, `ReconServiceImpl` and `AiCustomerServiceAgent`.
- **Dead or stale code:** `@RequiresAudience` has zero usages. It is documentation only and nothing enforces it. The `McpConfig` javadoc still lists only 2 tools.

### F4. Security findings outside this ADR's scope that must not wait for it

These were found while building the inventory. They come from **code reading only and are not yet live-verified**. They need Security & Compliance confirmation and their own issues. ADR-026 is a behaviour-preserving refactor and must not be the vehicle that fixes them.

1. **`AiAgentConfig.allowedTools` is never enforced.** `StoreAiAgent` puts every `McpServerConfig` into the OpenAI `tools` array. `McpServerConfig` has no `allowed_tools` field, and `getAllowedTools()` is only read in `StoreLineProvisioningService` to copy the template.
   - So a STORE-line agent is offered all 18 tools.
   - The people talking to that agent are members of the public messaging a store's WhatsApp line. A prompt injection on that line therefore reaches the 13 STORE-unrestricted tools in F1: any user's full profile (including bank) by phone, any order, any payouts, writes to any user's document tags, and `create_user` for any phone.
   - This also invalidates the evidence AMD-01 cited for the `find_users` change ("store template's allowedTools contains only find_store_or_shops_by_id"). The `find_users` change itself was a tightening, so it remains safe.
2. **`ProfileRoles.ADMIN` is self-assignable**, which undermines AMD-01's fail-closed property.
   - `create_user` (MCP) accepts any `role`, including `ADMIN`, for the caller's own phone.
   - REST `POST /user` and `PATCH /user/{id}` fall through to `anyRequest().permitAll()` (#138) and also accept `role`. `PATCH` has no ownership check (`BeanUtils.copyProperties`).
   - An ADMIN-role profile grants `ROLE_ADMIN` on REST through `FirebaseJwtAuthenticationConverter`. Once `whatsapp.cloud.appSecret` is configured in prod, it also gets ADMIN MCP audience minted on that user's next WhatsApp message.
   - **Recommendation: close role self-assignment before configuring `appSecret` in production.**
3. **`start_order` with STORE audience** checks neither `shopId == permittedStoreId` nor `customerId` binding. The `customerId` gap is the known SA-022-01 follow-up; the `shopId` gap is new.

### F5. MCP-SEC-02 / ADR-025 state

ADR-025 is no longer the sequencing anchor, and it cannot be resumed as written.

- **Phase 1 never shipped:**
  - Branch `feature/mcp-sec-02-phase1-caller-identity-aop` has 0 commits beyond `3e6479f` (the PR #151 merge) and is an ancestor of `develop`.
  - `develop` has no `@CallerIdentityCheck`, `CallerOwnershipResolver`, `CallerIdentityCheckAspect`, `spring-boot-starter-aop` or ArchUnit. None of those files exists in any worktree.
- **The design documents are also gone:** neither `docs/adr/ADR-025-*.md` nor `docs/feature-briefs/MCP-SEC-02-*.md` exists on any branch, worktree, or in git history.
- **The only remnant is `stash@{5}`** (2026-11-02 22:26, "WIP on feature/mcp-sec-02-phase1-caller-identity-aop"). It holds 3 tracked-file edits and no untracked files:
  - `izinga-ordermanager/pom.xml` adds `spring-boot-starter-aop` and `archunit-junit5:1.3.0`
  - `AgentIdentityAuditLog.ViolationType` adds `RESOLVER_NOT_FOUND` and `OWNERSHIP_MISMATCH`, with a comment citing an "OQ-2 resolution 2026-11-01" (identical caller-facing denial)
  - two `docs/test-plans/README.md` rows. One records Phase 1 QA as BLOCKED at 70.6% branch coverage on the aspect.
- **Its blockers have cleared:** the JWT-fallback fix (#152) and the JUnit-4 fix (#155) both merged. The phase itself was never restarted.

---

## Decision

### D1. One MCP adapter layer in `izinga-ordermanager` — Decided

**Package and language** — Decided: Java, single package `io.curiousoft.izinga.ordermanagement.mcp` in `izinga-ordermanager`. All MCP tool surfaces move to this package, written in Java. The package already exists on the test side (`McpToolRegistrationTest`, `McpSseEndpointTest`).
- `izinga-ordermanager` is the only module that depends on every other module, and it already owns `McpConfig`, `StoreScopeValidationFilter`, `StoreScopeContext` and the typed `Audience` enum.
- Usermanagement and recon cannot see `Audience`, which is why `CallerIdentityValidator` works with strings today.
- `izinga-ordermanager` compiles no Kotlin. We will not add Kotlin compilation to it, given the non-deterministic Kotlin build failures already seen on `develop`.

**Naming — Decided: Option A.** `@McpToolController` class-level marker annotation; classes named `*McpController`. Matches the existing REST-controller naming convention even though these are not REST controllers.

| Controller | Tools | Moves from |
|---|---|---|
| `OrderCreationMcpController` | `start_order` | `McpOrderService` |
| `DocumentUploadMcpController` | 4 document tools | `DocumentUploadMcpService` |
| `UserMcpController` | 4 user tools | `UserProfileService` |
| `OrderMcpController` | 5 order reads | `OrderServiceImpl` |
| `StoreMcpController` | 3 store tools | `StoreService` |
| `PayoutMcpController` | 1 payout tool | `ReconServiceImpl` |

**Bean shape**
- `@McpToolController` is a class-level marker meta-annotated with `@Component`. We use an annotation rather than a marker interface.
- Controllers are concrete classes with no interfaces, and they carry no advice. Spring AI 1.1.5's `MethodToolCallbackProvider` resolves `@Tool` methods on `AopUtils.getTargetClass(bean)` and invokes that `Method` reflectively on the bean. A JDK proxy would make that invocation fail at call time. With this design the controllers are never proxied at all.

**Tool contract freeze**
- Tool names, descriptions, `@ToolParam` names and descriptions, and parameter order and types are byte-identical to today.
- They are a stable contract for `AiAgentConfig` system prompts in MongoDB, the WhatsApp agents, and the external izinga MCP connector.

**Spring AI feasibility (verified against the 1.1.5 jars in `~/.m2`)**
- `MethodToolCallbackProvider` accepts any object.
- It throws `IllegalStateException` on duplicate tool names within one provider. It also throws when a tool object has no `@Tool` methods.
- `ToolCallbackConverterAutoConfiguration` aggregates every `ToolCallbackProvider` bean but **de-duplicates by name silently** across providers. So we keep exactly one provider bean: a tool accidentally left on both a service and a controller then fails fast at startup instead of one copy silently winning.
- `McpToolUtils` turns any exception from `ToolCallback.call()` into `CallToolResult(text = e.getMessage(), isError = true)`. Refusals thrown at the boundary reach the agent exactly as they do today.

### D2. Authorization lives only at the boundary, in two tiers — Decided: plain GuardedToolCallback, no AOP

**Decided: plain wrapper in McpConfig, no AOP.** The `GuardedToolCallback` decorator around registered tool callbacks is the guard mechanism. No `spring-boot-starter-aop`, no `CallerIdentityCheckAspect`. The guard runs only when the MCP server dispatches a call; an internal Java call can never accidentally pass through it.

**Tier 1: coarse isMcpRequest assert (and audience gating once OQ-4 is decided)**
- Every `@Tool` method carries `@McpAudience({...})`, which must be non-empty.
- `McpConfig` builds the single provider from the controller beans and wraps each `ToolCallback` in a `GuardedToolCallback` decorator. The decorator implements Spring AI's `ToolCallback` and delegates to the `MethodToolCallback`.
- **Phase 0 / OQ-4-deferred behavior:** The decorator asserts `CallerIdentityContext.isMcpRequest()` fail-closed. That is the only runtime check the wrapper applies while OQ-4 is deferred. Audience rejection, audit write, and refusal shaping are NOT in the wrapper in this phase, because three tools have non-standard refusal shapes that must remain unchanged:
  - `start_order` returns `StartOrderResponse.rejected(...)` — a success-shaped payload.
  - `find_stores` and `find_store_or_shops_by_id` throw `AudienceViolationException` without the `IDENTITY_REFUSAL:` prefix for tokenless and OTP callers.
  Standardising these in the wrapper would change agent-visible shapes, which OQ-4 deferred explicitly forbids.
- **IS_MCP_REQUEST verification:** `StoreScopeValidationFilter.doFilterInternal()` calls `CallerIdentityContext.setMcpRequest(true)` unconditionally before token parsing (line 76 in the filter, before the `if (scopeParam != null)` branch). So for every `/mcp/**` request — including tokenless ones — `isMcpRequest()` is `true`. The fail-closed assert therefore cannot fire on any current `/mcp` path. It fires only if a `GuardedToolCallback` is invoked outside the MCP servlet path: a scheduler, an internal caller, or a future switch to `type: async` transport where ThreadLocals do not propagate. No path that today returns a different shape is affected.
- **When OQ-4 is decided:** the wrapper takes over steps 2–5 (null-audience reject, allowlist reject, audit write, standard `IDENTITY_REFUSAL:` throw). Controller bodies shrink. `McpAccessMatrixTest` is updated to assert the unified shapes.

**Tier 2: fine-grained ownership (explicit, in the controller body)**
- Per-tool audience checks, audit writes, and refusal shapes stay in each controller body, byte-equivalent to today's service implementations.
- Checks like "this order's `customerId` is the caller", "this store is the permitted store" and "this phone is the caller's phone" stay explicit first statements in each controller method.
- Per the persisted-state principle, they run against the entity loaded through the (now ungated) service, never against request parameters.
- They use one `McpCaller` facade: audience, `callerPhone`, `permittedStoreId`, and a request-cached `resolveUserId()` that replaces the three duplicated prefix loops.
- Bank-account masking and `markUp` decoration also move here, on the object returned to the agent. Controllers have no repository access (R5 below), so a masked entity can never be saved.
- The `@McpAudience` annotation is a declarative contract checked by the R2 ArchUnit rule and by `McpAccessMatrixTest`. It is not used by the wrapper for runtime gating in Phase 0.

**Services**
- Services lose every `isMcpRequest()` branch and every `CallerIdentityValidator` / `StoreScopeValidator` call.
- Domain-level checks that exist only for MCP move up into the corresponding controller. The main one is the CUSTOMER ownership check inside `OrderServiceImpl.startOrder()`.
- `IS_MCP_REQUEST` survives only as the filter-set sentinel that the guard asserts.

### D3. ADMIN and the webhook-signature check: where the boundary sits

The trust chain, end to end:

1. WhatsApp webhook → `WhatsAppWebhookController.verifyHmacSignature` (bypassed while `appSecret` is blank).
2. `WhatsappInboundEventHandler` (`@Async`) → `AiCustomerServiceAgent.buildScopedMcpTools`. **ADMIN is minted here only if `whatsappConfig.isSignatureVerificationEnabled()` is true and the caller's profile role is `ADMIN`.**
3. `StoreScopeJwtService` signs `{storeId, audience, callerPhone}` with `STORE_SCOPE_JWT_SECRET` (5-minute TTL) into the MCP URL.
4. OpenAI calls `/mcp?scope=...`.
5. `StoreScopeValidationFilter` verifies the JWT and populates the ThreadLocals.
6. **[ADR-026]** `GuardedToolCallback` (tier 1), then the controller (tier 2).

**ADR-026 touches only step 6. The `isSignatureVerificationEnabled()` decision stays entirely inside step 2, unchanged, and controllers must not re-check it.**

- **Wrong fact at the wrong layer.** Webhook signature verification is a property of the inbound WhatsApp hop, steps 1–2. At step 6 the only verifiable fact is "this JWT was signed with our scope secret". A controller that consults `WhatsappConfig` creates a second, divergent source of truth for "who is ADMIN".
- **It would mis-scope future channels.** ADMIN-over-MCP from a non-WhatsApp channel (the open 2026-11-03 question: a token-issuance endpoint, or Firebase bearer on `/mcp`) would be wrongly blocked by a WhatsApp configuration flag.
- **It would add almost nothing.** With verification off, minting is already off. The only ADMIN tokens that could still arrive are:
  - one minted at most 5 minutes before a config flip and restart, or
  - one forged by someone who holds `STORE_SCOPE_JWT_SECRET`.
- **Where defense-in-depth belongs, if wanted.** If the second case is a concern (the committed placeholder secret must be treated as compromised, and the AWS-side provisioning from PR #152 is not confirmed here), the right place is a single token-acceptance rule in `StoreScopeValidationFilter`: "reject `audience=ADMIN` while ADMIN minting is disabled". That is step 5, it is a separate change, and it is OQ-6.

**OQ-6 deferred (2026-11-04 decision):** the filter-level ADMIN rejection when signature verification is off is out of scope for this migration. It is a separate change to be tracked independently.

Inside the controller layer, ADMIN is just another allowlist member. Each tool's `@McpAudience` reproduces today's ADMIN reach exactly (all 18). Tier-2 ownership checks keep today's ADMIN pass-through (AMD-01 REQ-A10), expressed once in `McpCaller` instead of inside `CallerIdentityValidator`.

### D4. The migration preserves behaviour; tightening is separate

Each move phase must reproduce today's allow/deny matrix exactly: F1 × {tokenless, DRIVER, CUSTOMER, STORE, ADMIN, OTP} × {own, other}, including today's permissive STORE/OTP cells.

**OQ-5 decision (2026-11-04):** Preserve the current access matrix exactly during the migration. No access rule is tightened as part of moving a tool. The two known tightening items, F4-1 (allowedTools never enforced) and F4-2 (self-assignable ADMIN), go to a SEPARATE, later deployment, not this one. Note: this differs from the original recommendation, which was to fix F4-1 now in the current service gates. Lindani's decision is to defer both F4-1 and F4-2 entirely.

Closing the F4 gaps, or changing any F1 cell, is a separate, explicitly approved change with its own before/after table, in the same style as AMD-01. This keeps every migration PR reviewable as a pure move.

The only approved behaviour change inside the migration is OQ-7 (create_user role restriction). See the "OQ-7 Approved Behaviour Change" section below.

### D5. Mechanical enforcement (ArchUnit, `archunit-junit5`, approved by Lindani 2026-11-01)

- R1: Any class declaring a `@Tool` method must be annotated `@McpToolController` and live in `..ordermanagement.mcp..`.
- R2: Every `@Tool` method must carry a non-empty `@McpAudience`. There is no exemption annotation, because every tool today requires an authenticated audience.
- R3: No class outside `..mcp..` may depend on an `@McpToolController` class. Nothing internal calls the boundary.
- R4: Outside `..mcp..` and `..security..`, no class may call `CallerIdentityContext` getters or `isMcpRequest()`, `StoreScopeContext` getters, `CallerIdentityValidator.validate*` / `rejectTokenlessMcp`, or `StoreScopeValidator.*`. This is the "services are context-free" invariant.
- R5: `@McpToolController` classes must not depend on `*Repository` / `*Repo` types. They go through services.
- R6: Only `StoreScopeValidationFilter` may call `CallerIdentityContext.setMcpRequest` or the `StoreScopeContext` setters.

Rules start with a named `LEGACY_EXCLUSIONS` list (the 6 current service classes; this pattern was approved 2026-11-01). Each phase shrinks the list. Phase 6 deletes it and the rules hard-fail.

**LEGACY_EXCLUSIONS — confirmed sufficient on today's code.** The 6 service classes that hold `@Tool` methods today are also the only production classes outside `..mcp..` / `..security..` that call R4-restricted methods:
- `OrderServiceImpl` — R1, R4 (`StoreScopeValidator.validateCallerOwnsId`, `validateCallerPhone`, `normalizeLast9`, `CallerIdentityContext.isMcpRequest`)
- `McpOrderService` — R1, R4 (`CallerIdentityContext.isMcpRequest`, `getAudience`)
- `StoreService` — R1, R4 (`StoreScopeValidator.validate`, `validateAudience`, `isMcpRequest`)
- `ReconServiceImpl` — R1, R4 (`CallerIdentityValidator.validateCallerOwnsId`)
- `UserProfileService` — R1, R4 (`CallerIdentityValidator.rejectTokenlessMcp`, `validateCallerPhone`, `validateMcpAudience`)
- `DocumentUploadMcpService` — R1, R4 (`CallerIdentityValidator.validateCallerPhone`)

No other production class calls R4-restricted methods. `AiCustomerServiceAgent` calls `CallerIdentityValidator.normalizeLast9`, which is a utility method not in the R4 restricted list (`validate*` / `rejectTokenlessMcp`). R6 is satisfied today: only `StoreScopeValidationFilter` calls `StoreScopeContext` setters and `CallerIdentityContext.setMcpRequest`.

Two non-ArchUnit contract tests, written in Phase 0 against the current code and kept green through every phase:

- **`McpToolContractSnapshotTest`:** a golden JSON file of all 18 `ToolDefinition`s (name, description, inputSchema). Any byte diff fails the build.
- **`McpAccessMatrixTest`:** a parameterized characterization test of the D4 matrix. This test pins all three current refusal shapes byte-exact (see D2). Any migration PR that changes a shape fails here. It is updated deliberately only in the OQ-7 Phase 2b PR (create_user role restriction) and when OQ-4 is eventually decided.

---

## Affected repos

- **`ijudi-api`:** the only repo with code changes. `izinga-ordermanager` (new `mcp` package, `McpConfig`), `izinga-usermanagement`, `izinga-recon` (tool annotations and gates removed from services), `izinga-commons` (no API change; `CallerIdentityValidator` may shrink in Phase 6).
- **`ijudi`, `cs-lifestyle`, `furniture-delivery-app`, `izinga-onboarding`, `izinga-pay`, `izinga-products-website`:** no change. No REST path, method, request or response shape changes.

## Affected agents

- **WhatsApp agents** (`driver_support`, `customer_support`, per-store `StoreAiAgent` lines) and the external izinga MCP connector, including the "iZinga Driver Customer Service" agent. They see no tool contract change (the snapshot test enforces this). The only possible agent-visible change is the refusal-shape unification in OQ-4 (deferred).
- **iZinga Backend Developer:** implements phases. Must not change tool names, parameter names or descriptions, and must not tighten any matrix cell inside a migration PR.
- **iZinga QA & Test Automation:** ports the 14 test classes that exercise MCP gating to the controller layer. Runs live UAT verification of the matrix per phase.
- **iZinga Code Reviewer:** reviews the ArchUnit `LEGACY_EXCLUSIONS` diff and the snapshot diff (which must be empty) on every phase.
- **iZinga Security & Compliance:** owns the F4 issues, which are separate from this ADR.

## Breaking change

**No.** Single deployable, no REST change, tool contract frozen and snapshot-enforced, access matrix frozen and characterization-tested.

**OQ-4 deferred — refusal shapes frozen:** All 18 tools keep their current rejection response shapes during this migration. The two proposed shape changes are deferred:
- `start_order`: the success-shaped `rejected` payload remains unchanged.
- `find_stores` / `find_store_or_shops_by_id`: the prefix-less `AudienceViolationException` for tokenless/OTP callers remains unchanged.

No refusal-text changes in this migration. The proposed unification is deferred to a separate decision.

---

## Safe implementation sequence

Each phase is its own `feature/adr-026-phase-N-*` branch with the standard gates:

- a PO-approved feature brief
- an AI Code Review PASS
- a full **unscoped** `./mvnw test` (JDK 17)
- QA live verification on the `uat` profile against the real `/mcp` endpoint

Every phase is independently deployable and revertible. No cross-repo sequencing is needed.

| Phase | Scope | Behaviour change | Notes |
|---|---|---|---|
| **0. Foundation** | `@McpToolController`, `@McpAudience`, `GuardedToolCallback` (asserts `isMcpRequest()` only — no audience gate in Phase 0), `McpCaller`, `McpRefusal`; `McpConfig` keeps one provider and wraps only controller-declared tools (none yet); `archunit-junit5` + R1–R6 with `LEGACY_EXCLUSIONS` (6 service classes, confirmed sufficient); `McpToolContractSnapshotTest` + `McpAccessMatrixTest` written against current code, pinning all 3 refusal shapes | None | The two contract tests are the safety net for everything after. Write them first. |
| **1. Existing adapters (5 tools)** | `McpOrderService` → `OrderCreationMcpController`; `DocumentUploadMcpService` (Kotlin) → `DocumentUploadMcpController` (Java); move the CUSTOMER ownership check out of `OrderServiceImpl.startOrder()` | None | Lowest risk: zero non-MCP callers. Proves the pattern end to end. |
| **2. User tools (4)** | `find_user_by_phone`, `find_users`, `create_user`, `get_missing_fields_by_phone` → `UserMcpController`; strip gates from `UserProfileService` | None | Largest payoff. `FirebaseJwtAuthenticationConverter`, OTP, invites and `UserController` become context-free. |
| **2b. create_user role restriction** | Dedicated PR: disable role assignment via MCP in `UserMcpController.create_user`. Before/after table (AMD-01 style). `McpAccessMatrixTest` and snapshot updated deliberately in this PR. | Tightening: MCP callers can no longer set role | Ships immediately after Phase 2. See "OQ-7 Approved Behaviour Change" section. |
| **3. Order reads (5)** | `OrderServiceImpl` read tools → `OrderMcpController` | None | Highest REST fan-in; the matrix test carries the load. |
| **4. Stores (3)** | `StoreService` tools → `StoreMcpController`; masking moves; `StoreScopeValidator.validate` leaves `StoreService.find` | None | Removes the masked-entity persistence hazard. The `find()` default-hours seeding side effect stays in the service, unchanged. |
| **5. Payouts (1)** | `get_payouts_for_user` → `PayoutMcpController` | None | **Start only after `feature/payout-disbursement-fixes` merges.** It changes `ReconServiceImpl` by +172/-45. |
| **6. Lock-down** | Empty `LEGACY_EXCLUSIONS` and hard-fail R1–R6; zero `isMcpRequest()` hits outside `mcp`/`security`; retire `@RequiresAudience`; update the `McpConfig` SA-021-18 / SA-022-01 javadoc rules to point at controllers; mark ADR-027 Alternative C done | None | |

Phases 2–5 touch disjoint service classes. They can run in parallel after Phase 1, with only trivial conflicts in `McpConfig` and the exclusion list. Sequential is recommended to keep reviews small.

---

## Risks

| Risk | Mitigation |
|---|---|
| A moved tool's JSON schema drifts (renamed parameter, `required` flag, description) and agents silently degrade | `McpToolContractSnapshotTest`, byte-exact |
| A matrix cell silently changes during a move | `McpAccessMatrixTest`, written before any move; D4 forbids tightening inside a migration PR |
| A tool is left on both a service and a controller | One provider bean, so Spring AI throws on duplicate names at startup; R1 |
| ThreadLocal thread-affinity breaks (a future `type: async` or transport change) | The guard asserts `isMcpRequest()` and fails closed. Future option: read the scope from `McpTransportContext` instead of ThreadLocals (Alternative D) |
| Porting the 3 Kotlin surfaces to Java introduces subtle null-handling differences | The matrix test covers null and blank phone and id inputs. Port the existing Kotlin gate tests (`UserProfileServicePhoneGuardTest`, `DocumentUploadMcpServiceTest`, `ReconServiceImplCallerIdentityTest`, ...) to the controllers |
| Non-deterministic full-reactor build failures on `develop` stall phase gates | Fix or escalate them per the no-scoped-exclusions rule; do not green a phase on a partial run |
| The F4 exposures stay open while this refactor proceeds | Track and fix them independently and immediately. The F4-2 fix must land before `appSecret` is configured in prod |
| `create_user` validates the raw `mobileNumber` (last 9 chars) before `fomatMobileNumber` normalizes it, so a different country prefix with the same last 9 digits passes (for example `+44…`) | The controller validates on the normalized number. Low severity; handle in Phase 2 as part of the move, not as a matrix change |

---

## Alternatives considered

- **A. Keep gates in services and finish ADR-025's annotation + AOP aspect (MCP-SEC-02).** Rejected:
  - It makes the imperative checks declarative but leaves them on dual-use service methods. Internal callers still pass through the same aspect, which is the root problem.
  - It adds AOP proxying to classes that Spring AI invokes reflectively.
  - Its design docs no longer exist.
  - **AOP not adopted, confirmed by Lindani 2026-11-04 ("don't do AOP for now").** The AOP part of Alternative A is not adopted.
  - What carries over: the ArchUnit + `LEGACY_EXCLUSIONS` approach. The `RESOLVER_NOT_FOUND` / `OWNERSHIP_MISMATCH` audit-enum split (in `stash@{5}`) is NOT absorbed into Phase 0 (OQ-3 decision); it remains open for a later decision.
- **B. Per-module `mcp` packages (usermanagement and recon keep Kotlin adapters; guard in `izinga-commons`).** Rejected for now:
  - It keeps string-typed audiences.
  - It spreads the boundary across 3 modules.
  - It needs a cross-module guard registry.
  - It would be the right choice only if a module ever needs to run its MCP tools without `izinga-ordermanager`, which nothing does today.
- **C. Guard as an AOP aspect on `@McpToolController` classes.** **Rejected, confirmed by Lindani 2026-11-04 ("don't do AOP for now").** The `ToolCallback` decorator runs only on real MCP dispatch, needs no new dependency, and keeps controllers unproxied.
- **D. Carry caller scope in `McpTransportContext` (a `McpTransportContextExtractor` on the streamable transport) instead of ThreadLocals.** Deferred. It removes the thread-affinity dependency entirely, but it changes the filter and the token plumbing, so it belongs in its own ADR. The controller layer makes it a one-class swap later (`McpCaller`).
- **E. Big-bang move of all 18 tools in one PR.** Rejected: unreviewable, and it maximises conflicts with in-flight recon work.

---

## Open questions for Lindani

1. ~~**OQ-1. Placement and language:** one `ordermanagement.mcp` package, all controllers in Java (D1)? This moves 3 Kotlin tool surfaces into Java, unlike ADR-025's OQ-4 answer (Kotlin) for the annotation. Alternative B keeps Kotlin per module at the cost of string audiences and a split boundary. *Recommendation: Java, single package.*~~

> **Decided (2026-11-04, Lindani Masinga):** Java, single package `io.curiousoft.izinga.ordermanagement.mcp` in `izinga-ordermanager`. D1 table names and Java-only compilation constraint confirmed.

2. ~~**OQ-2. Naming:** `*McpController` with `@McpToolController` (matches your proposal), or `*McpTools`? *Recommendation: `*McpController`.*~~

> **Decided (2026-11-04, Lindani Masinga):** Option A. `@McpToolController` class-level marker annotation; classes named `*McpController` (OrderMcpController, StoreMcpController, etc.). Matches the existing REST-controller naming convention even though these are not REST controllers.

3. ~~**OQ-3. Retire ADR-025 / MCP-SEC-02 formally?** If yes: drop the `feature/mcp-sec-02-phase1-caller-identity-aop` branch, and either drop `stash@{5}` or keep its audit-enum change for Phase 0. *Recommendation: retire it; absorb the enum split.*~~

> **Decided (2026-11-04, Lindani Masinga — partial):** ADR-025's AOP approach is NOT adopted. `feature/mcp-sec-02-phase1-caller-identity-aop` branch and `stash@{5}` are left untouched; disposal is not explicitly decided. The `RESOLVER_NOT_FOUND` / `OWNERSHIP_MISMATCH` audit-enum split is NOT absorbed into Phase 0. Open for Lindani.

4. ~~**OQ-4. Unify refusal shape:** all boundary refusals become a thrown `IDENTITY_REFUSAL:` plus an audit record. This changes `start_order`'s `{success:false, message}` refusal and the prefix-less `AudienceViolationException` text on the two store tools. Do any production `AiAgentConfig` prompts depend on `start_order`'s `success:false` shape? Prompts live in MongoDB and are not visible from code. *Recommendation: unify.*~~

> **Decided (2026-11-04, Lindani Masinga):** Undecided; revisit later. All 18 tools keep their current rejection response shapes during this migration. No refusal-text changes.

5. ~~**OQ-5. Preserve first, tighten separately (D4)?** Or fold F4-1 (13 STORE-unrestricted tools, OTP) and F4-3 into the migration phases? *Recommendation: preserve, and fix F4-1 now in the current service gates as its own security change. It should not wait for any phase.*~~

> **Decided (2026-11-04, Lindani Masinga):** Preserve the current access matrix exactly. F4-1 and F4-2 go to a separate, later deployment — not this migration. Note: this differs from the recommendation, which was to fix F4-1 now in the current service gates.

6. ~~**OQ-6. Defense-in-depth for ADMIN at the filter (step 5, never in controllers):** should `StoreScopeValidationFilter` reject `audience=ADMIN` tokens while `isSignatureVerificationEnabled()` is false? This guards against a forged ADMIN token if `STORE_SCOPE_JWT_SECRET` is or was compromised. It is a small change, but it couples the filter to WhatsApp config until a non-WhatsApp ADMIN channel exists. *Recommendation: yes, as a temporary rule, removed by the future ADMIN-channel ADR.*~~

> **Decided (2026-11-04, Lindani Masinga):** Deferred. Fix later; not part of this work.

7. ~~**OQ-7. `create_user` role allowlist over MCP:** which `ProfileRoles` may an AI agent create (CUSTOMER and MESSENGER only? STORE_ADMIN?). Separately, how urgent is closing REST `POST`/`PATCH /user` role self-assignment (F4-2) relative to configuring `appSecret`? This is a product and security call; this ADR only flags it.~~

> **Decided (2026-11-04, Lindani Masinga):** Disable role assignment via MCP entirely for now. The `create_user` MCP tool must not be able to set or change a profile's role at all until this is properly designed. This closes the F4-2 MCP vector specifically. The REST-side gap (POST /user, PATCH /user/{id}, issue #138) is separate and stays open. See "OQ-7 Approved Behaviour Change" section below.

> **Phase 2b resolved (2026-11-05, Lindani Masinga):** Role-allowlist approach not adopted. `create_user` removed from the MCP surface entirely (17 tools). No self-service registration on WhatsApp for any role; registration happens on the web apps (driver.izinga.co.za, shop.izinga.co.za, izinga-onboarding) via REST. `UserProfileService.create(...)` stays intact for REST/internal callers. REST role self-assignment (issue #138) remains open and out of scope for this ADR.

---

## OQ-7 Approved Behaviour Change: create_user Role Restriction (Phase 2b)

**Status:** Approved, 2026-11-04, Lindani Masinga
**Scope:** NOT Phase 0. NOT inside any pure-move PR. Ships as its own dedicated, separately-reviewed PR, with a before/after table in AMD-01 style.
**Default placement:** Phase 2b — immediately after `create_user` moves to `UserMcpController` (Phase 2). Lindani may pull it earlier as a standalone change before Phase 2 if the F4-2 security risk demands it.

### Decision
The `create_user` MCP tool must not be able to set or change a profile's role. Role assignment via MCP is disabled entirely until this is properly designed.

This is the only exception to the "preserve exactly" rule in D4. All other migration PRs are pure moves.

### Deployment gating
- The Phase 0 `McpAccessMatrixTest` rows for `create_user` are updated deliberately in the Phase 2b PR. The diff is the reviewed evidence.
- If the tool description or JSON schema changes (see pre-implementation question 4 below), the `McpToolContractSnapshotTest` golden file is updated in the same PR, and the diff is explicitly reviewed.

### Separation from REST
The REST-side role-assignment gap (POST /user, PATCH /user/{id}, issue #138) is separate and stays open. This change closes only the MCP vector.

### Pre-implementation questions (not answered here — require Lindani's input before implementation)

1. **WhatsApp self-registration flow.** The `create_user` `@Tool` description at `izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/UserProfileService.kt:129` says "It can be a normal customer or a driver or a store owner depending on the role specified in the profile object." WhatsApp driver and store-owner self-registration through agents may rely on setting role=MESSENGER or role=STORE_ADMIN via this tool. If role assignment is disabled, those flows break. Must be verified in production agent configs (MongoDB) before implementation.

2. **Role-null placeholder collision.** `validateUserProfileForCreate` at `UserProfileService.kt:285-287` requires `role != null` and throws "role is required" when it is null. The ONB-FIX duplicate-detection logic at `UserProfileService.kt:157-176` treats a profile with `role == null` as an OTP placeholder (created by `WhatsAppOtpService`): "role == null → this is the OTP placeholder → completing it is the intended path." If the MCP tool strips or rejects role, the caller must somehow supply a role but it cannot take effect — or the validation must change to allow null with a server-assigned default. This needs explicit design before implementation.

3. **Rejection vs. ignore.** Should a supplied ADMIN (or other privileged) role value be (a) rejected with an error (revealing to the caller that ADMIN is a known role), or (b) silently overridden to a safe default? The rejection shape interacts with the deferred OQ-4: a new error shape here may be inconsistent with today's other shapes and would itself be a snapshot contract question.

4. **Tool description and schema change.** The `@Tool` name, description, and `@ToolParam` annotations at `UserProfileService.kt:129` are part of the frozen snapshot contract. Disabling role via MCP likely requires changing the description ("It can be a normal customer or a driver or a store owner depending on the role specified...") and may affect the `role` parameter schema. Any change is a `McpToolContractSnapshotTest` failure — it must be explicitly approved, and the updated golden file is reviewed evidence in the Phase 2b PR.

---

## Approved by

Accepted: Lindani Masinga (co-founder, CEO), 2026-11-04, via answers to OQ-1 to OQ-7.

F4 items (F4-1: allowedTools never enforced; F4-2: self-assignable ADMIN role) still need to be flagged to Lindani Masinga and Hloniphani Manzi before implementation of any tightening change. There is no record that this notification has occurred as of this writing.

---

## Implementation status

**Branch:** `feature/adr-026-phase-0-foundation`

| Phase | Description | Status |
|---|---|---|
| 0 | Foundation (annotations, GuardedToolCallback, McpConfig wiring, ArchUnit R1-R6, contract snapshot + access matrix) | Done — commit `c71d029` |
| 1 | Move `start_order` + 4 document tools to `OrderCreationMcpController` / `DocumentUploadMcpController` | Done — commit `3c0ca9a` |
| 2 | Move 4 user tools to `UserMcpController` | Done — commit `e996dc1` |
| 3+4 | Move 5 order-read tools to `OrderMcpController`; move 3 store tools to `StoreMcpController` (bank masking at controller) | Done — commit `f77f647` |
| Step B | `StoreService.findByIdNoSideEffects()` — side-effect-free ownership probe for `StoreMcpController.findByOwner` | Done (uncommitted — awaiting code review) |
| 5 | Move `get_payouts_for_user` to `PayoutMcpController`; `ReconServiceImpl.getAllPayouts` becomes ungated | Done (uncommitted — awaiting code review) |
| 6 | Lock-down: `LEGACY_EXCLUSIONS` deleted, R1/R4 hard-fail, `allowEmptyShould` removed, `McpConfig` takes only `ApplicationContext`, `@RequiresAudience` retired, all tests updated | Done (uncommitted — awaiting code review) |
| 2b | `create_user` removed from MCP surface entirely (OQ-7) | **Done (uncommitted — awaiting code review)** — Lindani 2026-11-05: role-allowlist approach not adopted; `create_user` removed entirely; REST role self-assignment (#138) remains open. |

**Non-vacuity state (Phase 6 + Phase 2b):** 17 tools, 6 `@McpToolController` classes, 0 legacy service classes with `@Tool` methods.
