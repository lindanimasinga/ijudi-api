# Feature Brief: TIER-BILLING-01 — PayFast Subscription Billing for iZinga Merchant Tiers

**Feature name:** PayFast Subscription Billing for iZinga Merchant Tiers

**Status:** Approved for Implementation

**Requested by:** Lindani Masinga (co-founder)

**Business objective:** iZinga publicly offers Premium Tier 1 (R800/month) and Premium Tier 2 (R3,000/month) merchant subscriptions. ONB-02 built the tier selection and merchant agreement flows — but there is currently no billing engine: `StoreProfile.subscriptionTier` is a database flag only, set manually or by admin. No store owner is actually charged for a Premium subscription. This means iZinga is acquiring merchant commitments to paid tiers with zero revenue collected. TIER-BILLING-01 closes that gap by wiring PayFast subscription billing into the post-onboarding flow. When a store owner selects a Premium tier, they are redirected to PayFast's hosted subscription checkout immediately after store creation; PayFast owns the recurring debit engine; iZinga receives webhook confirmation of each payment and updates the store's subscription state accordingly. Lindani confirmed on 3 October 2026 that iZinga holds a live, already-approved PayFast merchant account (ID 16791971) with Recurring Billing/Subscriptions active (Tokenization toggle enabled, T&Cs pre-accepted). No new PayFast account provisioning is required — engineering can target this account immediately.

**Architecture sign-off:** ADR-023 approved by iZinga Solution Architect (design produced in response to this feature need, 3 October 2026). Decisions in ADR-023 are binding on this brief.

**Audience:** Store owners / merchant partners (biz.izinga.co.za) — specifically those selecting Premium Tier 1 or Premium Tier 2 at onboarding

**Products affected:**
- `ijudi-api` — new `izinga-payfast` Maven module (checkout initiation, ITN handler, MerchantSubscription model); one `@EventListener` addition to `izinga-ordermanager` `StoreService.java`
- `izinga-onboarding` — new subscription payment flow inserted after store creation for Premium tier selections

**Out of scope:**
- FREE tier: no billing flow, no change
- Subscription upgrade/downgrade/cancellation flows — deferred to a future brief
- Subscription management portal or billing history UI — deferred
- PayFast refund processing — deferred
- Admin dashboard billing views — deferred
- Automated dunning or retry logic beyond PayFast's native handling — PayFast owns the recurring debit schedule and retry behaviour; iZinga does not build a billing scheduler
- Any change to `izinga-yoco-pay`, Yoco checkout, or any B2C customer order/tip payment flow — confirmed zero blast radius; fully separate modules, endpoints, and MongoDB collections
- `cs-lifestyle`, `furniture-delivery-app`, or `ijudi` Flutter app — no changes required in this phase
- SMS or email notifications to the merchant on payment success/failure — deferred
- Subscription billing for existing stores already holding a Premium tier flag — out of scope for Phase 1; a separate migration path is required and will be scoped separately

---

## Context: The Billing Gap

ONB-02 (feature/ONB-02-store-owner-onboarding-tiers, shipped to develop 2 October 2026) produces a `StoreProfile` with `subscriptionTier`, `subscriptionTierSince`, and the merchant agreement acceptance fields. ONB-02 explicitly deferred subscription billing to this brief (ONB-02 REQ-11: "Subscription billing — deferred to TIER-BILLING-01"; ONB-02 Out of Scope: "Subscription billing and payment collection for monthly subscription fees (deferred to TIER-BILLING-01)").

The result is a complete store record with a declared tier but no payment collected. This brief builds the payment side as a clean additive layer on top of ONB-02's data model. No ONB-02 code is modified.

**PayFast hosting model:** iZinga does not self-host a recurring billing scheduler. PayFast's subscription/tokenization product owns the monthly debit cycle. iZinga's backend only handles two things:
1. Checkout initiation — build the PayFast form parameters, create a `PENDING_PAYMENT` subscription record, redirect the merchant to PayFast's hosted page.
2. ITN receipt — receive PayFast's server-to-server Instant Transaction Notification, validate the signature, update the subscription to `ACTIVE`, fire a domain event that updates `StoreProfile.subscriptionTier`.

**Known async UX issue:** PayFast's browser return redirect back to iZinga (the `return_url`) fires as soon as the merchant completes payment on PayFast's hosted page. PayFast's server-to-server ITN may arrive before, during, or shortly after this redirect — but there is no guarantee the ITN has been processed by the time the merchant sees iZinga's success page. The `StoreProfile.subscriptionTier` may still read `FREE` for a brief window even though payment succeeded. The success page must communicate this clearly ("Your Premium tier will activate within a few minutes") and must not render a tier badge that requires the subscription to already be `ACTIVE`.

---

## User Stories

**US-01 (Premium Tier 1 new store owner):** As a merchant who selected Premium Tier 1 during onboarding and completed store setup, I am taken directly to a payment page where I can pay my R800/month subscription via PayFast. After payment, I see a confirmation page that tells me my Premium tier will be active within minutes.

**US-02 (Premium Tier 2 new store owner):** As a merchant who selected Premium Tier 2, the same subscription flow applies at R3,000/month.

**US-03 (FREE tier new store owner):** As a merchant who selected Free tier, I am taken directly to the dashboard after store creation — I see no subscription payment step and am not asked for payment.

**US-04 (Merchant who abandons PayFast checkout):** As a merchant who clicks "Cancel" on the PayFast hosted page or navigates away, I am returned to iZinga with a clear message that my subscription was not activated. My store exists but my tier is shown as FREE until I complete payment.

**US-05 (iZinga ADMIN):** As an ADMIN, I can query the `merchant_subscriptions` MongoDB collection to see the current subscription status, PayFast token, and billing dates for any store.

---

## Requirements

### Phase 1 — Core billing flow (must ship before any Premium subscription revenue is realised)

**REQ-01: New `izinga-payfast` Maven module in `ijudi-api`**

