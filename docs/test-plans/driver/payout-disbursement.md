# Payout Disbursement — Driver & Shop

**userType:** driver (messenger) / admin  
**Feature:** Full payout pipeline: STAGE_7_ALL_PAID → payout generation (PENDING) → CSV export (PROCESSING flip) → bank reconciliation (COMPLETED or PENDING reversion) → missing-payout recovery  
**App:** ijudi-api (backend) + izinga-onboarding (admin UI at /admin/recon and /admin/recon/detail)  
**Last verified:** 2026-11-03 | feature/payout-disbursement-fixes Gate 1 QA — full reactor `./mvnw test` 258/0/3-skipped PASS; auth enforcement live-verified; GAP-3/BUG-3/GAP-7/BUG-7 unit-test verified; PATCH live blocked by ADMIN auth constraint (documented)

---

## Scope

**In scope:**
- Payout generation at `STAGE_7_ALL_PAID` via `OrderPayoutEventHandler` and `ReconServiceImpl.generatePayoutForMessengerAndOrder()`
- Payout generation for shops via `ReconServiceImpl.generatePayoutForShopAndOrder()` (added to scope 2026-11-03 — GAP-7 fix)
- Null-bank NPE guard for messenger payout path (BUG-3) and shop payout path (GAP-7)
- WhatsApp notification via `PayoutNotificationPort` when bank details missing (BUG-3/GAP-7)
- Payout state machine: PENDING → PROCESSING (CSV download) → COMPLETED (paid=true PATCH) or PENDING reversion (paid=false PATCH)
- Failure tracking: `failureReason` + `attemptCount` fields on all Payout subtypes (GAP-3)
- RFC 4180 CSV quoting in `csvAppend()` — double-quote wrapping + internal quote escaping (BUG-7)
- `updateMissingPayouts` scheduler — recovery logic for orders where initial payout creation failed
- Admin recon UI at `/admin/recon` and `/admin/recon/detail` in izinga-onboarding
- CSV download via `GET /reconcsv/messenger-payout-bundle` (DESTRUCTIVE: advances to PROCESSING)
- Reconciliation update via `PATCH /recon/messengerPayoutBundle`
- Backend ADMIN role enforcement on all recon endpoints

**Out of scope:**
- PayFast/Yoco payment processing (tested separately)
- Customer-facing order flow post-payment (covered in customer/furniture-booking.md)

**Cross-link:** Initiating order flow lives at [customer/furniture-booking.md](../customer/furniture-booking.md)

---

## Repos affected

| Repo | Test work |
|---|---|
| ijudi-api / izinga-commons | PayoutNotificationPort interface (new) |
| ijudi-api / izinga-messaging | WhatsappNotificationService — notifyDriverMissingBankDetails, notifyStoreMissingBankDetails (new) |
| ijudi-api / izinga-recon | ReconServiceImpl, CsvWriter, Payout model; CsvWriterQuotingTest, NullBankGuardPayoutTest, PayoutFailureStatusUpdateTest (new) |
| ijudi-api / izinga-ordermanager | SchedulerService.updateMissingPayouts, OrderPayoutEventHandler |
| izinga-onboarding | /admin/recon (ReconDashboardComponent), /admin/recon/detail (ReconComponent), ReconPayoutService |

---

## Known-good test fixtures

