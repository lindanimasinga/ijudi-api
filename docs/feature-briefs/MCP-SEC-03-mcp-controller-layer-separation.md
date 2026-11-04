# Feature Brief: MCP Tools Controller-Layer Separation (MCP-SEC-03)

**Feature name:** MCP Controller-Layer Separation (ADR-026)

**Status:** BLOCKED — 7 open questions require Lindani Masinga's answers before this brief can move to Approved for Implementation. See "Open Questions" section below. No feature branch may be opened until every question is resolved and this Status field is updated to Approved for Implementation.

**Requested by:** Lindani Masinga (co-founder, proposal of 2026-11-02)

**Business objective:** Remove the structural root cause of a class of payout-scheduler bugs (the most recent being the 2026-11-02 bug fixed in PR #155, where `SchedulerService.updateMissingPayouts()` accidentally called the gated `get_payouts_for_user` tool method and was silently blocked). The root cause is that all 18 MCP `@Tool` methods sit directly on domain service classes, making those methods dual-use: they are both external AI-agent entry points (where caller-identity enforcement must happen) and internal service methods (where it must not). The fix is to move every `@Tool` annotation off service classes and onto a dedicated controller layer in `io.curiousoft.izinga.ordermanagement.mcp`, so that authorization checks run only at the external boundary, and all internal callers of service methods can never accidentally pass through those checks. This eliminates an entire class of latent bug and makes the access matrix auditable, declarative, and ArchUnit-enforced.

**Audience:** Internal — Engineering, Security & Compliance

**Products affected:**
- `ijudi-api` (`izinga-ordermanager` — new `mcp` controller package, `McpConfig`, `StoreScopeValidationFilter`; `izinga-usermanagement` — tool annotations and gates removed from `UserProfileService`, `DocumentUploadMcpService`; `izinga-recon` — tool annotation and gate removed from `ReconServiceImpl`; `izinga-commons` — `CallerIdentityValidator` may shrink in Phase 6)

**Out of scope:**
- No REST endpoint, path, method, request shape, or response shape changes.
- No tool name, description, parameter name, or parameter type changes. The tool contract is frozen and snapshot-enforced (see REQ-04).
- No change to any frontend or mobile repo (`ijudi`, `cs-lifestyle`, `furniture-delivery-app`, `izinga-onboarding`, `izinga-pay`, `izinga-products-website`).
- No change to the access matrix — every tool's allow/deny matrix for DRIVER, CUSTOMER, STORE, ADMIN, and OTP audiences is preserved exactly as it stands on `develop`. Tightening any cell is a separate, explicitly approved change with its own before/after table.
- **Two security findings discovered during this investigation are explicitly out of scope for this feature** (see "Out-of-Scope Security Findings" section below). They must be tracked and fixed independently.
- Superseding ADR-025 / MCP-SEC-02 formally is included as a Phase 6 task but requires Lindani's answer to OQ-3 first.
- The `feature/payout-disbursement-fixes` branch is not part of this work; Phase 5 of this feature is sequenced to start only after that branch merges to `develop`.

---

## Open Questions — BLOCKING (Lindani Masinga must answer all 7 before Status moves to Approved for Implementation)

The ADR-026 investigation identified 7 judgment calls that cannot be defaulted without Lindani's input. These are listed here verbatim from ADR-026 Section "Open questions for Lindani", with the ADR's recommendation noted for each. This brief may not proceed to implementation until Lindani answers them all.

| # | Question | ADR recommendation | Lindani's answer |
|---|---|---|---|
| OQ-1 | **Placement and language:** one `ordermanagement.mcp` package, all controllers in Java? This moves 3 Kotlin tool surfaces (`UserProfileService`, `DocumentUploadMcpService`, `ReconServiceImpl`) into Java, unlike ADR-025's OQ-4 answer (Kotlin) for the annotation. Alternative B keeps Kotlin per module at the cost of string audiences and a split boundary. | Java, single package | — |
| OQ-2 | **Naming:** `*McpController` with `@McpToolController` (matches Lindani's proposal), or `*McpTools`? | `*McpController` | — |
| OQ-3 | **Retire ADR-025 / MCP-SEC-02 formally?** If yes: drop `feature/mcp-sec-02-phase1-caller-identity-aop` branch, and either drop `stash@{5}` or absorb its audit-enum additions (`RESOLVER_NOT_FOUND`, `OWNERSHIP_MISMATCH`) into Phase 0. | Retire; absorb the enum split from `stash@{5}` | — |
| OQ-4 | **Unify refusal shape:** should all boundary refusals become a thrown `IDENTITY_REFUSAL:` plus an audit record? This changes `start_order`'s current `{success:false, message}` success-shaped refusal and the prefix-less `AudienceViolationException` text on `find_stores` and `find_store_or_shops_by_id`. Do any production `AiAgentConfig` prompts in MongoDB depend on `start_order`'s `success:false` shape? Prompts are not visible from code. | Unify — but Lindani must confirm no agent prompt logic parses the `success:false` shape | — |
| OQ-5 | **Preserve behaviour first, tighten separately (D4)?** Or fold F4-1 (the STORE-unrestricted 13-tool gap and the OTP audience gap) into the migration phases? | Preserve exactly; fix F4-1 as a separate, independent security change in the current service gates before migration begins, not inside any phase | — |
| OQ-6 | **Defense-in-depth for ADMIN at the filter (step 5):** should `StoreScopeValidationFilter` reject `audience=ADMIN` tokens while `isSignatureVerificationEnabled()` is false? This guards against a forged ADMIN token if `STORE_SCOPE_JWT_SECRET` is or was compromised (the placeholder secret committed to the repo must be treated as potentially compromised). It couples the filter to WhatsApp config until a non-WhatsApp ADMIN channel exists. | Yes, as a temporary rule, removed when a future non-WhatsApp ADMIN channel ADR exists | — |
| OQ-7 | **`create_user` role allowlist over MCP:** which `ProfileRoles` values may an AI agent create? (CUSTOMER and MESSENGER only? STORE_ADMIN?) Separately, how urgently should REST `POST /user` and `PATCH /user/{id}` role self-assignment (F4-2 security finding) be fixed relative to configuring `appSecret` in production? | The ADR flags this but defers the product decision to Lindani | — |

**Once Lindani answers all 7, the Product Owner will update this brief with the agreed decisions, set Status to Approved for Implementation, and brief the Project Manager directly.**

---

## Out-of-Scope Security Findings (route to iZinga Security & Compliance)

Two security findings were discovered while building the ADR-026 tool inventory. They come from code reading only and are **not yet live-verified**. They are explicitly out of scope for this feature — this is a behaviour-preserving refactor. They must not wait for ADR-026, and Lindani should route them to iZinga Security & Compliance for live confirmation and tracking as their own GitHub issues, following the same pattern as issues #153 and #154.

**Finding F4-1 — `AiAgentConfig.allowedTools` is never enforced:**
`StoreAiAgent` puts every `McpServerConfig` into the OpenAI tools array without filtering by `allowedTools`. `McpServerConfig` has no `allowed_tools` field, and `getAllowedTools()` is only read in `StoreLineProvisioningService` to copy the template. Result: a STORE-line agent is offered all 18 tools, not the configured subset. Members of the public messaging a store's WhatsApp line can therefore reach all 13 STORE-unrestricted tools in the access matrix via prompt injection: any user's full profile by phone, any order, any payouts, writes to any user's document tags, and `create_user` for any phone. This also invalidates the evidence cited by AMD-01 for the `find_users` tightening ("store template's `allowedTools` contains only `find_store_or_shops_by_id`") — the `find_users` tightening itself is correct and safe; only the cited evidence is wrong.

**Finding F4-2 — `ProfileRoles.ADMIN` is self-assignable:**
`create_user` (MCP) accepts any `role`, including `ADMIN`, for the caller's own phone number. REST `POST /user` and `PATCH /user/{id}` fall through to `anyRequest().permitAll()` (tracked broadly in issue #138) and also accept `role`; the PATCH has no ownership check. An ADMIN-role profile grants `ROLE_ADMIN` on REST and, once `whatsapp.cloud.appSecret` is configured in production, would receive an ADMIN MCP audience token on that user's next WhatsApp message. **`appSecret` must not be configured in production until this role self-assignment gap is closed.**

Both findings should be raised as separate GitHub issues and routed to iZinga Security & Compliance for live verification. They are not acceptance criteria for this brief.

---

## Requirements

- REQ-01: A dedicated Java package `io.curiousoft.izinga.ordermanagement.mcp` in `izinga-ordermanager` must contain all classes annotated `@McpToolController`. (Exact controller naming subject to Lindani's OQ-2 answer.)

- REQ-02: A new class-level marker annotation `@McpToolController` (meta-annotated `@Component`) must be defined. A new method-level annotation `@McpAudience(Audience[] value)` must be defined. Every `@Tool` method in the codebase must carry a non-empty `@McpAudience`.

- REQ-03: A `GuardedToolCallback` decorator must wrap each `ToolCallback` registered from a controller-declared `@Tool` method. Before delegating to the underlying method, it must: (1) assert `CallerIdentityContext.isMcpRequest()` and fail closed if false; (2) reject a null audience; (3) reject any audience not in `@McpAudience`; (4) write the audit record; (5) throw a standard `IDENTITY_REFUSAL:` message from a single `McpRefusal` constants class. No AOP or `spring-boot-starter-aop` may be used.

- REQ-04: A `McpToolContractSnapshotTest` must be written in Phase 0 against the current code, producing a golden JSON file of all 18 `ToolDefinition`s (name, description, inputSchema). Any byte-level diff to that file fails the build. This test must remain green through every subsequent phase.

- REQ-05: A `McpAccessMatrixTest` parameterized characterization test must be written in Phase 0 against the current code, covering the full allow/deny matrix from ADR-026 Table F1 for all 18 tools × {tokenless, DRIVER, CUSTOMER, STORE, ADMIN, OTP} × {own, other}. This test must remain green through every subsequent phase. No migration phase PR may tighten any cell of this matrix — matrix changes are separate, explicitly approved work.

- REQ-06: ArchUnit rules R1 through R6 (as specified in ADR-026 §D5) must be added in Phase 0 with a named `LEGACY_EXCLUSIONS` list containing the 6 current service classes. Each subsequent phase shrinks this list. Phase 6 deletes the list entirely and the rules hard-fail on any violation.

- REQ-07: `McpConfig` must keep exactly one `MethodToolCallbackProvider` bean. It builds this provider from controller beans only. Any tool accidentally annotated on both a service and a controller causes a startup-time `IllegalStateException` on duplicate tool name, failing fast.

- REQ-08: Each controller must delegate all data access to its corresponding service. Controllers must not depend on `*Repository` or `*Repo` types (ArchUnit rule R5). Bank-account masking and `markUp` decoration must move to the controller methods, applied to the returned objects before they are returned to the agent.

- REQ-09: Services must have all `isMcpRequest()` branches and all `CallerIdentityValidator` / `StoreScopeValidator` calls removed by Phase 6. After Phase 6 there must be zero uses of `CallerIdentityContext` getters, `isMcpRequest()`, `StoreScopeContext` getters, `CallerIdentityValidator.validate*`, `rejectTokenlessMcp`, or `StoreScopeValidator.*` outside the `mcp` and `security` packages (ArchUnit rule R4).

- REQ-10: Phase 5 (`get_payouts_for_user` → `PayoutMcpController`) must not start until `feature/payout-disbursement-fixes` merges to `develop`, because that branch rewrites `ReconServiceImpl` by +172/-45 lines. The Product Owner must gate Phase 5's branch opening on that merge.

- REQ-11: `@RequiresAudience` currently has zero usages and enforces nothing. It must be explicitly retired in Phase 6 (removed from the codebase) rather than left as silent documentation. The `McpConfig` Javadoc SA-021-18 comment must be updated in Phase 6 to point at controllers rather than at service-method annotations.

---

## Acceptance Criteria

- AC-01: Given the Phase 0 branch is complete, when `./mvnw test` runs (full unscoped, JDK 17), then `McpToolContractSnapshotTest` and `McpAccessMatrixTest` both pass, and ArchUnit rules R1–R6 pass with the 6-class `LEGACY_EXCLUSIONS` list.

- AC-02: Given any migration phase (1–5) PR is complete, when the full unscoped `./mvnw test` runs, then `McpToolContractSnapshotTest` produces a byte-identical snapshot (zero diff), `McpAccessMatrixTest` passes with no matrix cell changed, and ArchUnit R1–R6 pass with the reduced `LEGACY_EXCLUSIONS` list for that phase.

- AC-03: Given Phase 1 is complete, when the MCP server dispatches a tool call to `start_order` or any of the 4 document tools, then the `GuardedToolCallback` decorator fires before the controller method body, writing an audit record and enforcing the audience allowlist.

- AC-04: Given any phase is complete, when an internal Java caller (scheduler, event listener, REST controller) invokes a service method that was previously a `@Tool` method, then no `CallerIdentityViolationException` or `AudienceViolationException` is thrown — the service method executes as pure business logic with no MCP gating.

- AC-05: Given Phase 6 is complete, when `./mvnw test` runs, then `LEGACY_EXCLUSIONS` is empty, all R1–R6 rules hard-fail on any violation, and `grep -r "isMcpRequest\|StoreScopeContext\|CallerIdentityValidator\|StoreScopeValidator\|RequiresAudience" --include="*.java" --include="*.kt"` returns zero hits outside the `mcp` and `security` packages.

- AC-06: Given Phase 6 is complete, when the `McpConfig` Javadoc is reviewed, then the SA-021-18 comment refers to `@McpAudience` on controller methods, not to any annotation on service methods, and the SA-022-01 scope-JWT rules still document the filter-layer contract unchanged.

- AC-07: Given a tool call arrives with a null audience (tokenless request), when the `GuardedToolCallback` runs, then it throws a standard `IDENTITY_REFUSAL:` message (from `McpRefusal` constants class) and writes an audit record — regardless of which tool is called.

- AC-08: Given a tool call arrives with an audience value not present in that tool's `@McpAudience` list, when the `GuardedToolCallback` runs, then it throws a standard `IDENTITY_REFUSAL:` message and writes an audit record.

- AC-09: Given a tool accidentally annotated on both a service and a controller (regression check), when the application context starts, then it throws `IllegalStateException` on duplicate tool names from the single `MethodToolCallbackProvider` bean — not silently winning with one copy.

- AC-10: Given Phase 5 is ready to begin, when the Product Owner reviews the branch gate, then `feature/payout-disbursement-fixes` must already be merged to `develop` before the Phase 5 branch is opened.

- AC-11: (Conditional on OQ-4 answer: unify refusal shape.) Given `start_order` is called with an unauthorized audience or no token, when the `GuardedToolCallback` rejects the call, then the agent receives a thrown `IDENTITY_REFUSAL:` text error — not a success-shaped `{success:false, message}` payload.

- AC-12: (Conditional on OQ-6 answer: add filter-level ADMIN rule.) Given `StoreScopeValidationFilter` receives a request with `audience=ADMIN` and `isSignatureVerificationEnabled()` is false, when the filter runs, then it rejects the request before any tool method executes.

---

## Dependencies

- **`develop` at commit `d23a595`** (after PR #151 ADR-024, PR #152, PR #155, PR #156 ADR-027/AMD-01) — the confirmed baseline.
- **Lindani Masinga answers to OQ-1 through OQ-7** — this brief is blocked on all 7.
- **`feature/payout-disbursement-fixes`** must merge to `develop` before Phase 5 branch opens (REQ-10).
- **iZinga Security & Compliance confirmation** that the two F4 findings (Section "Out-of-Scope Security Findings") are tracked as their own GitHub issues with live verification, before Phase 6 ships to production. This brief does not gate on those being fixed — it only gates on them being tracked.
- **OQ-4 resolution requires Lindani to check production MongoDB:** whether any `AiAgentConfig` document's system prompt contains logic that parses `start_order`'s `success:false` response shape. This cannot be determined from code — it requires a MongoDB read by Lindani.
- **ADR-026 approval by Lindani Masinga** (currently Proposed — the brief needs ADR-026 to be formally Accepted before work begins).

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
| OQ-4 (refusal shape unification) causes a production agent prompt regression if any agent parses `start_order`'s `success:false` shape | Lindani must check production MongoDB `AiAgentConfig` documents before OQ-4 is answered |
| F4-2 (`ADMIN` self-assignable) is not fixed before `appSecret` is configured in production | Block `appSecret` configuration until iZinga Security & Compliance closes F4-2 (tracked as separate issue) |

**Flag to Solution Architect:** R4 (zero `CallerIdentityContext`/`StoreScopeContext` usage outside `mcp`/`security`) and R6 (only `StoreScopeValidationFilter` sets the sentinel) affect shared classes in `izinga-commons`. The ArchUnit rules enforce these contracts. The Solution Architect should review Phase 6's empty-exclusions lock-down to confirm no undiscovered internal caller is broken. The `StoreAdminStoreProfileEventHandler.handleNewStoreCreatedEvent` latent caller (which calls `findUserByPhone` via `userProfileService::findUserByPhone` on a synchronous event) must be rerouted to the ungated service method in Phase 2 before Phase 6 locks down.

---

## Stage 0 Gate Determination

This is a backend-only structural refactor with no new user-facing behaviour, no new data collection, and no new external interface.

- **Legal & Compliance required:** No. This change introduces no new data collection, no new consent flows, no new contracts, and no new regulatory exposure. The access matrix is frozen at today's behaviour. The two F4 security findings are routed to Security & Compliance as separate issues, not through Legal.
- **UI/UX Designer required:** No. No user-facing screens, no frontend changes, no API shape changes, no tool description changes.
- **Marketing required:** No. This is an internal backend refactor with no customer-visible or driver-visible change.

---

## Agent Task Breakdown

Tasks are ordered by phase dependency. Phases 2–4 touch disjoint service classes and may run in parallel after Phase 1, but sequential is recommended to keep reviews small. Phase 5 is sequenced after `feature/payout-disbursement-fixes` merges.

| Task | Agent | Depends on |
|---|---|---|
| **T-00: Close OQ-1 through OQ-7 with Lindani** | iZinga Product Owner (facilitates); Lindani Masinga answers | None — this is the prerequisite for all tasks below |
| **T-01 (Phase 0): Define `@McpToolController`, `@McpAudience`, `GuardedToolCallback`, `McpCaller`, `McpRefusal`** — no tools moved yet; `McpConfig` registers no controller tools yet; ArchUnit R1–R6 added with 6-class `LEGACY_EXCLUSIONS`; `McpToolContractSnapshotTest` and `McpAccessMatrixTest` written against current code; (if OQ-3: absorb `RESOLVER_NOT_FOUND`/`OWNERSHIP_MISMATCH` audit-enum additions from `stash@{5}`) | iZinga Backend Developer | T-00 |
| **T-01-QA (Phase 0): Run full unscoped `./mvnw test` (JDK 17), verify snapshot and matrix tests green, verify ArchUnit passes with exclusions** | iZinga QA & Test Automation | T-01 |
| **T-01-CR (Phase 0): Code review — verify ArchUnit rules, exclusion list, snapshot golden file, no production behaviour changed** | iZinga Code Reviewer | T-01-QA |
| **T-02 (Phase 1): Move `start_order` → `OrderCreationMcpController`; move 4 document tools (`DocumentUploadMcpService` Kotlin) → `DocumentUploadMcpController` (Java); move CUSTOMER ownership check out of `OrderServiceImpl.startOrder()`; shrink `LEGACY_EXCLUSIONS` by 2** | iZinga Backend Developer | T-01-CR |
| **T-02-QA (Phase 1): Full unscoped test; UAT live verification of matrix against real `/mcp` endpoint; verify snapshot byte-identical** | iZinga QA & Test Automation | T-02 |
| **T-02-CR (Phase 1): Code review — verify snapshot diff is empty, exclusion list shrank by 2, no matrix cell changed** | iZinga Code Reviewer | T-02-QA |
| **T-03 (Phase 2): Move `find_user_by_phone`, `find_users`, `create_user`, `get_missing_fields_by_phone` → `UserMcpController`; strip gates from `UserProfileService`; reroute `StoreAdminStoreProfileEventHandler.handleNewStoreCreatedEvent` to ungated `userProfileService` method; shrink exclusions by 1** | iZinga Backend Developer | T-02-CR |
| **T-03-QA (Phase 2): Full unscoped test; UAT live verification; snapshot byte-identical** | iZinga QA & Test Automation | T-03 |
| **T-03-CR (Phase 2): Code review — verify `FirebaseJwtAuthenticationConverter`, OTP paths, `StoreDriverInviteService` now call ungated service method; exclusion list shrank** | iZinga Code Reviewer | T-03-QA |
| **T-04 (Phase 3): Move 5 order-read tools → `OrderMcpController`; strip gates from `OrderServiceImpl`; shrink exclusions by 1** | iZinga Backend Developer | T-03-CR |
| **T-04-QA (Phase 3): Full unscoped test; UAT live verification; snapshot byte-identical** | iZinga QA & Test Automation | T-04 |
| **T-04-CR (Phase 3): Code review — verify `OrderController`, `YocoPaymentController`, `IzingaOrderManagerClient` paths now call ungated service** | iZinga Code Reviewer | T-04-QA |
| **T-05 (Phase 4): Move 3 store tools → `StoreMcpController`; masking moves to controller; `StoreScopeValidator.validate` leaves `StoreService.find`; shrink exclusions by 1** | iZinga Backend Developer | T-04-CR |
| **T-05-QA (Phase 4): Full unscoped test; UAT live verification; snapshot byte-identical; verify masked entity cannot be saved (no repository access from controller)** | iZinga QA & Test Automation | T-05 |
| **T-05-CR (Phase 4): Code review — verify masked-entity persistence hazard is gone; `EmailNotificationService.notifyOrder` scheduler path now calls ungated `StoreService.find`** | iZinga Code Reviewer | T-05-QA |
| **T-06-GATE: Confirm `feature/payout-disbursement-fixes` has merged to `develop` before Phase 5 branch opens** | iZinga Product Owner | T-05-CR and `feature/payout-disbursement-fixes` merge |
| **T-06 (Phase 5): Move `get_payouts_for_user` → `PayoutMcpController`; strip gate from `ReconServiceImpl`; shrink exclusions to 0** | iZinga Backend Developer | T-06-GATE |
| **T-06-QA (Phase 5): Full unscoped test; UAT live verification; snapshot byte-identical** | iZinga QA & Test Automation | T-06 |
| **T-06-CR (Phase 5): Code review — verify `ReconController` REST path calls ungated service; exclusions empty** | iZinga Code Reviewer | T-06-QA |
| **T-07 (Phase 6): Empty `LEGACY_EXCLUSIONS`, hard-fail R1–R6; remove all `isMcpRequest()` from services; retire `@RequiresAudience`; update `McpConfig` SA-021-18/SA-022-01 Javadoc to point at controllers; (if OQ-3 Yes) formally mark ADR-025 / MCP-SEC-02 retired** | iZinga Backend Developer | T-06-CR |
| **T-07-QA (Phase 6): Full unscoped test; `grep` scan confirms zero `isMcpRequest` / `CallerIdentityValidator` / `StoreScopeContext` outside `mcp`/`security` packages** | iZinga QA & Test Automation | T-07 |
| **T-07-CR (Phase 6): Final code review — verify ArchUnit hard-fails; exclusions empty; no residual dual-use service methods** | iZinga Code Reviewer | T-07-QA |
| **T-08: Notify Release Manager that all 6 phases are code-review-passed and QA-verified; Release Manager merges to `develop` and coordinates production deployment** | iZinga Release Manager | T-07-CR |

---

## Priority

**P2 — Medium.** The root cause (dual-use service methods) is structural, not broken in production today (the specific PR #155 fix closed the active bug). The refactor eliminates the entire class of risk. It becomes P1 if a new tool is added to a service method before this is complete, since that creates a new instance of the original hazard.

**Marketing trigger:** No — internal backend refactor.

**UI/UX Designer required:** No — no UI changes.

**Legal & Compliance required:** No — backend structural refactor with no data collection, consent, or contract change. The two F4 security findings are routed to iZinga Security & Compliance as separate issues, not to Legal.

---

## Definition of Done

The following state must exist before this feature is considered complete and in production:

1. All 6 migration phases (0–6) have been code-review-passed (iZinga Code Reviewer PASS verdict) and QA-signed-off (iZinga QA full unscoped run PASS) on their respective `feature/adr-026-phase-N-*` branches.
2. `McpToolContractSnapshotTest` has remained byte-identical through every phase merge (never modified the golden file, never skipped the test).
3. `McpAccessMatrixTest` has remained green through every phase merge with no matrix cell changed.
4. ArchUnit R1–R6 run with an empty `LEGACY_EXCLUSIONS` list and zero violations in production code.
5. `grep -r "isMcpRequest\|StoreScopeContext.get\|CallerIdentityValidator\|StoreScopeValidator\|RequiresAudience" --include="*.java" --include="*.kt" izinga-ordermanager/src/main izinga-usermanagement/src/main izinga-recon/src/main izinga-commons/src/main` returns zero hits outside the `mcp` and `security` packages.
6. The `McpConfig` Javadoc SA-021-18 and SA-022-01 comments describe the controller-layer boundary, not service-level annotations.
7. iZinga Security & Compliance has confirmed the two F4 security findings are tracked as named GitHub issues with live verification status (independent of their fix timeline).
8. Release Manager has merged the final phase to `develop` and deployed to production.

---

*Feature Brief prepared: 2026-11-04*
*Prepared by: iZinga Product Owner*
*Status: BLOCKED — awaiting Lindani Masinga answers to OQ-1 through OQ-7*
*ADR reference: ADR-026-mcp-tools-controller-layer-separation.md*
*Supersedes: MCP-SEC-02 / ADR-025 (once OQ-3 is answered and confirmed)*