A new Maven module named `izinga-payfast` must be created inside the `ijudi-api` multi-module project. This module is a library — it has no `@SpringBootApplication` of its own. It is added as a dependency of `izinga-ordermanager`. The module is responsible for:
- All PayFast HTTP integration (form parameter construction, signature generation/validation)
- The `MerchantSubscription` document model (see REQ-03)
- The `MerchantSubscriptionRepository` interface
- The `MerchantSubscriptionActivatedEvent` domain event class
- The `PayFastCheckoutService` (checkout initiation logic)
- The `PayFastItnHandler` (ITN signature validation and subscription state machine)

No PayFast logic may be placed in `izinga-ordermanager` directly — it routes through the `izinga-payfast` module exclusively. This separation ensures PayFast can be replaced or audited without touching the ordermanager.

**REQ-02: PayFast checkout initiation endpoint**

New endpoint: `POST /merchant/subscription/initiate`

Module: `izinga-ordermanager`, `izinga-payfast`

Auth: `STORE_ADMIN` role. The storeId is read from the JWT token claim — never from the request body (IDOR prevention).

Request body:
```json
{
  "tier": "PREMIUM_1"
}
```

Service-layer validation (`PayFastCheckoutService`):
- `tier` must be `PREMIUM_1` or `PREMIUM_2` — return HTTP 400 for any other value including `FREE`.
- The store identified by the JWT storeId claim must exist and have `icaAccepted = true` — return HTTP 422 with `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }` if not.
- If an `ACTIVE` `MerchantSubscription` already exists for this storeId, return HTTP 409 with `{ "error": "SUBSCRIPTION_ALREADY_ACTIVE" }` (idempotency guard).
- If a `PENDING_PAYMENT` subscription exists for this storeId and was created within the last 30 minutes, return the existing `mPaymentId` in the response (idempotency — avoid duplicate checkout sessions; the merchant may have refreshed mid-flow).

On success:
1. Create a `MerchantSubscription` document in `PENDING_PAYMENT` status (see REQ-03).
2. Build the PayFast hosted subscription form parameters using iZinga's merchant credentials (`merchant_id = 16791971`, `merchant_key` from environment secret, `passphrase` from environment secret). The PayFast form parameters must include: `subscription_type = 1` (recurring subscription), `billing_date` (first billing date = today), `recurring_amount` (tier amount in rands, integer or two-decimal string as PayFast requires), `frequency = 3` (monthly), `cycles = 0` (indefinite), `return_url`, `cancel_url`, `notify_url` (the ITN webhook endpoint), `m_payment_id` (the `MerchantSubscription._id`), `item_name`, `amount` (first charge amount = recurring amount for subscriptions), `email_address` (store owner email from `UserProfile`).
3. Generate the PayFast MD5 signature over the parameter string (parameters sorted alphabetically, URL-encoded, passphrase appended).
4. Return HTTP 200 with the signed form parameters as JSON — the frontend will construct and auto-submit the form to PayFast's hosted page.

PayFast base URLs (must be environment-configured, not hardcoded):
- Sandbox: `https://sandbox.payfast.co.za/eng/process`
- Production: `https://www.payfast.co.za/eng/process`

Merchant credentials (`merchant_id`, `merchant_key`, `passphrase`) must be stored as Spring Boot environment secrets (not in `application.yml` in version control). Sandbox and production credentials are separate secrets — the active set is selected by the active Spring profile.

**REQ-03: `MerchantSubscription` MongoDB document and collection**

New MongoDB collection: `merchant_subscriptions`

Document fields:

| Field | Type | Notes |
|---|---|---|
| `_id` / `mPaymentId` | `String` | iZinga-generated payment ID, used as PayFast `m_payment_id` |
| `storeId` | `String` | FK to `StoreProfile._id` |
| `ownerId` | `String` | UID of the store owner at time of subscription creation |
| `tier` | `SubscriptionTier` | `PREMIUM_1` or `PREMIUM_2` — never `FREE` |
| `status` | `MerchantSubscriptionStatus` | Enum: `PENDING_PAYMENT`, `ACTIVE`, `PAUSED`, `PAYMENT_FAILED`, `CANCELLED` |
| `amountRands` | `BigDecimal` | Subscription amount in rands (800 for Tier 1, 3000 for Tier 2) |
| `createdDate` | `Date` | Server timestamp when subscription record was created |
| `activatedDate` | `Date` | Timestamp when ITN confirmed first payment — null until ACTIVE |
| `nextBillingDate` | `Date` | From PayFast ITN response — null until ACTIVE |
| `lastBillingDate` | `Date` | Updated on each successful ITN — null until first payment |
| `payFastToken` | `String` | PayFast subscription token from ITN — null until ACTIVE; handle as a secret (do not log) |
| `payFastPaymentId` | `String` | PayFast `pf_payment_id` from ITN |
| `cancelledDate` | `Date` | Null unless cancelled |
| `cancelledReason` | `String` | Null unless cancelled |

`MerchantSubscriptionStatus` enum lives in `izinga-payfast`:
```kotlin
enum class MerchantSubscriptionStatus {
    PENDING_PAYMENT, ACTIVE, PAUSED, PAYMENT_FAILED, CANCELLED
}
```

`MerchantSubscriptionRepository` must expose: `save()`, `findById()`, `findByStoreId()`, `findByStoreIdAndStatus()`. No delete operation is exposed (subscriptions are soft-cancelled via the `status` and `cancelledDate` fields; hard delete is not permitted).

**REQ-04: PayFast ITN webhook handler**

New endpoint: `POST /merchant/subscription/itn`

Module: `izinga-ordermanager` (controller), `izinga-payfast` (handler logic)

Auth: **No JWT auth** — this endpoint is called by PayFast's servers, not by an authenticated iZinga user. The endpoint must be publicly reachable over HTTPS. Security is enforced entirely by PayFast signature validation (see below), not by iZinga JWT.

Signature validation (mandatory — `PayFastItnHandler` must implement):
1. Extract all ITN POST parameters from the request body.
2. Build the parameter string: all parameters except `signature`, sorted alphabetically, URL-encoded, with the iZinga merchant passphrase appended as `passphrase=<value>`.
3. Compute MD5 hash of the parameter string.
4. Compare to the `signature` field from the ITN. If they do not match, return HTTP 200 (PayFast expects 200 to stop retrying) but write a `SIGNATURE_VALIDATION_FAILED` log entry and do NOT update any subscription record.
5. Verify `merchant_id` matches iZinga's registered merchant ID (`16791971`). If it does not match, treat as invalid.
6. Optionally verify the source IP against PayFast's published IP range (Security & Compliance to advise — see Gate (a)).

