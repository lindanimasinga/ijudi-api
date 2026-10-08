# Test Plan: Driver — Quote Acceptance & Delivery Fulfilment

**userType:** driver (messenger)
**Feature:** WhatsApp quote notification → accept/reject via web portal → pickup → drop-off → completion → payout
**App:** `furniture-delivery-app` `quote-approval` web portal (dual-purposed as the driver portal — **not** the Flutter `ijudi` driver app), backend `ijudi-api` (`izinga-ordermanager`, `izinga-recon`)
**Last verified:** 2026-10-08, live E2E run, order `JK19S`, driver Thabo — PASS (with defects, see below)

## Scope

Starts where [`../customer/furniture-booking.md`](../customer/furniture-booking.md) ends (order created, driver notified). Covers: driver receiving the real WhatsApp quote, accepting it via `/quote-approval/:orderId`, and progressing the order through pickup → drop-off → completion → payout generation.

## Repos affected

- `furniture-delivery-app` — `quote-approval.component.ts`
- `ijudi-api` — `izinga-ordermanager` (`OrderServiceImpl.acceptQuote`, `progressNextStage`), `izinga-messaging` (WhatsApp notifications), `izinga-recon` (`ReconServiceImpl.generatePayoutForMessengerAndOrder`)

## Known-good test fixtures

- **Driver account must satisfy all 4 `MessengerLookUpService` gates** to be matched at all:
  1. `termsAccepted == true`
  2. `profileApproved == true`
  3. `availabilityStatus` is `ONLINE` or `AWAY`
  4. `canCarryLoad` — `vehicle.loadCapacity` ≥ order weight AND `description` (e.g. `"Small/Medium Vehicle Driver"`) is in the order's `shippingData.category` list (see category→driver-type mapping in the customer test plan)
- Example known-good driver profile used successfully: role `MESSENGER`, `description: "Small/Medium Vehicle Driver"`, `vehicle.loadCapacity: 500`, location Cape Town CBD `(-33.9221, 18.4231)`.
- **The WhatsApp quote link cannot be tap-tested from code** — Meta template URL wiring isn't verifiable statically. To test the driver's acceptance flow, navigate directly to `/quote-approval/{orderId}` with the real order id from the sent notification.
- **Session isolation is required between customer and driver test actions.** If both are driven from tabs sharing one browser profile/localStorage (e.g. two tabs in the same Claude Browser session), the quote-approval page's "who am I" (`storageService.userProfile`) bleeds across tabs and the wrong party gets assigned as messenger (see Defect #1 below — this is both a test-methodology trap *and* a real product bug). **Use two separate browser profiles/apps for customer vs. driver testing** (e.g. Claude's own Browser pane for the customer, a real Chrome profile via the Claude-in-Chrome extension for the driver), not two tabs of the same profile.

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| TC-01 | Driver receives real WhatsApp quote notification | E2E | Order created with ≥1 matching driver in range | `quote_request_for_acceptance2` template sent, `message_status: accepted` | Verify via backend log, not just UI |
| TC-02 | Driver accepts quote via web portal | E2E | Navigate to `/quote-approval/{orderId}` **as the actual invited driver** (own OTP session), click Accept | Order's `shippingData.messengerId` set to the driver's own id; `tag.quoteAcceptedBy` set; customer receives `quote_accepted_customer` WhatsApp with payment link | **Must** use an isolated/driver-authenticated session — see fixtures note above |
| TC-03 | Start Pickup | E2E | Click "Start Pickup" | `GET /order/{id}/nextstage` fires; stage → `STAGE_2_STORE_PROCESSING`; opens Google Maps directions in a new tab (expected, not a bug — close it) | No customer WhatsApp fires at this stage |
| TC-04 | Notify Customer I Arrived (pickup) | E2E | Click "Notify Customer I Arrived" | Stage → `STAGE_3_READY_FOR_COLLECTION`; customer receives `driver_arrived_pickup` WhatsApp | — |
| TC-05 | Start Dropoff | E2E | Click "Start Dropoff" | Stage → `STAGE_4_ON_THE_ROAD`; opens Google Maps directions | — |
| TC-06 | Notify Customer Arrived (drop-off) | E2E | Click "Notify Customer Arrived" | Stage → `STAGE_5_ARRIVED`; customer receives `driver_arrived_dropoff` WhatsApp | — |
| TC-07 | Complete Delivery | E2E | Click "Complete Delivery" (may need to be clicked twice — see note) | Stage progresses `STAGE_6_WITH_CUSTOMER` → `STAGE_7_ALL_PAID` | Observed needing two clicks of the same button across two stage transitions in one run — confirm expected vs. a UI refresh lag before treating as a bug |
| TC-08 | Payout generation on completion | E2E (cross-boundary) | Order reaches `STAGE_7_ALL_PAID` | `ReconServiceImpl` creates/updates a `MessengerPayout` (bundled per driver per `PayoutStage.PENDING`, not per-order) containing this order; driver receives `messenger_order_completed` WhatsApp with the payout amount | Payout amount = delivery fee minus `izingaCommission`, **not** the full delivery fee — verify against `shippingData.izingaCommission` when reconciling |

