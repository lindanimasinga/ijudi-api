# Test Plan: Driver — Store & Messenger-Admin Driver Invitation (ADR-018)

**userType:** driver (receiving side)
**Feature:** A driver receives a WhatsApp invite from a store or messenger-admin, clicks the deep-link, authenticates, and accepts or rejects the invitation.
**App:** `izinga-onboarding` — `StoreInviteComponent` at `/indivisuals/store-invite?code={code}`, `PhoneVerificationComponent` (returnUrl support)
**Primary plan:** [store/store-driver-invitation.md](../store/store-driver-invitation.md)
**Last verified:** 2026-10-12, automated unit-test pass (Gate 1 per-feature), `ng test` (780/780 green). StoreInviteComponent fully implemented.

---

## Driver-side unit tests (covered — see FE-01 through FE-18 in primary plan)

The `StoreInviteComponent` 16-test suite covers all seven render states and all driver actions:

| State / Action | Tests | AC |
|---|---|---|
| Loading → Pending (valid, not expired) | FE-01 | AC-09 |
| Loading → Not Found (404) | FE-02 | AC-12 |
| Loading → Already Responded (ACCEPTED / REJECTED) | FE-03, FE-04 | AC-11 |
| Loading → Expired (EXPIRED status) | FE-05 | AC-10 |
| Loading → Expired (past expiresAt with PENDING) | FE-06 | AC-10 |
| Unauthenticated Accept → redirects to verify with returnUrl | FE-07 | AC-13 |
| Unauthenticated Reject → redirects to verify with returnUrl | FE-08 | AC-13 |
| Authenticated Accept (STORE context) → calls acceptStoreInvite | FE-09 | AC-15 |
| Authenticated Accept (MESSENGER_ADMIN) → calls acceptMessengerAdminInvite | FE-10 | AC-16 |
| Accept success → accepted state | FE-11 | AC-17 |
| Accept 403 → phone-mismatch message | FE-12 | AC-19 |
| Accept 410 → expired state | FE-13 | AC-20 |
| Accept 409 → already-linked message | FE-14 | — |
| contextName = storeName for STORE | FE-15 | AC-09 |
| contextName = "iZinga Team" for MESSENGER_ADMIN | FE-16 | AC-09 |
| goToDashboard → /indivisuals/dashboard | FE-18 | AC-17 |
| returnUrl accepted for internal paths (PhoneVerificationComponent) | FE-26 | AC-14 |
| returnUrl rejected for external paths — https:// | FE-27 | AC-21 |
| returnUrl rejected for external paths — no scheme | FE-28 | AC-21 |
| Verify success → navigateByUrl to returnUrl | FE-30 | AC-14 |

---

## Driver-side E2E actions still requiring manual / UAT testing

These require a running backend with a real WhatsApp-approved template (RISK-04 gate):

- Clicking the WhatsApp URL button and landing on `izinga-onboarding` at `/indivisuals/store-invite?code={code}`
- Authenticating via Firebase OTP if not already signed in, and returning to the invite via returnUrl
- Verifying the invite details rendered from `GET /invite/{code}` — masked phone last 4 digits, store name, expiry date in en-ZA locale
- Accepting / rejecting end-to-end and verifying the driver profile's tag is set in MongoDB
- Attempting to accept an already-expired invite and seeing the 410 state
- Testing the full returnUrl round-trip: WhatsApp link → verify screen → return to invite → accept

---

See the primary plan for backend test cases, fixtures, integration scenarios, regression checks, and known gaps (including GAP-06: AC-04 INVITED idempotent message).
