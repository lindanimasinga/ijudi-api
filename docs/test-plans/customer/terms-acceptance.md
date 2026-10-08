---
userType: customer (primary); also covers Ambassador, Driver/MESSENGER, STOREADMIN
Feature: Terms & ICA acceptance flow — all four branches (General T&Cs, Ambassador ICA, Driver ICA, Merchant ICA), including proactive dashboard profile-completeness gate, blank-imageUrl PATCH stripping, and profile-incomplete CTA
App: izinga-onboarding (biz.izinga.co.za / onboarding.izinga.co.za)
Last verified: 2026-10-08 | automated unit pass | PASS
---

## Scope

Covers the full `TermsConditionsComponent` acceptance flow across all four role branches:
- CUSTOMER / default — general Terms & Conditions
- AMBASSADOR — Ambassador ICA v2
- MESSENGER (Driver) — Driver ICA driver-v2
- STOREADMIN / ADMIN — Merchant/Store Partner Agreement store-partner-v2

Also covers the proactive companion gate introduced in commit b6a1a3a (2026-10-08):
- `DashboardComponent.isProfileCompleteForTerms()` checks 6 core profile fields (`name`, `surname`, `emailAddress`, `address`, `mobileNumber`, `imageUrl`) on `ngOnInit`, **before** routing to the ICA/terms screen.
- If any field is blank, the user is redirected to `/business/user` (business context) or `/indivisuals/user` (individual context) so the subsequent acceptance PATCH cannot fail with a 400.
- This gate fires only inside the `!hasAcceptedTerms` block — users who have already accepted are not affected.
- Tests: TC-PROF-01 through TC-PROF-06 (added in this branch).

Also covers the reactive bugfix introduced in commit 3e43e02 (2026-10-08):
- `buildSafePayload()` strips a blank `imageUrl` from the PATCH body before sending to the backend, preventing a `400 "imageUrl is required"` loop for legacy users.
- `isRequiredFieldError()` + `handleAcceptError()` distinguish a 400 "is required" response from all other errors and surface a "Complete Profile" CTA routing to `/indivisuals/user` or `/business/user`.

**Out of scope:** ICA PDF hosting/embedding, ADR-017 audit log inserts (backend), skip-redirect for the general-terms branch (termsAccepted=true case), subscription tier billing after Merchant ICA acceptance. The proactive gate only checks the 6 fields listed above — it does not validate bank account fields (those are conditionally validated by the backend separately).

Cross-links: the Driver ICA acceptance flow overlaps with the driver onboarding flow — see [driver/quote-acceptance-and-fulfilment.md](../driver/quote-acceptance-and-fulfilment.md) for post-acceptance delivery flows.

## Repos affected

| Repo | Test work |
|---|---|
| izinga-onboarding | Primary — `DashboardComponent` (proactive gate), `TermsConditionsComponent` (reactive CTA), specs, HTML templates |
| ijudi-api | Backend validation (imageUrl required check added 2026-10-07, commit 3340ec49) — `UserProfileService.validateUserProfileForUpdate` defines the 6 required fields the frontend gate mirrors |

## Known-good test fixtures