ITN replay prevention: before processing any ITN, check whether a `MerchantSubscription` with matching `mPaymentId` AND `payFastPaymentId` has already been processed to `ACTIVE`. If yes, return HTTP 200 without re-processing (idempotent handler).

On valid ITN with `payment_status = COMPLETE`:
1. Retrieve the `MerchantSubscription` by `mPaymentId`.
2. Update status to `ACTIVE`, set `activatedDate`, `lastBillingDate`, `nextBillingDate`, `payFastToken`, `payFastPaymentId`.
3. Publish a `MerchantSubscriptionActivatedEvent` carrying `storeId` and `tier`.
4. Return HTTP 200.

On valid ITN with `payment_status = FAILED`:
1. Update `MerchantSubscription` status to `PAYMENT_FAILED`.
2. Log the failure with `storeId` and PayFast payment ID.
3. Return HTTP 200.

The `payFastToken` field must never appear in application logs or error messages (it is a billing secret enabling future charges).

**REQ-05: `@EventListener` in `StoreService.java` for `MerchantSubscriptionActivatedEvent`**

This is a single-method addition to `izinga-ordermanager`'s `StoreService.java`. It must not modify any existing method signature or behaviour.

```java
@EventListener
public void onMerchantSubscriptionActivated(MerchantSubscriptionActivatedEvent event) {
    updateSubscriptionTier(event.getStoreId(), event.getTier());
}
```

`updateSubscriptionTier()` already exists in `StoreService` (introduced by ONB-02, T-09). This event listener is the sole trigger that elevates a store's `subscriptionTier` from `FREE` to `PREMIUM_1` or `PREMIUM_2` via the billing path. No other code path should call `updateSubscriptionTier()` in response to a PayFast payment.

**REQ-06: Subscription flow insertion in `izinga-onboarding` — Premium tiers only**

After store creation completes successfully in `BusinessUpdateComponent` (the `registerBusinessAndStock()` success callback), the onboarding flow must branch on tier:

- `subscriptionTier == FREE` (or null): navigate directly to `/business/dashboard`. No change from current behaviour.
- `subscriptionTier == PREMIUM_1` or `PREMIUM_2`: navigate to `/business/subscription/:storeId`.

The branching logic must read the tier from the same session state used by `TierSelectionComponent` (introduced by ONB-02, T-10) — it must not make an additional backend call to retrieve the tier.

New routes (all lazy-loaded):
- `/business/subscription/:storeId` — `SubscriptionCheckoutComponent`
- `/business/subscription-success/:storeId` — `SubscriptionSuccessComponent`
- `/business/subscription-cancel/:storeId` — `SubscriptionCancelComponent`

**REQ-07: `SubscriptionCheckoutComponent` at `/business/subscription/:storeId`**

On component initialisation:
1. Call `POST /merchant/subscription/initiate` with the store's selected tier.
2. On success: construct a hidden HTML form with all returned PayFast parameters and auto-submit it (standard PayFast hosted payment page redirect pattern). Show a loading spinner with the message "Taking you to secure payment..." while the call is in progress.
3. On HTTP 409 (`SUBSCRIPTION_ALREADY_ACTIVE`): navigate directly to `/business/dashboard` — the subscription is already active, no payment needed.
4. On any other error: display "We couldn't start your payment. Please try again or contact support." with a retry button that re-calls initiate.

A `PremiumTierGuard` must be registered on this route. If the user navigates directly to `/business/subscription/:storeId` and their `StoreProfile.subscriptionTier` is already `ACTIVE` (i.e. not `PREMIUM_1` or `PREMIUM_2` with a subscription in `PENDING_PAYMENT` state), redirect to `/business/dashboard`. If the store's subscription tier is `FREE` or null, also redirect to `/business/dashboard` (FREE stores have no subscription requirement).

**REQ-08: `SubscriptionSuccessComponent` at `/business/subscription-success/:storeId`**

This is the PayFast `return_url` destination. The component must:
1. Display: "Payment received — thank you! Your Premium tier will activate within a few minutes as we confirm your payment. You can continue to your dashboard now."
2. Provide a "Go to Dashboard" button that navigates to `/business/dashboard`.
3. Must NOT poll for subscription activation status — the async messaging is the designed UX.
4. Must NOT display a Premium tier badge on this screen — the tier may not yet be active when this page renders.

**REQ-09: `SubscriptionCancelComponent` at `/business/subscription-cancel/:storeId`**

This is the PayFast `cancel_url` destination. The component must:
1. Display: "Your subscription payment was not completed. Your store has been created on the Free tier. You can upgrade to Premium from your dashboard at any time."
2. Provide a "Go to Dashboard" button that navigates to `/business/dashboard`.
3. Must NOT delete or modify the store record — the store exists and is usable on the FREE tier.

**REQ-10: Pricing constants**

Subscription amounts are defined as constants in `izinga-payfast`, not hardcoded inline. These must match ONB-02's published pricing:

```kotlin
object MerchantSubscriptionPricing {
    val PREMIUM_1_RANDS = BigDecimal("800")
    val PREMIUM_2_RANDS = BigDecimal("3000")
}
```

Any change to subscription pricing requires updating these constants and a corresponding note in the release changelog.

---

## Data Model Changes (ADR-023 — binding)

### New `MerchantSubscription` MongoDB document (see REQ-03 for full field table)

Collection: `merchant_subscriptions`

This collection is entirely new — no existing collection is modified. The `MerchantSubscription` document is owned by `izinga-payfast` and is additive. No existing `StoreProfile`, `UserProfile`, or any other document is structurally changed by this feature.

**`StoreProfile` interaction:** `StoreProfile.subscriptionTier` and `subscriptionTierSince` (added by ONB-02) are updated by the `@EventListener` in `StoreService` when the `MerchantSubscriptionActivatedEvent` fires. No new fields are added to `StoreProfile` in TIER-BILLING-01.

### New `MerchantSubscriptionStatus` enum in `izinga-payfast`

