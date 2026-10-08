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

## Maintenance rule

Whenever a live or regression test is run against a feature covered here, **update the corresponding file** (test cases, known-good values, last-verified date, any newly discovered defects) instead of creating a throwaway report only. A one-off test *report* (dated, point-in-time, with pass/fail verdict and defects found) is still worth writing separately — but the reusable *test plan* in this library must be kept current so the next QA pass starts from here instead of re-reading application code from scratch.

When a feature is tested for the first time and has no file here yet, create one following the format in [`../../../.claude/agents` — iZinga QA & Test Automation agent](../../../README.md) (Scope, Repos affected, Risk areas, Test cases table, Integration scenarios, Regression checks, Validation commands), plus a "Known-good test fixtures" section recording real values (test accounts, coordinates, category/driver-type mappings) that worked, so future runs don't have to rediscover them.
