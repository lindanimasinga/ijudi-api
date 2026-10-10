# iZinga Test Plan Library

Persistent, reusable test plans for manual and automated QA passes across iZinga repos, structured **per userType then per feature** so a test plan can be found and reused without re-deriving flow knowledge from code each time.

## Structure

```
test-plans/
  customer/
    <feature>.md
  driver/
    <feature>.md
  merchant/
    <feature>.md
  admin/
    <feature>.md
```

Each userType directory holds one file per feature. A feature that spans two userTypes (e.g. a delivery that involves both a customer and a driver) gets a primary file under the userType that initiates it, cross-linked from the other userType's directory.

## Index

| userType | Feature | File | Last verified |
|---|---|---|---|
| customer | Furniture/parcel booking | [customer/furniture-booking.md](customer/furniture-booking.md) | 2026-10-08 |
| driver | Quote acceptance & delivery fulfilment | [driver/quote-acceptance-and-fulfilment.md](driver/quote-acceptance-and-fulfilment.md) | 2026-10-08 |
| store | PayFast subscription billing — tier selection, checkout, ITN activation | [store/payfast-subscription-billing.md](store/payfast-subscription-billing.md) | 2026-10-08 |
| store | EWALLET bank validation carve-out + STORE-BANK-01 fallback fix (POST /user, PATCH /user/{id}, POST /store) | [store/ewallet-bank-validation.md](store/ewallet-bank-validation.md) | 2026-10-08 |
| admin | Chat sessions — list, filter, thread view, send message | [admin/chat-sessions.md](admin/chat-sessions.md) | 2026-10-08 |
| admin | Pending approvals — profile review, service-type filter, approve gate | [admin/pending-approvals.md](admin/pending-approvals.md) | 2026-10-08 |
| customer (all roles) | Terms & ICA acceptance — all 4 branches, proactive dashboard profile-completeness gate (TC-PROF-01–06), blank-imageUrl stripping, profile-incomplete CTA, fixed-bottom button translucency fix (BUG-1), post-ICA training invisible button fix (BUG-2), Ambassador ICA v2→v3 version bump (TC-55, TC-56, TC-DASH-10b) | [customer/terms-acceptance.md](customer/terms-acceptance.md) | 2026-10-09 |
| driver (all roles) | Phone verification & WhatsApp OTP login — OTP send/verify, double-submit guard (ONB-BUG-03), SMS fallback via triple-tap, phone normalisation, returnUrl redirect | [driver/phone-verification.md](driver/phone-verification.md) | 2026-10-09 |
| individual / driver / ambassador | Welcome page card theme consistency — /indivisuals route, all 3 role contexts (individual benefits grid, driver 9-step flow, ambassador 4-step flow) | [individual/welcome-card-theme.md](individual/welcome-card-theme.md) | 2026-10-09 |

## Maintenance rule

Whenever a live or regression test is run against a feature covered here, **update the corresponding file** (test cases, known-good values, last-verified date, any newly discovered defects) instead of creating a throwaway report only. A one-off test *report* (dated, point-in-time, with pass/fail verdict and defects found) is still worth writing separately — but the reusable *test plan* in this library must be kept current so the next QA pass starts from here instead of re-reading application code from scratch.

When a feature is tested for the first time and has no file here yet, create one following the format in [`../../../.claude/agents` — iZinga QA & Test Automation agent](../../../README.md) (Scope, Repos affected, Risk areas, Test cases table, Integration scenarios, Regression checks, Validation commands), plus a "Known-good test fixtures" section recording real values (test accounts, coordinates, category/driver-type mappings) that worked, so future runs don't have to rediscover them.

## Admin pages — shared gotchas

These apply to every file in `admin/` and must be understood before testing any admin route in `izinga-onboarding`:

- **No route guard on admin routes.** Both `/business/chat-sessions` and `/business/pending-approvals` can be reached after any OTP login — no `role: ADMIN` check is enforced at the Angular router level. On UAT/local the sandbox DB has no ADMIN user, so the admin dashboard tiles do not render, but navigating directly to the route loads the component.
- **Sandbox has no ADMIN user.** UAT/local testing must navigate directly to the admin route after any OTP login. Do not wait for an admin dashboard tile that will never appear.
- **Verify colours with `getComputedStyle`, never by reading class names.** The app loads Bootstrap 4.5.0 but uses Bootstrap 5 utility classes (`me-*`, `gap-*`, `fw-bold`, `visually-hidden`, `btn-close`). These are now shimmed globally by `src/styles/bootstrap5-compat.css`, but class-name inspection is unreliable — a class can be present and still have no visual effect if the shim is incomplete or reverted. Always confirm colours, spacing, and visibility using `getComputedStyle` in the browser.
- **Icons must be Material ligatures.** Font Awesome is **not** loaded in `izinga-onboarding`. Any element using `fa fa-*` classes renders nothing (zero width). All icon assertions must confirm `offsetWidth > 0` on the icon element; a non-zero width confirms a Material ligature is rendering correctly.