```kotlin
package io.curiousoft.izinga.payfast.model

enum class MerchantSubscriptionStatus {
    PENDING_PAYMENT, ACTIVE, PAUSED, PAYMENT_FAILED, CANCELLED
}
```

This enum is local to `izinga-payfast`. It is not added to `izinga-commons` (it has no cross-repo consumers in Phase 1 — the Flutter app and Angular apps do not read subscription status directly in this phase).

---

## API Changes

### New endpoints

**`POST /merchant/subscription/initiate`** — see REQ-02 for full spec.

Summary: STORE_ADMIN only; storeId from JWT; creates `PENDING_PAYMENT` subscription record; returns signed PayFast form parameters. Returns HTTP 409 if already ACTIVE. Returns HTTP 422 if ICA not accepted. Returns HTTP 400 for FREE tier.

**`POST /merchant/subscription/itn`** — see REQ-04 for full spec.

Summary: public endpoint (no JWT); PayFast signature validation mandatory; idempotent; fires `MerchantSubscriptionActivatedEvent` on COMPLETE; updates subscription status on FAILED.

### Zero changes to existing endpoints

The following endpoints are confirmed unmodified by TIER-BILLING-01:
- All Yoco payment endpoints in `izinga-yoco-pay`
- `POST /store`, `PATCH /store/{id}/subscription-tier`, `PATCH /store/{id}/ica-acceptance` (all ONB-02 endpoints)
- All customer order endpoints
- All driver endpoints

---

## UX Flow (izinga-onboarding)

The TIER-BILLING-01 flow inserts after ONB-02's existing store creation flow. The ONB-02 flow is unchanged.

```
[ONB-02 flow unchanged]
  → /business/tier-select/:id      TierSelectionComponent (ONB-02, T-10)
  → /business/terms/:id            TermsConditionsComponent (ONB-02, T-11)
  → /business/info/:id             BusinessUpdateComponent (ONB-02)
      registerBusinessAndStock() success callback:
        if tier == FREE:
          → /business/dashboard                        [unchanged]
        if tier == PREMIUM_1 or PREMIUM_2:
          → /business/subscription/:storeId            [NEW — T-11]

  [NEW — TIER-BILLING-01 flow]
  → /business/subscription/:storeId
      SubscriptionCheckoutComponent
        — calls POST /merchant/subscription/initiate
        — auto-submits PayFast form (redirect to PayFast hosted page)
        — spinner: "Taking you to secure payment..."
        — on error: retry prompt
        — on HTTP 409 (already active): → /business/dashboard

  [PayFast hosted payment page — off iZinga domain]
      Merchant completes payment
        → return_url: /business/subscription-success/:storeId
        → cancel_url: /business/subscription-cancel/:storeId

  → /business/subscription-success/:storeId
      SubscriptionSuccessComponent
        — "Payment received — your Premium tier will activate within a few minutes"
        — "Go to Dashboard" → /business/dashboard

  → /business/subscription-cancel/:storeId
      SubscriptionCancelComponent
        — "Payment not completed — store created on Free tier"
        — "Go to Dashboard" → /business/dashboard

  [Server-side, async — may arrive before or after success page renders]
  POST /merchant/subscription/itn  (PayFast → iZinga backend)
      PayFastItnHandler validates signature
        → on COMPLETE: MerchantSubscription → ACTIVE
                       MerchantSubscriptionActivatedEvent fired
                       StoreService.onMerchantSubscriptionActivated()
                         → StoreProfile.subscriptionTier = PREMIUM_1 or PREMIUM_2
                         → StoreProfile.subscriptionTierSince = now()
        → on FAILED:   MerchantSubscription → PAYMENT_FAILED
```

---

## Dependencies and Gates

### Gate (a) — Security & Compliance review (blocks ITN implementation start)

Three specific areas require iZinga Security & Compliance assessment before the ITN handler is implemented. The checkout initiation and frontend work (T-03 through T-07, T-10 through T-13) may proceed in parallel with this review. Only T-08 (the ITN handler itself) is blocked.

**SEC-TB01-01:** ITN webhook endpoint — public endpoint with no JWT auth. Confirm signature validation is sufficient protection; advise whether PayFast source IP allowlisting is required as an additional layer; confirm no sensitive data is logged from the ITN request body (particularly `payFastToken`).

**SEC-TB01-02:** Replay attack prevention — confirm that the `mPaymentId` + `payFastPaymentId` deduplication check (REQ-04) is sufficient to prevent PayFast ITN replay from double-activating a subscription.

**SEC-TB01-03:** Merchant credentials at rest — confirm that `merchant_key` and `passphrase` are stored only as Spring Boot environment secrets and are never written to logs, error responses, or any database field. Confirm passphrase is not included in any response body from `POST /merchant/subscription/initiate`.

Engineering does not implement T-08 until Security & Compliance returns a PASS or CONDITIONAL-PASS verdict for all three areas.

### Gate (b) — PayFast subscription rate confirmation (release gate — does not block engineering start)

PayFast's subscription/recurring billing charge rate is assumed to be the standard card rate (R2.00 + 3.20% excl VAT) based on public documentation. This has not been confirmed in writing from PayFast support for the recurring subscription product specifically.

**This is a release gate, not an engineering blocker.** Engineering proceeds to completion. The feature branch may not merge to `main` and go live until:
1. Written confirmation from PayFast support confirms the applicable charge rate for subscription billing on account ID 16791971.
2. The rate is reviewed against iZinga's margin assumptions for Premium Tier 1 and Tier 2 pricing.
3. Lindani Masinga provides sign-off on the confirmed rate.

The Release Manager must hold this confirmation on file before authorising the production deployment.

### Gate (c) — Lindani BackOffice setup (required before end-to-end integration testing — does not block starting code)

Lindani must complete the following PayFast account configuration steps before the Backend Developer and QA can run end-to-end tests against PayFast's sandbox:

1. Enable Tokenization toggle on the PayFast merchant account (ID 16791971) if not already active.
2. Confirm the merchant passphrase (set in PayFast merchant portal) and provide it to engineering as a secure secret for the sandbox environment.
3. Create (or confirm existence of) a PayFast sandbox merchant account and provide sandbox `merchant_id`, `merchant_key`, and `passphrase` for local and CI testing.
4. Confirm iZinga's registered `notify_url` (ITN webhook URL) is configured in PayFast settings for the production account, or confirm that PayFast uses the `notify_url` parameter per-request (engineering to verify against PayFast docs).

