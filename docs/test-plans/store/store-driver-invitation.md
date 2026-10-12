# Test Plan: Store & Messenger-Admin Driver Invitation (ADR-018)

**userType:** store / messenger-admin (issuing side); driver (receiving side)
**Feature:** Store or messenger-admin invites a driver by mobile number; driver accepts or rejects via a code embedded in a WhatsApp deep-link; 5-day expiry; idempotency; cross-link to driver test plan below.
**App:** `izinga-onboarding` (`StoreMessengersComponent`, `TeamMessengersComponent`, `StoreInviteComponent`, `PhoneVerificationComponent`), `ijudi-api` (`izinga-usermanagement`, `izinga-messaging`)
**Cross-link:** [driver/store-driver-invitation.md](../driver/store-driver-invitation.md)
**Last verified:** 2026-10-12, automated unit-test pass (Gate 1 per-feature), `./mvnw test` (ijudi-api: 258/258) + `ng test` (izinga-onboarding: 780/780) — CONDITIONAL PASS. Backend: SIGN-OFF. Frontend: CONDITIONAL PASS. AC-04 gap documented below.

---

## Scope

**In scope:**
- Five invitation outcomes for both store and messenger-admin context types
- Phone normalisation (E.164 +27 prefix, 9-digit suffix extraction, BAD_REQUEST on <9 digits)
- 5-day expiry: isExpired computed property, materialisation of expired PENDING invite to EXPIRED status before replacement, 410 GONE on accept/reject of expired invite
- Accept/reject identity checks: contextId mismatch (404), phone mismatch (403), caller profile not found (403)
- Race-condition re-check in acceptInvitation: driver already linked to different context (409) vs same context (idempotent 200)
- WhatsApp notification non-propagation (template-not-approved failure must not return 5xx)
- Public deep-link lookup (`GET /invite/{code}`): masked phone, ISO expiresAt, STORE vs MESSENGER_ADMIN context name resolution
- SecurityConfig authorisation rules for all six new endpoints
- HTTP status codes: 201 for new profile/invite, 200 for idempotent outcomes

**Out of scope:**
- Flutter driver app flow (no invite acceptance screen built there yet)
- Meta template approval verification (cannot be automated)
- Concurrent write safety under MongoDB-level race (application has no optimistic/pessimistic locking — flagged as a known gap)
- Full E2E WhatsApp round-trip (requires approved `driver_store_invite` Meta template)

---

## Repos affected

- `ijudi-api` — `izinga-usermanagement` (new classes + T-01 `contextType` field), `izinga-commons` (new entity + repo), `izinga-messaging` (new `sendDriverStoreInvitation` on `WhatsappNotificationService`), `izinga-ordermanager` (SecurityConfig)
- `izinga-onboarding` — `StoreMessengersComponent` (T-03), `TeamMessengersComponent` (T-04), `PhoneVerificationComponent` (T-06 returnUrl), `StoreInviteComponent` (T-07), `IzingaOrderManagementService` (T-02: 7 new invite methods)

---

## Known-good test fixtures

These are currently unit-test-only; no live E2E fixture exists yet.

- **Normalised phone format:** `0841234567` normalises to `+27841234567` (last-9 rule)
- **Exactly 9-digit phone:** `841234567` (no prefix) normalises to `+27841234567` — boundary valid
- **8-digit phone:** `84123456` throws 400 BAD_REQUEST — boundary invalid
- **International E.164 input:** `+27841234567` (12 chars) normalises to `+27841234567` — last 9 of `+27841234567` is `841234567`
- **Expiry simulation:** set `expiresAt` to 1 day in the past for expired; 5 days in the future for valid
- **Tag keys:** `storeId` for STORE context, `messengerAdminId` for MESSENGER_ADMIN context
- **Invitation code:** UUID string, unique per invitation (MongoDB unique index on `invitationCode`)
- **Template name:** `driver_store_invite` (UTILITY category on Meta)