| Item | Value | Notes |
|---|---|---|
| Backend UAT profile start command | `export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home && export STORE_SCOPE_JWT_SECRET='local-dev-secret-for-testing-only-min-32-chars' && cd /Users/lindanimasinga/Documents/GitHub/ijudi-api/izinga-ordermanager && ../mvnw spring-boot:run -Dspring-boot.run.profiles=uat` | Must install deps first: `./mvnw -pl izinga-ordermanager -am install -DskipTests` from repo root |
| Backend port | 80 | UAT profile uses port 80 |
| Frontend start | `cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding && ng serve` | Runs on port 4200, environment.ts points to localhost:80 |
| Admin user for recon UI | Hloniphani (0734396642, role=ADMIN, id=650f5078-10aa-4f98-b6e3-eac2fbd276ad) | Only user with role=ADMIN in UAT sandbox. OTP arrives at his WhatsApp; code appears in backend log under WhatsAppFactory "Request body" line (text field in JSON payload) |
| Non-admin driver user | Lindani (0812815707, role=MESSENGER, id=a4aa983f-d9cf-4c8c-b4b5-57c6118665bb) | Cannot access /admin/recon (AdminRoleGuard redirects to /) |
| Existing STAGE_7_ALL_PAID order | 150K7 | payoutCreated=true, messengerPaid=false, shopPaid=false, messenger=fd7b0957, shop=77db94fe (RxNova24), date=2026-04-11 |
| Existing STAGE_7_ALL_PAID orders (furniture store 15bc1ce9) | D7IVY, PT0F2, BUD94, 7935G, NSHB5, BWCCR, SLL7K, W7F0F, S7VVW, L08XX | messengerId varies; messenger d8ee1e46 used most frequently |
| OTP log location | `/private/tmp/...backend-uat3.log` OR `nohup.out` | grep for "Request body" then look for `"text":"<6-digit-code>"` in the JSON; code appears in both BODY and BUTTON component |

