---
userType: store
Feature: EWALLET bank validation carve-out + STORE-BANK-01 fallback fix (POST /user, PATCH /user/{id}, POST /store)
App: ijudi-api (Spring Boot — izinga-usermanagement + izinga-ordermanager modules)
Last verified: 2026-10-08 | Gate 1 automated | PASS
---

## Scope

Covers the two commits on `bugfix/ewallet-bank-validation` (merged at `c0f2218`):

1. **EWALLET validation carve-out** (`a089a79`) — `POST /user` and `PATCH /user/{id}` previously required `bank.accountId`, `bank.name`, and `bank.branchCode` unconditionally. For `BankAccType.EWALLET` (identified by phone number only) these fields are not applicable. The fix adds an EWALLET branch to `UserProfileService.validateBankForProfile()` that requires only `phone` + `type`.

2. **STORE-BANK-01 fallback fix** (`198b61c`) — `StoreService.create()` previously fell back to the user's personal bank whenever the submitted store bank had a blank `accountId`. Because EWALLET banks never carry an `accountId`, any store that submitted its own EWALLET bank had it silently overwritten by the user's personal bank. The fix introduces `hasMeaningfulBank` — an EWALLET bank with a non-blank `phone` is treated as meaningful and the fallback is suppressed.

**Out of scope:**
- PATCH /store (bank updates on existing stores — not part of this fix)
- Payment processing, payout routing, or Yoco flows
- Frontend (izinga-onboarding, furniture-delivery-app, cs-lifestyle) — no Angular changes in this fix

## Repos affected

| Repo | Module | Files changed |
|---|---|---|
| ijudi-api | izinga-usermanagement | `UserProfileService.kt`, `UserProfileValidationTest.kt` |
| ijudi-api | izinga-ordermanager | `StoreService.java`, `StoreServiceBankSelectionTest.java`, `StoreServiceBankValidationTest.java` |

## Known-good test fixtures

No live endpoint fixture values — this fix is fully covered by unit tests with mocked dependencies. No real database, Firebase, or Yoco credentials required to run the test suite.

