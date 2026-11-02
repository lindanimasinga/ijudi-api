# Test Plan: Engineering — Referral Commission Nightly Scheduler Wiring

**userType:** engineering (CI regression — no user-facing flow)
**Feature:** `@Scheduled(cron = "0 30 2 * * *")` annotation present and correct on `ReconServiceImpl.reconcilePendingReferralCommissions()` — the nightly fallback that picks up PENDING referral commission records with no linked payout
**App:** `ijudi-api` (`izinga-recon`)
**Last verified:** 2026-11-02, automated CI, branch `feature/gap5-referral-reconciliation-scheduler-coverage`, commit `94c06f8` — PASS (1/1 test, 0 failures)

## Scope

Closes GAP-5 from the payout test-plan pass. The gap was: `reconcilePendingReferralCommissions()` had zero test coverage on its scheduler trigger. If the `@Scheduled` annotation were accidentally removed or the cron expression changed, nightly referral commissions would silently stop being paid with no CI alarm.

This plan covers only the scheduler-wiring assertion. Behavioral coverage (happy path, skip-existing, skip-PAID, skip-no-bank, furniture type) is in `GenerateReferralPartnerPayoutTest.kt` which already has 12 test cases.

**Out of scope:** the live business behavior of the reconciliation job itself — that is covered by the behavioral tests in `GenerateReferralPartnerPayoutTest.kt`.

## Repos affected

- `ijudi-api` — `izinga-recon` (`ReconServiceImpl.kt`, `ReconServiceImplSchedulerWiringTest.kt`)

## Known-good test fixtures

- No live fixtures needed. Pure reflection test — no Spring context, no database.
- Java reflection target: `ReconServiceImpl::class.java.getDeclaredMethod("reconcilePendingReferralCommissions")`
- Expected annotation: `@Scheduled(cron = "0 30 2 * * *")` (02:30 daily, runs after nightly payout bundle)

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| SC-01 | `reconcilePendingReferralCommissions` carries `@Scheduled` | Unit (reflection) | `getDeclaredMethod("reconcilePendingReferralCommissions").getAnnotation(Scheduled::class.java)` | Non-null `Scheduled` annotation | Catches accidental annotation removal |
| SC-02 | cron expression is exactly `0 30 2 * * *` | Unit (reflection) | `scheduled.cron` | `"0 30 2 * * *"` | Catches wrong time, extra/missing field, or mistaken Quartz format |

## Integration test scenarios

N/A — this is a CI-time structural check, not an integration scenario.

## Regression checks

- [ ] `ReconServiceImpl.reconcilePendingReferralCommissions()` still has `@Scheduled(cron = "0 30 2 * * *")` after any refactor of the method signature or class constructor
- [ ] Behavioral test class `GenerateReferralPartnerPayoutTest` still passes (12 tests) after any change to `ReconServiceImpl`
- [ ] Full reactor `./mvnw test` passes with zero failures after any change in `izinga-recon`

## Known gaps / Known defects

No known gaps. Prior to this plan, GAP-5 was open.

**Defects:** None.

**Previously open gaps now closed:**
- GAP-5 (closed 2026-11-02): scheduler-registration not covered by any test. Fixed by `ReconServiceImplSchedulerWiringTest` (commit `94c06f8`).

**Note on doc comment:** The KDoc comment above `reconcilePendingReferralCommissions()` previously claimed the method "runs daily (and on-demand)". No on-demand call site was found in any controller, service, or event handler. The comment was corrected in the same commit to read "scheduled-only; no on-demand call site exists". If an on-demand trigger is added in future (e.g. via a ReconController endpoint), update both the doc comment and this plan.

## Validation commands

```bash
# Scoped (fast iteration):
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
./mvnw -pl izinga-recon -am test

# Full reactor (mandatory before any PASS verdict):
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
./mvnw test
```