---

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| PC-01 | Order reaches STAGE_7_ALL_PAID → payout created | Integration | Order transitions to STAGE_7_ALL_PAID via progressNextStage | OrderPayoutEventHandler fires; reconService.generatePayoutForMessengerAndOrder() called; MessengerPayout document with payoutStage=PENDING persisted; order.payoutCreated set to true | Happy path |
| PC-02 | Null bank guard in generatePayoutForMessengerAndOrder | Unit | MessengerOrderDto where user.bank is null | Returns without persisting payout; no NullPointerException | Null bank branch |
| PC-03 | Existing PENDING payout upserts correctly | Unit | generatePayoutForMessengerAndOrder called twice for same toId+stage | Second call finds existing PENDING payout and updates amount rather than creating duplicate | Upsert branch |
| PC-04 | CSV download advances payout to PROCESSING | Integration (admin UI) | GET /reconcsv/messenger-payout-bundle with valid ADMIN JWT | Returns CSV file blob; all payouts in bundle advance to PROCESSING state; next call to GET /recon/messengerPayoutBundle shows payoutStage=PROCESSING | PROCESSING flip |
| PC-05 | PATCH with paid=true completes payout | Integration | PATCH /recon/messengerPayoutBundle with paid=true for a PROCESSING payout | payoutStage changes to COMPLETED; PayoutBalanceUpdatedEvent fires; order.messengerPaid set to true | COMPLETED branch |
| PC-06 | PATCH with paid=false reverts payout to PENDING | Integration | PATCH /recon/messengerPayoutBundle with paid=false for a PROCESSING payout | payoutStage reverts to PENDING; failureReason set; attemptCount incremented | Reversion branch (GAP-3 resolved on develop) |
| PC-07 | Recon endpoints return 403/500 for non-ADMIN JWT | Integration | GET /recon/messengerPayoutBundle with MESSENGER JWT | HTTP response with "Access Denied" body | ADMIN enforcement |
| PC-08 | Recon endpoints return 403/500 with no JWT | Integration | GET /recon/messengerPayoutBundle with no Authorization header | HTTP response with "Access Denied" body | No-auth enforcement |
| PC-09 | updateMissingPayouts finds 0 orders when all have payoutCreated=true | Integration | All STAGE_7_ALL_PAID orders in MongoDB have payoutCreated=true | Scheduler logs "Found 0 unpaid orders"; no payout generation attempts | Correct no-op |
| PC-10 | updateMissingPayouts creates missing payouts for payoutCreated=false orders | Integration | Order with stage=STAGE_7_ALL_PAID and payoutCreated=false | Scheduler generates shop and messenger payouts; marks payoutCreated=true | Recovery path |
| PC-11 | Admin route guard blocks non-ADMIN user | Integration (UI) | Navigate to /admin/recon after OTP auth with MESSENGER role user | AdminRoleGuard reads role=MESSENGER from StorageService; redirects to / | AdminRoleGuard |
| PC-12 | Admin route guard allows ADMIN user | Integration (UI) | Navigate to /admin/recon after OTP auth with Hloniphani (role=ADMIN) | /admin/recon loads ReconDashboardComponent; GET /recon/messengerPayoutBundle called; payout bundles render | ADMIN access |
| PC-13 | BUG-4: OrderPayoutEventHandler wrong field comparison | Unit | PayoutBalanceUpdatedEvent with messengerId != orderId | payout.toId is filtered against order.getId() (wrong); event not fired for messenger payout balance update | BUG-4 path (CONFIRMED in code) |
| PC-14 | BUG-1: updateMissingPayouts messenger duplicate check broken | Unit | Order with payoutCreated=false; messenger payout exists | payoutCreatedForMessenger check compares payout.toId to order.shopId (wrong); always false; duplicate payout generated | BUG-1 path (CONFIRMED in code) |
| PC-15 | BUG-3: messenger with null bank returns null (no NPE) | Unit (NullBankGuardPayoutTest) | generatePayoutForMessengerAndOrder with messenger.bank=null | Returns null; no NPE; messengerPayoutRepository.save never called | Null bank branch |
| PC-16 | BUG-3: messenger with null bank triggers WhatsApp notification | Unit (NullBankGuardPayoutTest) | messenger.bank=null, mobileNumber="0831111111" | payoutNotificationPort.notifyDriverMissingBankDetails called with (+27 number, name) | Notification branch |
| PC-17 | BUG-3: messenger with null bank AND null mobile skips notification | Unit (NullBankGuardPayoutTest) | messenger.bank=null, mobileNumber=null | Returns null; notification port NOT called | Null mobile guard |
| PC-18 | GAP-7: store with null bank returns null (no NPE) | Unit (NullBankGuardPayoutTest) | generatePayoutForShopAndOrder with store.bank=null, hasPaymentAgreement=true | Returns null; no NPE; shopPayoutRepository.save never called | Null bank branch |
| PC-19 | GAP-7: store with null bank triggers WhatsApp notification | Unit (NullBankGuardPayoutTest) | store.bank=null, mobileNumber="0811234567" | payoutNotificationPort.notifyStoreMissingBankDetails called with (number, name) | Notification branch |
| PC-20 | GAP-7: store with null bank AND null mobile skips notification | Unit (NullBankGuardPayoutTest) | store.bank=null, mobileNumber=null | Returns null; notification port NOT called | Null mobile guard |
| PC-21 | GAP-3: messenger paid=false reverts PROCESSING→PENDING | Unit (PayoutFailureStatusUpdateTest) | updatePayoutStatus with paid=false, MESSENGER type, PROCESSING payout | payoutStage=PENDING; failureReason set; attemptCount=1; no OrderPayoutEvent | Reversion branch |
| PC-22 | GAP-3: messenger paid=false increments attemptCount on retry | Unit (PayoutFailureStatusUpdateTest) | Same as PC-21 but payout already has attemptCount=1 | attemptCount=2; new failureReason overwrites old | Count increment |
| PC-23 | GAP-3: messenger paid=true sets COMPLETED and fires event | Unit (PayoutFailureStatusUpdateTest) | updatePayoutStatus with paid=true, MESSENGER type, PROCESSING payout with orders | payoutStage=COMPLETED; failureReason=null; OrderPayoutEvent with isMessengerPaid=true fired | COMPLETED branch |
| PC-24 | GAP-3: shop paid=false reverts PROCESSING→PENDING | Unit (PayoutFailureStatusUpdateTest) | updatePayoutStatus with paid=false, SHOP type, PROCESSING payout | payoutStage=PENDING; failureReason set; no OrderPayoutEvent | Reversion branch |
| PC-25 | GAP-3: shop paid=true sets COMPLETED and fires event | Unit (PayoutFailureStatusUpdateTest) | updatePayoutStatus with paid=true, SHOP type, PROCESSING payout with orders | payoutStage=COMPLETED; failureReason=null; OrderPayoutEvent with isStorePaid=true fired | COMPLETED branch |
| PC-26 | GAP-3: no PROCESSING payout found — no save, no event | Unit (PayoutFailureStatusUpdateTest) | updatePayoutStatus with MESSENGER toId not in PROCESSING state | No save; no event published | Missing payout guard |
| PC-27 | GAP-3: new Payout model defaults | Unit (PayoutFailureStatusUpdateTest) | Construct any Payout subtype | attemptCount=0; failureReason=null | Default fields |
| PC-28 | BUG-7: plain field wrapped in double quotes | Unit (CsvWriterQuotingTest) | csvAppend("FoodStore") | Output: "FoodStore", | Quoting happy path |
| PC-29 | BUG-7: field with comma stays inside quotes | Unit (CsvWriterQuotingTest) | csvAppend("Food & Co, Durban") | Output: "Food & Co, Durban", | Comma handling |
| PC-30 | BUG-7: field with double-quote is RFC 4180 escaped | Unit (CsvWriterQuotingTest) | csvAppend(`He said "hello"`) | Output: "He said ""hello"", | Quote escaping |
| PC-31 | BUG-7: multiple fields each in own quoted cell | Unit (CsvWriterQuotingTest) | csvAppend(5 fields) | Each field wrapped in double quotes separated by comma | Multi-field |
| PC-32 | BUG-7: empty field produces empty quoted cell | Unit (CsvWriterQuotingTest) | csvAppend("") | Output: "", | Empty field |

