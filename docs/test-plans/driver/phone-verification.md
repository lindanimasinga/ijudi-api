---
userType: driver (primary); also applies to Ambassador, STOREADMIN, and any role onboarded via izinga-onboarding
Feature: Phone verification & WhatsApp OTP login — OTP send, verify, double-submit guard (ONB-BUG-03), SMS fallback via triple-tap, phone normalisation
App: izinga-onboarding (onboarding.izinga.co.za / biz.izinga.co.za)
Last verified: 2026-10-09 | automated unit pass | PASS
---

## Scope

Covers `PhoneVerificationComponent` (`src/app/phone-verification/`) — the first-step OTP authentication screen used by all user types entering via izinga-onboarding:

- WhatsApp OTP flow (default): `sendWhatsAppOtp` → `verifyWhatsAppOtp` → `signInWithWhatsAppToken` → navigate to dashboard
- SMS fallback flow (hidden): activated by triple-tapping the "Registration" heading within 1 500 ms; uses Firebase Phone Auth (`requestVerification` → `confirmCode`)
- Phone number normalisation to E.164 (`+27XXXXXXXXX`) before all API/Firebase calls — prevents double-`+27` regression (ONB-REGRESSION-03)
- Double-submit guard (ONB-BUG-03): `isConfirming` TypeScript guard + `type="button"` on both action buttons; a rapid second click is a no-op; guard resets only on error (success navigates away)
- Return-URL redirect: if `StorageService.returnUrl` is set (set by the auth guard when a user reaches a deep link via QR), `confirmCode()` uses `navigateByUrl(returnUrl)` instead of navigating to the dashboard

**Out of scope:** post-login flows (ICA acceptance, training screen, dashboard); Firebase reCAPTCHA rendering in SMS mode; push-notification permission; deep-link QR referral logic beyond the returnUrl redirect.

Cross-links: ICA acceptance after a successful OTP login — see [customer/terms-acceptance.md](../customer/terms-acceptance.md).

## Repos affected

| Repo | Test work |
|---|---|
| izinga-onboarding | Primary — `PhoneVerificationComponent` (`.ts`, `.html`), spec: `phone-verification.component.spec.ts` |
| ijudi-api | Backend OTP endpoints: `POST /auth/whatsapp/otp/send`, `POST /auth/whatsapp/otp/verify`. Double-submit is observable in backend logs (two requests per click before the guard was added) |

## Known-good test fixtures

| Field | Value | Notes |
|---|---|---|
| Test phone (local format) | `0815551234` | Input as typed — normalised to `+27815551234` before API call |
| Test phone (already normalised) | `+27815551234` | Passed through as-is |
| Test phone (27-prefix) | `27815551234` | Normalised to `+27815551234` |
| Test OTP code | `654321` | Used across all WhatsApp OTP unit tests |
| Test SMS code | `123456` | Used in SMS confirmCode unit tests |
| Test custom token | `tok123` | Returned by `verifyWhatsAppOtp` spy in happy-path tests |
| returnUrl fixture | `/indivisuals/some-deep-route` | Stored in `StorageService.returnUrl`; consumed by `confirmCode()` |
| Triple-tap window | 1 500 ms | Three taps within this window activate SMS mode |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Component creates | Unit | Default | component truthy | Basic smoke |
| TC-02 | Starts in WhatsApp mode | Unit | Default | `loginMethod === 'whatsapp'` | Initial state |
| TC-03 | Triple-tap activates SMS mode + calls createCapture | Unit | 3 taps within 1 500 ms | `loginMethod === 'sms'`, `createCapture` called once | Triple-tap gesture |
| TC-04 | Slow taps do NOT activate SMS mode | Unit | Tap, wait 1 600 ms, tap, tap | `loginMethod === 'whatsapp'` | Timer expiry resets counter |
| TC-05 | Counter resets across multiple slow sequences | Unit | 3 sequences with 1 600 ms gaps | `loginMethod === 'whatsapp'` | Counter reset |
| TC-06 | Further taps ignored once in SMS mode | Unit | 3 quick + 3 more taps | `createCapture` called once | Guard on already-SMS mode |
| TC-07 | Triple-tap resets error state | Unit | `hasError=true`, 3 taps | `hasError=false, errorMessage=undefined, isVerificationRequested=false` | State reset on mode switch |
| TC-08 | WhatsApp OTP success — sets isVerificationRequested | Unit | `phoneNumber='0815551234'`, WhatsApp mode | `isVerificationRequested===true` | WhatsApp send success |
| TC-09 | WhatsApp OTP — calls sendWhatsAppOtp with normalised +27 number | Unit | `phoneNumber='0815551234'` | `sendWhatsAppOtp('+27815551234')` called | Phone normalisation |
| TC-10 | WhatsApp OTP send failure — sets hasError | Unit | sendWhatsAppOtp throws | `hasError===true` | WhatsApp send error |
| TC-11 | SMS requestVerification called in SMS mode | Unit | SMS mode + `phoneNumber='0815551234'` | `requestVerification('+27815551234')` called | SMS send path |
| TC-12 | SMS requestVerification success | Unit | SMS mode, success | `isVerificationRequested===true` | SMS send success |
| TC-13 | SMS requestVerification failure | Unit | SMS mode, throws | `hasError===true` | SMS send error |
| TC-14 | WhatsApp confirmCode — full happy path | Unit | `code='654321'`, WhatsApp mode | `verifyWhatsAppOtp` then `signInWithWhatsAppToken` called; router.navigate to dashboard | WhatsApp confirm success |
| TC-15 | WhatsApp confirmCode — verifyWhatsAppOtp failure | Unit | verifyWhatsAppOtp throws | `hasError===true`; `signInWithWhatsAppToken` NOT called | OTP verify error |
| TC-16 | WhatsApp confirmCode — signInWithWhatsAppToken failure | Unit | verifyWhatsAppOtp succeeds, Firebase throws | `hasError===true, errorMessage` set | Firebase token error |
| TC-17 | Navigate to returnUrl on success when set | Unit | `returnUrl='/indivisuals/some-deep-route'` | `navigateByUrl('/indivisuals/some-deep-route')` called | returnUrl redirect |
| TC-18 | Navigate to dashboard when no returnUrl | Unit | `returnUrl=null` | `router.navigate(['../dashboard'], { relativeTo: route })` called | Default dashboard nav |
| TC-19 | SMS confirmCode — happy path | Unit | SMS mode, `confirmCode` | `firebaseService.confirmCode` called; router navigates | SMS confirm success |
| TC-20 | SMS confirmCode — failure | Unit | SMS mode, `confirmCode` throws | `hasError===true` | SMS confirm error |
| **TC-DS-01** | **Double-submit guard — second call blocked** | **Unit** | **`confirmCode()` called twice in rapid succession** | **`verifyWhatsAppOtp` called exactly once** | **isConfirming guard entry — ONB-BUG-03** |
| **TC-DS-02** | **isConfirming resets after OTP verify error** | **Unit** | `verifyWhatsAppOtp` throws | `isConfirming===false`, `hasError===true` after error | OTP error reset |
| **TC-DS-03** | **isConfirming resets after Firebase token error** | **Unit** | OTP succeeds, Firebase throws | `isConfirming===false`, `hasError===true` | Firebase error reset |
| **TC-DS-04** | **isConfirming resets after SMS confirmCode error** | **Unit** | SMS mode, `confirmCode` throws | `isConfirming===false`, `hasError===true` | SMS error reset |
| TC-PP-01 | No `.privacy-notice` element rendered | Unit | Default render | `querySelector('.privacy-notice') === null` | ONB-UX-02 |

