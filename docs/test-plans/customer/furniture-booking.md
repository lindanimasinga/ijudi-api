# Test Plan: Customer — Furniture/Parcel Booking

**userType:** customer
**Feature:** Category selection → item selection → address entry → OTP login → checkout → shipping → delivery readiness checklist → order creation
**App:** `furniture-delivery-app` (Angular), backend `ijudi-api` (`izinga-ordermanager`)
**Last verified:** 2026-10-08, live E2E run, order `JK19S` — PASS

## Scope

Covers the full customer journey from landing page through order creation (`STAGE_0_CUSTOMER_NOT_PAID`). Does **not** cover payment (see note under Known gaps) or anything past order creation — that's the driver's side, see [`../driver/quote-acceptance-and-fulfilment.md`](../driver/quote-acceptance-and-fulfilment.md).

## Repos affected

- `furniture-delivery-app` (frontend)
- `ijudi-api` — `izinga-ordermanager` (`OrderServiceImpl.startOrder`), `izinga-usermanagement` (OTP/profile), `izinga-messaging` (WhatsApp OTP + quote notification)

## Known-good test fixtures

- **Category → driver-type mapping** (`furniture-selection.component.ts` `tagDriverTypes`):
  - `Small Parcels` → `['Bike Delivery Driver', 'Small/Medium Vehicle Driver']`
  - `Parcels` → same as above
  - `Large Furniture` → `['Truck Delivery Driver']`
  - `Small Furniture & Large Parcels` → `['Bakkie Delivery Driver', 'Truck Delivery Driver']`
  - `Medicine` → `['Small/Medium Vehicle Driver', 'Bike Delivery Driver']`
- **Test store** (hard-coded, single-store model): id `15bc1ce9-3a0b-42f7-a1d8-34ffbb9a7d22` — must have `isQuoteRequired: true` or no driver notification fires at all.
- **To exercise real driver geo-matching:** place the test driver's `latitude`/`longitude` within 10km of the address you type as pickup (`MessengerLookUpService.findNearbyMessengers` radius). Cape Town CBD coordinates used successfully: driver at `(-33.9221, 18.4231)`, pickup address "15 Long Street, Cape Town City Centre, Cape Town, 8001" (backend geocoded to `-33.9202916, 18.4217393`, well within range).
- A fresh customer phone number is required per test run (OTP login creates a placeholder `userProfile` keyed to the number) — reusing a number that already has a role assigned will skip signup-specific paths.

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| TC-01 | Category selection loads live categories | E2E | Navigate to home | Category tiles render from live store data (`Small Parcels`, `Small Furniture & Large Parcels`, `Large Furniture`) | Confirms `storeId` + `izingaUrl` env wiring |
| TC-02 | Item selection + address entry (happy path) | E2E | Select item, type pickup/drop-off address, agree to T&Cs | Form validates, Continue enabled | — |
| TC-03 | Address entry without Google Places Autocomplete | E2E | Type full address text directly into the field (bypassing the suggestion dropdown) | Address string still saved; later checkout step still produces a correctly server-geocoded quote | Known gap — see below. Confirms backend geocodes the typed string independently of the client-side Places widget. |
| TC-04 | New customer OTP login | E2E | Fresh phone number, request code, enter correct code | Real WhatsApp OTP received, login succeeds, placeholder `userProfile` created with `role=null` | Verify via backend log: `"Created placeholder UserProfile ... role=null via WhatsApp OTP login"` |
| TC-05 | Checkout shows real server-computed quote | E2E | After login, on Checkout screen | Distance, driver type, delivery fee, service fee, total all computed server-side and match `shippingData` on the created order | — |
| TC-06 | Shipping form validation (`invalidData()`) | E2E | Leave required field blank (name / address / mobile / building type) | Continue button (`*ngIf="!invalidData()"`) stays hidden until all required fields + T&Cs are filled | `deliverySchedule` defaults to `LATER` (`SCHEDULED_DELIVERY`) — date/time fields are **required** even though the NOW/LATER toggle UI is currently commented out in the template; must fill date+time or Continue never appears |
| TC-07 | Delivery readiness checklist | E2E | Check all 5 confirmation boxes | Continue enabled, submitting creates the order | — |
| TC-08 | Order creation triggers driver geo-matching | E2E (cross-boundary) | Submit checklist with ≥1 approved driver within 10km of pickup | `OrderQuoteCreatedEventHandler` logs `Found N nearby messengers`; matched driver(s) receive real WhatsApp `quote_request_for_acceptance2` | Requires `isQuoteRequired: true` on the store |

## Integration test scenarios

- **End-to-end booking → driver notified:** full TC-01 through TC-08 in sequence against a live/UAT backend, with a real approved driver in range — confirms the whole customer→driver handoff, not just isolated units.
- **Quote not required:** store with `isQuoteRequired: false` — order should create without any `OrderQuoteCreatedEvent`/driver notification (not yet re-verified this cycle; was true per code read).

## Regression checks

- [ ] Category tiles still pull from live store, not stale/cached data
- [ ] `tagDriverTypes` mapping unchanged (or test cases above updated if it changes)
- [ ] `deliverySchedule` default and the commented-out NOW/LATER toggle — if someone re-enables the toggle, re-verify TC-06 still requires date/time only when `LATER` is selected
- [ ] OTP placeholder creation still uses `role=null`, not `CUSTOMER` (regression here breaks the driver-onboarding-via-same-number flow)

## Known gaps / environment issues (not booking-logic bugs, but block full E2E verification)

- **Google Maps Places Autocomplete does not load on `localhost`** (`RefererNotAllowedMapError`) — the pickup/drop-off `app-place-autocomplete` widget never initializes in local/dev testing, so `lat`/`long` outputs from it can't be exercised; TC-03 above is the workaround. Needs the Maps API key's allowed referrers updated in GCP Console to include local dev origins, or this stays a permanent gap in local verification.
- **Yoco payment cannot be completed in UAT** — see `../driver/quote-acceptance-and-fulfilment.md` Known gaps; this blocks a *fully* unattended customer-side test (payment step currently needs a manual DB stage bypass).

## Validation commands

- `ng test --watch=false --code-coverage` (unit, `furniture-delivery-app`)
- `./mvnw test` (full reactor, `ijudi-api` — no `-pl` exclusions)
- Manual E2E: see steps above against a UAT-pointed local backend (`-Dspring-boot.run.profiles=uat`)
