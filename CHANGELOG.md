# Changelog

## [1.17.0] — 2026-11-06

**Status:** Release branch `release/1.17.0` cut from `develop` @ `22a90484` on 2026-11-06. NOT yet merged to `master`. Finish is waiting on the open gate items listed under "Gate citations".

**Release type:** Feature + Security (P1/P2). Contains **breaking changes to the MCP tool surface only**. Lindani Masinga approved shipping them as 1.17.0, with no major-version bump, on 2026-11-06. The REST API contract is unchanged (see "Breaking changes").

**Summary:** Hardens the AI-agent MCP surface end to end. Every MCP tool is now scoped to the WhatsApp caller's identity (ADR-024). Tokenless MCP calls are rejected (SEC-MCP-01/02, ADR-027). All 17 tools sit behind a dedicated controller layer, and `create_user` is removed from MCP (ADR-026). The store-scope JWT now signs with the real Secrets Manager secret instead of a public fallback (SEC-WA02-01-B, PR #162). About 300 JUnit 4 tests that had been silently skipped are running again (868 → 1,167 at PR #155 merge), and the real production bugs they exposed are fixed. Payout disbursement is fixed: no more duplicate messenger payouts, no more silently abandoned payouts, one notification per 24 h at most, and payouts no longer fail when a driver has no email address (ADR-028).

### Changes

**AI-agent identity scoping and MCP authentication**