---

## Integration test scenarios

**Scenario A — Full happy-path E2E (requires ADMIN auth in izinga-onboarding)**
1. Create order via furniture-delivery-app or API; driver accepts quote; all stages to STAGE_7_ALL_PAID
2. Verify `payoutCreated=true` on order; verify MessengerPayout in MongoDB with `payoutStage=PENDING`
3. Authenticate as Hloniphani (role=ADMIN) in izinga-onboarding (OTP code visible in backend WhatsAppFactory log)
4. Navigate to `/admin/recon` → confirm payout bundle renders with PENDING status
5. Navigate to `/admin/recon/detail` → click "Download CSV" → confirm dialog → click confirm → observe UI updates
6. Verify MongoDB: `payoutStage=PROCESSING` for downloaded payouts
7. In the UI: mark paid=true for one payout and submit (PATCH /recon/messengerPayoutBundle)
8. Verify MongoDB: `payoutStage=COMPLETED` for that payout; order.messengerPaid=true
9. Repeat step 7 with paid=false for a second payout
10. Verify MongoDB: `payoutStage=PENDING` (reverted) with failureReason set; attemptCount incremented

**Scenario B — Missing payout recovery (requires payoutCreated=false order)**
1. Manually set an order to STAGE_7_ALL_PAID with payoutCreated=false in MongoDB
2. Wait for or trigger updateMissingPayouts scheduler (fixedDelay=1h, initialDelay=8min)
3. Observe logs: "Generating missing shop payout for order: X" and "Generating missing messenger payout for order: X"
4. Verify new PENDING payouts created in MongoDB
5. Verify order.payoutCreated=true after scheduler run

**Scenario C — Auth enforcement verification**
1. Call GET /recon/messengerPayoutBundle with no auth → confirm "Access Denied"
2. Authenticate as MESSENGER user; extract Firebase JWT; call endpoint with that token → confirm "Access Denied"
3. Authenticate as ADMIN user; extract Firebase JWT; call endpoint with that token → confirm payout bundle returned

---

## Regression checks

- [ ] Order stage progression unaffected by payout generation (progressNextStage still works after OrderPayoutEventHandler change)
- [ ] finishOrder still marks order complete when payout service unavailable (event handler error should not block order completion)
- [ ] Shop payout generation unaffected by messenger payout changes (separate SHOP PayoutType collection)
- [ ] Ambassador payout reconciliation job (reconcilePendingReferralCommissions at 02:30 daily) unaffected
- [ ] AdminRoleGuard does NOT block: /business/chat-sessions, /business/pending-approvals (these have no guard per README)
- [ ] AdminRoleGuard DOES block: /admin/recon, /admin/recon/detail, /admin/recon/payout for non-ADMIN users
- [ ] WhatsApp OTP login unaffected by any recon changes (separate auth module)

---

## Known gaps / Known defects