---

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Outcome 1 (store): no existing profile → create MESSENGER profile with storeId tag | Unit | store found, `findByMobileNumber` returns null | CREATED, `profileService.create()` called, tag["storeId"] = storeId, role = MESSENGER, imageUrl = PENDING | Happy path: new driver |
| TC-02 | Outcome 1: default name "New Driver" used when name not supplied | Unit | InviteRequest(mobileNumber only) | profile.name == "New Driver" | name-null branch |
| TC-03 | Outcome 2 (store): existing profile already linked to this store | Unit | tag["storeId"] == storeId | ALREADY_LINKED, no save | Already-linked branch |
| TC-04 | Outcome 3 (store): existing profile linked to a different store | Unit | tag["storeId"] == "other-store-id" | 409 CONFLICT, "DRIVER_LINKED_TO_OTHER_STORE" | Linked-to-other branch |
| TC-05 | Outcome 4 (store): no link, no pending invite → create invitation | Unit | tag empty, invitationRepo returns null | INVITED, invitation saved with contextType=STORE, status=PENDING | New invite branch |
| TC-06 | Outcome 4: expired existing invite is materialised EXPIRED then replaced | Unit | tag empty, expired PENDING invite exists | TWO saves: first status=EXPIRED (same code), second status=PENDING (new code) | Expired-replace branch |
| TC-07 | Outcome 5 (store): pending non-expired invite already exists → idempotent | Unit | tag empty, valid PENDING invite exists | INVITED with existing code, no save | Idempotent branch |
| TC-08 | WhatsApp send failure (outcome 4) does not propagate | Unit | notificationPort throws RuntimeException | No exception thrown, INVITED result returned | Try-catch in processInvite |
| TC-09 | `acceptInvitation` happy path | Unit | valid code, matching phone, not expired | storeId tag set on driver, invitation ACCEPTED, respondedAt set | Happy path |
| TC-10 | `acceptInvitation` — contextId mismatch | Unit | code exists but invitation.contextId != path storeId | 404 NOT_FOUND | contextId guard |
| TC-11 | `acceptInvitation` — phone mismatch | Unit | caller phone != invitation driverMobileNumber | 403 FORBIDDEN | Phone check |
| TC-12 | `acceptInvitation` — expired invitation | Unit | expiresAt in past | 410 GONE, "INVITATION_EXPIRED" | Expiry check |
| TC-13 | `acceptInvitation` — race: driver already linked to different store at accept time | Unit | driver.tag["storeId"] == "other-store" | 409 CONFLICT, "DRIVER_ALREADY_LINKED" | Race re-check |
| TC-14 | `acceptInvitation` — race: driver already linked to this store (idempotent) | Unit | driver.tag["storeId"] == storeId | 200, no save, no throw | Idempotent race path |
| TC-15 | `rejectInvitation` happy path with reason | Unit | valid code, matching phone, reason string | REJECTED, rejectionReason set, respondedAt set, driver NOT saved | Happy path |
| TC-16 | `rejectInvitation` — null reason allowed | Unit | reason = null | REJECTED saved with null rejectionReason | Null-reason branch |
| TC-17 | `rejectInvitation` — phone mismatch | Unit | caller phone != invite phone | 403 FORBIDDEN | Phone guard |
| TC-18 | `rejectInvitation` — expired invitation | Unit | expiresAt in past | 410 GONE | Expiry check |
| TC-19 | Messenger-admin outcome 2: already linked to this admin | Unit | tag["messengerAdminId"] == adminId | ALREADY_LINKED | Messenger-admin path |
| TC-20 | Messenger-admin outcome 3: linked to different admin | Unit | tag["messengerAdminId"] == "other-admin" | 409 CONFLICT | Messenger-admin other |
| TC-21 | Messenger-admin outcome 4: creates invitation with MESSENGER_ADMIN contextType | Unit | no tag, no pending invite | INVITED, contextType=MESSENGER_ADMIN, contextId=adminId | contextType branch |
| TC-22 | AmbassadorAdminController.PENDING_PHOTO_URL regression (B-02) | Unit | constant access | equals IzingaConstants.PENDING_PHOTO_URL | Constant delegation |
| **TC-23** | **Store not found returns 404** | **Unit (gap)** | storeRepository.findById returns empty | 404 NOT_FOUND, no profile/invite created | Store-not-found guard |
| **TC-24** | **Messenger-admin not found returns 404** | **Unit (gap)** | userProfileRepo.findById(adminId) returns empty | 404 NOT_FOUND | Admin-not-found guard |
| **TC-25** | **Messenger-admin outcome 1: no profile → create MESSENGER profile with messengerAdminId tag** | **Unit (gap)** | admin found, findByMobileNumber returns null | CREATED, tag["messengerAdminId"] = adminId | Messenger-admin new-driver |
| **TC-26** | **Messenger-admin outcome 5: pending invite exists idempotent** | **Unit (gap)** | no tag, valid PENDING invite exists | INVITED with existing code, no save | Messenger-admin idempotent |
| **TC-27** | **Messenger-admin WhatsApp failure non-propagation** | **Unit (gap)** | notificationPort throws | No exception propagated | Messenger-admin try-catch |
| **TC-28** | **Messenger-admin expired invite replaced** | **Unit (gap)** | expired PENDING invite exists | TWO saves: EXPIRED + new PENDING | Messenger-admin expiry-replace |
| **TC-29** | **acceptInvitation — code not found (null from repo)** | **Unit (gap)** | invitationRepo.findByInvitationCode returns null | 404 NOT_FOUND | Null-code guard |
| **TC-30** | **acceptInvitation — caller profile not found** | **Unit (gap)** | userProfileRepo.findById(callerUserId) returns empty | 403 FORBIDDEN | Caller-not-found guard |
| **TC-31** | **rejectInvitation — code not found** | **Unit (gap)** | invitationRepo.findByInvitationCode returns null | 404 NOT_FOUND | Null-code guard |
| **TC-32** | **rejectInvitation — contextId mismatch** | **Unit (gap)** | invitation.contextId != path contextId | 404 NOT_FOUND | contextId guard |
| **TC-33** | **rejectInvitation — caller profile not found** | **Unit (gap)** | userProfileRepo.findById returns empty | 403 FORBIDDEN | Caller-not-found guard |
| **TC-34** | **normalizePhoneForLookup: exactly 9 digits** | **Unit (gap)** | "841234567" (9 chars) | "+27841234567" — no exception | Boundary valid |
| **TC-35** | **normalizePhoneForLookup: 8 digits throws 400** | **Unit (gap)** | "84123456" (8 chars) | ResponseStatusException BAD_REQUEST | Boundary invalid |
| **TC-36** | **acceptInvitation via MESSENGER_ADMIN context uses messengerAdminId tag key** | **Unit (gap)** | invitation.contextType = MESSENGER_ADMIN, contextId = adminId | driver.tag["messengerAdminId"] = adminId (not "storeId") | Tag-key dispatch |
| **TC-37** | **rejectInvitation via MESSENGER_ADMIN context succeeds** | **Unit (gap)** | MESSENGER_ADMIN invitation, matching phone, not expired | REJECTED saved, driver NOT modified | Messenger-admin reject |
| **TC-38** | **findByCode — code not found returns null** | **Unit (gap)** | invitationRepo.findByInvitationCode returns null | null returned (no exception) | Null path |
| **TC-39** | **GET /invite/{code} — unknown code returns 404** | **Unit (gap)** | service.findByCode returns null | 404 Not Found | Controller null guard |
| **TC-40** | **GET /invite/{code} — STORE invite returns storeName + imageUrl, masked phone, ISO expiresAt** | **Unit (gap)** | STORE invitation, store found | storeId, storeName, storeImageUrl, masked phone, ISO timestamp | STORE lookup branch |
| **TC-41** | **GET /invite/{code} — MESSENGER_ADMIN invite returns null storeName/imageUrl** | **Unit (gap)** | MESSENGER_ADMIN invitation | storeName=null, storeImageUrl=null | MESSENGER_ADMIN lookup branch |
| **TC-42** | **Expiry boundary: isExpired false at expiresAt - 1 second** | **Unit (gap)** | expiresAt = now + 1 second | isExpired == false | Boundary valid |
| **TC-43** | **Expiry boundary: isExpired true at expiresAt + 1 second** | **Unit (gap)** | expiresAt = now - 1 second | isExpired == true | Boundary expired |
| **TC-44** | **isExpired always false when status != PENDING** | **Unit (gap)** | status = ACCEPTED, expiresAt in past | isExpired == false | Non-PENDING guard |