- [NEW] `feat(ADR-024)`: Caller-identity scoping on all MCP tools. The scope JWT now carries a `callerPhone` claim. `CallerIdentityValidator` (izinga-commons) makes CUSTOMER and DRIVER callers reach only their own profile, orders, payouts and documents, or orders assigned to them as a driver. Any attempt to reach a third party throws `IDENTITY_REFUSAL:` with product-reviewed wording. Each violation writes an async record to the new `agent_identity_audit_log` collection. (PR #151, commits `0263571d`, `de49462e`)
- [FIX] `fix(security)` SEC-MCP-01 (#153): Closes the tokenless MCP auth bypass. Before this fix, a `/mcp` request with no scope token, or with an invalid or expired one, skipped every identity check. `StoreScopeValidationFilter` now flags every `/mcp/**` request as an MCP request before parsing the token, and all 17 tools hard-reject a null audience. (PR #156, `ddbc9f71`)
- [FIX] `fix(security)` SEC-MCP-02 (#154): Adds per-tool audience gates to the four tools that had none: `find_orders_by_messenger_id`, `find_stores_by_owner`, `find_users`, `start_order`. (PR #156, `ddbc9f71`)
- [NEW] SEC-MCP-07: Bank `accountId` is masked in every store payload returned to an MCP caller (`find_store_or_shops_by_id`, `find_stores_by_owner`, `find_stores`). The masked value is never written to MongoDB. (PR #156)
- [NEW] `feat(security)` ADR-027 AMD-01: New `ADMIN` MCP audience (`Audience.ADMIN`). It is minted at runtime only when the WhatsApp caller resolves to a `ProfileRoles.ADMIN` profile **and** webhook HMAC signature verification is active (`whatsapp.cloud.appSecret` configured). It fails closed: while `appSecret` is unset, as it is in production today, ADMIN is never minted and no extra DB read happens. ADMIN can never come from `AiAgentConfig`; a config with audience ADMIN is demoted to CUSTOMER with a WARN. Gate changes: `find_users` → ADMIN only; `find_orders_by_messenger_id` → DRIVER (own) + ADMIN; `find_stores_by_owner` → STORE (owner match) + ADMIN; `start_order` → CUSTOMER, STORE, ADMIN. (PR #156, `9a5f1eb4`)
- [IMPROVED] `refactor` ADR-026 / MCP-SEC-03: All MCP `@Tool` methods moved out of domain services into dedicated `@McpToolController` beans: `OrderCreationMcpController`, `DocumentUploadMcpController`, `UserMcpController`, `OrderMcpController`, `StoreMcpController`, `PayoutMcpController`. Domain services no longer read caller-identity ThreadLocals. `GuardedToolCallback` fails closed if a tool is ever invoked outside the MCP request path. Each tool declares its allowed audiences with `@McpAudience`. ArchUnit rules R1–R6, a tool-contract golden-file snapshot and an access-matrix test now break the build on any regression. `@RequiresAudience` is retired. (PRs #157, #158, #159, #160; `c71d0290` … `ff1d9fe9`)
- [REMOVED] **BREAKING (MCP surface):** `create_user` removed from the MCP tool surface (ADR-026 Phase 2b / OQ-7, Lindani's decision 2026-11-05). MCP now exposes 17 tools instead of 18. User creation stays on REST (`UserController`) and internal callers. AI agents can no longer register users over WhatsApp; drivers and stores register through driver.izinga.co.za, shop.izinga.co.za or izinga-onboarding. (PR #160, `4e388c0f`)

**Secrets / startup**

- [FIX] `fix(security)` SEC-WA02-01-B: Removed the silent `:change-this-secret-to-minimum-32-chars!` fallback for the store-scope JWT secret. The fallback is publicly visible in git, and production has been signing and verifying scope JWTs with it, so anyone with repo access could forge a scope token. A missing secret now fails startup loudly. (PR #152, `9777c3c2`)
- [FIX] `fix` (release-blocking): The store-scope JWT placeholder is now `${store.scope.jwt.secret}`, matching the key name actually stored in AWS Secrets Manager (`izinga-uat` and `izinga-prod`). Without this fix, PR #152 would have crashed `izinga-ordermanager` on startup in UAT and production (`Could not resolve placeholder 'STORE_SCOPE_JWT_SECRET'`). Live boot against the real `izinga-uat` secret was verified before and after the fix. The key's presence in `izinga-prod` (64 chars) was confirmed by the Release Manager on 2026-11-06. The local-dev env var `STORE_SCOPE_JWT_SECRET` still works through relaxed binding. (PR #162, `b612cf6a`)

**Test engine and the production bugs it exposed (PR #155, `d4a43158`)**

- [FIX] `test`: Added `junit-vintage-engine`. Since the Spring Boot 3.3.6 / Surefire 3.2.5 upgrade, every JUnit 4 test class had been silently reporting 0 tests. The full reactor now runs **1,632 tests** (0 failures, 0 errors, 19 skipped).
- [FIX] Payout scheduler duplicate messenger payouts: `updateMissingPayouts` compared messenger payouts against `order.getShopId()`, so the "payout already exists" check never matched and a second messenger payout was generated. The same path also called a `@Tool` method from the scheduler thread, which crashed under caller-identity enforcement. It now calls the internal generate methods.
- [FIX] Google Maps outage / unroutable address: `IjudiUtils.calculateDrivingDirectionKM` no longer crashes on an empty geocode response and no longer prices a no-route trip as 0 km. Delivery-distance logic is extracted into `DeliveryCalculationService`. `POST /order` and the delivery-price estimate now return **503** (`DeliveryDistanceUnavailableException`) or **400** with `{"error": "..."}`, and no order is persisted, instead of a 500.
- [FIX] Service fee: no longer NPEs on INSTORE orders without `shippingData`. The delivery-fee part of the service fee is 0 when the order has free delivery or no shipping data.
- [FIX] `PATCH /store/{id}` data loss: a partial payload no longer wipes `name`, `address`, `latitude`/`longitude` or `categories`. The admin-set flags `profileApproved`, `hasPaymentAgreement`, `deliversFromMultipleAddresses`, `generateMissingImages` and `quoteRequired` are OR-merged and are no longer reset to `false` by an omitted field. **Side effect:** these five flags can no longer be switched from `true` to `false` through this endpoint. See "Known behaviour changes".
- [FIX] `OrderServiceImpl`: null `orderType` guard; terminal-stage progress guard (400 instead of an index error); vehicle-type normalisation for delivery fee lookup.
- [FIX] `Device.token` `@NotBlank` now actually validates (`@field:` target). A blank device token is rejected with 400 at the model.
- [FIX] FCM: `FCMMessage`/`FCMNotification` equality; WebPush block sent only when a link is present.
- [FIX] `GooglePassGenerator`: credentials refresh lazily instead of at construction.

**Payout disbursement (PR #161, `2bca9bb4`, `375f8929`, ADR-028)**

- [NEW] ADR-028: Payout generation returns a sealed `PayoutGenerationOutcome`: `Created`, `NotApplicable` or `Deferred`. When a store or messenger with a payment agreement has no bank details, the outcome is `Deferred`. The order keeps `payoutCreated=false` and is retried on the next hourly scheduler run. Before this change the payout was silently dropped and the order marked done.
- [NEW] ADR-028: A cross-stage double-pay guard checks `existsByToIdAndOrderId` (on `orders._id`) before generating any payout, covering PENDING, PROCESSING, COMPLETED and VOIDED. New compound indexes `messenger_payout_cross_stage_idx` and `shop_payout_cross_stage_idx` are auto-created on startup.
- [NEW] BUG-3 / GAP-7: WhatsApp notification to drivers (`driver_missing_bank_details`) and store owners (`store_missing_bank_details`) when a payout is deferred for missing bank details. It is throttled to one per recipient per type per 24 h through the new `payout_notification_cooldowns` collection (TTL 86,400 s). The store template is still pending Meta approval, so sends fail with HTTP 400, which is logged and swallowed and never blocks payout generation.
- [FIX] DEF-1: A messenger with no email address no longer NPEs during payout generation (`emailAddress` is no longer forced with `!!`).
- [FIX] GAP-3: The bank reconciliation result now persists `failureReason` and increments `attemptCount` on each attempt. On `paid=false` the payout goes back to PENDING for retry. On `paid=true` it advances to COMPLETED and the reason is cleared.
- [FIX] BUG-7: The payout bundle CSV (FNB BInSol) now quotes every field per RFC 4180, with internal quotes doubled, and the trailing `", "` separator becomes `","`. A comma in a store name or reference no longer shifts the columns. **This changes the bank upload file format.** See "Pre-deploy checks".

**Docs / tests**

- ADR-026, ADR-027, ADR-028, the MCP-SEC-03 feature brief, the start-order prompt-injection security review, and test plans: `admin/mcp-controller-layer-separation.md`, `admin/mcp-tools.md`, `driver/payout-disbursement.md`, `driver/ai-agent-boundary-discipline.md`, `driver/ai-agent-whatsapp-live-trace.md`, `admin/junit4-engine-and-production-null-guards.md`.
- Added `.env.example` for local dev and put `.env` in `.gitignore`.
- `MCP_VSCODE_GUIDE.md` updated for the `create_user` removal.

### Breaking changes

**REST API:** none. No REST endpoint path, method or success-response shape was removed or renamed. Behaviour changes on REST are limited to error paths: 400/503 with an `{"error"}` body where there used to be a 500, plus the PATCH flag semantics described under "Known behaviour changes". JWT/Firebase auth structure is unchanged. `Audience` (internal MCP/WhatsApp enum, not exposed on REST) gains the `ADMIN` value. `Payout` gains `failureReason` and `attemptCount`, which are additive.

**MCP surface (breaking for MCP clients):**
1. `create_user` is removed (18 → 17 tools).
2. Every MCP tool call now needs a valid, unexpired store-scope JWT in the `?scope=` query parameter. Tokens have a 5-minute TTL and are signed with the real `store.scope.jwt.secret`. External MCP clients without a scope token get `IDENTITY_REFUSAL` on every tool. That covers the Claude connector used by the "iZinga Driver Customer Service" agent, VS Code / Copilot set up from `MCP_VSCODE_GUIDE.md`, and any token minted under the old public fallback secret. In-process WhatsApp agents mint their own tokens and are unaffected.
3. Audience narrowing: CUSTOMER/DRIVER can no longer call `find_stores_by_owner`; STORE can no longer call `find_users`. Until `whatsapp.cloud.appSecret` is configured in production, `find_users` cannot be reached at all, and `find_orders_by_messenger_id` is DRIVER-own only.

### Known behaviour changes (accepted or flagged, not regressions in the success path)

- `PATCH /store/{id}` can no longer turn `profileApproved`, `hasPaymentAgreement`, `deliversFromMultipleAddresses`, `generateMissingImages` or `quoteRequired` from `true` to `false`. Visible impact: in izinga-onboarding, choosing "Generate missing images → NO" on business-update is silently ignored for a store where it is currently YES. Any store un-approval or suspension must go through a direct DB update until a targeted admin endpoint exists.
- Orders whose payouts were silently abandoned under 1.16.0 (already `payoutCreated=true` with no payout) are **not** retried. The deferred retry applies only to orders processed from now on.
- Existing duplicate PENDING messenger payouts created by the pre-fix scheduler bug are **not** cleaned up by this release. **Known risk, accepted by Lindani Masinga on 2026-11-06.** The cleanup is deferred and will be handled separately, outside this release's scope. Until then, check payout bundles for repeated recipient/order pairs before upload.
- `agent_identity_audit_log` stores `callerPhone`, but the code creates only a plain (non-TTL) index on `timestamp`. The 90-day retention in the KDoc is not enforced until a TTL index is created in MongoDB (POPIA retention follow-up).

### Known open items (NOT in this release, not blocking)

- WhatsApp webhook signature verification (`whatsapp.cloud.appSecret`) is still not configured in production. This is a known risk Lindani has accepted. While it stays unset, ADMIN MCP minting stays dormant (fail-closed). Configuring it later turns on HMAC verification and ADMIN minting together and needs its own rollout plan.
- ADR-028 PQ-1 (missing-profile retry semantics) and PQ-2 (720 h window escalation): open product questions.
- F4-1 (store-line agent tool allowlist not enforced), REST role self-assignment (#138), formal ADR-025 retirement: tracked separately.
- Two Claude agent `.md` files still reference the removed `create_user` MCP tool. The Agent Engineering Manager owns that fix; it is not part of this code release.
- Build instability (task_723cde3e): `OrderControllerTest` / `OrderServiceTest` flake non-deterministically on CI. The master deploy build runs `mvn clean package` with tests, so a flake can fail the deploy job, which then needs a re-run.

### Deployment sequence

1. **ijudi-api only.** No frontend deploy is required. Every REST change is additive or error-path only. izinga-onboarding, cs-lifestyle, furniture-delivery-app and ijudi (Flutter) keep working unchanged.
2. Deploy = `git flow release finish` push to `master`, which triggers `.github/workflows/aws-ecr-push.yml`: build with tests, ECR push, then an ECS rolling deploy (`ijudi-api` cluster, `ijudi-api-service`, Fargate, min 100 % / max 200 %, deployment circuit breaker **disabled**).
3. **Do NOT open a pull request from `release/1.17.0` to `master`.** `aws-ecr-push.yml` also triggers on `pull_request` to `master`, its deploy job has no event guard, and the `production` environment has no protection rules. Opening the PR would deploy the PR head to production immediately.
4. Preferred window: 05:00–07:00 SAST. During the rolling overlap, the old task (fallback secret) and the new task (real secret) both sit behind the load balancer. A scope JWT minted on one task can fail validation on the other, so a few WhatsApp agent replies may get `IDENTITY_REFUSAL` for up to ~5–10 minutes.

### Pre-deploy checks

- [x] `store.scope.jwt.secret` present in `izinga-prod` (64 chars) and `izinga-uat` (64 chars). Checked on key names and length only; the value was never read out.
- [x] Every other property newly injected in this release (`google.maps.api.key`, `ukheshe.main.account`, `service.markup.perc`) resolves in prod. No new ECS env vars, no IAM change, no new secrets.
- [ ] FNB BInSol dry-run: running in parallel, not a hard blocker on release prep (Lindani, 2026-11-06). Shop and messenger dry-run bundles were generated with the 1.17.0 `CsvWriter.kt` code path using synthetic QA data: a name containing a comma, a name containing double quotes, and plain names, with R1–R3 amounts and fake accounts. Lindani is to import them into FNB Online Banking himself, validate only and do NOT authorise, and confirm the quoted format is accepted before the first post-release payout bundle is uploaded.
- [x] ~~Prod Mongo duplicate-payout count~~: deferred. Known risk accepted by Lindani on 2026-11-06 and out of scope for this release.
- [ ] Confirm whether any external MCP client (the Claude connector behind "iZinga Driver Customer Service") is still needed against prod. It will lose all tools at cutover.

### Rollback steps

**Full rollback (preferred for any startup failure, auth regression or payout anomaly). Takes about 3–5 minutes and needs no CI:**
1. `aws ecs update-service --cluster ijudi-api --service ijudi-api-service --task-definition first-run-task-definition:299 --force-new-deployment --region af-south-1`. Revision 299 is the live 1.16.0 task (image `ijudi-api:ae3df6a3…`, tag `1.16.0`).
2. Do **not** revert on `master` while incident-handling: every push to `master` redeploys. Forward-fix through `hotfix/1.17.1` cut from `master`, which then back-merges to `develop`.
3. Notify Lindani that rollback was triggered, with the reason.

**Data and behaviour consequences of rolling back to 1.16.0:**
- No schema migration needs reverting. `agent_identity_audit_log`, `payout_notification_cooldowns`, the two payout compound indexes and the new `failureReason`/`attemptCount` fields are additive and ignored by 1.16.0.
- Rolling back **re-opens**: the tokenless MCP bypass, the public fallback JWT secret, `create_user` on MCP, duplicate messenger payouts, silent payout abandonment, the Maps-outage 500s and PATCH data loss. Orders left `payoutCreated=false` by a `Deferred` outcome will be marked done by 1.16.0's scheduler without a payout. Keep any rollback short and do not export a payout bundle while on 1.16.0.

**Per-piece rollback (forward-fix in `hotfix/1.17.1`, used when only one area misbehaves):**
- **App fails to start (secret/placeholder):** with the circuit breaker off, ECS keeps the 1.16.0 task serving and retries the new task, and the GitHub deploy job times out on service stability. Run step 1 above to stop the retries, then fix the key in Secrets Manager or the placeholder (`b612cf6a`).
- **ADMIN-over-MCP (PR #156 AMD-01):** dormant while `appSecret` is unset, so nothing to roll back at deploy. If ADMIN misbehaves after `appSecret` is configured later, removing `appSecret` and restarting disables ADMIN minting with no code change (this also disables HMAC verification).
- **SEC-MCP-01/02 or ADR-024 rejecting a legitimate agent:** first check the agent's `AiAgentConfig.audience` and its `?scope=` minting. The code is layered (ADR-024 → #156 → ADR-026), so there is no isolated revert. Use the full rollback if WhatsApp agents broadly fail.
- **`create_user` removal:** revert `4e388c0f` in a hotfix to restore the tool (golden file and ArchUnit tests update with it).
- **Payout CSV rejected by FNB:** revert the `csvAppend` hunk of `2bca9bb4` (`CsvWriter.kt`) in a hotfix. Until then, re-save the CSV without quotes manually for the upload.
- **Deferred retry / notification throttle misbehaving (e.g. a notification storm or a scheduler loop):** hotfix-revert the ADR-028 scheduler hunk. The 24 h cooldown caps notifications even while a fix is pending.
- **PATCH flag OR-merge blocking a required un-set:** set the flag directly in Mongo as a stop-gap; the targeted admin endpoint is a follow-up.

### Smoke test plan (run immediately after the ECS deployment reaches steady state)

1. **Startup**: CloudWatch `/ecs/first-run-task-definition` shows `Started IjudiApplication`, MCP `Registered tools: 17`, and no `Could not resolve placeholder` / `IllegalStateException`. Expected: one healthy task on the new image, old task drained.
2. **Tokenless MCP rejected**: `POST https://api.izinga.co.za/mcp` `tools/call find_order_by_id` without `?scope=`. Expected: `IDENTITY_REFUSAL` or audience-violation refusal, no order data.
3. **Customer WhatsApp line**: from a test number with a known order, ask "where is my order". Expected: the agent looks the order up and replies with the real status. This proves scope JWT mint and validate both work with the real secret. Asking about another number's order should return the refusal wording.
4. **Store WhatsApp line**: message a store line. Expected: the store agent answers using `find_store_or_shops_by_id`, and no bank account number appears.
5. **Order placement (REST)**: place a normal order through cs-lifestyle or furniture-delivery-app. Expected: 200 and the order is created with a correct delivery fee and service fee. A delivery-price estimate for an unroutable address should return 400 with an `error` message, not 500.
6. **Store PATCH**: in izinga-onboarding business-update, save a store without touching categories. Expected: name, address, coordinates and categories are preserved.
7. **First hourly payout scheduler run**: logs show created/deferred counters with no NPE, and no second messenger payout for an order that already has one.

### Post-deployment monitoring

- **15 min**: ECS service has 1 running task on the new image; no 5xx spike on the ALB target group; no `IDENTITY_REFUSAL` / `AUDIENCE_VIOLATION` burst beyond the rolling-overlap window; WhatsApp agents replying.
- **1 h**: first payout scheduler run is clean (counts of created / deferred / errors); `payout_notification_cooldowns` entries appear only for recipients actually missing bank details; `store_missing_bank_details` HTTP 400s are expected (template pending Meta) and must not block payouts; `agent_identity_audit_log` growth rate is sane; no 503 spike from `DeliveryDistanceUnavailableException` (that would point to a Maps key or quota problem, see the Oct 2026 key-rotation gap).
- **24 h**: order volume within normal range (Growth & Analytics); no duplicate payouts in the next payout bundle; deferred-payout list reviewed (drivers and stores needing bank details); no support complaints about the WhatsApp agents refusing legitimate own-account lookups; no store-owner reports of settings that "won't save".

### Gate citations

- Code Review PASS (Gate 1): PR #151 (2026-11-01), #152 (2026-11-01), #155 (on merged result), #156 (after one FAIL cycle), #160 (ADR-026 phases), #161. **PR #162: no Code Review PASS or QA sign-off recorded on the PR** (and its PR CI run failed on the known `OrderControllerTest` flake). Gate 1 evidence gap, flagged to the Product Owner and Lindani. Mitigated: a one-line config change, independently verified by the Release Manager (prod key present, full suite green).
- Feature Brief: MCP-SEC-03 "Approved for Implementation" (PR #158/#159) for ADR-026; ADR-024, ADR-027 AMD-01 and ADR-028 approved by Lindani Masinga.
- QA Gate 1: per-feature PASS with live UAT verification for ADR-026 (17 tools, `create_user` absent, 2026-11-05) and ADR-028 (4 live scheduler runs, 2026-11-06).
- Release Manager full unscoped regression on `develop` tip `22a90484`: `./mvnw -B clean test`, Corretto 17.0.15, **1,632 tests, 0 failures, 0 errors, 19 skipped, BUILD SUCCESS** (2026-11-06). GitHub Actions `Java CI with Maven` on the same SHA (run 42231404309): 1,632 / 0 / 0 / 19, success.
- QA Gate 2 (full regression on `release/1.17.0` by iZinga QA & Test Automation): **pending**.
- Product Owner sign-off: **pending**.
- MCP breaking change: approved by Lindani Masinga as part of 1.17.0 (no major bump), 2026-11-06. No Hloniphani notification required (Lindani's decision).
- Deployment authorisation: **pending.** Requires Lindani Masinga's explicit, direct go-ahead before `git flow release finish`.

---

## [1.16.0] — 2026-10-20

**Release type:** Feature (P2 — new functionality)

**Summary:** Adds WhatsApp inbound location-message handling (AI agents can now receive and act on a customer's live location pin) and two new MCP tools — `find_stores` and `start_order` — enabling AI agent-driven store discovery and order placement, plus a critical idempotency fix that prevented duplicate orders on repeated `start_order` calls.

### Changes

- [NEW] `feat(izinga-messaging)`: WhatsApp inbound location-message handling — `WhatsappInboundEventHandler` now parses `type: location` payloads and routes the customer's latitude, longitude, and optional label to `StoreAiAgent` and `AiCustomerServiceAgent` for location-aware responses. (merge commit 9b68f73, PR #149)
- [NEW] `feat(ADR-020)`: `find_stores` MCP tool — Spring AI `@Tool` method on `StoreService` that returns nearby open stores given a latitude, longitude, and radius. Strips PII (owner details) and applies markup before returning results to the AI agent. (commit f37e04a, PR #150)
- [NEW] `feat(ADR-020)`: `start_order` MCP tool — Spring AI `@Tool` method on `McpOrderService` that validates customer identity, confirms customer intent flag, performs idempotency check, and places an order on behalf of an AI agent session. Guards per SA-022-01: `customerConfirmed` must be `true` before the order is created. (commit f37e04a, PR #150)
- [FIX] `fix`: Correct BSON field name in idempotency `@Query` (`OrderRepository`) — `BaseModel.createdDate` is stored in MongoDB as `createdDate` (not `date`, which is the Jackson `@JsonProperty` alias only). The wrong field name caused the `$gte` condition to evaluate false on a non-existent field, silently returning `null` for every idempotency lookup and allowing duplicate orders on repeated `start_order` calls. Corrected to `createdDate`. (commit e5508de)

### Breaking changes

None. All changes are additive. No REST endpoints added, removed, or modified. No MCP tool names changed or removed. No JWT/auth token structure changed. No enum values changed.

### Legal & Compliance note

This release was reviewed under addendum L-REVIEW-001-A2 (Legal & Compliance review, 2026-10-20) with verdict "AGREE-WITH-RESERVATIONS". The customer-identity binding code fix identified in L-REVIEW-001-A2 was deliberately excluded from this release at Lindani Masinga's direction; the AI agent's system prompt provides a compensating prompt-level instruction. A follow-up Security & Compliance review covering the identity-binding gap has been scoped as a separate backlog item and does not block this release.

### Deployment sequence

1. ijudi-api only — no frontend changes required. All new functionality is backend-only (WhatsApp webhook handling, MCP server tools).

### Rollback steps

1. Redeploy the v1.15.0 artifact (tip of `master` before this release, commit `1116430`) via ECS. The `find_stores` and `start_order` MCP tools will be unavailable and WhatsApp location messages will be unhandled (silently ignored at the webhook level). No data loss — no schema changes.
2. Notify Lindani immediately if rollback is triggered.
3. BSON field name fix note: rolling back removes the idempotency correction. Duplicate order risk on repeated `start_order` calls re-activates. Monitor order duplication metrics if rollback is held for more than 1 hour.

### Smoke test plan

1. **WhatsApp location message** — Send a WhatsApp message with a live location pin to the iZinga AI assistant number. Verify the AI agent acknowledges the location (responds with a location-aware reply, not a null or error response).
2. **find_stores MCP tool** — Invoke `find_stores` via the MCP client with a valid lat/long pair inside a known store's delivery radius. Verify a list of open stores is returned with no raw owner PII fields visible.
3. **start_order MCP tool — happy path** — Invoke `start_order` with `customerConfirmed: true`, valid customer ID, valid store ID, and a well-formed basket. Verify an order is created and the order ID is returned.
4. **start_order idempotency** — Submit the same `start_order` call twice with the same idempotency key within the time window. Verify only one order is created in MongoDB (`createdDate` field must exist in the order document — verify via `db.orders.findOne()` that the field is present and typed as a date, not missing).
5. **start_order guard — customerConfirmed: false** — Invoke `start_order` with `customerConfirmed: false`. Verify the call is rejected (400 or equivalent) and no order document is created.

### Post-deployment monitoring

- **15 min**: Check application logs for MCP server registration of both `find_stores` and `start_order` tools in the Spring context startup output. Verify no NullPointerException events from `WhatsappInboundEventHandler` on any inbound message type.
- **1 hour**: Confirm no duplicate order complaints from customers; check order creation rates are within normal bounds (idempotency fix should reduce any previously-silent duplicates). Growth & Analytics to watch for anomalies in order volume.
- **24 hours**: Verify MCP tool invocation logs if AI agent traffic is active; confirm `start_order` idempotency key lookup is hitting the `createdDate` field correctly in slow-query logs.

### Gate citations

- Code Review: PASS — iZinga Code Reviewer (both feature branches, Gate 1)
- QA Gate 1: PASS — iZinga QA & Test Automation (per-feature, both branches)
- Legal & Compliance: AGREE-WITH-RESERVATIONS — addendum L-REVIEW-001-A2, 2026-10-20
- QA Gate 2 (full regression): PASS — iZinga QA & Test Automation, 0 failures / 0 errors across all 10 modules (full unscoped reactor, release/1.16.0, 2026-10-20, Corretto 17)
- Deployment authorised by: Lindani Masinga — direct instruction 2026-10-20

---

## [1.15.0] — 2026-10-12

**Release type:** Feature (P2 — new functionality)

**Summary:** ADR-018 — a store (or messenger-admin) can invite a driver to join by mobile number via WhatsApp, with 5-day expiry and public deep-link accept/reject.

### Changes

- [NEW] Store-driver invitation (ADR-018): `POST /store/{storeId}/invite` and `POST /messenger-admin/{adminId}/invite` create an invite for a driver by mobile number, mapped to 5 outcomes (CREATED, ALREADY_LINKED, CONFLICT 409, INVITED new, INVITED idempotent). `GET /invite/{code}` is a public, unauthenticated deep-link lookup for the WhatsApp message's URL button. Accept/reject via `POST .../invite/{code}/accept` and `.../reject` enforce caller-phone match against the invitation, 5-day expiry (410 GONE past expiry), and atomic re-check on accept to guard the race between two near-simultaneous accepts. New entity `StoreDriverInvitation`, repo `StoreDriverInvitationRepo`, service `StoreDriverInviteService`, and 3 controllers in `StoreDriverInviteController.kt`. Notification sent via new `driver_store_invite` WhatsApp template (UTILITY, Meta-approved 2026-10-12) through `DriverInviteNotificationPort`. (branch `feature/ADR-018-store-driver-invitation`)
- [NEW] `InviteCodeResponse` includes `contextType` (STORE | MESSENGER_ADMIN) so the frontend can route accept/reject to the correct endpoint family.

### Breaking changes

None. All new endpoints are additive; no existing endpoint path, method, response field, or enum value was modified. `SecurityConfig` changes are additive rule insertions only.

### Deployment sequence

1. ijudi-api first (this release) — the frontend release depends on these endpoints already being live.
2. izinga-onboarding (INVITE-DRV-01) follows immediately after.

### Rollback steps

1. Redeploy the v1.14.1 artifact (tag `1.14.1` on master) via ECS. No data migration rollback required — `StoreDriverInvitation` documents are additive and not read by any other service; a rollback simply stops new invites from being created or accepted.
2. Notify Lindani that rollback was triggered.

### Smoke test plan

1. As a STORE_ADMIN, POST `/store/{storeId}/invite` with a new mobile number — verify 201 CREATED and a `driver_welcome_2` WhatsApp message is sent.
2. Invite an existing-profile mobile number — verify 201/200 INVITED and a `driver_store_invite` WhatsApp message is sent with a working deep link.
3. Open `GET /invite/{code}` unauthenticated — verify store name, masked phone, status, and expiry are returned with no auth required.
4. Accept the invite as the matching driver — verify 200 and the invitation status moves to ACCEPTED.
5. Attempt to accept as a different authenticated user — verify 403.
6. Attempt to accept an expired invite — verify 410 GONE.

### Post-deployment monitoring

- 15 min: Confirm `POST /store/*/invite` and `GET /invite/*` appear in access logs with 2xx/404 responses only (no 5xx).
- 1 hour: Confirm `driver_store_invite` WhatsApp sends return `message_status: accepted` in logs, not template-not-found errors.
- 24 hours: Confirm no anomalous spike in 403 (phone-mismatch) or 410 (expired) responses that would indicate a client-side bug in the frontend's invite flow.

### Gate citations

- Feature Brief: INVITE-DRV-01 (store driver invitation UI) — approved by Lindani Masinga
- Code Review: PASS, approved for deploy — iZinga Code Reviewer, 2026-10-12
- QA Gate: SIGN-OFF — 258 tests, 0 failures, 0 errors, Skipped: 3 (pre-existing), BUILD SUCCESS, Corretto 17, 2026-10-12
- Manual live verification: full end-to-end flow confirmed working against the real WhatsApp Graph API, including the live `driver_store_invite` template — Lindani Masinga + Claude, 2026-10-12

---

## [1.14.1] — 2026-10-11

**Release type:** Hotfix (P0 — production broken)

**Summary:** Emergency fix for a YAML misconfiguration that silently disabled the WhatsApp AI assistant for all users across releases 1.12.0, 1.13.0, and 1.14.0 (approximately 3 days of outage, 2026-10-08 to 2026-10-11).

### Incident summary

Commit `2740db1` (2026-10-08) accidentally nested the `spring.ai.mcp.server` configuration block under `feign:` instead of `spring:`. Spring Boot therefore never loaded the MCP server config, leaving `StoreAiAgent` unable to resolve the MCP server and returning `null` on every WhatsApp AI request. Because `StoreAiAgent` catches the failure silently, affected users received no response — not even an error message. All customer, driver, and PRO store WhatsApp AI interactions were silently dropped for the full outage window.

Root cause independently verified against the live file; fix is a pure YAML indentation move (8 lines, 1 file). This is the second key corrected by this fix — `spring.ai.mcp.server` mirrors an identical block (`spring.ai.mcp.client`) fixed in the same commit via `a4e13d6`.

**Outage window:** ~2026-10-08 to 2026-10-11 (3 days). Post-mortem required within 24 hours.

### Changes

- [FIX] Move `spring.ai.mcp.server` configuration block from under `feign:` to under `spring:` where Spring Boot can load it. Affected file: `izinga-ordermanager/src/main/resources/application.yml`. (commit `1054f08`)

### Breaking changes

None. Pure YAML indentation correction — zero application code changes, no API shape, enum, or JWT structure altered.

### Deployment sequence

1. ijudi-api only — no frontend changes required.

### Rollback steps

1. Redeploy the v1.14.0 artifact (tag `1.14.0` on master) via ECS. The WhatsApp AI assistant will revert to the broken state, but no data is lost — this fix is configuration-only.
2. Notify Lindani immediately if rollback is triggered.
3. Note: rolling back to any version before this fix reactivates the silent failure; do not remain on a rolled-back version without a forward plan.

### Smoke test plan

1. Send a WhatsApp message to the iZinga AI assistant as a customer — verify a non-null response is returned (any valid AI response confirms the MCP server config loaded).
2. Send a WhatsApp message as a driver — verify a non-null AI response.
3. Send a WhatsApp message via a PRO store channel — verify a non-null AI response.
4. Check application logs at startup for `izinga-mcp-server` registration — verify the MCP server appears in the Spring context startup output (previously absent).
5. Confirm `StoreAiAgent` no longer logs null-return events for inbound WhatsApp messages in the 15 minutes post-deploy.

### Post-deployment monitoring

- 15 min: Check logs for MCP server startup registration and absence of null-return events in `StoreAiAgent`.
- 1 hour: Confirm WhatsApp AI response volume has recovered to pre-incident levels; Growth & Analytics to watch for message delivery anomalies.
- 24 hours: Full post-mortem completed; verify no secondary symptoms (missed orders or escalations) attributable to the 3-day outage window.

### Gate citations

- Root cause: independently verified by Lindani Masinga against live file (2026-10-11)
- Code Review: PASS — iZinga Code Reviewer (commit `1054f08`); independently verified by Lindani Masinga
- QA Gate 2 (full regression): PASS — 258 tests, 0 failures, 0 errors, BUILD SUCCESS, Corretto 17 (run on release/1.14.1, 2026-10-11)
- P0 co-founder approval: Lindani Masinga — direct dispatch 2026-10-11

---

## [1.14.0] — 2026-10-10

**Release type:** Feature (P2 — new functionality)

**Summary:** Introduces ambassador tiered commission payouts by vehicle type (ADR-019), broadens the missing-document scheduler notification to all non-CUSTOMER drivers, and removes a hardcoded debug phone filter that was silently suppressing onboarding notifications in production.

### Changes

- [NEW] Ambassador tiered commission by vehicle type (ADR-019): payout rates are now BIKE R50 / CAR R60 / BAKKIE_AND_TRUCK R70 per approved driver. `AmbassadorReconService` reads `ProfileVehicle` from the approved driver profile to select the commission tier. Introduced `VehicleTierCommission` entity, `VehicleTierCommissionRepository`, and `AmbassadorTieredCommissionService`. (commits `a433c67`, `f5cd1ed`)
- [FIX] Missing-document onboarding notifications now fire for all non-CUSTOMER driver profiles. The scheduler role filter was narrowed incorrectly to `== MESSENGER`; it now uses `!= CUSTOMER` so DRIVER, STORE_ADMIN, and MESSENGER roles are all correctly included. Notification calls consolidated to a single `saveAll()` for efficiency; additional document-completeness checks added. (commit `07beff3`)
- [FIX] Removed hardcoded debug phone filter `"+27735749416"` from the `newDrivers` scheduler job. The leftover `.filter()` would have silently suppressed onboarding notifications for every unapproved driver except that one number in production. (commit `f7ab426`)

### Breaking changes

None. All changes are additive — new entities and service methods; no REST API endpoint shapes, response models, enum values, or JWT/auth token structure altered.

### Deployment sequence

1. ijudi-api only — no frontend changes required.

### Rollback steps

1. Redeploy the v1.13.0 artifact (tag `1.13.0` on master) via ECS. No data migration rollback required — the tiered-commission entities are additive and the data is not read by any other service.
2. Notify Lindani that rollback was triggered; monitor driver-approval notification delivery and ambassador payout recon for the 24 hours post-rollback.

### Smoke test plan

1. Approve a driver with vehicle type BIKE — verify ambassador commission payout of R50 is created in the recon payout table.
2. Approve a driver with vehicle type CAR — verify ambassador commission payout of R60 is created.
3. Approve a driver with vehicle type BAKKIE_AND_TRUCK — verify ambassador commission payout of R70 is created.
4. Trigger the `newDrivers` scheduler job (or wait for the next scheduled run) with at least one unapproved non-CUSTOMER driver who has incomplete documents — verify a WhatsApp notification is dispatched to that driver's mobile number.
5. Confirm no WhatsApp notification is sent to `+27735749416` unless that number is genuinely an unapproved driver in the system (i.e., the debug filter is gone and the job now processes all qualifying profiles).

### Post-deployment monitoring

- 15 min: Check scheduler logs — confirm `Processing N messenger drivers` where N > 1 (i.e., not filtered to a single number).
- 1 hour: Confirm ambassador payout records in the recon collection show the correct tiered amounts for any driver approvals that occurred.
- 24 hours: Growth & Analytics to confirm driver onboarding notification delivery rate is not anomalous; verify ambassador payout totals in the weekly recon.

### Gate citations

- Feature Brief: ADR-019 (ambassador tiered commission by vehicle type) — approved by Lindani Masinga
- Code Review: PASS — iZinga Code Reviewer (commits `a433c67`, `f5cd1ed`, `07beff3` independently reviewed); PASS — Release Manager re-review of `f7ab426` (clean single-line deletion, confirmed via `git show`) 2026-10-10
- QA Gate 2 (full regression): PASS — 258 tests, 0 failures, 0 errors, Skipped: 3 (pre-existing), BUILD SUCCESS, Corretto 17, 2026-10-10
- Product Owner sign-off: Lindani Masinga — "The blocking defect is fixed" 2026-10-10

---

## [1.13.0] — 2026-10-08

**Release type:** Patch (P1 — bug fix, no new features)

**Summary:** Removes CUSTOMER-role profiles and null-role OTP placeholders from the admin Pending Approvals list so only genuine driver and store approval requests are surfaced.

### Changes

- [FIX] `GET /pending-approvals` no longer returns CUSTOMER-role profiles — new Spring Data derived query `findByProfileApprovedAndRoleNot` excludes the CUSTOMER role at the database level (commit `abec2f6`).
- [FIX] `GET /pending-approvals` no longer returns null-role OTP placeholders — an in-memory post-query filter removes profiles where `role == null`; MongoDB `$ne` does not exclude documents with a missing field, so a code-level guard is required (commit `0173743`). The upstream cause (OTP placeholder created with `role=null`) was fixed in v1.12.0 (commit `73ff2a9`); this patch cleans up any pre-existing placeholders that already exist in production.

### Breaking changes

None. Change is additive and query-only; no API contract, response shape, or enum values altered.

### Deployment sequence

1. ijudi-api only — no frontend changes required; this is a backend-only patch.

### Rollback steps

1. Redeploy the v1.12.0 artifact (tag `1.12.0`, commit `a6f3f02` on master) via ECS to revert. No data migration required — the fix is query-only and leaves no persistent state.
2. Notify Lindani of rollback; monitor `/pending-approvals` response count post-revert.

### Smoke test plan (post-deploy — minimum checks within 15 minutes)

1. `GET /pending-approvals` with admin JWT — confirm response contains zero CUSTOMER-role entries — expected: only DRIVER and STORE_ADMIN profiles in the list.
2. `GET /pending-approvals` with admin JWT — confirm response contains zero null-role entries — expected: no profile with `role: null` present.
3. Create a new store (triggers OTP with `role=null`), then do NOT complete signup — call `GET /pending-approvals` and confirm the placeholder does not appear in the list.
4. Approve a driver and a store from the admin panel using the pending-approvals list — confirm approvals complete successfully end-to-end.
5. Verify total pending count reflects only actionable requests (drivers awaiting approval + stores awaiting approval) — no noise from sign-up artifacts.

### Post-deployment monitoring

- 15 min: `GET /pending-approvals` response count — confirm the count is not inflated by customer/null-role noise; spot-check 3–5 results.
- 1 hour: Admin panel error rate — confirm no 500s on the pending-approvals endpoint.
- 24 hours: Growth & Analytics to confirm driver and store approval throughput is normal (no approvals silently blocked).

### Gate citations

- Bugfix Branch: `bugfix/pending-approvals-exclude-customer` — merged to develop 2026-10-08
- Code Review: PASS WITH MINOR NOTES — iZinga Code Reviewer 2026-10-08 (both commits independently reviewed)
- QA Gate 1: PASS — 249/249 tests, 100% branch coverage on changed method, Corretto 17, 2026-10-08
- Live verification: pending count dropped 14 → 4, zero CUSTOMER/null-role records in raw JSON response
- QA Gate 2 (regression): PASS — 249 tests, 0 failures, 0 errors, BUILD SUCCESS, Corretto 17, 2026-10-08
- Product Owner sign-off: Lindani Masinga — "lets release" 2026-10-08

---

## [1.12.0] — 2026-10-08

**Release type:** Feature (P2)

**Summary:** Adds PayFast subscription billing (izinga-payfast module), store owner onboarding tiers (ONB-02), and a batch of OTP/signup bug fixes found during furniture-delivery E2E testing.

### Changes

- [NEW] `izinga-payfast` Maven module — full PayFast subscription billing: checkout initiation (`POST /merchant/subscription/initiate`), server-to-server ITN validation handler (`POST /merchant/subscription/itn`), `MerchantSubscription` lifecycle (PENDING_PAYMENT → ACTIVE / FAILED). Complies with all SEC-TB01 security requirements (atomic idempotency, server-side validate mandatory, `payFastToken` @JsonIgnore, no bypass path in production).
- [NEW] `SubscriptionTier` enum in `izinga-commons` — FREE, PREMIUM_1, PREMIUM_2 with display labels and pricing constants. Contract test (`SubscriptionTierContractTest`) prevents accidental enum value changes.
- [NEW] `StoreProfile` tier fields — `subscriptionTier`, `icaAccepted`, `icaAcceptedDate`, `icaVersion` (all additive, no breaking changes to existing API consumers).
- [NEW] STORE_ADMIN role assignment path — store creators are now assigned STORE_ADMIN role; ICA acceptance gate blocks store activation until ICA is accepted.
- [NEW] `StoreServiceSubscriptionEventListener` — activates store on `MerchantSubscriptionActivatedEvent` (PayFast ITN COMPLETE).
- [NEW] QA test-plan library (`docs/test-plans/`) — structured test plans for customer furniture-booking, driver quote-fulfilment, and store PayFast billing flows.
- [FIX] `ONB-FIX`: WhatsApp OTP placeholder created with `role=null` instead of `CUSTOMER` — prevents downstream signup rejection when the signup completes via `POST /user` (commits 73ff2a9, 361993f, 41b37ca).
- [FIX] `mobileNumber` unique index scoped to `UserProfile` only — removes erroneous cross-collection uniqueness constraint that was blocking driver/customer registrations when a store owner with the same number existed (commit 6368659).
- [FIX] Required-field validation — `@NotNull` / `@field:NotNull` use-site target applied correctly; profile create and update now reject missing `imageUrl`, `name`, `surname`, `emailAddress` (commits 22d0d11, 3340ec4, 2d1cb22).
- [FIX] `isUserNotFound()` used wrong Firebase Admin SDK error-code accessor — brand-new Firebase sign-ups threw 500 instead of returning a clean "not found" signal; now uses `getErrorCode()` not `getMessage()` (commit 791cb68).
- [FIX] `STORE-BANK-01`: store-submitted bank details are no longer unconditionally overwritten by the user's primary bank on store creation (commit 3e44483).
- [FIX] Pre-existing test failures in recon, usermanagement, and yoco-pay modules resolved (commit 2734ed8).
- [FIX] `ResponseStatusException` now propagates through `IjudiErrorHandler` — previously swallowed, causing opaque 500 responses for intentional HTTP error responses (commit ef84e8b).
- [FIX] Firebase storeId JWT claim set on store creation — downstream PayFast checkout uses JWT claim for IDOR-safe storeId resolution (commit 29406e5).
- [FIX] ICA gate bypass closed for CUSTOMER-role first-time store creators (commit 9f4b9e0).

### Breaking changes

None. All API changes are additive. `SubscriptionTier` enum values must not be renamed — enforced by `SubscriptionTierContractTest`.

### Known issues (tracked separately — not blocking this release)

- **SEC-CRIT-01**: Multiple production credentials hardcoded in `izinga-ordermanager/src/main/resources/application-prod.yml` — pre-existing, flagged 2026-10-02, credential rotation and git-history scrub tracked as high-priority issue per Lindani Masinga direct authorization 2026-10-08.
- **SEC-IDOR-01**: `OrderServiceImpl.acceptQuote()` trusts client-supplied `messengerId` with no identity check — pre-existing, tracked as follow-up.
- **BILLING-PROD-GATE**: PayFast merchant 16791971 production rate confirmation (Gate b) and BackOffice setup (Gate c) must be completed before Premium billing tiers are activated in production. Free tier activation is safe. PREMIUM_1 and PREMIUM_2 are disabled ("Coming Soon") in the frontend.

### Deployment sequence

1. ijudi-api (this release) — deploy first; backend changes are additive and safe for existing frontend clients.
2. izinga-onboarding v1.17.0 — deploy after backend is confirmed stable.

### Rollback steps

1. `git revert -m 1 <merge-commit-sha>` on master — reverts to v1.11.0 behavior; creates a revert commit rather than a forced reset.
2. Redeploy the previous artifact (v1.11.0 tag on master, `49f3392`).
3. If `MerchantSubscription` documents were written to MongoDB during the window: collection can remain; no foreign key constraints are broken by rolling back the code. Subscriptions will remain in PENDING_PAYMENT state until the next PayFast ITN lands — which will fail cleanly once the handler is restored in a hotfix.
4. Notify Lindani and Hloniphani of the rollback trigger conditions (500 rate spike, PayFast ITN failures, OTP signup failure rate).

### Smoke test plan (post-deploy — minimum checks within 15 minutes)

1. `POST /user` with `role=null` body — new store owner OTP flow completes signup without 400/500 — expected: 200 with profile created.
2. `POST /merchant/subscription/initiate` with valid STORE_ADMIN JWT — returns PayFast checkout URL and `mPaymentId` — expected: 200 with checkout params.
3. `POST /merchant/subscription/itn` with a valid sandbox COMPLETE ITN payload — `MerchantSubscription` transitions to ACTIVE, store `subscriptionTier` updated — expected: 200.
4. Store creation with different bank details from user bank — verify store retains submitted bank, not user's primary bank — expected: stored bank matches submitted values.
5. New user registration (fresh Firebase account) — verify no 500 on `isUserNotFound()` path — expected: clean 404 or redirect to registration flow.

### Post-deployment monitoring

- 15 min: Error rate on `/user`, `/merchant/subscription/initiate`, `/merchant/subscription/itn` — baseline < 1% errors.
- 1 hour: Monitor MongoDB `merchant_subscriptions` collection for unexpected FAILED states; confirm PENDING_PAYMENT → ACTIVE transitions via PayFast sandbox ITN.
- 24 hours: Growth & Analytics to check for OTP signup failure rate (target: < 2% drop-off at OTP step); store creation success rate.

### Gate citations

- Feature Brief: Lindani Masinga — direct authorization 2026-10-08 (this conversation)
- Code Review: PASS — iZinga Code Reviewer 2026-10-08
- QA Gate 1: PASS — 1460 tests / 0 failures (ijudi-api, 2026-10-08)
- Security gate (a): CONDITIONAL PASS — all six SEC-TB01 required changes verified in implementation
- SEC-CRIT-01: Acknowledged by Lindani Masinga 2026-10-08; rotation tracked as separate issue

**Approved by:** Lindani Masinga — 2026-10-08
