# Changelog

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