Rows in **bold** were gaps at the prior CONDITIONAL PASS; they are now covered by the 8 new backend tests (TC-INV-01 through TC-INV-08 in `InviteCodeLookupControllerTest.kt`) and 16 + 8 + 4 new frontend unit tests (see below).

### Frontend test cases (izinga-onboarding — INVITE-DRV-01)

| ID | Description | Type | Input | Expected output | AC |
|---|---|---|---|---|---|
| FE-01 | StoreInviteComponent — pending invite rendered | Unit | PENDING invite, future expiresAt | state = 'pending', invite = response | AC-09 |
| FE-02 | StoreInviteComponent — 404 resolves to not-found | Unit | HTTP 404 | state = 'not-found' | AC-12 |
| FE-03 | StoreInviteComponent — ACCEPTED resolves to already-responded | Unit | status = 'ACCEPTED' | state = 'already-responded' | AC-11 |
| FE-04 | StoreInviteComponent — REJECTED resolves to already-responded | Unit | status = 'REJECTED' | state = 'already-responded' | AC-11 |
| FE-05 | StoreInviteComponent — EXPIRED resolves to expired | Unit | status = 'EXPIRED' | state = 'expired' | AC-10 |
| FE-06 | StoreInviteComponent — past expiresAt with PENDING resolves to expired | Unit | PENDING, past expiresAt | state = 'expired' | AC-10 |
| FE-07 | StoreInviteComponent — unauthenticated Accept redirects to verify with returnUrl | Unit | currentUser = null, click Accept | navigate(['/indivisuals/verify'], {queryParams: {returnUrl: contains '/indivisuals/store-invite'}}) | AC-13 |
| FE-08 | StoreInviteComponent — unauthenticated Reject redirects to verify with returnUrl | Unit | currentUser = null, click Reject | same redirect | AC-13 |
| FE-09 | StoreInviteComponent — STORE context calls acceptStoreInvite | Unit | contextType='STORE', authenticated | acceptStoreInvite(storeId, code) called | AC-15 |
| FE-10 | StoreInviteComponent — MESSENGER_ADMIN context calls acceptMessengerAdminInvite | Unit | contextType='MESSENGER_ADMIN', authenticated | acceptMessengerAdminInvite(adminId, code) called | AC-16 |
| FE-11 | StoreInviteComponent — accept success → accepted state | Unit | acceptStoreInvite returns of(undefined) | state = 'accepted' | AC-17 |
| FE-12 | StoreInviteComponent — 403 shows phone-mismatch message | Unit | acceptStoreInvite throws 403 | actionError contains 'different phone number' | AC-19 |
| FE-13 | StoreInviteComponent — 410 transitions to expired state | Unit | acceptStoreInvite throws 410 | state = 'expired' | AC-20 |
| FE-14 | StoreInviteComponent — 409 shows already-linked message | Unit | acceptStoreInvite throws 409 | actionError contains 'already linked' | — |
| FE-15 | StoreInviteComponent — contextName = storeName for STORE | Unit | contextType='STORE', storeName='Test Store' | contextName = 'Test Store' | AC-09 |
| FE-16 | StoreInviteComponent — contextName = 'iZinga Team' for MESSENGER_ADMIN | Unit | contextType='MESSENGER_ADMIN' | contextName = 'iZinga Team' | AC-09 |
| FE-17 | StoreInviteComponent — contextName = 'iZinga Team' when storeName is null | Unit | storeName = null | contextName = 'iZinga Team' | AC-09 |
| FE-18 | StoreInviteComponent — goToDashboard navigates to /indivisuals/dashboard | Unit | goToDashboard() | navigate(['/indivisuals/dashboard']) | AC-17 |
| FE-19 | StoreMessengersComponent — single HTTP call (no prelim lookup) | Unit | sendInvite() with valid phone | inviteDriverToStore called once | AC-06 |
| FE-20 | StoreMessengersComponent — CREATED shows "Driver added" and reloads | Unit | outcome = 'CREATED' | inviteResultMessage contains 'Driver added', reload triggered | AC-01 |
| FE-21 | StoreMessengersComponent — ALREADY_LINKED shows "already on your team" | Unit | outcome = 'ALREADY_LINKED' | inviteResultMessage contains 'already on your team' | AC-02 |
| FE-22 | StoreMessengersComponent — INVITED shows invite-sent message | Unit | outcome = 'INVITED' | inviteResultMessage.length > 0, type='success' | AC-03 |
| FE-23 | StoreMessengersComponent — 409 shows conflict message | Unit | HTTP 409 | inviteResultMessage contains 'another team' | AC-05 |
| FE-24 | StoreMessengersComponent — empty mobile shows validation message | Unit | mobileNumber = '' | service not called | AC-06 |
| FE-25 | StoreMessengersComponent — missing storeId shows Store not found | Unit | storeId = undefined | service not called | — |
| FE-26 | PhoneVerificationComponent — internal returnUrl stored in StorageService | Unit | queryParams = {returnUrl: '/indivisuals/store-invite?code=abc'} | storageSvc.returnUrl = '/indivisuals/store-invite?code=abc' | AC-14 |
| FE-27 | PhoneVerificationComponent — external returnUrl (https://) not stored (AC-21) | Unit | queryParams = {returnUrl: 'https://evil.com'} | storageSvc.returnUrl = null | AC-21 |
| FE-28 | PhoneVerificationComponent — external returnUrl without scheme not stored | Unit | queryParams = {returnUrl: 'evil.com/steal'} | storageSvc.returnUrl = null | AC-21 |
| FE-29 | PhoneVerificationComponent — absent returnUrl leaves storage unchanged | Unit | queryParams = {} | storageSvc.returnUrl = null | AC-14 |
| FE-30 | PhoneVerificationComponent — navigates to returnUrl from StorageService on verify success | Unit | storageSvc.returnUrl = '/indivisuals/some-deep-route' | navigateByUrl called with that URL | AC-14 |
| FE-31 | Public route — no PhoneVerifiedGuard on /indivisuals/store-invite | Static | app-routing.module.ts line 91 | `{ path: 'store-invite', component: StoreInviteComponent }` — no canActivate | AC-08 |
| FE-32 | getInviteByCode — no Authorization header | Static | izinga-order-management.service.ts line 720-724 | Uses `this.headers` only, no Bearer token | AC-22 |

---

## Integration test scenarios

These cannot be covered by Mockito unit tests and require a running MongoDB instance or a test container:

| ID | Scenario | Risk |
|---|---|---|
| IT-01 | **Duplicate PENDING invite race** — two concurrent `POST /store/{storeId}/invite` with the same mobileNumber and no existing record; both read `findByContextIdAndDriverMobileNumberAndStatus` returning null before either save completes; both create an invitation | Service has no atomic CAS; MongoDB unique index on `invitationCode` prevents duplicate codes (UUIDs) but does NOT prevent two PENDING rows for the same (contextId, phone). Two PENDING records would accumulate — both would be returned by subsequent `findByContextIdAndDriverMobileNumberAndStatus` (which expects at most one). **Severity: MEDIUM.** |
| IT-02 | **Accept race** — two near-simultaneous `POST /store/{storeId}/invite/{code}/accept` for the same code by the same driver; both read `driver.tag[tagKey] == null` before either save completes; both succeed; driver profile is saved twice | No optimistic locking on `UserProfile`. Both saves set the same tag value so the second write is idempotent in terms of outcome, but two `invitationRepo.save()` calls would both set status=ACCEPTED (the second save copies the already-ACCEPTED invitation). **Severity: LOW** (idempotent outcome, but two Mongo writes). |
| IT-03 | **Replace-then-accept race** — replacement invite creation (outcome 4) materialises old invite to EXPIRED, but driver is mid-accept on the old invite code; the accept reads the old invite (still PENDING in their read), materialise sets it EXPIRED, accept then tries to save it ACCEPTED | Materialise happens first in most cases because both run inside the same request thread, but under concurrent load the old invite could be read by the accept path after the materialise has already set it to EXPIRED. The `isExpired` check would then correctly return 410. Requires integration test with artificial delay. **Severity: LOW.** |
| IT-04 | **5-day expiry boundary** — invitation created at T, read at T+5 days minus 1 second (valid), read at T+5 days plus 1 second (expired) | Must use a real clock or test-container with time manipulation. **Severity: LOW** (covered by unit tests with simulated dates, but exact boundary requires real-time verification). |

---

## Regression checks

These must pass on `develop` after this feature merges:

- [ ] `POST /user` (phone normalisation via `UserProfileService.createProfile`) still normalises correctly — the `normalizePhoneForLookup` method in `StoreDriverInviteService` is separate but uses the same logic
- [ ] `GET /user?role=MESSENGER` still returns drivers correctly — `findMessengersByAdminId` added to `UserController` touches the same endpoint parameter set
- [ ] WhatsApp `acceptQuote` path, `startOrder`, and other notification paths unaffected — `sendDriverStoreInvitation` is a new method that does not touch existing template-send paths
- [ ] `StoreProfile` and `UserProfile` entities unchanged — no field additions
- [ ] AmbassadorAdminController `PENDING_PHOTO_URL` constant still delegates to `IzingaConstants.PENDING_PHOTO_URL` (confirmed by TC-22)
- [ ] SecurityConfig: existing order management and store endpoints still accessible with their prior auth rules (new rules are additive, not replacements)
- [ ] `GET /invite/*` is publicly accessible — no auth token required (confirmed in SecurityConfig line 64)

---

## Known gaps / known defects

| # | Type | Severity | Description | Date found |
|---|---|---|---|---|
| GAP-01 | Coverage gap | MEDIUM | TC-23 through TC-44 (22 test cases) are not implemented in `StoreDriverInviteServiceTest.kt`. The cases for store/admin not found, phone boundary, reject contextId mismatch, MESSENGER_ADMIN accept/reject, findByCode, and `InviteCodeLookupController` controller-layer tests are all absent. | 2026-10-12 |
| GAP-02 | Concurrency | MEDIUM | No atomic guard against duplicate PENDING invitations under concurrent `POST /store/{storeId}/invite` requests. `findByContextIdAndDriverMobileNumberAndStatus` + `save` is not atomic. A unique compound index on `(contextId, driverMobileNumber, status=PENDING)` would prevent this, but MongoDB does not support partial unique indexes via Spring Data annotations without a `@CompoundIndex` with a filter. Alternatively, application-level idempotency key or optimistic retry is needed. | 2026-10-12 |
| GAP-03 | Concurrency | LOW | No optimistic locking on `UserProfile` during `acceptInvitation`. Two simultaneous accept requests for the same code by the same driver will both succeed. Outcome is idempotent (same tag value written twice) but involves two Mongo writes. | 2026-10-12 |
| GAP-04 | Controller coverage | MEDIUM | `StoreDriverInviteController`, `InviteCodeLookupController`, and `MessengerAdminDriverInviteController` have no `@WebMvcTest` or `MockMvc` tests. HTTP status codes (201 vs 200 for INVITED based on `invitation?.id`), auth enforcement, and the ISO date format in `GET /invite/{code}` responses are not verified by any automated test. | 2026-10-12 |
| GAP-05 | E2E | LOW | `GET /invite/{code}` for a MESSENGER_ADMIN invite returns `storeId` field (field is named `storeId` in the response DTO) but populated with `adminId`. Consumers of the API could be confused by the field name. Not a functional bug but a documentation/naming concern. Frontend correctly treats it as `contextId`. | 2026-10-12 |
| GAP-06 | AC-04 / UX | MEDIUM | `INVITED` outcome for idempotent case (HTTP 200) and new-invite case (HTTP 201) both reach the Angular success (`next:`) handler. Without `observe: 'response'`, the component cannot distinguish 201 vs 200. Both cases show the new-invite message ("Invite created. A WhatsApp message will be sent...") instead of showing "A pending invite already exists for this number." for the idempotent case. Dead code at `error?.status === 200 && error?.error?.outcome === 'INVITED'` can never fire. AC-04 is not met. Fix requires adding `observe: 'response'` to `inviteDriverToStore()` and `inviteDriverToMessengerAdmin()`. **Tracked as pre-release fix before WhatsApp template goes live.** | 2026-10-12 |
| GAP-07 | Spec coverage | LOW | `store-messengers.component.spec.ts` was not updated by the developer when the component was refactored from the two-phase Check & Add flow to the single-call invite flow. The stale spec referenced removed methods (`checkAndAddDriver`, `createNewDriver`, etc.) and caused compile failure. **Fixed by QA in this session** — stale tests replaced with correct `sendInvite()` five-outcome tests. | 2026-10-12 |
| GAP-08 | Spec coverage | LOW | `phone-verification.component.spec.ts` used `routeSvc = {}` as the `ActivatedRoute` stub — this had no `queryParams` Observable. T-06's addition of `this.route.queryParams.subscribe()` to `ngOnInit` caused all 24 existing tests to fail with `TypeError: Cannot read properties of undefined (reading 'subscribe')`. **Fixed by QA in this session** — `routeSvc` now provides `queryParams: of({})`. AC-21 and T-06 returnUrl tests added. | 2026-10-12 |

---

## Validation commands

```bash
# Backend — scoped iteration
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
./mvnw -pl izinga-usermanagement -am test

# Backend — final mandatory unscoped run (do not report green without this)
./mvnw test
# Expected: 258 tests, 0 failures

# Frontend — full unscoped run (mandatory before any PASS verdict)
cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding
ng test --watch=false --browsers=ChromeHeadless
# Expected: 780 tests, 0 failures
```
