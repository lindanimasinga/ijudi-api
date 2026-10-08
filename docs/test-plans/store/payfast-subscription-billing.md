---
userType: store
Feature: PayFast subscription billing — tier selection, checkout, ITN activation, token refresh
App: izinga-onboarding (Angular), ijudi-api (Spring Boot — izinga-payfast module)
Last verified: 2026-10-08 | Gate 1 automated | BLOCKED (101 failures in izinga-onboarding; ijudi-api PASS)
---

## Scope

Covers the full PayFast subscription checkout flow initiated by a new store owner after completing profile creation:
- SubscriptionTier enum and pricing constants in izinga-commons
- PayFastCheckoutService: creates MerchantSubscription, builds signed PayFast params, returns existing PENDING_PAYMENT record
- PayFastSignatureUtil: HMAC-MD5 signature generation and validation
- PayFastValidateClientImpl: server-side ITN validation against PayFast API
- PayFastItnHandler: processes COMPLETE/FAILED ITN, activates or fails MerchantSubscription
- TierSelectionComponent (izinga-onboarding): lets a new STORE_ADMIN pick a tier; reads stored selection on edit path
- BusinessUpdateComponent token refresh (izinga-onboarding): calls refreshIdToken after new store creation, navigates to subscription
- UserUpdateComponent tier-select routing gate (izinga-onboarding): routes new STORE_ADMIN (no storeId, no selectedTier) to tier-select after profile update

Out of scope: actual PayFast payment page, refunds, subscription renewal (recurring billing), multi-store owners, store deletion.

Cross-link: none — single userType flow.

## Repos affected

| Repo | Work |
|---|---|
| ijudi-api / izinga-payfast | New module: checkout service, signature util, validate client, ITN handler |
| ijudi-api / izinga-commons | SubscriptionTier enum, SubscriptionTierContract test |
| ijudi-api / izinga-ordermanager | StoreServiceSubscriptionEventListener (listens for activation event) |
| izinga-onboarding | TierSelectionComponent, BusinessUpdateComponent (token refresh + ICA stamping), UserUpdateComponent (routing gate) |

## Known-good test fixtures

From the 2026-10-08 Gate 1 run:

| Fixture | Value | Notes |
|---|---|---|
| Merchant ID (test) | 16791971 | Used in PayFastItnHandlerTest; MERCHANT_ID_MISMATCH test uses 99999999 |
| Tier PREMIUM_1 amount | R800.00 | PayFastCheckoutServiceTest happy path |
| Tier PREMIUM_2 amount | R3000.00 | PayFastCheckoutServiceTest multi-tier |
| Idempotent PENDING_PAYMENT key | `existing-pending-<timestamp>` | Reuse test: service returns existing record |
| Concurrent ITN mPaymentId | `sub-concurrent` | Idempotent skip test |
| Valid mPaymentId | `sub-001` | Standard activation test |

## Test cases

### izinga-payfast (all PASSING on 2026-10-08)

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| PF-01 | Create checkout for new store — happy path | Unit | storeId=store-1, tier=PREMIUM_1 | MerchantSubscription created PENDING_PAYMENT, signed params returned | main path |
| PF-02 | Create checkout returns existing PENDING_PAYMENT | Unit | existing PENDING_PAYMENT record in DB | returns same mPaymentId without creating a new record | idempotent |
| PF-03 | ITN COMPLETE activates MerchantSubscription | Unit | ITN with payment_status=COMPLETE, valid sig, valid pf_payment_id | MerchantSubscription status → ACTIVATED, event published | activation |
| PF-04 | ITN FAILED marks MerchantSubscription PAYMENT_FAILED | Unit | ITN with payment_status=FAILED | MerchantSubscription status → PAYMENT_FAILED | failure |
| PF-05 | ITN missing m_payment_id — rejected | Unit | ITN without m_payment_id | rejected, no state change | null guard |
| PF-06 | ITN missing pf_payment_id — rejected | Unit | ITN without pf_payment_id | rejected, no state change | null guard |
| PF-07 | Signature mismatch rejects ITN | Unit | ITN with wrong signature | rejected, no state change | security |
| PF-08 | Merchant ID mismatch rejects ITN | Unit | ITN with wrong merchant_id (99999999) | rejected, no state change | security |
| PF-09 | Concurrent ITN is idempotent (already ACTIVATED) | Unit | second ITN for sub-concurrent | idempotent skip, no double-activation | concurrency |
| PF-10 | PayFast validate client — null response | Unit | mock returns null | treated as INVALID | null guard |
| PF-11 | PayFast validate client — INVALID response | Unit | mock returns "INVALID" | treated as INVALID | error path |
| PF-12 | Signature generation is deterministic | Unit | fixed params + passphrase | fixed HMAC-MD5 output | main path |
| PF-13 | Signature validation passes with correct sig | Unit | correct sig | validateSignature returns true | main path |
| PF-14 | Signature validation fails with wrong sig | Unit | wrong sig | validateSignature returns false | security |

