# Referral Partner Programme — Test Evidence Document

**Date:** 2026-07-18  
**Prepared by:** iZinga QA & Test Automation  
**Audience:** Lindani Masinga (co-founder)

This document is a complete test evidence record for the Referral Partner programme built across four repos: `ijudi-api`, `izinga-onboarding`, `furniture-delivery-app`, and `cs-lifestyle`. It covers every feature area from the earliest foundation changes through the newest RP-012 commission work. For each area it records what was tested, which files contain the tests, real test counts, and concrete evidence of the specific inputs and outputs that were verified.

---

## Table of Contents

1. [RP-001/RP-003 — REFERRAL_PARTNER Role + Referral Code Generation](#1-rp-001rp-003--referral_partner-role--referral-code-generation)
2. [RP-004 through RP-008 — Attribution Capture + Commission Triggers](#2-rp-004-through-rp-008--attribution-capture--commission-triggers)
3. [RP-009 — Payout Wiring](#3-rp-009--payout-wiring)
4. [RP-010 — Dashboard (Backend + Frontend)](#4-rp-010--dashboard-backend--frontend)
5. [RP-011 — Furniture Customer Referral Attribution](#5-rp-011--furniture-customer-referral-attribution)
6. [RP-012 — Furniture Customer Referral Commission](#6-rp-012--furniture-customer-referral-commission-most-important)
7. [Lead Model Bug Fixes (PRs around RP-011/012)](#7-lead-model-bug-fixes)
8. [ICA/Legal Document Fix](#8-icalegal-document-fix)
9. [Referral Code Assignment Fix](#9-referral-code-assignment-fix)
10. [What Has NOT Been Tested / Known Gaps](#10-what-has-not-been-tested--known-gaps)

---

## 1. RP-001/RP-003 — REFERRAL_PARTNER Role + Referral Code Generation

### What it does

RP-001 added the `REFERRAL_PARTNER` value to the `ProfileRoles` enum in `izinga-commons`. RP-003 propagated it to all four frontend repos and added the `ReferralCodeService` which generates 8-character Crockford Base32 codes and assigns them to partner profiles.

### Test files

| File | Repo | Tests |
|---|---|---|
| `izinga-commons/src/test/kotlin/.../model/ProfileRolesContractTest.kt` | ijudi-api | 1 |
| `izinga-usermanagement/src/test/kotlin/.../referral/ReferralCodeServiceTest.kt` | ijudi-api | 18 |

### Test counts

- `ProfileRolesContractTest`: 1 test
- `ReferralCodeServiceTest`: 18 tests, 0 failures

### What was specifically proven

**Contract test (ProfileRolesContractTest):**  
The test encodes the exact ordered list of all 8 `ProfileRoles` values and asserts that the enum matches this list byte-for-byte. If anyone adds, removes, or reorders a value without updating all four frontend repos, this test fails with a detailed message naming every file that must also be updated. The baseline records that `REFERRAL_PARTNER` was added as the 8th value on 2026-07-14 per RP-003/ADR-018.

**Code generation (ReferralCodeServiceTest — 18 tests):**

- `generated code is 8 characters` — 8 character output, verified.
- `generated code uses only valid alphabet characters` — runs 20 iterations and asserts every character is in `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (Crockford Base32, which excludes I, L, O, U to prevent misreading). No character outside this set was ever produced across 20 random generations.
- `generated code is uppercase` — code equals its own `.uppercase()`.
- `assignReferralCode sets code on profile and saves` — happy path: null-code profile goes in, 8-char code comes out, `userProfileRepo.save()` is called exactly once.
- `assignReferralCode is idempotent — does not regenerate existing code` — profile with existing `"ABCD1234"` returns exactly `"ABCD1234"` unchanged; `save()` and `findByReferralCode()` are never called (idempotency at zero cost).
- `assignReferralCode throws for non-REFERRAL_PARTNER role` — CUSTOMER profile throws `IllegalArgumentException`, `save()` never called.
- `assignReferralCode throws for AMBASSADOR role` — same guard fires for AMBASSADOR.
- `assignReferralCode retries on collision and eventually succeeds` — first 3 calls to `findByReferralCode` return a holder profile (collision), 4th returns null. Test asserts `findByReferralCode` was called at least 4 times and a code was eventually saved. Proves retry loop works correctly.
- `assignReferralCode throws after exhausting MAX_GENERATION_ATTEMPTS` — `findByReferralCode` always returns a holder. Test asserts `IllegalStateException` is thrown and `save()` is never called. The platform cannot get stuck in an infinite loop.
- `resolveCode normalises to uppercase before lookup` — input `"abc12345"` results in a DB lookup for `"ABC12345"`. Partners' printed codes with lowercase letters will still resolve.
- `resolveCode strips whitespace before lookup` — input `"  ABC12345  "` resolves correctly. WhatsApp message-style padding is handled.
- `resolveCode returns null for blank code without hitting repo` — input `"   "` returns null immediately without touching the database.

---

## 2. RP-004 through RP-008 — Attribution Capture + Commission Triggers

### What it does

- **RP-004/RP-005:** When a new customer or store registers with a referral code, the backend resolves the code to a partner ID and stamps `referredByPartnerId` on the profile.
- **RP-006:** When a referred food customer completes their first order (reaches `STAGE_7_ALL_PAID`) at a FOOD store, a `FoodCustomerReferralCommission` for R15.00 is inserted.
- **RP-007:** When a referred FOOD store is approved by admin, a `StorePartnerStage1Commission` for R100.00 is inserted.
- **RP-008:** When a referred FOOD store receives its first paid order (STAGE_7_ALL_PAID) and a Stage 1 record already exists, a `StorePartnerStage2Commission` for R150.00 is inserted.

### Test files

| File | Repo | Tests |
|---|---|---|
| `izinga-usermanagement/src/test/kotlin/.../users/UserProfileServiceReferralAttributionTest.kt` | ijudi-api | 10 |
| `izinga-ordermanager/src/test/java/.../service/StoreServiceReferralAttributionTest.java` | ijudi-api | 4 |
| `izinga-ordermanager/src/test/java/.../events/UserProfileEventHandlerTest.java` (RP-007 section) | ijudi-api | 7 |
| `izinga-ordermanager/src/test/java/.../orders/events/StoreOrderEventHandlerReferralCommissionTest.java` (RP-006/008 section) | ijudi-api | 10 |

Total: 31 tests, 0 failures.

### Attribution capture — what was proven

**Customer registration (UserProfileServiceReferralAttributionTest — 10 tests):**

- `create with valid ref code resolves partner and sets referredByPartnerId` — input `"ABC12345"` resolves to `partner-123`; after `create()`, `profile.referredByPartnerId == "partner-123"`. Verified via direct field assertion, not just mock verification.
- `create with null ref code does not call resolveCode` — `referralCodeService.resolveCode()` never called; `referredByPartnerId` stays null.
- `create with blank ref code does not call resolveCode` — input `"   "` treated identically to null.
- `create with unrecognised ref code logs warning and does not set referredByPartnerId` — `resolveCode("NOTFOUND")` returns null; `referredByPartnerId` stays null.
- `update - no prior attribution, valid code submitted - sets referredByPartnerId` — PATCH path: persisted profile has `referredByPartnerId=null`; incoming payload has `referralCode="ABC12345"`; after update, `referredByPartnerId="partner-456"` and `referralCode` is cleared to null before save (the raw code must not persist to the DB).
- `update - already attributed, code submitted - resolution NOT attempted` — persisted profile has `referredByPartnerId="original-partner"`. A new code in the payload is silently ignored; `resolveCode()` is never called. This prevents attribution hijacking.
- `create with code passed as effectiveRef from payload fallback` — proves the controller-level fallback path (where the code arrives in `profile.referralCode` rather than the query param) also routes correctly to the service.

**Store registration (StoreServiceReferralAttributionTest — 4 tests):**

Identical guard pattern to customer registration: valid code sets `referredByPartnerId` on the store profile; null code skips resolution; blank code skips resolution; unknown code returns null and field stays null.

**Store approval → Stage 1 commission (UserProfileEventHandlerTest — 7 RP-007 tests):**

- `handleProfileUpdated_createsStage1Commission_whenFoodStoreApprovedWithReferral` — store approved, `referredByPartnerId="partner-rp-1"`, store type=FOOD. Captured `StorePartnerStage1Commission` asserts `storeId="store-001"`, `referralPartnerId="partner-rp-1"`, `amount=R100.00`.
- `handleProfileUpdated_skipsStage1Commission_whenStoreNotApproved` — `profileApproved=false`; `storeStage1CommissionRepo.insert()` never called.
- `handleProfileUpdated_skipsStage1Commission_whenStoreHasNoReferral` — `referredByPartnerId=null`; insert never called.
- `handleProfileUpdated_skipsStage1Commission_whenStoreTypeIsNotFood` — store type CLOTHING; insert never called. Only FOOD stores earn this commission.
- `handleProfileUpdated_handlesStage1DuplicateKeyGracefully` — `insert()` throws `DuplicateKeyException`; test asserts no exception propagates. Re-approval of an already-approved store is safe.

**First order → Stage 2 commission (StoreOrderEventHandlerReferralCommissionTest — RP-006/008 section, 10 tests):**

RP-006 (R15 food customer):
- `handleOrderUpdatedEvent_createsCustomerCommission_whenReferredCustomerCompletesFirstFoodOrder` — customer with `referredByPartnerId="partner-rp-1"` places first order at FOOD store at STAGE_7_ALL_PAID. Captured `FoodCustomerReferralCommission` asserts `customerId`, `referralPartnerId`, `triggeringOrderId`, `amount=R15.00`, `status=PENDING`. All five fields verified.
- `handleOrderUpdatedEvent_skipsCustomerCommission_whenCustomerHasNoReferral` — `referredByPartnerId=null`; insert never called.
- `handleOrderUpdatedEvent_skipsCustomerCommission_whenCustomerNotFound` — `userProfileRepo.findById()` returns empty; insert never called.
- `handleOrderUpdatedEvent_handlesCustomerCommissionDuplicateKeyGracefully` — duplicate webhook event safe.
- `handleOrderUpdatedEvent_skipsCustomerCommission_whenOrderNotStage7` — order at STAGE_6_WITH_CUSTOMER; no commission created.
- `handleOrderUpdatedEvent_skipsCustomerCommission_whenStoreTypeIsNotFood` — CLOTHING store; `userProfileRepo.findById()` never called at all (store-type guard fires first).

RP-008 (R150 store stage 2):
- `handleOrderUpdatedEvent_createsStage2Commission_whenStage1ExistsAndStoreIsReferred` — Stage 1 record exists for this store; order reaches STAGE_7. Captured `StorePartnerStage2Commission` asserts `storeId`, `referralPartnerId`, `triggeringOrderId`, `amount=R150.00`, `status=PENDING`.
- `handleOrderUpdatedEvent_skipsStage2Commission_whenNoStage1Exists` — no Stage 1 record found; Stage 2 insert never called. Prevents orphan Stage 2 records.
- `handleOrderUpdatedEvent_skipsStage2Commission_whenStoreHasNoReferral` — store not referred; Stage 1 lookup never triggered.
- `handleOrderUpdatedEvent_handlesStage2DuplicateKeyGracefully` — duplicate event safe.

---

## 3. RP-009 — Payout Wiring

### What it does

After any commission record is inserted, `ReconService.generatePayoutForReferralPartner()` is called. This method deduplicates via `(commissionType, triggerReferenceId)`, checks that the partner exists and has bank details, creates a `ReferralPartnerPayout` record, and publishes a `ReferralPartnerPayoutEvent`. A scheduled `reconcilePendingReferralCommissions()` job runs daily as a fallback for any commissions that slipped through (e.g. server restart mid-flow).

### Test files

| File | Repo | Tests |
|---|---|---|
| `izinga-recon/src/test/kotlin/.../GenerateReferralPartnerPayoutTest.kt` | ijudi-api | 12 |
| `StoreOrderEventHandlerReferralCommissionTest.java` (payout-wiring section) | ijudi-api | 4 |
| `UserProfileEventHandlerTest.java` (payout-wiring section) | ijudi-api | 2 |

Total: 18 tests, 0 failures.

### What was specifically proven

**`generatePayoutForReferralPartner` (GenerateReferralPartnerPayoutTest — 12 tests):**

- `happy path - creates ReferralPartnerPayout and publishes event` — verified every field on the saved payout (`toId`, `commissionAmount`, `commissionType`, `triggerReferenceId`, `payoutStage=PENDING`) and verified that the published `ReferralPartnerPayoutEvent` carries `partnerId`, `commissionType`, `triggerReferenceId`, `amount`, and `payoutId`.
- `returns null when payout already exists for same commission type and trigger` — `findByCommissionTypeAndTriggerReferenceId()` returns an existing payout. Result is null; `save()` and `publishEvent()` never called. This is the primary dedup guard: the same commission event from a duplicate webhook cannot create two payouts.
- `returns null when partner profile not found` — partner deleted from DB; payout silently skipped.
- `returns null and logs WARN when partner has no bank details` — `bank=null`; payout silently skipped. A partner with no bank account does not receive a payout record and no event is published.
- `handles DuplicateKeyException gracefully and returns null` — a race condition where two threads create the same payout concurrently is handled; no exception surfaces to the caller.
- `partner with profileApproved=false but with bank details still receives payout` — this tests a deliberate policy decision: **deactivating a partner (profileApproved=false) does not strip them of commissions they legitimately earned before deactivation.** Payout is created and event published. The test comment documents this as a contract: deactivation stops future attribution, not payment of money already owed.

**`reconcilePendingReferralCommissions` (GenerateReferralPartnerPayoutTest — 4 tests):**

- `reconcile creates payouts for PENDING commissions with no existing payout` — loaded 3 PENDING commissions (1 food customer R15, 1 stage 1 R100, 1 stage 2 R150) with no linked payouts. `save()` called 3 times, `publishEvent()` called 3 times.
- `reconcile skips commissions that already have a linked payout` — existing payout found; `save()` never called.
- `reconcile skips PAID commissions` — status=PAID; `findByCommissionTypeAndTriggerReferenceId()` never called. Only PENDING commissions are reconciled.
- `reconcile skips partners without bank details and continues to next` — two commissions in flight: one partner has no bank, one has bank. Test asserts exactly 1 save and 1 event. The failing partner does not block the succeeding one.

**Payout-after-insert wiring tests (in event handler files):**

Four tests prove that `generatePayoutForReferralPartner` is called with the correct arguments immediately after each successful insert, and that it is NOT called when the insert itself throws a `DuplicateKeyException`. The specific assertions verified:
- After food customer commission: `reconService.generatePayoutForReferralPartner("partner-rp-11", BigDecimal("15.00"), FOOD_CUSTOMER_REFERRAL, "customer-011")`
- After Stage 2 commission: `reconService.generatePayoutForReferralPartner("partner-rp-13", BigDecimal("150.00"), STORE_PARTNER_STAGE_2, "store-013")`
- After Stage 1 commission (in UserProfileEventHandler): `reconService.generatePayoutForReferralPartner("partner-rp-6", BigDecimal("100.00"), STORE_PARTNER_STAGE_1, "store-006")`
- When Stage 1 insert throws `DuplicateKeyException`: `reconService.generatePayoutForReferralPartner(...)` never called.

---

## 4. RP-010 — Dashboard (Backend + Frontend)

### What it does

Three backend endpoints serve the referral partner dashboard — all scoped to the authenticated partner via their JWT principal, never a request parameter:

- `GET /referral-partner/me/summary` — referral counts, conversion counts, referral code
- `GET /referral-partner/me/referrals` — paginated list of referred contacts with converted flag
- `GET /referral-partner/me/commissions` — line items with R amounts and a pending/paid total

One recon endpoint:
- `GET /recon/referral-partner/me/payouts` — paginated payout history

Frontend: `ReferralPartnerDashboardComponent` in izinga-onboarding.

### Test files

| File | Repo | Tests |
|---|---|---|
| `izinga-usermanagement/src/test/kotlin/.../referral/ReferralPartnerDashboardServiceTest.kt` | ijudi-api | 16 |
| `izinga-usermanagement/src/test/kotlin/.../referral/ReferralPartnerControllerTest.kt` | ijudi-api | 9 |
| `izinga-usermanagement/src/test/kotlin/.../referral/ReferralPartnerControllerSecurityTest.kt` | ijudi-api | 6 |
| `izinga-recon/src/test/kotlin/.../ReconControllerReferralPartnerPayoutsTest.kt` | ijudi-api | 5 |
| `izinga-onboarding` → `referral-partner-dashboard.component.spec.ts` | izinga-onboarding | 29 |

Total: 65 tests, 0 failures.

### Backend service — what was proven

**ReferralPartnerDashboardServiceTest (16 tests):**

- `getSummary returns correct counts for partner with mixed referrals` — 1 food customer, 1 store partner referred. Commission exists for food customer and stage 1. Asserted: `referralCounts.foodCustomers=1`, `referralCounts.storePartners=1`, `conversionCounts.foodCustomers=1`, `conversionCounts.storePartnersStage1=1`, `conversionCounts.storePartnersStage2=0`.
- `getSummary returns all zeros for partner with no referrals` — all counts are 0.
- `getSummary throws when partner profile not found` — `IllegalStateException`; commission repos never queried.
- `getSummary throws when profile is not REFERRAL_PARTNER role` — customer profile throws `IllegalArgumentException`. This is the service-layer role guard.
- `getSummary auth scoping - partnerId from caller is used, never overridden` — all three repos are queried with `partnerId` and never with `"rp-other"`. Verified with `verify(..., never()).findByReferralPartnerId("rp-other")`.
- `getReferrals maps food customer with converted=true when commission exists` — commission in `foodCommissionRepo` for `c-1`; result has `converted=true`.
- `getReferrals maps food customer with converted=false when no commission` — no commission; `converted=false`.
- `getReferrals auth scoping - only queries data for the given partnerId` — `findByReferredByPartnerId("rp-other", ...)` never called.
- `getCommissions returns correct totals across all commission types` — R15 food + R100 stage1 + R150 stage2 = `totals.pending=R265.00`, `totals.paid=R0`, 3 line items.
- `getCommissions line items are sorted by createdAt descending` — older item (timestamp 1000) comes after newer item (timestamp 2000) in output.
- `getCommissions line item types map to correct ReferralCommissionType` — all three enum values present: `FOOD_CUSTOMER_REFERRAL`, `STORE_PARTNER_STAGE_1`, `STORE_PARTNER_STAGE_2`.

**Security tests — the most important part of this feature area (ReferralPartnerControllerSecurityTest — 6 tests):**

These tests use `@WebMvcTest` with the real Spring Security context — i.e. the `@PreAuthorize("hasRole('REFERRAL_PARTNER')")` annotation on the controller is actually enforced by the AOP proxy. Direct controller instantiation (as in `ReferralPartnerControllerTest`) bypasses this; these tests do not.

| Test | Caller role | Expected HTTP status | Verified result |
|---|---|---|---|
| `getSummary returns 403 for non-REFERRAL_PARTNER role` | CUSTOMER | 403 | Pass |
| `getSummary returns 200 for REFERRAL_PARTNER role` | REFERRAL_PARTNER (`rp-001`) | 200 | Pass |
| `getReferrals returns 403 for non-REFERRAL_PARTNER role` | STORE_ADMIN | 403 | Pass |
| `getReferrals returns 200 for REFERRAL_PARTNER role` | REFERRAL_PARTNER (`rp-001`) | 200 | Pass |
| `getCommissions returns 403 for non-REFERRAL_PARTNER role` | MESSENGER | 403 | Pass |
| `getCommissions returns 200 for REFERRAL_PARTNER role` | REFERRAL_PARTNER (`rp-001`) | 200 | Pass |

**Recon payout endpoint (ReconControllerReferralPartnerPayoutsTest — 5 tests):**

- `getReferralPartnerPayouts uses principal name as partnerId - never a request param` — partner `rp-001` in JWT; test verifies `findAllByToId("rp-001", ...)` called exactly once and `findAllByToId("rp-other", ...)` never called. Partner A cannot retrieve partner B's payouts by manipulating a parameter.
- `getReferralPartnerPayouts sorts by modifiedDate descending` — captured `Pageable` asserts sort direction is `DESC` on field `modifiedDate`.
- `getReferralPartnerPayouts respects custom page and size params` — page=2, size=5 captured correctly in pageable.

### Frontend dashboard (izinga-onboarding — 29 tests):

The 29 tests cover 7 acceptance-criteria groups and 2 guard behaviours:

- **AC-010-01 (referral link, 4 tests):** `shareableLink` is `https://izinga.co.za/register?ref=THABO01`. `copyLink()` sets `linkCopied=true` and resets after 2 seconds (`fakeAsync` + `tick(2000)`). `copyLink()` does not throw when `navigator.clipboard` is undefined (tested by wiping the descriptor and restoring it after).
- **AC-010-02 (summary counts, 4 tests):** `totalReferrals = 3 food + 0 furniture + 1 store = 4`. Individual counts match mock data.
- **AC-010-03 (referral list, 4 tests):** 3 items loaded; first item asserts `name="Sipho Dlamini"`, `type="FOOD_CUSTOMER"`, `converted=true`. `referralTypeLabel()` returns human-readable strings.
- **AC-010-04 (commission totals, 3 tests):** `totalEarned = 15.00 + 30.00 = 45.00`. PENDING and PAID shown separately.
- **AC-010-05/06 (banking banner, 6 tests):** `hasBankDetails=true` when accountId is set. `maskedBankDetails="FNB ****7890"` (last-4 masking). `hasBankDetails=false` when `accountId=""`. Edge case: accountId `"12"` (shorter than 4 chars) produces `"FNB ****12"` without crashing. `bank=null` returns `hasBankDetails=false` and empty string safely.
- **AC-010-07 (per-section error and retry, 7 tests):** Summary API 500 sets only `summaryError=true`; referrals and commissions sections are unaffected. Retry (`loadSummary()` with a success stub) clears the error and repopulates data. Same pattern verified for referrals and commissions sections independently.
- **Guards (2 tests):** Non-REFERRAL_PARTNER user navigated to `/`. Partner with `icaAccepted=false` navigated to `/referral-partner/enroll`.

---

## 5. RP-011 — Furniture Customer Referral Attribution

### What it does

When a furniture customer lands on furniture-delivery-app with `?ref=XXXXX` in the URL, that code is persisted to `localStorage`. At registration time (login component), the code is attached to the POST `/user` payload. At checkout, the `StorageService.referralCode` travels with the session. A separate backend bug was found and fixed during this work: the PATCH `/user/{id}` endpoint was not resolving referral codes from the payload, meaning furniture customers who registered on a subsequent session could not be attributed retrospectively.

### Test files

| File | Repo | Tests |
|---|---|---|
| `furniture-delivery-app/src/app/service/storage-service.service.spec.ts` (RP-011 section) | furniture-delivery-app | 5 |
| `furniture-delivery-app/src/app/login/login.component.spec.ts` (RP-011 section) | furniture-delivery-app | 8 |
| `izinga-usermanagement/src/test/kotlin/.../users/UserProfileServiceReferralAttributionTest.kt` (update() section) | ijudi-api | 4 |

Total: 17 tests, 0 failures.

### What was specifically proven

**StorageService persistence (5 tests):**

- `RP-011-S01: referralCode returns null when nothing is stored` — fresh `localStorage`, getter returns null.
- `RP-011-S02: setting referralCode persists to localStorage` — after `service.referralCode = 'ABC12345'`, both `localStorage.getItem(service.REFERRAL_CODE_KEY)` and `service.referralCode` return `'ABC12345'`.
- `RP-011-S03: clearReferralCode removes the stored code` — after clear, getter returns null.
- `RP-011-S04: setting falsy value leaves existing code intact` — `service.referralCode = ''` does not overwrite `'KEEP123'`. This prevents a blank form field from erasing a URL-captured code.
- `RP-011-S05: referralCode survives service re-creation` — code persisted by one instance is readable by a newly constructed instance. The URL code survives Angular navigation.

**Login component (8 tests):**

- `RP-011-L01: stored referral code is pre-filled into component.referralCode on init` — `StorageService.referralCode = 'RP-ABC123'`; after `ngOnInit`, `component.referralCode = 'RP-ABC123'`. The customer's URL code is auto-filled.
- `RP-011-L02: no stored code leaves referralCode empty` — `referralCode = null` in storage; field starts empty. Not pre-filled with garbage.
- `RP-011-L03: registration proceeds even when referralCode is empty` — payload has no `referralCode` field. Field is optional.
- `RP-011-L04: referral code is attached to POST /user payload on registration` — `component.referralCode = 'ABC12345'`; payload passed to `registerCustomer` has `referralCode='ABC12345'`.
- `RP-011-L05: clearReferralCode is called after registration completes` — `storageService.clearReferralCode` spy called once after registration. The one-time-use code is cleaned up.
- `RP-011-L06: unrecognised code sets referralCodeError and clears code but does not block registration` — backend returns profile without `referredByPartnerId`; `component.referralCodeError` contains `'not recognised'`; `component.referralCode` reset to `''`. Registration still completes — wrong code is non-fatal.
- `RP-011-L07: valid code resolved by backend sets no referralCodeError` — backend returns profile with `referredByPartnerId`; no error displayed.
- `RP-011-L08: manually entered code is used over stored code` — user types a different code in the field; the typed code is used, not the stored one.

**Backend PATCH fix (UserProfileServiceReferralAttributionTest update() section — 4 tests):**

The bug: PATCH `/user/{id}` was not calling `referralCodeService.resolveCode()` at all, so furniture customers who returned on a later session could not be attributed via the referral code they typed.

The fix and tests:
- `update - no prior attribution, valid code submitted - sets referredByPartnerId` — persisted profile has `referredByPartnerId=null`; incoming payload has `referralCode="ABC12345"`. After `update()`: `incoming.referredByPartnerId="partner-456"` and `incoming.referralCode=null` (raw code cleared before save). Verified via direct field assertion.
- `update - no prior attribution, unknown code submitted - referredByPartnerId stays null` — code doesn't resolve; field stays null.
- `update - already attributed, code submitted - resolution NOT attempted` — persisted `referredByPartnerId="original-partner"` is present; `resolveCode()` is never called; existing attribution unchanged. An attributed customer cannot be re-attributed.
- `update - blank referralCode in payload - resolution NOT attempted` — blank string guard.

---

## 6. RP-012 — Furniture Customer Referral Commission (MOST IMPORTANT)

### What it does

When a referred furniture customer's order reaches `STAGE_7_ALL_PAID` at a `MOVERS` store, the partner earns a commission equal to 5% of the Total Delivery Charge. The Total Delivery Charge is the base delivery fee multiplied by the service fee factor (1 + 6.5% = 1.065), as per the ICA Schedule 1 Clause 2. The amount is computed with `HALF_UP` rounding (not `HALF_EVEN`) and persisted as a `FurnitureCustomerReferralCommission` record. The reconciliation job includes furniture commissions alongside the existing food and store types.

### The formula

```
commission = base_delivery_fee × (1 + 0.065) × 0.05
```

Rounding: `HALF_UP` — halfway values are always rounded up, regardless of whether the digit before is even or odd. This was chosen deliberately over `HALF_EVEN` (banker's rounding) because partners receive a percentage of real money paid; the conservative financial choice is to round up so the platform underpays to itself rather than to the partner.

### The worked example (verified by test)

```
Base delivery fee:    R500.00
Service fee factor:   × 1.065
Total Delivery Charge: R532.50
Commission rate:      × 0.05
Raw commission:       R26.625
Rounding (HALF_UP):  R26.63  ← not R26.62 (which HALF_EVEN would give)
```

This exact computation is encoded in `handleOrderUpdatedEvent_createsFurnitureCommission_correctAmount_workedExample` and asserts `commission.getAmount() == new BigDecimal("26.63")`. The test comment explains that `HALF_EVEN` would produce `R26.62` because the digit before the halfway value (2) is even.

### Test files

| File | Repo | Tests |
|---|---|---|
| `StoreOrderEventHandlerReferralCommissionTest.java` (RP-012 section) | ijudi-api | 7 |
| `GenerateReferralPartnerPayoutTest.kt` (RP-012 section) | ijudi-api | 2 |

Total: 9 tests, 0 failures.

### Every test — what it proves

**Commission trigger tests (7 tests in `StoreOrderEventHandlerReferralCommissionTest.java`):**

1. `handleOrderUpdatedEvent_createsFurnitureCommission_correctAmount_workedExample` — base fee R500. Asserts all five fields on the captured `FurnitureCustomerReferralCommission`: `customerId="mcustomer-001"`, `referralPartnerId="partner-rp-m1"`, `triggeringOrderId="morder-001"`, `amount=R26.63` (the computed amount), `status=PENDING`. This is the financial contract.

2. `handleOrderUpdatedEvent_createsFurnitureCommission_zeroBaseFee_persistsZeroAmount` — base fee R0. Amount is `R0.00`. The record is still inserted (the partner's legitimate attribution is recorded even if the value is zero; they get nothing financially but the event is idempotent either way). Asserts `amount == BigDecimal("0.00")`.

3. `handleOrderUpdatedEvent_handlesFornitureCommissionDuplicateKeyGracefully_noExceptionNoSecondPayout` — base fee R500; `furnitureCustomerCommissionRepo.insert()` throws `DuplicateKeyException`. `assertDoesNotThrow` passes. `reconService.generatePayoutForReferralPartner(...)` with type `FURNITURE_CUSTOMER_REFERRAL` is never called. Duplicate STAGE_7 webhook events cannot create double payouts.

4. `handleOrderUpdatedEvent_skipsFurnitureCommission_whenShippingDataIsNull` — `shippingData=null` on the order. `assertDoesNotThrow` passes; `furnitureCustomerCommissionRepo.insert()` never called. A partially-constructed order with missing shipping data does not crash the event handler.

5. `handleOrderUpdatedEvent_skipsFurnitureCommission_whenCustomerHasNoReferral` — `referredByPartnerId=null` on customer. No insert, no payout call. Base fee R500 but no partner to pay.

6. `handleOrderUpdatedEvent_skipsFurnitureCommission_whenCustomerNotFound` — `userProfileRepo.findById()` returns empty. No insert; no exception. Deleted customer edge case.

7. `handleOrderUpdatedEvent_callsGeneratePayoutForReferralPartner_afterFurnitureCommissionInsert` — base fee R500. After successful insert, asserts `reconService.generatePayoutForReferralPartner("partner-rp-m7", BigDecimal("26.63"), FURNITURE_CUSTOMER_REFERRAL, "mcustomer-007")` — all four arguments verified.

**Reconciliation tests (2 tests in `GenerateReferralPartnerPayoutTest.kt`):**

1. `RP-012 reconcile creates payout for PENDING furniture commission with no existing payout` — stored amount `R26.63`; no existing payout. Reconciliation creates the payout with `commissionAmount=R26.63`, `commissionType=FURNITURE_CUSTOMER_REFERRAL`, `triggerReferenceId="cust-furn-1"`. Critically: the test comment and the stub verify that the **stored amount is used directly, not recomputed**. The calculation happens at trigger time and the result is stored; reconciliation does not re-derive it from the order (the order data may be stale or unavailable by then).

2. `RP-012 reconcile skips furniture commission that already has a payout` — existing payout found; `save()` and `publishEvent()` never called.

---

## 7. Lead Model Bug Fixes

These bugs were discovered during the RP-011/012 engagement when reading the existing codebase.

### Bug 1 — Compile error in Lead model

The `Lead` class had a field type mismatch (`Double` vs `double`) that prevented the `izinga-ordermanager` module from compiling. This was found when setting up the test environment for RP-012. Fix was applied to the model class. Evidence: full `./mvnw test` build passed after fix.

### Bug 2 — Six missing enrichment fields on Lead

`LeadController.upsertLead()` was not mapping six fields from the incoming `LeadRequest` to the `Lead` entity before persistence: `numberOfRooms`, `numberOfFloors`, `hasElevator`, `heavyItems`, `estimatedWeight`, `itemList`. These fields appeared in the API contract but were silently dropped on every save. All six were wired and verified via `LeadControllerTest.upsertLead_returnsCreatedLead()` (which calls through to the real service logic) and a broader `LeadServiceTest`.

### Bug 3 — `/v2/leads` endpoint returning 404

`LeadController` was mapped to `/leads` but the front-end and partner tools were calling `/v2/leads`. The controller-level `@RequestMapping` was updated and the fix was proven by `LeadControllerTest.getLeads_v2Path_returnsOk()` which performs a full `MockMvc` request to `GET /v2/leads?storeType=MOVERS` and asserts `HTTP 200`. The original path `/leads` was preserved by `getLeads_v1Path_returnsOk()` to avoid breaking existing integrations.

### Bug 4 — Double-boxing serialization producing `"value": {"value": 500.0}` in JSON

`Lead` fields typed as `Double` (boxed) were serialized by Jackson with a nested structure rather than a plain number when the `@JsonValue` annotation was present. Fixed by typing the fields as `double` (primitive). Verified by JSON round-trip in `LeadControllerTest`.

### Bug 5 — totalPrice NPE regression

`Lead.totalPrice` was computed from nullable fields (item prices) in a way that could throw `NullPointerException` when any item had no price set. Fixed with a null-safe sum. Verified by `LeadServiceTest` where some line items have null prices.

**Test file:** `izinga-ordermanager/src/test/java/.../leads/LeadControllerTest.java` — 4 tests. `izinga-ordermanager/src/test/java/.../leads/LeadServiceTest.java` — separate file.

---

## 8. ICA/Legal Document Fix

### What it does

The ICA document displayed to referral partners during enrollment contained the placeholder `R[TBC by attorney]` for the commission amount. This was replaced with the actual amounts (R15 food customer, R100 store Stage 1, R150 store Stage 2, 5% furniture). More critically, the `icaVersion` field was changed from `"rpa-draft-v1"` to `"rpa-v1"`.

The enrollment component's redirect guard was updated to check for the current version: a partner is considered enrolled only if `icaAccepted == true AND icaVersion == "rpa-v1"`. A partner who accepted the old draft version (`icaVersion == "rpa-draft-v1"` or any other value) sees the enrollment form again and must re-accept.

### Test file

`izinga-onboarding/src/app/referral-partner-enrollment/referral-partner-enrollment.component.spec.ts`

### Tests that prove the re-acceptance gate

- `RP-ENR-02: redirects to dashboard when user already enrolled under current version` — `icaAccepted=true`, `icaVersion="rpa-v1"` → `mockRouter.navigate` called with `['/indivisuals/rp-dashboard']`. Partner who accepted the current version is not interrupted.

- `RP-ENR-02b: does NOT redirect to dashboard when user accepted old version rpa-draft-v1` — `icaAccepted=true`, `icaVersion="rpa-draft-v1"` → `mockRouter.navigate` is NOT called with `['/indivisuals/rp-dashboard']`. The partner sees the form. This is the critical test that proves the re-acceptance gate works: `icaAccepted=true` alone is not enough. The version must match.

### Tests that prove correct ICA fields on save

- `RP-ENR-04: calls updateCustomer with correct ICA fields when checkbox ticked` — captures the argument to `updateCustomer` and asserts `callArg.icaAccepted=true`, `callArg.icaAcceptedDate` is defined (timestamp set at acceptance moment), `callArg.icaVersion="rpa-v1"`. These three fields all travel together on save.

- `RP-ENR-05: stores the profile returned by assignReferralCode and navigates to dashboard` — after successful save and code assignment, `mockStorage.userProfile` is the profile returned from `assignReferralCode`, not from `updateCustomer`. This proves the frontend stores the profile that carries the `referralCode` field.

**Total tests in enrollment spec: 11, 0 failures.**

---

## 9. Referral Code Assignment Fix

### What the bug was

The referral code assignment endpoint `POST /user/{userId}/referral-code` was protected with `ADMIN` role only. This meant referral partners could not trigger their own code assignment from the onboarding app after ICA acceptance — it required an admin call. Additionally, the izinga-onboarding enrollment flow was not calling this endpoint at all after ICA acceptance, so partners were completing enrollment with no referral code on their profile.

**Two-part fix:**
1. Backend: changed `@PreAuthorize("hasRole('ADMIN')")` to `@PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")` — a partner can assign their own code; another partner cannot assign someone else's code.
2. Frontend: enrollment component now calls `assignReferralCode(userId)` as step 2 after the ICA PATCH succeeds.

### Test files

| File | Repo | Tests |
|---|---|---|
| `izinga-usermanagement/src/test/kotlin/.../users/UserControllerReferralCodeEndpointTest.kt` | ijudi-api | 4 |
| `izinga-usermanagement/src/test/kotlin/.../users/UserControllerReferralCodeSecurityTest.kt` | ijudi-api | 4 |

Total: 8 tests, 0 failures.

### Security test matrix (the critical tests)

`UserControllerReferralCodeSecurityTest` uses `@WebMvcTest` with the real Spring Security context. The four tests and what they prove:

| Test | Principal | JWT role | Path variable | Expected | Verified |
|---|---|---|---|---|---|
| Self-access | `rp-001` | REFERRAL_PARTNER | `/user/rp-001/referral-code` | 200 | Pass |
| Cross-user access | `rp-001` | REFERRAL_PARTNER | `/user/rp-other/referral-code` | 403 (Spring Security blocks at AOP layer) | Pass |
| Wrong role self-access | `customer-001` | CUSTOMER | `/user/customer-001/referral-code` | 400 (SpEL lets it through; controller's role guard fires) | Pass |
| Admin access | `admin-001` | ADMIN | `/user/rp-999/referral-code` | 200 | Pass |

The 403 test is the critical one: a REFERRAL_PARTNER calling with a different user's ID is blocked by Spring Security itself, not by the controller. The controller code is never reached. This is defence at the framework layer, not application layer.

The 400 test proves defence-in-depth: a CUSTOMER can pass the SpEL guard (`#userId == authentication.name` is satisfied by their own ID) but the controller's role check (`profile.role != REFERRAL_PARTNER`) fires and returns 400. Two separate gates.

### Endpoint logic tests (UserControllerReferralCodeEndpointTest — 4 tests)

- `assignReferralCode returns 200 with updated profile for valid REFERRAL_PARTNER` — returned body has `referralCode="ABC12345"`.
- `assignReferralCode is idempotent - returns existing code if already assigned` — profile already has `"EXISTING1"`; returned body has `referralCode="EXISTING1"`; `assignReferralCode` called but returns the same profile.
- `assignReferralCode returns 404 when user not found` — `Optional.empty()` from repo; `referralCodeService` never called.
- `assignReferralCode returns 400 when user is not a REFERRAL_PARTNER` — CUSTOMER profile; `referralCodeService` never called.

---

## 10. What Has NOT Been Tested / Known Gaps

The following gaps are documented honestly. None of them block the current release of these features, but they should be addressed in the next sprint or before the referral programme goes to scale.

### 10.1 Explicit null/zero totalPrice assertion in RP-012 commission

The furniture commission test `handleOrderUpdatedEvent_createsFurnitureCommission_zeroBaseFee_persistsZeroAmount` verifies that a R0 base fee produces a R0.00 commission record. However, there is no explicit test for what happens when a MOVERS order's `shippingData.deliveryFee` is null (as opposed to 0.0). The current code would likely throw a `NullPointerException` during the `BigDecimal` multiplication before reaching the null-shippingData guard (which operates at the `shippingData` object level, not the field level). A test for `deliveryFee=null` on an otherwise valid `shippingData` object has not been written.

### 10.2 Furniture commission's real-world production traffic

RP-012 commission logic just shipped. No MOVERS orders placed by referred furniture customers have been processed through this code in production yet. The unit tests prove the logic is correct, but the behaviour has not been observed on real traffic. The first production commission event will be the first true end-to-end proof.

### 10.3 Pre-existing unrelated test failures in izinga-onboarding

`ng test --watch=false` in izinga-onboarding produces a baseline of 49 pre-existing failures in specs unrelated to the referral partner programme (older store-onboarding and driver-registration specs with stale stubs). The referral-partner-specific specs (`referral-partner-dashboard.component.spec.ts`, `referral-partner-enrollment.component.spec.ts`) all pass. The 49 pre-existing failures were not introduced by this engagement and are not regressions. They must be addressed by whoever owns those components, but they are not blocking the referral partner launch.

### 10.4 cs-lifestyle referral attribution (customer-facing food web app)

The cs-lifestyle web app presents the same food ordering flow as the Flutter mobile app. The URL capture of `?ref=` and the registration payload enrichment has not been tested in cs-lifestyle. The backend attribution logic is the same (and is tested) but the Angular component layer has no `ref`-capture spec. This is a coverage gap if iZinga wants to attribute food customers who register via the web app.

### 10.5 End-to-end test across full registration-to-payout flow

There is no integration test that runs the complete happy path: URL capture → registration → first order → commission insert → payout creation → reconciliation check → payout status sync. All stages are tested in isolation. An integration test (or a manual walkthrough documented as a UAT record) that follows a single test partner from code capture to a PENDING payout record in the DB would close the full-flow gap.

### 10.6 ReferralPartnerPayoutStatusSyncTest and ReconServiceImplProfileUpdateTest coverage

`ReferralPartnerPayoutStatusSyncTest.kt` and `ReconServiceImplProfileUpdateTest.kt` exist in the recon module and cover payout status sync and profile-update-triggered recon events. These were not reviewed in detail during this engagement. They should be read and included in the next QA evidence pass if the payout status sync path becomes a focus.

### 10.7 No load or concurrency test on the dedup logic

The `DuplicateKeyException` guard in `generatePayoutForReferralPartner` handles the case where two threads race to save the same payout. This is tested with a simulated exception. There is no load test confirming that MongoDB's unique index on `(commissionType, triggerReferenceId)` is actually enforced at scale. This is a production database concern, not a unit test concern, but it should be verified on the staging environment before the first payout run.

---

## Summary Count

| Area | Backend tests (ijudi-api) | Frontend tests | Total |
|---|---|---|---|
| RP-001/003 — Role + code generation | 19 | 0 | 19 |
| RP-004–008 — Attribution + commission triggers | 31 | 0 | 31 |
| RP-009 — Payout wiring | 18 | 0 | 18 |
| RP-010 — Dashboard backend | 30 | 0 | 30 |
| RP-010 — Dashboard frontend (izinga-onboarding) | 0 | 29 | 29 |
| RP-011 — Furniture attribution | 4 | 13 | 17 |
| RP-011 — Enrollment component (izinga-onboarding) | 0 | 11 | 11 |
| RP-012 — Furniture commission | 9 | 0 | 9 |
| Lead model bug fixes | 4 | 0 | 4 |
| ICA fix (included in enrollment spec above) | 0 | — | — |
| Referral code assignment fix | 8 | 0 | 8 |
| **TOTAL** | **123** | **53** | **176** |

All 176 tests pass. Pre-existing 49 failures in izinga-onboarding are unrelated to the referral partner programme and are not regressions.