These steps do not block any coding work. T-03 through T-11 can be completed before Gate (c). T-09 (integration test) requires Gate (c) to be complete.

### Gate (d) — AI Code Review PASS before merge to develop

All pull requests from the feature branch into `develop` must carry an AI Code Review PASS verdict. No merge without the verdict.

### Gate (e) — Release Manager owns merge to main

The Release Manager merges `release/*` to `main`. No direct merges to `main`. Gate (b) PayFast rate confirmation must be in hand before the Release Manager authorises production deployment.

### Dependencies on ONB-02

TIER-BILLING-01 depends on ONB-02 being present in `develop` before the feature branch is opened:
- `SubscriptionTier` enum (used in REQ-02 request body validation and REQ-05 event)
- `StoreProfile.subscriptionTier` and `subscriptionTierSince` fields (updated by REQ-05)
- `updateSubscriptionTier()` method in `StoreService` (called by REQ-05 event listener)
- `TierSelectionComponent` and tier session state (used by REQ-06 branching logic in `BusinessUpdateComponent`)

Do not open the TIER-BILLING-01 feature branch until ONB-02 is confirmed in `develop`.

---

## Risks

**RISK-01 (UX — async activation window):** PayFast's browser redirect (`return_url`) fires before the server-to-server ITN is guaranteed to have been processed. A merchant may see the success page and immediately navigate to the dashboard, finding their tier still showing as FREE. This is expected and normal for PayFast's hosted subscription model. Mitigation: `SubscriptionSuccessComponent` must not display a tier badge or imply instant activation. The copy "will activate within a few minutes" is mandatory. A future enhancement could add a light-polling or push notification mechanism, but that is out of scope here.

**RISK-02 (FINANCIAL — PayFast rate unconfirmed):** The R2.00 + 3.20% charge rate for PayFast subscriptions has not been confirmed in writing. If the actual rate differs (e.g. subscriptions attract a different rate than standard card transactions), this affects iZinga's net revenue per subscription. Gate (b) is the mitigation — the feature cannot go live until the rate is confirmed. Lindani to action the PayFast support request as soon as engineering starts.

**RISK-03 (SECURITY — ITN replay):** PayFast may send duplicate ITN notifications if its server does not receive a timely HTTP 200 response. Without replay prevention, a duplicate ITN could trigger `MerchantSubscriptionActivatedEvent` twice, potentially causing duplicate `subscriptionTierSince` writes or confusing state. Mitigation: the `mPaymentId` + `payFastPaymentId` deduplication check in `PayFastItnHandler` (REQ-04) is the engineering control. Security & Compliance SEC-TB01-02 must confirm adequacy before T-08 ships.