| Field | Value | Notes |
|---|---|---|
| Test userId | `user-001` | Used in all unit tests via ActivatedRoute mock |
| Ambassador ICA version constant | `v2` | `TermsConditionsComponent.AMBASSADOR_ICA_VERSION` |
| Driver ICA version constant | `driver-v2` | `TermsConditionsComponent.DRIVER_ICA_VERSION` |
| Merchant ICA version constant | `store-partner-v2` | `TermsConditionsComponent.MERCHANT_ICA_VERSION` |
| Blank imageUrl trigger | `imageUrl: ''` | Sets profileIncompleteError=true after 400 |
| HTTP 400 string body | `{ status: 400, error: 'imageUrl is required' }` | Triggers profileIncompleteError=true |
| HTTP 400 JSON body | `{ status: 400, error: { message: 'imageUrl is required' } }` | Also triggers profileIncompleteError=true |
| Non-400 error | `{ status: 500, error: 'Internal Server Error' }` | Triggers acceptError=true (not profileIncompleteError) |
| Real imageUrl | `'https://storage.izinga.co.za/profile/user-001.jpg'` | Retained in payload as-is |
| Business URL context | `'/business/terms/user-001'` | Routes "Complete Profile" CTA to `/business/user` |
| Individual URL context | `'/indivisuals/terms'` (default) | Routes "Complete Profile" CTA to `/indivisuals/user` |
| Dashboard individual URL | `'/indivisuals/dashboard'` | Triggers indivisuals branch of proactive gate |
| Dashboard business URL | `'/business/dashboard'` | Triggers business branch of proactive gate |
| Complete user fixture | `{ id: 'user-prof-001', name: 'Sipho', surname: 'Dlamini', emailAddress: 'sipho@example.com', address: '1 Sandton Drive, Sandton', mobileNumber: '+27812815555', imageUrl: 'https://cdn.example.com/profile.jpg' }` | All 6 required fields set — gate passes |
| Incomplete user (blank imageUrl) | same as above but `imageUrl: ''` | Gate fires — redirect to profile update |
| Incomplete user (blank address) | same as above but `address: ''` | Gate fires — redirect to profile update |
| Current Driver ICA version constant | `CURRENT_DRIVER_ICA_VERSION` | exported from `dashboard.component.ts`; used in TC-PROF-06 |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Ambassador role detected | Unit | `role: AMBASSADOR` | `isAmbassador === true` | isAmbassador getter |
| TC-02 | Non-ambassador role | Unit | `role: MESSENGER` | `isAmbassador === false` | isAmbassador false branch |
| TC-03 | Ambassador acceptTerms() sets ICA fields | Unit | Ambassador + termsAccepted=true | PATCH called with `icaAccepted:true, icaVersion:'v2', icaAcceptedDate:Date` | Ambassador accept branch |
| TC-04 | Ambassador does NOT set termsAccepted | Unit | Ambassador accept | `termsAccepted` is undefined in PATCH | Ambassador branch exclusion |
| TC-05 | CUSTOMER sets termsAccepted only | Unit | CUSTOMER + termsAccepted=true | PATCH called with `termsAccepted:true, termsAcceptedDate:Date`; `icaAccepted` undefined | General terms branch |
| TC-06 | Analytics events per role | Unit | Ambassador / CUSTOMER accept | `ica_accepted` / `terms_accepted` events fired; opposite not fired | Analytics side-effects |
| TC-07 | Error state on failure (Ambassador + CUSTOMER) | Unit | updateCustomer throws Error | `acceptError===true` | Error branch |
| TC-08 | acceptError reset on retry | Unit | Fail then succeed (CUSTOMER) | `acceptError===false` after second call | Reset guard |
| TC-09 | Ambassador v1 must re-accept v2 | Unit | `icaAccepted:true, icaVersion:'v1'` | `needsIcaAcceptance===true` | Version mismatch |
| TC-10 | Ambassador v2 skips re-acceptance | Unit | `icaAccepted:true, icaVersion:'v2'` | `needsIcaAcceptance===false` | Version match |
| TC-11 | New ambassador must accept | Unit | No icaAccepted | `needsIcaAcceptance===true` | Falsy icaAccepted |
| TC-12 | Non-ambassador needsIca always false | Unit | MESSENGER | `needsIcaAcceptance===false` | Role guard |
| TC-13 | ica_accepted event carries icaVersion | Unit | Ambassador accept | `logEvent` called with `icaVersion:'v2'` | Analytics payload |
| TC-14 | MESSENGER isDriver=true | Unit | `role: MESSENGER` | `isDriver===true` | isDriver getter |
| TC-15 | Non-MESSENGER isDriver=false | Unit | `role: AMBASSADOR` | `isDriver===false` | isDriver false |
| TC-16 | New driver needs Driver ICA | Unit | MESSENGER, no icaAccepted | `needsDriverIcaAcceptance===true` | Falsy check |
| TC-17 | Driver with current version skips | Unit | `icaAccepted:true, icaVersion:'driver-v2'` | `needsDriverIcaAcceptance===false` | Version match |
| TC-18 | Driver with stale version re-gates | Unit | `icaAccepted:true, icaVersion:'driver-v1'` | `needsDriverIcaAcceptance===true` | Version mismatch |
| TC-19 | MESSENGER acceptTerms() sets all 5 fields | Unit | MESSENGER + termsAccepted=true | PATCH includes `icaAccepted, icaVersion:'driver-v2', icaAcceptedDate, termsAccepted, termsAcceptedDate` | Driver accept branch |
| TC-20 | MESSENGER logs driver_ica_accepted | Unit | MESSENGER accept | `driver_ica_accepted` event; `ica_accepted` and `terms_accepted` NOT fired | Driver analytics |
| TC-21 | MESSENGER navigates to /indivisuals/dashboard | Unit | MESSENGER accept (indivisuals URL) | `router.navigate(['/indivisuals/dashboard'])` | Driver nav |
| TC-22 | MESSENGER error sets acceptError | Unit | MESSENGER + updateCustomer throws | `acceptError===true` | Driver error |
| TC-23 | Non-MESSENGER needsDriverIca=false | Unit | AMBASSADOR | `needsDriverIcaAcceptance===false` | Driver role guard |
| TC-24 | MESSENGER on /business/ URL → /business/dashboard | Unit | MESSENGER + businessRouterSpy | `navigate(['/business/dashboard'])` | Business URL branch |
| TC-25 | STOREADMIN with Merchant ICA on /business/ — ngOnInit redirects | Unit | STOREADMIN + current ICA + business URL | `navigate(['/business/dashboard'])` in ngOnInit | Skip-redirect business |
| TC-26 | STOREADMIN with Merchant ICA on /indivisuals/ — ngOnInit redirects | Unit | STOREADMIN + current ICA + indivisuals URL | `navigate(['/indivisuals/dashboard'])` in ngOnInit | Skip-redirect indivisuals |
| TC-27 | STOREADMIN no ICA — ngOnInit does NOT redirect | Unit | STOREADMIN, no icaAccepted | `router.navigate` NOT called | No redirect guard |
| TC-28 | MESSENGER with current Driver ICA — ngOnInit redirects | Unit | MESSENGER + current ICA + termsAccepted | `navigate(['/indivisuals/dashboard'])` | Driver skip-redirect |
| TC-29 | AMBASSADOR with current ICA — ngOnInit → training-guide | Unit | AMBASSADOR + current ICA | `navigate(['/indivisuals/training-guide'])` | Ambassador skip-redirect |
| TC-30 | isStoreAdmin=true for STOREADMIN | Unit | `role: STOREADMIN` | `isStoreAdmin===true` | isStoreAdmin STOREADMIN |
| TC-31 | isStoreAdmin=true for ADMIN | Unit | `role: ADMIN` | `isStoreAdmin===true` | isStoreAdmin ADMIN |
| TC-32 | isStoreAdmin=false for MESSENGER | Unit | `role: MESSENGER` | `isStoreAdmin===false` | isStoreAdmin false |
| TC-33 | New STOREADMIN needs Merchant ICA | Unit | STOREADMIN, no icaAccepted | `needsMerchantIcaAcceptance===true` | Falsy check |
| TC-34 | STOREADMIN with current version skips | Unit | `icaAccepted:true, icaVersion:'store-partner-v2'` | `needsMerchantIcaAcceptance===false` | Version match |
| TC-35 | STOREADMIN with stale version re-gates | Unit | `icaAccepted:true, icaVersion:'store-partner-v1'` | `needsMerchantIcaAcceptance===true` | Version mismatch |
| TC-36 | MESSENGER needsMerchantIca=false | Unit | MESSENGER | `needsMerchantIcaAcceptance===false` | Role guard |
| TC-37 | STOREADMIN acceptTerms() sets all 5 fields | Unit | STOREADMIN + termsAccepted=true | PATCH includes all merchant ICA fields | Merchant accept branch |
| TC-38 | STOREADMIN logs merchant_ica_accepted | Unit | STOREADMIN accept | `merchant_ica_accepted` event; others NOT fired | Merchant analytics |
| TC-39 | STOREADMIN navigates to dashboard on success | Unit | STOREADMIN accept (indivisuals URL) | `navigate(['/indivisuals/dashboard'])` | Merchant nav |
| TC-40 | STOREADMIN error sets acceptError | Unit | STOREADMIN + updateCustomer throws | `acceptError===true` | Merchant error |
| TC-41 | ngOnInit STOREADMIN on /business/ redirects | Unit | STOREADMIN + current ICA + business URL | `navigate(['/business/dashboard'])` | Merchant skip-redirect business |
| TC-42 | ngOnInit STOREADMIN no icaAccepted — no redirect | Unit | STOREADMIN, no ICA | `router.navigate` NOT called | No redirect guard |
| **TC-43** | **AMBASSADOR with real imageUrl — imageUrl retained in PATCH** | **Unit** | **AMBASSADOR + imageUrl='https://...'** | **Sent payload includes imageUrl** | **buildSafePayload — non-blank** |
| **TC-44** | **AMBASSADOR with blank imageUrl — imageUrl stripped from PATCH** | **Unit** | **AMBASSADOR + imageUrl=''** | **Sent payload has no imageUrl key** | **buildSafePayload — falsy guard** |
| **TC-45** | **MESSENGER with blank imageUrl — imageUrl stripped from PATCH** | **Unit** | **MESSENGER + imageUrl=''** | **Sent payload has no imageUrl key** | **buildSafePayload — driver branch** |
| **TC-46** | **AMBASSADOR — 400 "imageUrl is required" → profileIncompleteError** | **Unit** | **AMBASSADOR + 400 string body** | **profileIncompleteError=true, acceptError=false** | **isRequiredFieldError — string body** |
| **TC-47** | **MESSENGER — 400 "imageUrl is required" → profileIncompleteError** | **Unit** | **MESSENGER + 400 string body** | **profileIncompleteError=true, acceptError=false** | **isRequiredFieldError — driver branch** |
| **TC-48** | **STOREADMIN — 400 "imageUrl is required" → profileIncompleteError** | **Unit** | **STOREADMIN + 400 string body** | **profileIncompleteError=true, acceptError=false** | **isRequiredFieldError — merchant branch** |
| **TC-49** | **CUSTOMER — 400 "imageUrl is required" → profileIncompleteError** | **Unit** | **CUSTOMER + 400 string body** | **profileIncompleteError=true, acceptError=false** | **isRequiredFieldError — general branch** |
| **TC-50** | **500 error → acceptError, not profileIncompleteError** | **Unit** | **AMBASSADOR + 500** | **acceptError=true, profileIncompleteError=false** | **isRequiredFieldError — non-400 guard** |
| **TC-51** | **400 with JSON body message field → profileIncompleteError** | **Unit** | **AMBASSADOR + 400 JSON body** | **profileIncompleteError=true** | **isRequiredFieldError — JSON body** |
| **TC-52** | **navigateToProfileUpdate() on /indivisuals/ → /indivisuals/user** | **Unit** | **indivisuals URL context** | **navigate(['/indivisuals/user'])** | **navigateToProfileUpdate — indivisuals** |
| **TC-53** | **navigateToProfileUpdate() on /business/ → /business/user** | **Unit** | **business URL context** | **navigate(['/business/user'])** | **navigateToProfileUpdate — business** |
| **TC-54** | **profileIncompleteError reset on retry** | **Unit** | **First call 400, second call success (CUSTOMER)** | **profileIncompleteError=false after second call** | **Reset guard** |

