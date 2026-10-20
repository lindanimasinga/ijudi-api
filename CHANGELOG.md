# Changelog

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
- Product Owner sign-off: required prior to release finish
- Deployment authorised by: Lindani Masinga — direct instruction 2026-10-20

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