**RISK-04 (SECURITY — webhook endpoint publicly accessible):** `POST /merchant/subscription/itn` is a public endpoint by design (PayFast cannot authenticate with iZinga's JWT). A malicious actor could POST fabricated ITN data to activate subscriptions fraudulently. Mitigation: PayFast MD5 signature validation is the primary control. SEC-TB01-01 must advise whether additional controls (IP allowlisting, rate limiting) are required.

**RISK-05 (CREDENTIALS — production account live):** iZinga's PayFast account (ID 16791971) is a live production account. Any accidental use of production credentials during development or testing will result in real charges to test store owners. Mitigation: the active PayFast base URL and credentials must be environment-gated; the sandbox profile (`spring.profiles.active=sandbox` or equivalent) must use the sandbox credentials and sandbox URL exclusively. The Backend Developer must confirm the sandbox profile is active on all non-production environments before running any end-to-end test.

**RISK-06 (DATA — payFastToken logging):** The `payFastToken` field in `MerchantSubscription` is a PayFast billing token that authorises future recurring charges. If logged in plaintext (e.g. in a catch block that logs the full ITN payload), it becomes a billing security exposure. The `payFastToken` field must be explicitly excluded from any logging framework serialisation. SEC-TB01-03 covers this.

**RISK-07 (REGRESSION — ONB-02 session state):** `SubscriptionCheckoutComponent` reads the selected tier from ONB-02's `TierSelectionComponent` session state. If the merchant refreshes the page between store creation and the subscription route, that session state is lost. REQ-07 specifies that the initiate endpoint's HTTP 409 response handles the "already active" case. The "state lost on refresh" case (where a PENDING_PAYMENT subscription exists but session state is gone) is not handled in Phase 1 — the merchant sees the initiate spinner, the backend finds an existing PENDING_PAYMENT subscription created within 30 minutes and returns the existing `mPaymentId`, and the checkout redirect proceeds. Engineering must confirm this path works end-to-end in T-09.

**RISK-08 (OPEN DECISION — abandoned subscriptions):** If a merchant completes store creation (store record created, tier = PREMIUM_1 set in session state) but then does NOT complete the PayFast checkout (closes the browser, or cancels), the store exists on FREE tier but the merchant may believe they are on Premium. The cancel path (REQ-09) handles the explicit cancel case. Browser close / navigation-away leaves a `PENDING_PAYMENT` `MerchantSubscription` orphaned. Phase 1 does not include a UI to resume payment from the dashboard. This is flagged as an open question below — Lindani to decide the handling.

---

## Task Breakdown

All backend tasks run on `feature/TIER-BILLING-01-payfast-subscription-billing` branched from `develop` in `ijudi-api`. All frontend tasks run on a branch of the same name in `izinga-onboarding`. The `develop` branch must contain ONB-02 before these branches are opened.

| ID | Task | Repo | Agent | Depends on | Phase |
|---|---|---|---|---|---|
| T-01 | Create feature branch `feature/TIER-BILLING-01-payfast-subscription-billing` from `develop` in both `ijudi-api` and `izinga-onboarding`; confirm ONB-02 is present in `develop` before branching | Both | iZinga Backend Developer + iZinga Onboarding UI/UX Developer | ONB-02 in develop | 1 |
| T-02 | Security & Compliance review: assess SEC-TB01-01 (ITN public endpoint + IP allowlisting), SEC-TB01-02 (replay attack prevention adequacy), SEC-TB01-03 (credential and token logging controls); return PASS or CONDITIONAL-PASS before T-08 starts | `ijudi-api` | iZinga Security & Compliance | T-01 | 1 |
| T-03 | Backend: create `izinga-payfast` Maven module — `pom.xml`, Spring Boot starter dependency declaration in `izinga-ordermanager/pom.xml`, base package `io.curiousoft.izinga.payfast`, empty module structure only | `ijudi-api` | iZinga Backend Developer | T-01 | 1 |
| T-04 | Backend: create `MerchantSubscription` Kotlin document class, `MerchantSubscriptionStatus` enum, `MerchantSubscriptionPricing` object, and `MerchantSubscriptionRepository` interface (expose save, findById, findByStoreId, findByStoreIdAndStatus — no delete) in `izinga-payfast` | `ijudi-api` | iZinga Backend Developer | T-03 | 1 |
| T-05 | Backend: implement `PayFastCheckoutService` in `izinga-payfast` — checkout form parameter construction, MD5 signature generation, `MerchantSubscription` creation in `PENDING_PAYMENT` status, 30-minute idempotency check for existing PENDING records; implement `POST /merchant/subscription/initiate` controller method in `izinga-ordermanager`; storeId from JWT only; return signed PayFast form parameters; PayFast base URL and credentials from environment secrets (never from application.yml) | `ijudi-api` | iZinga Backend Developer | T-04 | 1 |
| T-06 | Backend: create `MerchantSubscriptionActivatedEvent` domain event class in `izinga-payfast`; add `@EventListener onMerchantSubscriptionActivated()` method to `StoreService.java` in `izinga-ordermanager` — calls existing `updateSubscriptionTier()`; confirm no existing method signatures or tests are altered | `ijudi-api` | iZinga Backend Developer | T-04 | 1 |
| T-07 | Backend: implement `PayFastItnHandler` in `izinga-payfast` — signature validation algorithm (alphabetic sort, URL-encode, passphrase append, MD5), merchant_id verification, payFastToken logging exclusion; implement `POST /merchant/subscription/itn` controller method in `izinga-ordermanager` (public, no JWT); ITN replay prevention using mPaymentId + payFastPaymentId deduplication; on COMPLETE: update subscription to ACTIVE, publish `MerchantSubscriptionActivatedEvent`; on FAILED: update to PAYMENT_FAILED; always return HTTP 200 to PayFast — BLOCKED on Gate (a) PASS | `ijudi-api` | iZinga Backend Developer | T-05, T-06, Gate (a) | 1 |
| T-08 | Backend: write unit tests for `PayFastCheckoutService` (signature generation correctness, PREMIUM_1/PREMIUM_2 amounts, FREE tier rejection, ICA gate, duplicate ACTIVE guard, 30-min idempotency) and `PayFastItnHandler` (valid signature processes, invalid signature rejected, replay prevention, COMPLETE/FAILED state transitions); 100% branch coverage on both classes | `ijudi-api` | iZinga QA & Test Automation | T-05, T-07 | 1 |
| T-09 | Integration test: end-to-end test against PayFast sandbox — initiate checkout → sandbox PayFast payment → ITN received → `MerchantSubscription` transitions to ACTIVE → `StoreProfile.subscriptionTier` updates; confirm sandbox profile is active; confirm payFastToken never appears in logs — requires Gate (c) BackOffice setup to be complete | `ijudi-api` | iZinga Backend Developer + iZinga QA & Test Automation | T-07, T-08, Gate (c) | 1 |
| T-10 | Frontend: add `PremiumTierGuard` to `izinga-onboarding` — guards `/business/subscription/:storeId`; redirects to `/business/dashboard` if store's subscription is already ACTIVE or if tier is FREE/null; register guard in routing module | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-01 | 1 |
| T-11 | Frontend: implement `SubscriptionCheckoutComponent` at route `/business/subscription/:storeId` — calls `POST /merchant/subscription/initiate` on init; spinner with "Taking you to secure payment..."; on success auto-submits PayFast form; on HTTP 409 redirects to dashboard; on other error shows retry prompt; verify PayFast sandbox URL used in non-production environments | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-05, T-10 | 1 |
| T-12 | Frontend: implement `SubscriptionSuccessComponent` at route `/business/subscription-success/:storeId` — mandatory async messaging "will activate within a few minutes"; no tier badge; "Go to Dashboard" CTA | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-10 | 1 |
| T-13 | Frontend: implement `SubscriptionCancelComponent` at route `/business/subscription-cancel/:storeId` — "payment not completed, store on Free tier" messaging; no store modification; "Go to Dashboard" CTA | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-10 | 1 |
| T-14 | Frontend: update `BusinessUpdateComponent` — in `registerBusinessAndStock()` success callback, branch on tier from session state: FREE/null → navigate to `/business/dashboard`; PREMIUM_1/PREMIUM_2 → navigate to `/business/subscription/:storeId` | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-05, T-11 | 1 |
| T-15 | QA: write full test plan covering all Phase 1 ACs; execute against all acceptance criteria; full unscoped test suite must be green in both repos; block merge to `develop` until all ACs pass | Both | iZinga QA & Test Automation | T-09, T-11, T-12, T-13, T-14 | 1 |
| T-16 | AI Code Review: review all Phase 1 changes across both repos; verify payFastToken never logged; verify storeId from JWT only in initiate endpoint; verify ITN replay prevention; verify no regression on existing Yoco flow or ONB-02 endpoints; verify PayFast credentials not in application.yml; produce PASS/FAIL verdict | Both | iZinga Code Reviewer | T-15 | 1 |
| T-17 | Release Manager: confirm Gate (b) PayFast rate confirmation on file and Lindani sign-off received; prepare `release/*`; confirm sandbox vs production environment gating; merge to `main` | Both | iZinga Release Manager | T-16, Gate (b) | 1 |

**Safe implementation sequence:**

1. Confirm ONB-02 is in `develop` — block branch creation until confirmed.
2. T-01 (branch creation) and T-02 (Security & Compliance review) in parallel.
3. T-03 (module scaffold) → T-04 (data model) — sequential prerequisite for all backend work.
4. T-05 (checkout service + initiate endpoint), T-06 (event listener), T-10 through T-13 (frontend components) — all in parallel once T-04 complete; T-07 (ITN handler) blocked on Gate (a).
5. T-07 (ITN handler) — after Gate (a) PASS; T-14 (BusinessUpdateComponent wiring) — after T-05 and T-11.
6. T-08 (unit tests) — after T-05 and T-07.
7. T-09 (integration test) — after T-07, T-08, and Gate (c).
8. T-15 (QA) → T-16 (Code Review) → T-17 (Release Manager) — sequential.

---

## Acceptance Criteria

**AC-01 (FREE tier bypass):** Given a new store owner completes store creation with `subscriptionTier = FREE` (or null), when `registerBusinessAndStock()` succeeds, then the app navigates directly to `/business/dashboard` without rendering any subscription route or calling `POST /merchant/subscription/initiate`.

**AC-02 (Premium tier triggers subscription flow):** Given a new store owner completes store creation with `subscriptionTier = PREMIUM_1` or `PREMIUM_2`, when `registerBusinessAndStock()` succeeds, then the app navigates to `/business/subscription/:storeId` and `SubscriptionCheckoutComponent` begins to load.

**AC-03 (Initiate — storeId from JWT):** Given an authenticated `STORE_ADMIN` whose JWT storeId claim is `store-A` calls `POST /merchant/subscription/initiate` with any body, when the server processes the request, then the `MerchantSubscription` created has `storeId = "store-A"` — regardless of any storeId value in the request body.

**AC-04 (Initiate — FREE tier rejected):** Given a `STORE_ADMIN` calls `POST /merchant/subscription/initiate` with `{ "tier": "FREE" }`, when the server processes the request, then it returns HTTP 400 and no `MerchantSubscription` record is created.

**AC-05 (Initiate — ICA gate):** Given a `STORE_ADMIN` whose store has `icaAccepted = false` or null calls `POST /merchant/subscription/initiate`, when the server processes the request, then it returns HTTP 422 with `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }` and no `MerchantSubscription` record is created.

**AC-06 (Initiate — duplicate ACTIVE guard):** Given a `STORE_ADMIN` whose store already has an `ACTIVE` `MerchantSubscription` in the database calls `POST /merchant/subscription/initiate`, when the server processes the request, then it returns HTTP 409 with `{ "error": "SUBSCRIPTION_ALREADY_ACTIVE" }` and no new `MerchantSubscription` record is created.

**AC-07 (Initiate — 30-minute idempotency):** Given a `STORE_ADMIN` has an existing `PENDING_PAYMENT` `MerchantSubscription` created within the last 30 minutes and calls `POST /merchant/subscription/initiate` again for the same tier, when the server processes the request, then it returns HTTP 200 with the existing `mPaymentId` and no new `MerchantSubscription` record is created.

**AC-08 (Initiate — amount correctness):** Given `POST /merchant/subscription/initiate` is called with `tier = PREMIUM_1`, when the `MerchantSubscription` record is retrieved from the database, then `amountRands = 800` and the PayFast form parameter `recurring_amount` equals `800.00` (or the exact format PayFast requires). For `PREMIUM_2`, `amountRands = 3000` and `recurring_amount = 3000.00`.

**AC-09 (Checkout — PayFast redirect):** Given `SubscriptionCheckoutComponent` receives a 200 response from `POST /merchant/subscription/initiate`, when the component processes the response, then a form is submitted to PayFast's hosted page URL (`sandbox.payfast.co.za` on non-production environments) with all required PayFast parameters present in the form body.

**AC-10 (Checkout — HTTP 409 redirects to dashboard):** Given `SubscriptionCheckoutComponent` receives HTTP 409 from `POST /merchant/subscription/initiate`, when the component handles the response, then the app navigates to `/business/dashboard` without showing an error state.

**AC-11 (ITN — valid signature activates subscription):** Given `PayFastItnHandler` receives a POST to `/merchant/subscription/itn` with `payment_status = COMPLETE` and a valid PayFast signature, when the handler processes the request, then the `MerchantSubscription` record transitions from `PENDING_PAYMENT` to `ACTIVE`, `activatedDate` is set, `payFastToken` is populated, and `MerchantSubscriptionActivatedEvent` is published. The response is HTTP 200.

**AC-12 (ITN — invalid signature rejected):** Given `PayFastItnHandler` receives a POST with an invalid or missing `signature` field, when the handler processes the request, then the subscription record is not modified, a `SIGNATURE_VALIDATION_FAILED` log entry is written, and the response is HTTP 200 (to prevent PayFast retries on a permanently invalid request).

**AC-13 (ITN — replay prevention):** Given `PayFastItnHandler` receives a POST with `mPaymentId` and `payFastPaymentId` values that have already been processed to ACTIVE, when the handler processes the duplicate ITN, then the subscription record is not modified a second time and the response is HTTP 200.

**AC-14 (ITN — StoreProfile tier updated):** Given `MerchantSubscriptionActivatedEvent` is published for `storeId = X` with `tier = PREMIUM_1`, when the `@EventListener` in `StoreService` processes the event, then `StoreProfile` for store X has `subscriptionTier = PREMIUM_1` and `subscriptionTierSince` is a non-null timestamp.

**AC-15 (ITN — payFastToken not logged):** Given `PayFastItnHandler` processes any ITN (valid or invalid), when the handler runs, then no log line contains the value of the `payFastToken` or `token` fields from the ITN payload.

**AC-16 (Success page — async messaging):** Given a merchant is at `/business/subscription-success/:storeId`, when the component renders, then the text "will activate within a few minutes" (or equivalent phrasing per REQ-08) is visible, no Premium tier badge is displayed, and a "Go to Dashboard" button is present.

**AC-17 (Cancel page — store preserved as FREE):** Given a merchant is at `/business/subscription-cancel/:storeId` (having cancelled on PayFast's page), when the component renders, then the messaging indicates the store was created on Free tier, the "Go to Dashboard" button is present, and the store record in the database is unmodified (store still exists, subscriptionTier is FREE/null, no subscription record is in ACTIVE state).

**AC-18 (PremiumTierGuard — FREE tier blocked):** Given a merchant whose store has `subscriptionTier = FREE` or null navigates directly to `/business/subscription/:storeId`, when the route guard activates, then the merchant is redirected to `/business/dashboard` and `SubscriptionCheckoutComponent` does not render.

**AC-19 (Yoco — zero regression):** Given the `izinga-yoco-pay` module is deployed alongside the new `izinga-payfast` module, when any existing Yoco-backed customer checkout is executed (any order payment or tip), then the Yoco flow completes normally with no interference from any `izinga-payfast` class, bean, or event listener.

**AC-20 (Credentials — not in application.yml):** Given the `ijudi-api` codebase is inspected at the feature branch tip, when `application.yml`, `application-prod.yml`, and any other committed configuration file is reviewed, then PayFast `merchant_key` and `passphrase` values do not appear in any committed file. All credential references use Spring Boot property placeholders resolved from environment secrets.

---

## Marketing Trigger

**No** — this is payment infrastructure. The merchant-facing copy ("your Premium tier will activate") is factual, not promotional. A marketing trigger for the Premium tier launch (post-billing-engine go-live) should be raised with the Marketing Strategist once the full end-to-end flow is confirmed in production. Brief the Marketing Strategist at least 2 weeks before the planned campaign date.

---

## Definition of Done

All of the following must be true before TIER-BILLING-01 is considered complete and in production:

1. Security & Compliance has returned PASS or CONDITIONAL-PASS on SEC-TB01-01 through SEC-TB01-03 (Gate (a)).
2. `izinga-payfast` Maven module exists in `ijudi-api`, compiles cleanly, and is imported by `izinga-ordermanager` with no classpath conflicts.
3. `POST /merchant/subscription/initiate` returns signed PayFast form parameters for PREMIUM_1 and PREMIUM_2; correctly rejects FREE, missing ICA, and duplicate ACTIVE cases.
4. `POST /merchant/subscription/itn` validates PayFast signature, prevents replay, updates `MerchantSubscription` status, and fires `MerchantSubscriptionActivatedEvent`.
5. `StoreService.onMerchantSubscriptionActivated()` listener updates `StoreProfile.subscriptionTier` and `subscriptionTierSince` on event receipt.
6. `SubscriptionCheckoutComponent`, `SubscriptionSuccessComponent`, and `SubscriptionCancelComponent` are implemented; `PremiumTierGuard` is active on the subscription route.
7. `BusinessUpdateComponent` branches on tier after store creation — FREE goes to dashboard; Premium goes to subscription flow.
8. End-to-end sandbox test passes: Premium tier selection → store creation → `POST /merchant/subscription/initiate` → PayFast sandbox → ITN received → `MerchantSubscription` ACTIVE → `StoreProfile.subscriptionTier` updated.
9. `payFastToken` is confirmed absent from all application log output during end-to-end test.
10. PayFast `merchant_key` and `passphrase` are confirmed absent from all committed configuration files.
11. Full unscoped test suite is green in both `ijudi-api` and `izinga-onboarding` (no scoped or excluded test runs accepted).
12. AI Code Review PASS verdict in hand (Gate (d)).
13. Written PayFast subscription billing rate confirmation received and Lindani sign-off recorded (Gate (b)).
14. Release Manager confirms sandbox vs production environment gating before merge to `main` (Gate (e)).
15. iZinga Growth & Analytics briefed to report on subscription conversion rate (Premium tier selections → completed payments) and activation success rate 2 weeks after production launch.

---

## Open Questions for Lindani

**RESOLVED — no action required:**

- PayFast merchant account status: confirmed live, approved, Recurring Billing/Subscriptions active, Tokenization toggle available, T&Cs pre-accepted. Merchant ID: 16791971. No new account provisioning needed.
- Yoco separation: confirmed zero blast radius. Separate modules, endpoints, collections.
- Subscription amount constants: confirmed R800/month for PREMIUM_1, R3,000/month for PREMIUM_2 (VAT-exclusive), matching ONB-02 published pricing.

**Pending Lindani action:**

1. **PayFast rate confirmation** (Gate (b) — required before go-live): Action a written query to PayFast support asking for confirmation of the applicable transaction charge rate for subscription/recurring billing on merchant account 16791971. This is on the critical path to production — action promptly so it does not delay the release.

2. **BackOffice setup** (Gate (c) — required before end-to-end testing): Confirm Tokenization toggle is active; retrieve the merchant passphrase from the PayFast portal and provide it securely to engineering (not via chat — use a secrets manager or secure channel); provide sandbox merchant credentials (sandbox merchant_id, merchant_key, passphrase) for the test environment.

3. **Abandoned subscription handling** (RISK-08 — open decision): If a merchant completes store creation for a Premium tier and then closes their browser or navigates away before completing PayFast payment, their store exists on FREE tier and there is a `PENDING_PAYMENT` MerchantSubscription orphaned in the database. Phase 1 does not include a "resume payment" flow on the dashboard. Proposed default: do nothing in Phase 1 — the merchant can contact support or the `PENDING_PAYMENT` record expires after 24 hours (auto-expire logic in a follow-on brief). Confirm or amend before T-14 is implemented, as the `SubscriptionCancelComponent` copy is affected by this decision.

4. **Existing Premium-flagged stores** (out of scope for Phase 1 — requires decision): Any stores created during ONB-02 testing that have `subscriptionTier = PREMIUM_1` or `PREMIUM_2` set as a flag (without a corresponding `MerchantSubscription` record) will not be billed. A backfill or migration path is required. This is explicitly out of scope for TIER-BILLING-01 Phase 1 — but Lindani should be aware and confirm whether any live stores are in this state before go-live.

---

*Brief authored by iZinga Product Owner. Approved for Implementation as of 3 October 2026.*
*ADR-023 (iZinga Solution Architect, 3 October 2026) incorporated — all SA decisions binding.*
*Continuation of ONB-02 (feature/ONB-02-store-owner-onboarding-tiers). ONB-02 must be in `develop` before TIER-BILLING-01 feature branch is opened.*
*Gate (a) — Security & Compliance review — blocks T-07 (ITN handler) implementation start.*
*Gate (b) — PayFast rate confirmation — release gate; does not block engineering.*
*Gate (c) — Lindani BackOffice setup — blocks T-09 (integration test) only.*
*4 open questions pending Lindani action/confirmation (see Open Questions section).*