## Integration test scenarios

- **Full driver fulfilment chain (TC-01 → TC-08)** against a live/UAT backend with real WhatsApp — confirms every stage transition fires the correct message to the correct party and the payout lands against the correct driver.
- **Payout vs. customer payment reconciliation:** compare the order's quoted total (delivery fee + service fee) against the driver's payout amount (delivery fee − commission) — iZinga's net take should equal commission + service fee exactly. See worked example in the 2026-10-08 test report.

## Regression checks

- [ ] `acceptQuote()` — re-verify after any fix to the identity-verification defect below; a fix changing how `messengerId` is resolved could change the request/response contract
- [ ] `progressNextStage` stage sequence unchanged (`STAGE_1 → 2 → 3 → 4 → 5 → 6 → 7`)
- [ ] Payout bundling still per-driver-per-`PayoutStage.PENDING` bucket, not per-order (no per-order payout lookup endpoint exists — query `MessengerPayout.orders` directly, or admin `GET /recon/payout?payoutType=MESSENGER&toId={driverId}...`, ROLE_ADMIN only)

## Known defects (found 2026-10-08, filed separately — do not re-discover, check status first)

- **[HIGH] Quote-accept IDOR:** `acceptQuote()` trusts the client-supplied `messengerId` with no check that it matches the actually-invited driver, and the frontend just uses whatever profile happens to be logged in (`quote-approval.component.ts:82`). Any party with the quote-approval link — including the customer — can currently accept it and redirect the delivery fee to themselves. Tracked as a follow-up task; check its status before assuming this is still open.
- **[HIGH] Yoco payment cannot be completed in UAT:** `izinga-pay`'s `yoco.component.ts:57` hardcodes `processingMode: "live"`; combined with the UAT backend's `yoco.api.key` apparently being a live key (not test), real Yoco checkouts are rejected ("Checkouts with live keys can not have HTTP protocol"). Until fixed, driver-fulfilment testing that needs to start from a genuinely-paid order must either get a real Yoco test key provisioned for UAT, or bypass the payment stage manually (document that the bypass happened — never silently skip it).
- **[HIGH] Duplicate `userProfile` / `mobileNumber` risk:** if the driver test account was created or PATCHed via the WhatsApp-OTP-placeholder path, check for duplicate `userProfile` documents on that `mobileNumber` before relying on OTP login for driver-session testing (see customer test plan's linked defects). A duplicate causes `IncorrectResultSizeDataAccessException` on login.

## Validation commands

- `ng test --watch=false --code-coverage` (unit, `furniture-delivery-app`)
- `./mvnw test` (full reactor, `ijudi-api`)
- Manual E2E: see steps above, with customer and driver driven from genuinely separate browser sessions
