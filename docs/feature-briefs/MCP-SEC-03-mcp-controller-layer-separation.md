# Feature Brief: MCP Tools Controller-Layer Separation (MCP-SEC-03)

**Feature name:** MCP Controller-Layer Separation (ADR-026)

**Status:** Approved for Implementation (Gate 1 cleared: OQ-1 through OQ-7 answered by Lindani Masinga, 2026-11-04; ADR-026 is Accepted.)

**Requested by:** Lindani Masinga (co-founder, proposal of 2026-11-02)

**Business objective:** Remove the structural root cause of a class of payout-scheduler bugs (the most recent being the 2026-11-02 bug fixed in PR #155, where `SchedulerService.updateMissingPayouts()` accidentally called the gated `get_payouts_for_user` tool method and was silently blocked). The root cause is that all 18 MCP `@Tool` methods sit directly on domain service classes, making those methods dual-use: they are both external AI-agent entry points (where caller-identity enforcement must happen) and internal service methods (where it must not). The fix is to move every `@Tool` annotation off service classes and onto a dedicated controller layer in `io.curiousoft.izinga.ordermanagement.mcp`, so that authorization checks run only at the external boundary, and all internal callers of service methods can never accidentally pass through those checks. This eliminates an entire class of latent bug and makes the access matrix auditable, declarative, and ArchUnit-enforced.

**Audience:** Internal — Engineering, Security & Compliance

**Products affected:**
- `ijudi-api` (`izinga-ordermanager` — new `mcp` controller package, `McpConfig`, `StoreScopeValidationFilter`; `izinga-usermanagement` — tool annotations and gates removed from `UserProfileService`, `DocumentUploadMcpService`; `izinga-recon` — tool annotation and gate removed from `ReconServiceImpl`; `izinga-commons` — `CallerIdentityValidator` may shrink in Phase 6)

**Out of scope:**
- No REST endpoint, path, method, request shape, or response shape changes.
- No tool name, description, parameter name, or parameter type changes. The tool contract is frozen and snapshot-enforced (see REQ-04), except the deliberate description change in the Phase 2b PR if Lindani approves it after answering OQ-7 pre-implementation questions.
- No change to any frontend or mobile repo (`ijudi`, `cs-lifestyle`, `furniture-delivery-app`, `izinga-onboarding`, `izinga-pay`, `izinga-products-website`).
- **Access matrix preserved exactly.** Every tool's allow/deny matrix for DRIVER, CUSTOMER, STORE, ADMIN, and OTP audiences is preserved exactly as it stands on `develop` for all migration PRs. Tightening any cell is a separate, explicitly approved change with its own before/after table. The only approved behaviour change inside this migration is OQ-7 (`create_user` role restriction), delivered in Phase 2b as a dedicated, separately-reviewed PR.
- **F4-1 (allowedTools never enforced) and F4-2 (self-assignable ADMIN) tightening are explicitly deferred to a separate future deployment.** They are not part of any phase of this migration, in accordance with the OQ-5 decision (2026-11-04). They are tracked as separate GitHub issues under iZinga Security & Compliance.
- **OQ-4 (refusal shape unification) is deferred.** All 18 tools keep their current rejection shapes during the entire migration. The three distinct shapes (`IDENTITY_REFUSAL: CallerIdentityViolationException`, prefix-less `AudienceViolationException` on `find_stores`/`find_store_or_shops_by_id`, and success-shaped `StartOrderResponse.rejected` on `start_order`) are pinned by `McpAccessMatrixTest` and must remain byte-identical through every migration PR.
- **OQ-6 (filter-level ADMIN rejection when signature verification is off) is deferred.** Not part of this work.
- **Two security findings discovered during this investigation are explicitly out of scope for this feature** (see "Out-of-Scope Security Findings" section below). They must be tracked and fixed independently.
- ADR-025 / MCP-SEC-02 formal retirement depends on the open remainder of OQ-3 (AOP approach: not adopted; branch and `stash@{5}` disposal: TBD by Lindani).
- The `feature/payout-disbursement-fixes` branch is not part of this work; Phase 5 of this feature is sequenced to start only after that branch merges to `develop`.

---

## Open Questions — Answered (Lindani Masinga, 2026-11-04)

The ADR-026 investigation identified 7 judgment calls. All 7 have been answered by Lindani Masinga on 2026-11-04. Answers are recorded verbatim below.

| # | Question | ADR recommendation | Lindani's answer |
|---|---|---|---|
| OQ-1 | **Placement and language:** one `ordermanagement.mcp` package, all controllers in Java? This moves 3 Kotlin tool surfaces (`UserProfileService`, `DocumentUploadMcpService`, `ReconServiceImpl`) into Java, unlike ADR-025's OQ-4 answer (Kotlin) for the annotation. Alternative B keeps Kotlin per module at the cost of string audiences and a split boundary. | Java, single package | **Java, single package `io.curiousoft.izinga.ordermanagement.mcp` in `izinga-ordermanager`.** D1 table names and Java-only compilation constraint confirmed. |
| OQ-2 | **Naming:** `*McpController` with `@McpToolController` (matches Lindani's proposal), or `*McpTools`? | `*McpController` | **Option A. `@McpToolController` class-level marker annotation; classes named `*McpController` (OrderMcpController, StoreMcpController, etc.).** Matches the existing REST-controller naming convention. |
| OQ-3 | **Retire ADR-025 / MCP-SEC-02 formally?** If yes: drop `feature/mcp-sec-02-phase1-caller-identity-aop` branch, and either drop `stash@{5}` or absorb its audit-enum additions (`RESOLVER_NOT_FOUND`, `OWNERSHIP_MISMATCH`) into Phase 0. | Retire; absorb the enum split from `stash@{5}` | **Partial decision.** AOP approach is NOT adopted. `feature/mcp-sec-02-phase1-caller-identity-aop` branch and `stash@{5}` are left untouched; disposal is not explicitly decided and remains open for Lindani. The `RESOLVER_NOT_FOUND` / `OWNERSHIP_MISMATCH` audit-enum split is NOT absorbed into Phase 0. ADR-025 formal retirement remains open. |
| OQ-4 | **Unify refusal shape:** should all boundary refusals become a thrown `IDENTITY_REFUSAL:` plus an audit record? This changes `start_order`'s current `{success:false, message}` success-shaped refusal and the prefix-less `AudienceViolationException` text on `find_stores` and `find_store_or_shops_by_id`. Do any production `AiAgentConfig` prompts in MongoDB depend on `start_order`'s `success:false` shape? | Unify — but Lindani must confirm no agent prompt logic parses the `success:false` shape | **Undecided; revisit later.** All 18 tools keep their current rejection shapes during this migration. No refusal-text changes in this migration. |
| OQ-5 | **Preserve behaviour first, tighten separately (D4)?** Or fold F4-1 (the STORE-unrestricted 13-tool gap and the OTP audience gap) into the migration phases? | Preserve exactly; fix F4-1 as a separate, independent security change in the current service gates before migration begins, not inside any phase | **Preserve the current access matrix exactly during the migration. F4-1 and F4-2 go to a SEPARATE, later deployment — not this migration.** Note: this differs from the recommendation, which was to fix F4-1 now in the current service gates. |
| OQ-6 | **Defense-in-depth for ADMIN at the filter (step 5):** should `StoreScopeValidationFilter` reject `audience=ADMIN` tokens while `isSignatureVerificationEnabled()` is false? | Yes, as a temporary rule, removed when a future non-WhatsApp ADMIN channel ADR exists | **Deferred. Not part of this work.** |
| OQ-7 | **`create_user` role allowlist over MCP:** which `ProfileRoles` values may an AI agent create? Separately, how urgently should REST `POST /user` and `PATCH /user/{id}` role self-assignment (F4-2 security finding) be fixed relative to configuring `appSecret` in production? | The ADR flags this but defers the product decision to Lindani | **Disable role assignment via MCP entirely for now.** The `create_user` MCP tool must not be able to set or change a profile's role at all. This closes the F4-2 MCP vector only. The REST-side gap (POST /user, PATCH /user/{id}, issue #138) is separate and stays open. Ships as Phase 2b — a dedicated, separately-reviewed PR after `create_user` moves to `UserMcpController`. Lindani may pull it earlier as a standalone change. See REQ-12 and the "Pre-implementation questions for OQ-7" below. |

**Gate 1 cleared: all 7 OQ answers recorded. Status is Approved for Implementation.**

---

## Pre-implementation Questions for OQ-7 (Phase 2b Blocker)

These questions must be answered before Phase 2b implementation begins. They do NOT block Phase 0 or Phases 1–2.

1. **WhatsApp self-registration flow.** The `create_user` `@Tool` description at `izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/UserProfileService.kt:129` says "It can be a normal customer or a driver or a store owner depending on the role specified in the profile object." WhatsApp driver and store-owner self-registration through agents may rely on setting `role=MESSENGER` or `role=STORE_ADMIN` via this tool. If role assignment is disabled, those flows break. Must be verified in production agent configs (MongoDB) before implementation.

2. **Role-null placeholder collision.** `validateUserProfileForCreate` at `UserProfileService.kt:285-287` requires `role != null` and throws "role is required" when it is null. The ONB-FIX duplicate-detection logic at `UserProfileService.kt:157-176` treats a profile with `role == null` as an OTP placeholder. If the MCP tool strips or rejects role, the caller must somehow supply a role but it cannot take effect — or the validation must change to allow null with a server-assigned default. This needs explicit design before implementation.

3. **Rejection vs. ignore.** Should a supplied ADMIN (or other privileged) role value be (a) rejected with an error (revealing to the caller that ADMIN is a known role), or (b) silently overridden to a safe default? The rejection shape interacts with the deferred OQ-4.

4. **Tool description and schema change.** The `@Tool` name, description, and `@ToolParam` annotations at `UserProfileService.kt:129` are part of the frozen snapshot contract. Disabling role via MCP likely requires changing the description and may affect the `role` parameter schema. Any change is a `McpToolContractSnapshotTest` failure — it must be explicitly approved, and the updated golden file is reviewed evidence in the Phase 2b PR.

---

## Out-of-Scope Security Findings (route to iZinga Security & Compliance)

Two security findings were discovered while building the ADR-026 tool inventory. They come from code reading only and are **not yet live-verified**. They are explicitly out of scope for this feature — this is a behaviour-preserving refactor. They must not wait for ADR-026, and Lindani should route them to iZinga Security & Compliance for live confirmation and tracking as their own GitHub issues, following the same pattern as issues #153 and #154.

**Finding F4-1 — `AiAgentConfig.allowedTools` is never enforced:**
`StoreAiAgent` puts every `McpServerConfig` into the OpenAI tools array without filtering by `allowedTools`. `McpServerConfig` has no `allowed_tools` field, and `getAllowedTools()` is only read in `StoreLineProvisioningService` to copy the template. Result: a STORE-line agent is offered all 18 tools, not the configured subset. Members of the public messaging a store's WhatsApp line can therefore reach all 13 STORE-unrestricted tools in the access matrix via prompt injection: any user's full profile by phone, any order, any payouts, writes to any user's document tags, and `create_user` for any phone. This also invalidates the evidence cited by AMD-01 for the `find_users` tightening ("store template's `allowedTools` contains only `find_store_or_shops_by_id`") — the `find_users` tightening itself is correct and safe; only the cited evidence is wrong.

**Finding F4-2 — `ProfileRoles.ADMIN` is self-assignable:**
`create_user` (MCP) accepts any `role`, including `ADMIN`, for the caller's own phone number. REST `POST /user` and `PATCH /user/{id}` fall through to `anyRequest().permitAll()` (tracked broadly in issue #138) and also accept `role`; the PATCH has no ownership check. An ADMIN-role profile grants `ROLE_ADMIN` on REST and, once `whatsapp.cloud.appSecret` is configured in production, would receive an ADMIN MCP audience token on that user's next WhatsApp message. **`appSecret` must not be configured in production until this role self-assignment gap is closed.** OQ-7 (Phase 2b) closes the MCP vector only. The REST vector (#138) stays open separately.

Both findings should be raised as separate GitHub issues and routed to iZinga Security & Compliance for live verification. They are not acceptance criteria for this brief.

---

## Requirements

- REQ-01: A dedicated Java package `io.curiousoft.izinga.ordermanagement.mcp` in `izinga-ordermanager` must contain all classes annotated `@McpToolController`. Classes are named `*McpController` (Option A — decided: `OrderMcpController`, `StoreMcpController`, `UserMcpController`, `OrderCreationMcpController`, `DocumentUploadMcpController`, `PayoutMcpController`). The marker annotation is `@McpToolController`.

- REQ-02: A new class-level marker annotation `@McpToolController` (meta-annotated `@Component`) must be defined. A new method-level annotation `@McpAudience(Audience[] value)` must be defined. Every `@Tool` method in the codebase must carry a non-empty `@McpAudience`.

- REQ-03: A `GuardedToolCallback` decorator must wrap each `ToolCallback` registered from a controller-declared `@Tool` method in `McpConfig`. In Phase 0 and during the migration, the decorator asserts `CallerIdentityContext.isMcpRequest()` ONLY and fails closed if false. The decorator does no audience gating, no audit write, and no refusal shaping in this phase — those remain byte-equivalent in each controller method body. No AOP or `spring-boot-starter-aop` may be used. `StoreScopeValidationFilter.doFilterInternal()` sets `IS_MCP_REQUEST=true` unconditionally for every `/mcp` request (line 76, before the scope-param branch), so the assert cannot fire on any current `/mcp` path. The `@McpAudience` annotation is a declarative contract enforced by ArchUnit R2 and pinned by `McpAccessMatrixTest`, not by the wrapper at runtime. When OQ-4 is eventually decided, the wrapper takes over audience gating, audit writes, and standard `IDENTITY_REFUSAL:` throwing; at that point controller bodies shrink and `McpAccessMatrixTest` is updated deliberately.

- REQ-04: A `McpToolContractSnapshotTest` must be written in Phase 0 against the current code, producing a golden JSON file of all 18 `ToolDefinition`s (name, description, inputSchema). Any byte-level diff to that file fails the build. This test must remain green through every subsequent phase, except the Phase 2b PR where the diff is explicitly reviewed if OQ-7 pre-implementation question 4 results in a description or schema change.

- REQ-05: A `McpAccessMatrixTest` parameterized characterization test must be written in Phase 0 against the current code, covering the full allow/deny matrix from ADR-026 Table F1 for all 18 tools × {tokenless, DRIVER, CUSTOMER, STORE, ADMIN, OTP} × {own, other}. This test must pin all three current refusal shapes byte-exact (IDENTITY_REFUSAL CallerIdentityViolationException, prefix-less AudienceViolationException, and success-shaped StartOrderResponse.rejected). No migration phase PR may tighten any cell of this matrix — matrix changes are separate, explicitly approved work, except the Phase 2b PR (OQ-7) where the create_user rows are updated deliberately.

- REQ-06: ArchUnit rules R1 through R6 (as specified in ADR-026 §D5) must be added in Phase 0 with a named `LEGACY_EXCLUSIONS` list containing the 6 current service classes (confirmed sufficient on today's code: `OrderServiceImpl`, `McpOrderService`, `StoreService`, `ReconServiceImpl`, `UserProfileService`, `DocumentUploadMcpService`). Each subsequent phase shrinks this list. Phase 6 deletes the list entirely and the rules hard-fail on any violation.

- REQ-07: `McpConfig` must keep exactly one `MethodToolCallbackProvider` bean. It builds this provider from controller beans only. Any tool accidentally annotated on both a service and a controller causes a startup-time `IllegalStateException` on duplicate tool name, failing fast.

- REQ-08: Each controller must delegate all data access to its corresponding service. Controllers must not depend on `*Repository` or `*Repo` types (ArchUnit rule R5). Bank-account masking and `markUp` decoration must move to the controller methods, applied to the returned objects before they are returned to the agent.

- REQ-09: Services must have all `isMcpRequest()` branches and all `CallerIdentityValidator` / `StoreScopeValidator` calls removed by Phase 6. After Phase 6 there must be zero uses of `CallerIdentityContext` getters, `isMcpRequest()`, `StoreScopeContext` getters, `CallerIdentityValidator.validate*`, `rejectTokenlessMcp`, or `StoreScopeValidator.*` outside the `mcp` and `security` packages (ArchUnit rule R4).

- REQ-10: Phase 5 (`get_payouts_for_user` → `PayoutMcpController`) must not start until `feature/payout-disbursement-fixes` merges to `develop`, because that branch rewrites `ReconServiceImpl` by +172/-45 lines. The Product Owner must gate Phase 5's branch opening on that merge.

- REQ-11: `@RequiresAudience` currently has zero usages and enforces nothing. It must be explicitly retired in Phase 6 (removed from the codebase) rather than left as silent documentation. The `McpConfig` Javadoc SA-021-18 comment must be updated in Phase 6 to point at controllers rather than at service-method annotations.

- REQ-12: `UserMcpController.create_user` must not set or change a profile's role for any caller (any MCP audience: DRIVER, CUSTOMER, STORE, ADMIN). This closes the F4-2 MCP vector. This requirement ships as Phase 2b — a dedicated, separately-reviewed PR with a before/after table in AMD-01 style. Phase 2b implementation must not begin until the 4 pre-implementation questions listed in the "Pre-implementation Questions for OQ-7" section are answered by Lindani. The REST-side role-assignment gap (POST /user, PATCH /user/{id}, issue #138) is out of scope for Phase 2b.

  **Phase 2b resolved (2026-11-05, Lindani Masinga):** Role-allowlist approach not adopted. `create_user` removed from the MCP surface entirely. The 4 pre-implementation questions are moot. No self-service registration on WhatsApp for any role; registration happens on the web apps (driver.izinga.co.za, shop.izinga.co.za, izinga-onboarding) via REST. `UserProfileService.create(...)` stays intact for REST/internal callers. REST role self-assignment (issue #138) remains open and out of scope. The MCP surface now carries 17 tools. `McpAccessMatrixTest` create_user rows and REQ-13 test deleted; `noCreateUserToolIsExposed()` absence test added. Status: implementation done on `feature/adr-026-phase-0-foundation`, awaiting code review.

  REQ-12 satisfied by Phase 2b (2026-11-05): create_user removed from the MCP surface entirely, per Lindani Masinga; no MCP caller can set or change a role.

- REQ-13: ~~The `McpAccessMatrixTest` written in Phase 0 must include test cases for `create_user` that explicitly characterise today's role-accepting behaviour — that a supplied `role` field is accepted and persisted by `UserProfileService`. These test cases are written against the unmodified service. The Phase 2b PR updates these rows with a deliberate diff as the reviewed evidence of the OQ-7 role-restriction change. The snapshot test golden file is also updated in the Phase 2b PR if the tool description or schema changes.~~

  **REQ-13 superseded by Phase 2b resolution (2026-11-05):** `create_user` is removed from the MCP surface entirely rather than restricted. The characterisation rows were removed from `McpAccessMatrixTest`; a `createUserToolIsAbsentFromMcpSurface()` absence test replaced them. The golden snapshot no longer contains a `create_user` entry.

---

## Acceptance Criteria

- AC-01: Given the Phase 0 branch is complete, when `./mvnw clean test` runs (full unscoped, JDK 17 Corretto), then `McpToolContractSnapshotTest` and `McpAccessMatrixTest` both pass (written against the unmodified services — no tool moved in Phase 0), and ArchUnit rules R1–R6 pass with the 6-class `LEGACY_EXCLUSIONS` list. `McpAccessMatrixTest` pins all three current refusal shapes byte-exact. `McpAccessMatrixTest` rows for `create_user` explicitly characterise that today a supplied `role` is accepted (REQ-13). The `GuardedToolCallback` wraps zero controller-declared tools in Phase 0 (no controllers exist yet); the wrapper structure and isMcpRequest assert are in place but cannot fire on any current `/mcp` path.

- AC-02: Given any migration phase (1–5) PR is complete, when the full unscoped `./mvnw clean test` (JDK 17 Corretto) runs, then `McpToolContractSnapshotTest` produces a byte-identical snapshot (zero diff), `McpAccessMatrixTest` passes with no matrix cell changed, and ArchUnit R1–R6 pass with the reduced `LEGACY_EXCLUSIONS` list for that phase.

- AC-03: Given Phase 1 is complete, when the MCP server dispatches a tool call to `start_order` or any of the 4 document tools, then the `GuardedToolCallback` decorator fires before the controller method body and asserts `CallerIdentityContext.isMcpRequest()` only. Per-tool audience checks, audit writes, and refusal shapes remain byte-equivalent to the current service implementations, expressed inside each controller method body. The decorator does not enforce the audience allowlist or write audit records independently in Phase 0 or during the migration.

- AC-04: Given any phase is complete, when an internal Java caller (scheduler, event listener, REST controller) invokes a service method that was previously a `@Tool` method, then no `CallerIdentityViolationException` or `AudienceViolationException` is thrown — the service method executes as pure business logic with no MCP gating.

- AC-05: Given Phase 6 is complete, when `./mvnw clean test` runs, then `LEGACY_EXCLUSIONS` is empty, all R1–R6 rules hard-fail on any violation, and `grep -r "isMcpRequest\|StoreScopeContext\|CallerIdentityValidator\|StoreScopeValidator\|RequiresAudience" --include="*.java" --include="*.kt"` returns zero hits outside the `mcp` and `security` packages.

- AC-06: Given Phase 6 is complete, when the `McpConfig` Javadoc is reviewed, then the SA-021-18 comment refers to `@McpAudience` on controller methods, not to any annotation on service methods, and the SA-022-01 scope-JWT rules still document the filter-layer contract unchanged.

- AC-07: Given a tool call arrives with a null audience (tokenless request) on any `/mcp` path, when the `GuardedToolCallback` runs, then it asserts `CallerIdentityContext.isMcpRequest()` (which is true for all `/mcp` paths) and delegates to the controller method body. Each controller body handles the null audience in its preserved, byte-equivalent shape: a `IDENTITY_REFUSAL: CallerIdentityViolationException` for the 15 tools using `validateCallerPhone` / `validateCallerOwnsId` / `rejectTokenlessMcp`; a prefix-less `AudienceViolationException` for `find_stores` and `find_store_or_shops_by_id`; and a success-shaped `StartOrderResponse.rejected(...)` for `start_order`. The `GuardedToolCallback` does not write audit records or throw `IDENTITY_REFUSAL:` text directly. This behaviour changes only when OQ-4 is decided and the wrapper is extended.

- AC-08: Given a tool call arrives with an audience value not present in that tool's `@McpAudience` list, when the `GuardedToolCallback` runs, then it asserts `isMcpRequest()` only and delegates to the controller method body, which handles the audience rejection in its preserved, byte-equivalent shape (matching today's service gate). The `@McpAudience` annotation is enforced by the ArchUnit R2 rule and characterised by `McpAccessMatrixTest`; it is not enforced by the wrapper at runtime during this migration.

- AC-09: Given a tool accidentally annotated on both a service and a controller (regression check), when the application context starts, then it throws `IllegalStateException` on duplicate tool names from the single `MethodToolCallbackProvider` bean — not silently winning with one copy.

- AC-10: Given Phase 5 is ready to begin, when the Product Owner reviews the branch gate, then `feature/payout-disbursement-fixes` must already be merged to `develop` before the Phase 5 branch is opened.

- AC-11: (OQ-4 — Deferred — not in this feature.) Refusal shape unification is deferred. No change to any rejection shape is part of this migration.

- AC-12: (OQ-6 — Deferred — not in this feature.) Filter-level ADMIN rejection when signature verification is off is deferred. No change to `StoreScopeValidationFilter`'s ADMIN handling is part of this migration.

- AC-13: ~~Given Phase 2b is deployed, when any MCP caller (any audience) invokes `create_user` with a `role` field in the request, then `UserMcpController.create_user` does not set or change the user profile's role...~~

  **AC-13 superseded by Phase 2b resolution (2026-11-05, Lindani Masinga):** `create_user` removed from the MCP surface entirely on `feature/adr-026-phase-0-foundation`. No MCP caller (any audience) can invoke `create_user` — the tool is not registered. `McpAccessMatrixTest` has a `createUserToolIsAbsentFromMcpSurface()` absence assertion. `McpToolContractSnapshotTest` golden file has 17 entries (create_user removed). `UserProfileService.create(...)` remains callable by REST/internal callers. REST role self-assignment (issue #138) is out of scope.

- AC-14: Given Phase 0 is deployed to any environment, when any MCP tool is called on any existing `/mcp` path with any valid or invalid token, then the system response is byte-identical to the response before Phase 0 was deployed. No tool is moved in Phase 0; all tool annotations remain on service classes; `LEGACY_EXCLUSIONS` contains all 6 service classes; the `GuardedToolCallback` wraps zero controller tools; `StoreScopeValidationFilter` sets `IS_MCP_REQUEST=true` unconditionally for all `/mcp` requests so the isMcpRequest assert cannot fire on any current path.

---

## Dependencies

- **`develop` at commit `d23a595`** (after PR #151 ADR-024, PR #152, PR #155, PR #156 ADR-027/AMD-01) — the confirmed baseline.
- **ADR-026 is Accepted** (Lindani Masinga, 2026-11-04). No longer blocked on OQ answers.
- **`feature/payout-disbursement-fixes`** must merge to `develop` before Phase 5 branch opens (REQ-10).
- **iZinga Security & Compliance confirmation** that the two F4 findings (Section "Out-of-Scope Security Findings") are tracked as their own GitHub issues with live verification, before Phase 6 ships to production. This brief does not gate on those being fixed — it only gates on them being tracked.
- **OQ-4 resolution requires Lindani to check production MongoDB** (whether any `AiAgentConfig` document's system prompt contains logic that parses `start_order`'s `success:false` response shape). This is needed only when OQ-4 is revisited — it does not block any phase of this migration.
- **Phase 2b pre-implementation questions** (see "Pre-implementation Questions for OQ-7") must be answered before the Phase 2b branch opens. They do not block Phase 0, 1, or 2.

---

## Risks

| Risk | Mitigation |
|---|---|
| A moved tool's JSON schema drifts (renamed parameter, `required` flag, description) and agents degrade silently | `McpToolContractSnapshotTest` byte-exact check blocks the build |
| A matrix cell silently changes during a move (access loosened or tightened) | `McpAccessMatrixTest` characterization test; D4 rule forbids any matrix cell change inside a migration PR |
| A tool is left on both a service and a controller | One provider bean, Spring AI throws `IllegalStateException` on duplicate tool name at startup; ArchUnit R1 |
| ThreadLocal thread-affinity breaks on a future `type: async` or transport change | `GuardedToolCallback` asserts `isMcpRequest()` and fails closed. Future option: `McpTransportContext` extraction (Alternative D in ADR-026) is a one-class swap once controllers exist |
| Porting Kotlin gate logic to Java introduces null-handling differences | `McpAccessMatrixTest` covers null and blank phone/id inputs; Kotlin gate tests for `UserProfileService`, `DocumentUploadMcpService`, `ReconServiceImpl` must be ported to controllers in Phases 2, 4, 5 respectively |
| Non-deterministic full-reactor build failures on `develop` stall phase gates | No scoped or partial test runs — the no-scoped-build-exclusions rule applies; fix or escalate before greening any phase |
| `feature/payout-disbursement-fixes` merges after Phase 5 starts, conflicting with the recon rewrite | Phase 5 is gated on that branch merging first (REQ-10, AC-10) |
| OQ-4 (refusal shape unification) causes a production agent prompt regression if any agent parses `start_order`'s `success:false` shape | OQ-4 is deferred. When revisited, Lindani must check production MongoDB `AiAgentConfig` documents before deciding |
| F4-2 (`ADMIN` self-assignable) is not fixed before `appSecret` is configured in production | Block `appSecret` configuration until iZinga Security & Compliance closes F4-2 (tracked as separate issue); Phase 2b closes the MCP vector only |
| Phase 2b OQ-7 pre-implementation questions reveal a WhatsApp self-registration dependency on `role` being set via MCP | Answer question 1 before Phase 2b by reading production MongoDB `AiAgentConfig` documents; Lindani must approve the resulting design |

**Flag to Solution Architect:** R4 (zero `CallerIdentityContext`/`StoreScopeContext` usage outside `mcp`/`security`) and R6 (only `StoreScopeValidationFilter` sets the sentinel) affect shared classes in `izinga-commons`. The ArchUnit rules enforce these contracts. The Solution Architect should review Phase 6's empty-exclusions lock-down to confirm no undiscovered internal caller is broken. The `StoreAdminStoreProfileEventHandler.handleNewStoreCreatedEvent` latent caller (which calls `findUserByPhone` via `userProfileService::findUserByPhone` on a synchronous event) must be rerouted to the ungated service method in Phase 2 before Phase 6 locks down.

---

## Stage 0 Gate Determination

This is a backend-only structural refactor. The only approved behaviour change inside the migration is OQ-7 (Phase 2b: `create_user` role restriction via MCP), which restricts role assignment and introduces no new personal-data category or consent flow.

- **Legal & Compliance required:** No. This change introduces no new data collection, no new consent flows, no new contracts, and no new regulatory exposure. The access matrix is frozen at today's behaviour for all phases except Phase 2b, which restricts role assignment via MCP — a narrowing of an existing capability, not a new data category or consent requirement. The two F4 security findings are routed to iZinga Security & Compliance as separate issues, not through Legal.
- **UI/UX Designer required:** No. No user-facing screens, no frontend changes, no API shape changes. If Phase 2b results in a `create_user` tool description change (OQ-7 pre-implementation question 4), that is an MCP-layer contract change, not a UI/UX change, and is handled by the deliberate snapshot update in the Phase 2b PR.
- **Marketing required:** No. This is an internal backend refactor with no customer-visible or driver-visible change. The Phase 2b role restriction is an internal security tightening with no user-facing surface.

---

## Agent Task Breakdown

Tasks are ordered by phase dependency. Phases 2–4 touch disjoint service classes and may run in parallel after Phase 1, but sequential is recommended to keep reviews small. Phase 5 is sequenced after `feature/payout-disbursement-fixes` merges. Pipeline order is: Developer → Code Reviewer → QA. QA runs the full unscoped `./mvnw clean test` on JDK 17 Corretto.

| Task | Agent | Depends on |
|---|---|---|
| **T-00: Close OQ-1 through OQ-7 with Lindani — Done (2026-11-04)** | iZinga Product Owner (facilitated); Lindani Masinga (answered) | None |
| **T-01 (Phase 0): Define `@McpToolController`, `@McpAudience`, `GuardedToolCallback` (isMcpRequest assert only — no AOP, no audience gating, no audit write in wrapper), `McpCaller`, `McpRefusal`** — no tools moved; `McpConfig` keeps one provider, wraps zero controller-declared tools; ArchUnit R1–R6 added with 6-class `LEGACY_EXCLUSIONS` (confirmed sufficient on today's code); `McpToolContractSnapshotTest` and `McpAccessMatrixTest` written against current code, pinning all three current refusal shapes; `McpAccessMatrixTest` rows for `create_user` explicitly characterise today's role-accepting behaviour (REQ-13); no absorption of `stash@{5}` audit-enum additions (OQ-3 partial — branch/stash disposal TBD) | iZinga Backend Developer | T-00 |
| **T-01-CR (Phase 0): Code review — verify ArchUnit rules, exclusion list, snapshot golden file, no production behaviour changed, isMcpRequest assert only in wrapper, no AOP, create_user matrix rows characterise role-accepting behaviour** | iZinga Code Reviewer | T-01 |
| **T-01-QA (Phase 0): Run full unscoped `./mvnw clean test` (JDK 17 Corretto), verify snapshot and matrix tests green, verify ArchUnit passes with exclusions, confirm AC-14 (Phase 0 changes no production behaviour)** | iZinga QA & Test Automation | T-01-CR |
| **T-02 (Phase 1): Move `start_order` → `OrderCreationMcpController`; move 4 document tools (`DocumentUploadMcpService` Kotlin) → `DocumentUploadMcpController` (Java); move the CUSTOMER ownership check out of `OrderServiceImpl.startOrder()`; shrink `LEGACY_EXCLUSIONS` by 2** | iZinga Backend Developer | T-01-QA |
| **T-02-CR (Phase 1): Code review — verify snapshot diff is empty, exclusion list shrank by 2, no matrix cell changed, start_order and document tool refusal shapes preserved byte-exact** | iZinga Code Reviewer | T-02 |
| **T-02-QA (Phase 1): Full unscoped `./mvnw clean test`; UAT live verification of matrix against real `/mcp` endpoint; verify snapshot byte-identical** | iZinga QA & Test Automation | T-02-CR |
| **T-03 (Phase 2): Move `find_user_by_phone`, `find_users`, `create_user`, `get_missing_fields_by_phone` → `UserMcpController`; strip gates from `UserProfileService`; reroute `StoreAdminStoreProfileEventHandler.handleNewStoreCreatedEvent` to ungated `userProfileService` method; shrink exclusions by 1** | iZinga Backend Developer | T-02-QA |
| **T-03-CR (Phase 2): Code review — verify `FirebaseJwtAuthenticationConverter`, OTP paths, `StoreDriverInviteService` now call ungated service method; exclusion list shrank; create_user moves but role-accepting behaviour preserved until Phase 2b** | iZinga Code Reviewer | T-03 |
| **T-03-QA (Phase 2): Full unscoped `./mvnw clean test`; UAT live verification; snapshot byte-identical; create_user matrix rows unchanged from Phase 0 characterisation** | iZinga QA & Test Automation | T-03-CR |
| **T-03b (Phase 2b — OQ-7): Dedicated PR — disable role assignment via MCP in `UserMcpController.create_user`. Answer the 4 pre-implementation questions first (see "Pre-implementation Questions for OQ-7"). Before/after table in AMD-01 style. Update `McpAccessMatrixTest` create_user rows and `McpToolContractSnapshotTest` golden file deliberately in this PR (if description/schema changes). Closes F4-2 MCP vector only. REST gap (#138) stays open.** | iZinga Backend Developer | T-03-QA and all 4 Phase 2b pre-implementation questions answered by Lindani |
| **T-03b-CR (Phase 2b): Code review — verify before/after table is correct, matrix diff for create_user rows is deliberate, snapshot diff is deliberate or empty, no unintended matrix cell changes, REST gap (#138) is explicitly noted as out of scope** | iZinga Code Reviewer | T-03b |
| **T-03b-QA (Phase 2b): Full unscoped `./mvnw clean test` (JDK 17 Corretto); UAT live verification of create_user role-restriction against real `/mcp` endpoint; verify no other tool behaviours changed** | iZinga QA & Test Automation | T-03b-CR |
| **T-04 (Phase 3): Move 5 order-read tools → `OrderMcpController`; strip gates from `OrderServiceImpl`; shrink exclusions by 1** | iZinga Backend Developer | T-03b-QA |
| **T-04-CR (Phase 3): Code review — verify `OrderController`, `YocoPaymentController`, `IzingaOrderManagerClient` paths now call ungated service; exclusion list shrank** | iZinga Code Reviewer | T-04 |
| **T-04-QA (Phase 3): Full unscoped `./mvnw clean test`; UAT live verification; snapshot byte-identical** | iZinga QA & Test Automation | T-04-CR |
| **T-05 (Phase 4): Move 3 store tools → `StoreMcpController`; masking moves to controller; `StoreScopeValidator.validate` leaves `StoreService.find`; shrink exclusions by 1** | iZinga Backend Developer | T-04-QA |
| **T-05-CR (Phase 4): Code review — verify masked-entity persistence hazard is gone; `EmailNotificationService.notifyOrder` scheduler path now calls ungated `StoreService.find`; exclusion list shrank** | iZinga Code Reviewer | T-05 |
| **T-05-QA (Phase 4): Full unscoped `./mvnw clean test`; UAT live verification; snapshot byte-identical; verify masked entity cannot be saved (no repository access from controller)** | iZinga QA & Test Automation | T-05-CR |
| **T-06-GATE: Confirm `feature/payout-disbursement-fixes` has merged to `develop` before Phase 5 branch opens** | iZinga Product Owner | T-05-QA and `feature/payout-disbursement-fixes` merge |
| **T-06 (Phase 5): Move `get_payouts_for_user` → `PayoutMcpController`; strip gate from `ReconServiceImpl`; shrink exclusions to 0** | iZinga Backend Developer | T-06-GATE |
| **T-06-CR (Phase 5): Code review — verify `ReconController` REST path calls ungated service; exclusions empty** | iZinga Code Reviewer | T-06 |
| **T-06-QA (Phase 5): Full unscoped `./mvnw clean test`; UAT live verification; snapshot byte-identical** | iZinga QA & Test Automation | T-06-CR |
| **T-07 (Phase 6): Empty `LEGACY_EXCLUSIONS`, hard-fail R1–R6; remove all `isMcpRequest()` from services; retire `@RequiresAudience`; update `McpConfig` SA-021-18/SA-022-01 Javadoc to point at controllers; formally mark ADR-027 Alternative C done; note ADR-025 / MCP-SEC-02 formal retirement depends on the open remainder of OQ-3 (AOP: not adopted; branch and `stash@{5}` disposal: TBD by Lindani)** | iZinga Backend Developer | T-06-QA |
| **T-07-CR (Phase 6): Final code review — verify ArchUnit hard-fails; exclusions empty; no residual dual-use service methods; ADR-025 retirement note is accurate** | iZinga Code Reviewer | T-07 |
| **T-07-QA (Phase 6): Full unscoped `./mvnw clean test`; `grep` scan confirms zero `isMcpRequest` / `CallerIdentityValidator` / `StoreScopeContext` outside `mcp`/`security` packages** | iZinga QA & Test Automation | T-07-CR |
| **T-08: Notify Release Manager that all 6 phases are code-review-passed and QA-verified; Release Manager merges to `develop` and coordinates production deployment** | iZinga Release Manager | T-07-QA |

---

## Priority

**P2 — Medium.** The root cause (dual-use service methods) is structural, not broken in production today (the specific PR #155 fix closed the active bug). The refactor eliminates the entire class of risk. It becomes P1 if a new tool is added to a service method before this is complete, since that creates a new instance of the original hazard.

**Marketing trigger:** No — internal backend refactor.

**UI/UX Designer required:** No — no UI changes.

**Legal & Compliance required:** No — backend structural refactor with no data collection, consent, or contract change. OQ-7 (Phase 2b) restricts role assignment via MCP, introducing no new personal-data category. The two F4 security findings are routed to iZinga Security & Compliance as separate issues, not to Legal.

---

## Definition of Done

The following state must exist before this feature is considered complete and in production:

1. All 7 migration phases (0–2, 2b, 3–6) have been code-review-passed (iZinga Code Reviewer PASS verdict) and QA-signed-off (iZinga QA full unscoped run PASS) on their respective `feature/adr-026-phase-N-*` branches.
2. `McpToolContractSnapshotTest` has remained byte-identical through every phase merge, except the deliberate Phase 2b diff (which is explicitly reviewed in the Phase 2b PR).
3. `McpAccessMatrixTest` has remained green through every phase merge with no matrix cell changed except the deliberate Phase 2b diff for `create_user` role restriction.
4. ArchUnit R1–R6 run with an empty `LEGACY_EXCLUSIONS` list and zero violations in production code.
5. `grep -r "isMcpRequest\|StoreScopeContext.get\|CallerIdentityValidator\|StoreScopeValidator\|RequiresAudience" --include="*.java" --include="*.kt" izinga-ordermanager/src/main izinga-usermanagement/src/main izinga-recon/src/main izinga-commons/src/main` returns zero hits outside the `mcp` and `security` packages.
6. The `McpConfig` Javadoc SA-021-18 and SA-022-01 comments describe the controller-layer boundary, not service-level annotations.
7. iZinga Security & Compliance has confirmed the two F4 security findings are tracked as named GitHub issues with live verification status (independent of their fix timeline).
8. `create_user` over MCP cannot set or change a profile's role (Phase 2b deployed).
9. Release Manager has merged the final phase to `develop` and deployed to production.

---

*Feature Brief prepared: 2026-11-04*
*Approved for Implementation: 2026-11-04 (OQ-1 through OQ-7 answered by Lindani Masinga)*
*Prepared by: iZinga Product Owner*
*Status: Approved for Implementation*
*ADR reference: ADR-026-mcp-tools-controller-layer-separation.md*
*ADR status: Accepted, 2026-11-04, Lindani Masinga*
*Note: ADR-025 / MCP-SEC-02 formal retirement open — AOP approach not adopted (OQ-3 partial); branch and stash@{5} disposal TBD by Lindani.*