TC-43 to TC-54 are new tests added in commit 3e43e02 for the imageUrl bugfix.

## Integration test scenarios

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| INT-01 | Legacy user (blank imageUrl) accepts Ambassador ICA | 1. OTP login with legacy user (imageUrl='') 2. Navigate to /indivisuals/terms/:id 3. Check checkbox 4. Click "Accept Agreement" 5. Inspect network PATCH request body | PATCH body does NOT include imageUrl key; user is navigated to /indivisuals/training-guide |
| INT-02 | Legacy user (blank imageUrl) — acceptance fails even after strip (simulated backend failure) | 1. Steps 1-4 above 2. Backend returns 400 JSON body with "is required" | profileIncompleteError banner and "Complete Profile" button appear |
| INT-03 | Complete Profile CTA routes to profile screen | 1. After INT-02 step 2 2. Click "Complete Profile" | Router navigates to /indivisuals/user (or /business/user if in business context) |
| INT-04 | Non-legacy user (has imageUrl) accepts without issues | 1. OTP login with user who has a real imageUrl 2. Accept any ICA | imageUrl is present in PATCH body; acceptance succeeds |

## Regression checks

- [ ] Ambassador ICA acceptance still works (TC-03, TC-13)
- [ ] Driver ICA acceptance still works (TC-19, TC-20, TC-21)
- [ ] Merchant ICA acceptance still works (TC-37, TC-38, TC-39)
- [ ] General T&Cs acceptance still works (TC-05)
- [ ] Skip-redirect still fires for already-accepted users (TC-25 to TC-29)
- [ ] Generic network errors still show "try again" banner, not "Complete Profile" (TC-50)
- [ ] acceptError and profileIncompleteError are mutually exclusive and both reset at the start of each call (TC-08, TC-54)
- [ ] Existing users with a real imageUrl are NOT affected by the stripping logic (TC-43)
- [ ] Navigation to /business/ dashboard works correctly for all roles when in business URL context (TC-24, TC-25, TC-41)