TC-DS-01 to TC-DS-04 are the four regression guard tests for ONB-BUG-03 (double-submit), added in commit 5321287 (2026-10-09).

## Integration test scenarios

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| INT-01 | WhatsApp OTP happy path — single click confirms | 1. Enter phone number 2. Click "Get Code" 3. Receive WhatsApp OTP 4. Enter OTP code 5. Click "Confirm" once | Exactly one POST /auth/whatsapp/otp/verify in network tab; user navigates to dashboard |
| INT-02 | Double-click regression check (ONB-BUG-03) | 1–4 as above 5. Click "Confirm" twice rapidly (or double-click) | Only ONE POST /auth/whatsapp/otp/verify fires; user navigates to dashboard without seeing "Invalid or expired code" |
| INT-03 | Invalid OTP entered — retry works | 1–3 as above 4. Enter wrong code 5. Click "Confirm" | Error displayed; button re-enabled (isConfirming reset); user can click "Confirm" again |
| INT-04 | returnUrl redirect — QR deep link flow | 1. Open a driver QR link 2. Auth guard stores returnUrl in sessionStorage 3. User completes OTP | After confirmation, navigated to the original deep-link URL (not /dashboard) |

## Regression checks

- [ ] WhatsApp OTP send still works after any change to `verify()` (TC-08, TC-09)
- [ ] SMS fallback still activates on triple-tap (TC-03)
- [ ] Phone normalisation still produces `+27XXXXXXXXX` for all input formats (TC-09, TC-11)
- [ ] Double-submit guard prevents duplicate POST /auth/whatsapp/otp/verify (TC-DS-01, INT-02)
- [ ] isConfirming resets on all three error paths so retry is possible (TC-DS-02, TC-DS-03, TC-DS-04)
- [ ] returnUrl redirect still works when set by auth guard (TC-17)
- [ ] No `.privacy-notice` element in phone-verification template (TC-PP-01 — ONB-UX-02)

## Known gaps / Known defects

| ID | Description | Severity | Location | Date found | Status |
|---|---|---|---|---|---|
| GAP-01 | No automated E2E test for the double-click scenario (INT-02). Unit tests confirm the guard via synchronous observable; a live browser test (Cypress/Playwright with a real backend) would verify at the network level. | Medium | No E2E spec | 2026-10-09 | Open — backlog |
| GAP-02 | The double-submit unit test (TC-DS-01) uses a synchronously-completing spy. A reviewer note suggested using a never-completing `Subject` to more precisely model the "in-flight" state. The current test is still a valid regression guard. | Low | `phone-verification.component.spec.ts` — TC-DS-01 | 2026-10-09 | Open — non-blocking per Code Review PASS |
| GAP-03 | Dead code at line 284 of `phone-verification.component.spec.ts`: `const { Subject } = (window as any)['rxjs'] || {};` — `Subject` is destructured but never used. Does not affect test execution. | Low | `phone-verification.component.spec.ts:284` | 2026-10-09 | Open — non-blocking per Code Review |
| GAP-04 | No test for phone number already in +27 format (passes through as-is without double-prefixing). The normalisation guard covers it implicitly since `raw.startsWith('+27')` is tested, but no explicit unit test for this branch. | Low | `phone-verification.component.ts` — `verify()` normalisation | 2026-10-09 | Open |

## Validation commands

```bash
# izinga-onboarding — full test suite (mandatory before any QA PASS verdict)
cd /Users/lindanimasinga/Documents/GitHub/izinga-onboarding
npx ng test --watch=false --code-coverage

# izinga-onboarding — dev build (catches compile errors missed by prod-only builds)
npx ng build
```