**Test accounts (unit test scope only):**
- `owner-sb-01` through `owner-multistore` — synthetic owner IDs in `StoreServiceBankSelectionTest`
- `owner-ewallet-01` through `owner-legacy-01` — synthetic owner IDs in `StoreServiceBankValidationTest`
- `testId` — synthetic user ID in `UserProfileValidationTest`

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Non-EWALLET bank with populated accountId keeps store bank | Unit | storeBank.accountId = "STORE-ACC-ID" (CHEQUE) | result.bank.accountId = "STORE-ACC-ID"; user bank NOT used | hasMeaningfulBank = true, non-EWALLET path |
| TC-02 | Null store bank falls back to user bank | Unit | store.bank = null | result.bank.accountId = user bank accountId | hasMeaningfulBank = false, null bank |
| TC-03 | Empty Bank() (blank accountId) falls back to user bank | Unit | store.bank = new Bank() (accountId not set) | result.bank = user bank | hasMeaningfulBank = false, blank accountId |
| TC-04 | EWALLET bank with non-blank phone keeps store bank | Unit | storeBank.type = EWALLET, phone = "+27831234567" | result.bank.type = EWALLET, phone = "+27831234567"; user bank NOT used | hasMeaningfulBank = true, EWALLET+phone path |
| TC-05 | EWALLET bank with blank/null phone falls back to user bank | Unit | storeBank.type = EWALLET, phone not set | result.bank = user bank (CHEQUE) | hasMeaningfulBank = false, EWALLET+no phone |
| TC-06 | Multi-store: two stores keep distinct bank accounts | Unit | store1.bank.accountId = "STORE-1-ACC"; store2.bank.accountId = "STORE-2-ACC" | result1 and result2 each keep their own accountId | Regression — fallback must not bleed across stores |
| TC-07 | EWALLET bank with phone+type only passes validateBankForCreate (via user-bank fallback path) | Unit | user.bank = EWALLET+phone, store.bank = null → fallback copies user's EWALLET bank | No exception thrown; result.bank.type = EWALLET | validateBankForCreate EWALLET pass branch |
| TC-08 | EWALLET bank missing phone throws 400 from validateBankForCreate | Unit | user.bank = EWALLET (phone null), store.bank = null | ResponseStatusException 400 "Bank phone is required" | validateBankForCreate EWALLET fail branch |
| TC-09 | EWALLET bank with blank phone throws 400 from validateBankForCreate | Unit | user.bank = EWALLET (phone = "   "), store.bank = null | ResponseStatusException 400 "Bank phone is required" | validateBankForCreate EWALLET blank phone |
| TC-10 | CHEQUE bank missing accountId still throws 400 (regression guard, store) | Unit | chequeBank.accountId = null | ResponseStatusException 400 "Bank account ID is required" | validateBankForCreate non-EWALLET path unchanged |
| TC-11 | CHEQUE bank missing name still throws 400 (regression guard, store) | Unit | chequeBank.name = null | ResponseStatusException 400 "Bank name is required" | validateBankForCreate non-EWALLET path unchanged |
| TC-12 | CHEQUE bank missing branchCode still throws 400 (regression guard, store) | Unit | chequeBank.branchCode = null | ResponseStatusException 400 "Bank branch code is required" | validateBankForCreate non-EWALLET path unchanged |
| TC-13 | Legacy 'wallet' BankAccType still rejected (regression guard, store) | Unit | bank.type = BankAccType.wallet | ResponseStatusException 400 with "wallet" in reason | Legacy type rejection unchanged |
| TC-14 | UserProfile create() accepts EWALLET bank with only phone+type | Unit | bank.phone = "0812815707"; type = EWALLET; accountId/name/branchCode null | No exception; save() called | validateBankForProfile EWALLET pass branch |
| TC-15 | UserProfile create() rejects EWALLET bank missing phone | Unit | bank.phone = null; type = EWALLET | ResponseStatusException 400 "Bank phone is required" | validateBankForProfile EWALLET fail branch |
| TC-16 | UserProfile update() accepts EWALLET bank with only phone+type | Unit | bank.phone = "0812815707"; type = EWALLET | No exception; save() called | validateBankForProfile EWALLET pass branch (update path) |
| TC-17 | UserProfile update() rejects EWALLET bank with blank phone | Unit | bank.phone = ""; type = EWALLET | ResponseStatusException 400 "Bank phone is required" | validateBankForProfile EWALLET fail branch (update path) |
| TC-18 | UserProfile create() still rejects CHEQUE missing accountId (regression guard) | Unit | bank.accountId = null; type = CHEQUE | ResponseStatusException 400 "Bank account ID is required" | Non-EWALLET path in validateBankForProfile unchanged |

## Integration test scenarios

Not applicable at Gate 1 scope — all paths are fully covered by unit tests. End-to-end validation would require:
- A real store owner calling `POST /store` with an EWALLET bank JSON body
- Verifying the persisted Mongo document has `bank.type = EWALLET` and `bank.phone` set (not overwritten by the user's personal bank)
- Calling `POST /user` with an EWALLET bank body and confirming no 400 is returned

These scenarios are deferred to Gate 2 regression if end-to-end coverage is required before release.

## Regression checks

- [ ] `POST /user` still rejects non-EWALLET bank missing `accountId` (TC-18 via UserProfileValidationTest)
- [ ] `POST /store` still falls back to user's bank when store submits no bank (TC-02 via StoreServiceBankSelectionTest)
- [ ] `POST /store` still uses store-submitted CHEQUE bank when accountId is present (TC-01)
- [ ] Legacy `BankAccType.wallet` type is still rejected on store create (TC-13)
- [ ] Multi-store scenario: each store keeps its own bank, no cross-store bleed (TC-06)
- [ ] ICA gate still blocks store creation when `icaAccepted` is not true (covered by StoreOnboardingServiceTest — unchanged)

## Known gaps / Known defects

**Gap:** No integration/E2E test exercising the live `POST /store` endpoint with a real EWALLET JSON body. All coverage is unit-level with mocked repositories. Acceptable for Gate 1 — the fix is isolated to in-process Java/Kotlin logic with no external system dependency.

**Gap:** `PATCH /store` (store bank update endpoint) is not included in this fix and has no EWALLET carve-out. If a store owner attempts to update their bank to EWALLET via PATCH, the existing validation would reject it. This is out of scope for this bugfix but should be tracked.

No known defects found during this Gate 1 pass.

## Validation commands

```bash
# Full reactor (mandatory before any PASS verdict)
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
./mvnw test

# Scoped iteration (development only — NOT a substitute for the reactor)
./mvnw -pl izinga-usermanagement -am test
./mvnw -pl izinga-ordermanager -am test
```