## Known gaps / Known defects

| ID | Description | Severity | Location | Date found | Status |
|---|---|---|---|---|---|
| GAP-01 | STOREADMIN and CUSTOMER branches have no explicit unit test for imageUrl stripping (TC-44/TC-45 only cover AMBASSADOR and MESSENGER). The `buildSafePayload()` helper covers all 4 branches identically, so coverage is logically complete — but a reviewer noted the absence as a code-review minor note. | Low | `terms-conditions.component.spec.ts` — TC-44/TC-45 | 2026-10-08 | Open — non-blocking per code review |
| GAP-02 | `isRequiredFieldError` fires on any "is required" 400, not exclusively `imageUrl is required`. If the backend adds a different required-field validation in future (e.g. a missing `mobileNumber`), the "Complete Profile" CTA hint text says "add a profile photo" — which would be misleading. | Low | `terms-conditions.component.html` — profileIncompleteError copy + `isRequiredFieldError()` | 2026-10-08 | Open — non-blocking per code review; monitor when backend adds new required fields |
| GAP-03 | No automated E2E test covers INT-01 through INT-04. Manual E2E is required against a real backend instance with a real legacy user. | Medium | No test file | 2026-10-08 | Open — backlog |

## Validation commands

```bash
# izinga-onboarding — full test suite (mandatory before any QA PASS verdict)
cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding
npx ng test --watch=false --code-coverage

# izinga-onboarding — dev build (catches compile errors missed by prod-only builds)
npx ng build
```
