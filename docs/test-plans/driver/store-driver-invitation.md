# Test Plan: Driver — Store & Messenger-Admin Driver Invitation (ADR-018)

**userType:** driver (receiving side)
**Feature:** A driver receives a WhatsApp invite from a store or messenger-admin, clicks the deep-link, authenticates, and accepts or rejects the invitation.
**Primary plan:** [store/store-driver-invitation.md](../store/store-driver-invitation.md)

This file is a cross-link. All test cases, fixtures, integration scenarios, regression checks, gaps, and validation commands live in the primary plan above.

The driver-side actions that Gate 1 unit tests do NOT cover (E2E only):

- Clicking the WhatsApp URL button and landing on the driver onboarding app at `/indivisuals/store-invite?code={code}`
- Authenticating via OTP if not already signed in
- Seeing the invite details (masked phone, store name, expiry) rendered from `GET /invite/{code}`
- Accepting via `POST /store/{storeId}/invite/{code}/accept` or `POST /messenger-admin/{adminId}/invite/{code}/accept` and seeing the success state
- Rejecting with or without a reason
- Attempting to accept an already-expired invite and seeing the 410 error state

See the primary plan for the full test case table.