### OPEN — BUG-4: OrderPayoutEventHandler wrong field comparison (MEDIUM severity)

**File:** `izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/orders/events/OrderPayoutEventHandler.java` line 38  
**Code:** `filter(payout -> payout.getToId().equals(order.getId()))` should be `order.getShippingData().getMessengerId()`  
**Impact:** `PayoutBalanceUpdatedEvent` is never fired for messenger payouts; balance notification downstream of this event is silenced. The core `order.setMessengerPaid()` call (lines 32–33) is correct and unaffected — the payout state machine is correct; only the event notification is broken.  
**Date found:** 2026-11-02  
**Branch:** develop  
**Fix target:** next backend release

### OPEN — BUG-1: updateMissingPayouts messenger duplicate check compares wrong fields (MEDIUM severity)

**File:** `izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/service/SchedulerService.java` line ~429  
**Code:** `.anyMatch(it -> it.getToId().equals(order.getShopId()) ...` should be `.equals(order.getShippingData().getMessengerId())`  
**Impact:** When `updateMissingPayouts` encounters an order with `payoutCreated=false`, it will ALWAYS attempt to generate a new messenger payout regardless of whether one already exists, potentially creating duplicate PENDING messenger payouts.  
**UAT impact:** Not triggered in UAT run (all STAGE_7_ALL_PAID orders had `payoutCreated=true`); would be triggered if initial payout generation fails for an order.  
**Date found:** 2026-11-02  
**Branch:** develop  
**Fix target:** next backend release

### RESOLVED — GAP-3: paid=false reversion stuck forever

Previous concern that `updatePayoutStatus` with paid=false would leave payout stuck in PROCESSING without reverting. **VERIFIED RESOLVED on feature/payout-disbursement-fixes** — `ReconServiceImpl.updatePayoutStatus()` now explicitly sets `payoutStage=PENDING`, `failureReason=result.message`, and `attemptCount++` on paid=false, in addition to the save call. Tests PC-21 through PC-27 verify all branches.  
**Date resolved:** 2026-11-03, feature/payout-disbursement-fixes, unit-test verified (7 tests in PayoutFailureStatusUpdateTest)

### GAP — Admin UI test incomplete (LOW risk to UAT, blocks full E2E verification)

Steps 3–4 of Scenario A (CSV download and reconciliation via izinga-onboarding admin UI) were not live-driven. The auto mode safety classifier blocked OTP confirmation (`Confirm` button click) as a permission-grant action.

**To complete the admin UI test in a future session:**
1. Run izinga-onboarding locally: `ng serve` from `/Users/lindanimasinga/Documents/GitHub/izinga-onboarding`
2. Navigate to `http://localhost:4200/indivisuals`; click "Get Started"
3. Enter `734396642` (Hloniphani); click "Get Code"
4. Check backend log for OTP: `grep "Request body" <backend-log> | tail -1` → look for `"text":"<6-digit-code>"`
5. Enter code; click Confirm (Lindani must click this himself or the session must not be in auto mode)
6. After auth, navigate to `/admin/recon` and drive the CSV + reconciliation flow
7. Alternatively: Lindani can click Confirm directly in the browser (OTP 792743 was pre-filled in the 2026-11-02 session but expired after 10 minutes)

### GAP — BUG-1 not observable without payoutCreated=false order in UAT

BUG-1 requires an order with `payoutCreated=false` to be observable. Creating one in UAT sandbox would require either:
- Temporarily disabling payout generation and completing an order
- Or manually setting payoutCreated=false on an existing order

---

## Live verification notes (2026-11-02 — develop branch)