### izinga-onboarding TierSelectionComponent (ALL FAILING — introduced by this branch)

| ID | Description | Type | Expected | Actual | Status |
|---|---|---|---|---|---|
| TIER-UI-01 | Pre-select PREMIUM_1 from storageService on edit path | Unit | selectedTier = 'PREMIUM_1' | selectedTier = 'FREE' | FAIL |
| TIER-UI-02 | Pre-select PREMIUM_2 from storageService on edit path | Unit | selectedTier = 'PREMIUM_2' | selectedTier = 'FREE' | FAIL |
| TIER-UI-03 | tier-card--selected on Tier 1 when PREMIUM_1 | Unit | class applied | not applied | FAIL |
| TIER-UI-04 | tier-card--selected on Tier 2 when PREMIUM_2 | Unit | class applied | not applied | FAIL |
| TIER-UI-05 | tier-card--selected moves from Free to Tier 1 after chooseTier(PREMIUM_1) | Unit | class on PREMIUM_1 | class stays on FREE | FAIL |

Root cause: `TierSelectionComponent.ngOnInit()` does not read `storageService.selectedTier` on the edit path.

### izinga-onboarding BusinessUpdateComponent TB-TOKEN tests (ALL FAILING — introduced by this branch)

| ID | Description | Failure message | Status |
|---|---|---|---|
| TB-TOKEN-01 | refreshIdToken called after new store creation | `TypeError: this.izingaOrderManagementService.getBankConfigs is not a function` | FAIL |
| TB-TOKEN-02 | refreshIdToken NOT called when updating existing store | same root cause — test bed crashes in ngOnInit | FAIL |
| TB-TOKEN-03 | navigation to subscription deferred until refreshIdToken completes | same root cause | FAIL |

Root cause: Test bed mock for `IzingaOrderManagementService` does not include `getBankConfigs()`. This crashes `BusinessUpdateComponent.ngOnInit()` and prevents ALL BusinessUpdateComponent tests from running.

### izinga-onboarding UserUpdateComponent tier routing gate (ALL FAILING — introduced by this branch)

| ID | Description | Failure message | Status |
|---|---|---|---|
| TC-UPD-TIER-01 | STORE_ADMIN with no storeId and no selectedTier routes to tier-select | `navigate(['../tier-select', 'sa-new']) was never called` | FAIL |
| TC-UPD-TIER-02 | STORE_ADMIN with existing storeId routes to info (no regression) | same | FAIL |
| TC-UPD-TIER-03 | STORE_ADMIN with selectedTier in session but no storeId routes to info | same | FAIL |

Root cause: `UserUpdateComponent.updateCustomer()` does not implement the tier-select routing gate. The routing logic was written in the spec but not in the component.

## Integration test scenarios

| Scenario | Steps | Status |
|---|---|---|
| New store owner full flow | Register profile → tier-select → business-update → PayFast checkout → ITN callback → store activated | Not runnable (blocking failures in all 3 Angular components) |
| Existing store owner update | Edit profile → business-update → save → stays on info | Blocked by BusinessUpdateComponent mock failure |

## Regression checks

- [ ] PayFast ITN handler does not affect Yoco payment flow (separate modules)
- [ ] Ambassador referral commission is not disrupted by subscription activation event
- [ ] StoreServiceSubscriptionEventListener fires correctly on MerchantSubscriptionActivatedEvent
- [ ] Existing store owners with no subscription tier are not locked out
- [ ] TermsConditionsComponent ICA acceptance still works for MESSENGER and AMBASSADOR roles

## Known gaps / Known defects

| ID | Severity | Location | Description | Date found |
|---|---|---|---|---|
| DEF-TIER-01 | Blocker | izinga-onboarding / TierSelectionComponent.ngOnInit() | Does not read storageService.selectedTier on edit path — selectedTier always initialises to FREE | 2026-10-08 |
| DEF-TIER-02 | Blocker | izinga-onboarding / BusinessUpdateComponent spec | Test bed mock for IzingaOrderManagementService missing getBankConfigs() — crashes ngOnInit and kills all 51 BusinessUpdateComponent tests | 2026-10-08 |
| DEF-TIER-03 | Blocker | izinga-onboarding / UserUpdateComponent.updateCustomer() | Tier-select routing gate not implemented — navigate to ../tier-select never called for new STORE_ADMIN with no storeId | 2026-10-08 |
| DEF-TIER-04 | Blocker | izinga-onboarding / TermsConditionsComponent | isAmbassador, isDriver, isStoreAdmin all return false — ICA role checks broken; 33 test cases fail | 2026-10-08 |

Note: DEF-TIER-04 affects ICA acceptance tests that are unrelated to TIER-BILLING-01. These tests were likely brought into the feature branch from the ICA feature branches. They must still pass before Gate 1 can be awarded.

## Validation commands

```bash
# ijudi-api (full reactor — mandatory, no -pl):
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
./mvnw test

# izinga-onboarding (full suite — mandatory):
cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding
ng test --watch=false --code-coverage
```