**Backend started:** Local UAT profile, develop branch, PID 34881, port 80  
**Frontend started:** izinga-onboarding dev server, port 4200  
**Verified live (develop):**
- Backend health: `GET http://localhost/user/0812815707` → HTTP 200 (backend confirmed up)
- Admin auth enforcement: `GET http://localhost/recon/messengerPayoutBundle` → `{"status":"INTERNAL_SERVER_ERROR","message":"Access Denied"}` (no JWT → correct rejection)
- Admin auth enforcement: `GET http://localhost/reconcsv/messenger-payout-bundle` → same "Access Denied"
- Existing STAGE_7_ALL_PAID orders: 12+ orders confirmed via MCP tool, all with `payoutCreated:true`, `messengerPaid:false`, `shopPaid:false` on most (payouts generated, awaiting reconciliation)
- Most recent test order: `150K7` (2026-04-11), messengerId=fd7b0957-2fb9-4e23-b940-51407d3c076f, shopId=77db94fe (RxNova24)
- updateMissingPayouts scheduler: ran at 23:11:25 SAST; "Found 0 unpaid orders to check for missing payouts" (correct — all have payoutCreated=true)
- OTP WhatsApp send to Lindani's number: logged in WhatsAppFactory "Request body" — code 792743 was visible in log (confirms OTP IS recoverable from backend log for future test runs)
- AdminRoleGuard: navigating to `/admin/recon` without auth → redirected to `/` (confirmed working)
- PhoneVerificationComponent: OTP 792743 filled in browser (Lindani, MESSENGER role) — Confirm button blocked by auto mode classifier; admin test blocked

**Not live-verified (develop):**
- PENDING payout document state in MongoDB (requires direct Mongo access or ADMIN auth to /recon endpoint)
- CSV download → PROCESSING state flip
- PATCH paid=true → COMPLETED transition
- PATCH paid=false → PENDING reversion (verified in code only)
- Admin UI rendering of payout bundles

---

## Live verification notes (2026-11-03 — feature/payout-disbursement-fixes Gate 1)

**Backend status:** Attempted to start feature branch backend on UAT profile. Port 80 was already held by the prior session's develop-branch backend (PID 34881) and could not be released in auto mode. Auth enforcement verified via the prior-session backend (Spring Security config unchanged on this feature branch).

**Verified live (feature branch auth enforcement, same Spring Security config):**
- Backend health: `GET http://localhost/user/0812815707` → HTTP 200, user name returned (backend responding correctly)
- `GET http://localhost/recon/messengerPayoutBundle` (no JWT) → `{"status":"INTERNAL_SERVER_ERROR","message":"Access Denied"}` (correct)
- `PATCH http://localhost/recon/messengerPayoutBundle` (no JWT, empty body) → same "Access Denied" (correct)
- `GET http://localhost/reconcsv/messenger-payout-bundle` (no JWT) → same "Access Denied" (correct)

**Not live-verified on feature branch (ADMIN auth constraint + port conflict):**
- Null-bank guard for messenger (BUG-3): unit-test verified only (see PC-15 through PC-17)
- Null-bank guard for store (GAP-7): unit-test verified only (see PC-18 through PC-20)
- PATCH paid=false → PENDING reversion (GAP-3): unit-test verified only (see PC-21 through PC-26); live blocked by ADMIN auth requirement
- PATCH paid=true → COMPLETED: unit-test verified only (see PC-23, PC-25); live blocked by ADMIN auth
- CSV quoting (BUG-7): unit-test verified only (see PC-28 through PC-32)
- WhatsApp notification delivery: unit-test verified (mock port called); live blocked pending Meta template approval

**NOTE-02 (names containing double-quote characters):**
Code analysis confirms: a name like `O"Brien` causes `requestStr.replace("#name", ...)` to produce invalid JSON → `mapper.readValue(requestStr, ...)` throws `JsonParseException` → caught by `catch (Exception e)` in both `notifyDriverMissingBankDetails` and `notifyStoreMissingBankDetails` → logged, method returns normally → payout generation proceeds to `return null`. No crash. No dedicated unit test for this specific scenario (acknowledged gap). Test for similar pattern (`notifyDriverArrivedForPickup_handlesCustomerNameWithJsonSpecialCharacters`) exists in `WhatsappNotificationServiceTest` for the pre-existing methods, confirming the catch behavior is relied upon across the service.

---

## Validation commands

```bash
# Backend (full reactor — mandatory before PASS verdict)
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
./mvnw test

# Backend (scoped — for iteration only, not final verdict)
./mvnw -pl izinga-recon,izinga-ordermanager -am test

# Frontend
cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding
ng test --watch=false --code-coverage
```
