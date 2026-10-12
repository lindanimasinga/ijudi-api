# Feature Brief: INVITE-DRV-01 — Store & Messenger-Admin Driver Invitation UI

**Feature name:** Store & Messenger-Admin Driver Invitation UI

**Status:** Approved for Implementation

**Requested by:** Lindani Masinga (co-founder)

**Business objective:** ADR-018 (branch `feature/ADR-018-store-driver-invitation`, QA CONDITIONAL PASS, 258 tests green) delivered a complete backend invitation system: a store or messenger-admin invites a driver by mobile number, a WhatsApp message is sent with a deep-link containing an invite code, and the driver accepts or rejects. The backend is deployable but completely unusable because no frontend exists for any part of the flow — not the invite form, not the driver landing page, and not the accept/reject actions. Every driver invitation currently either bypasses the invite API (the old `StoreMessengersComponent` creates profiles directly) or falls into a dead end (the WhatsApp link has nowhere to point). INVITE-DRV-01 builds the minimum UI surface to make the end-to-end flow work in production.

**Architecture sign-off required:** Yes — see REQ-01 Backend Addendum. The `GET /invite/{code}` response must be extended with a `contextType` field before the driver landing page can be built. The iZinga Backend Developer must implement this addendum before the `StoreInviteComponent` (T-04) begins implementation. This is a single-field addition to an existing DTO; the Solution Architect does not need a new ADR — the Backend Developer may add it under the ADR-018 branch.

**Audience:** Store owners / Messenger-admins (invite issuers); Drivers (invite recipients)

**Products affected:**
- `izinga-onboarding` — three sets of changes: (1) update `StoreMessengersComponent`, (2) update `TeamMessengersComponent`, (3) new `StoreInviteComponent`
- `ijudi-api` — one minor backend addendum: add `contextType` field to `InviteCodeResponse` DTO

**Out of scope:**
- Flutter app (`ijudi`) — no accept/reject screens in the mobile app in Phase 1; deferred
- `cs-lifestyle`, `furniture-delivery-app` — no changes
- Push/in-app notification to the driver (not the WhatsApp message — that is backend-handled; this brief covers only web UI)
- Invite management screens — store owner cannot currently view or cancel sent invitations; deferred to INVITE-DRV-02
- Messenger-admin invite contextType name display — when `contextType = MESSENGER_ADMIN`, the invite landing page shows "iZinga Team" as a generic context name because the `GET /invite/{code}` response returns null `storeName` for that type; a named display is deferred
- Invite history on the store admin page — the existing list of linked drivers is kept; no invite-status history is added in Phase 1

---

## Context: What Already Exists vs. What Is Missing

### What the backend delivers (ADR-018, already built)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /store/{storeId}/invite` | STORE_ADMIN or ADMIN | Store admin invites a driver; five outcomes |
| `POST /store/{storeId}/invite/{code}/accept` | Authenticated driver | Driver accepts a store invite |
| `POST /store/{storeId}/invite/{code}/reject` | Authenticated driver | Driver rejects a store invite |
| `GET /invite/{code}` | PUBLIC — no auth | Returns invite details for the landing page |
| `POST /messenger-admin/{adminId}/invite` | MESSENGER_ADMIN | Admin invites a driver; same five outcomes |
| `POST /messenger-admin/{adminId}/invite/{code}/accept` | Authenticated driver | Driver accepts an admin invite |
| `POST /messenger-admin/{adminId}/invite/{code}/reject` | Authenticated driver | Driver rejects an admin invite |

### Five invite outcomes (both store and messenger-admin paths)

| Outcome | HTTP | Meaning | UI must show |
|---|---|---|---|
| CREATED | 201 | Driver had no profile — one was created and linked directly; no WhatsApp needed | "Driver added to your team." |
| ALREADY_LINKED | 200 | Driver is already on this store/admin's team | "This driver is already on your team." |
| INVITED (new) | 201 | Invite sent, WhatsApp dispatched | "Invite sent to [masked phone]. They have 5 days to accept." |
| INVITED (idempotent) | 200 | A valid pending invite already exists; WhatsApp NOT re-sent | "A pending invite already exists for this number." |
| CONFLICT | 409 | Driver is linked to a different store/admin | "This driver is already working with another team." |

### What the frontend currently does (gap)

`StoreMessengersComponent` (store admin, `business-update` area) has an "Add Driver" form that:
1. Calls `getCustomerByPhoneNumber()` to look up an existing profile
2. If found: shows an info-only message — never links the driver, never invites
3. If not found: shows a create form and calls `registerCustomer()` directly — bypasses the invite API entirely, sets `tag.storeId` in the frontend payload

`TeamMessengersComponent` (messenger-admin, `/indivisuals/team-messengers`) has the identical pattern with `getAllMessengersForAdmin()`.

Neither component calls any of the six ADR-018 endpoints. No `StoreInviteComponent` or `/indivisuals/store-invite` route exists.

---

## Backend Addendum Required Before T-04

### REQ-01: Add `contextType` to `InviteCodeResponse`

**File:** `izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/StoreDriverInviteController.kt`

**Current DTO:**
```kotlin
data class InviteCodeResponse(
    val storeId: String,        // actually contextId — both STORE and MESSENGER_ADMIN
    val storeName: String?,
    val storeImageUrl: String?,
    val status: String,
    val expiresAt: String,
    val driverMobileNumber: String
)
```

**Required change — add one field:**
```kotlin
data class InviteCodeResponse(
    val storeId: String,
    val storeName: String?,
    val storeImageUrl: String?,
    val status: String,
    val expiresAt: String,
    val driverMobileNumber: String,
    val contextType: String           // "STORE" or "MESSENGER_ADMIN" — populated from invitation.contextType.name
)
```

The `InviteCodeLookupController.getInvite()` response builder must include `contextType = invitation.contextType.name`.

Without this field, the `StoreInviteComponent` cannot determine whether to call `POST /store/{contextId}/invite/{code}/accept` or `POST /messenger-admin/{contextId}/invite/{code}/accept`. This addendum is non-negotiable for Phase 1 to function end-to-end.

**The Backend Developer must complete REQ-01 (T-01 below) before T-04 (StoreInviteComponent) begins.**

---

## Requirements

### Store-admin invite form (izinga-onboarding — StoreMessengersComponent)

**REQ-02: Replace the existing "Add Driver" two-step flow in `StoreMessengersComponent` with a single "Invite Driver" form that calls `POST /store/{storeId}/invite`.**

The current component at `/Users/lindanimasinga/Documents/GitHub/izinga-onboarding/src/app/store-messengers/store-messengers.component.ts` must be modified as follows:

- Remove the two-phase "Check & Add" / "Create New Driver" form and all associated component state (`showCreateForm`, `isCheckingDriver`, `isCreatingDriver`, `duplicateFoundMessage`, `createErrorMessage`)
- Replace with a single-phase "Invite Driver" form with three fields: Mobile Number (required), First Name (optional), Last Name (optional)
- The form must call a new service method `inviteDriverToStore(storeId, mobileNumber, name?, surname?)` which calls `POST /store/{storeId}/invite`
- All five outcomes must be surfaced via distinct, user-friendly messages (see table in Context section)
- The button label changes from "Check & Add" to "Send Invite"
- The loading state shows "Sending invite..."
- The existing driver list and remove-driver functionality remain unchanged

**REQ-03: The new service method `inviteDriverToStore()` must NOT also add the `getCustomerByPhoneNumber()` call as a preliminary lookup. One HTTP call only.**

### Messenger-admin invite form (izinga-onboarding — TeamMessengersComponent)

**REQ-04: Apply the same replacement to `TeamMessengersComponent`.**

The existing component at `/Users/lindanimasinga/Documents/GitHub/izinga-onboarding/src/app/team-messengers/team-messengers.component.ts` must receive the same changes as REQ-02, calling `inviteDriverToMessengerAdmin(adminId, mobileNumber, name?, surname?)` which calls `POST /messenger-admin/{adminId}/invite`.

The `adminId` is the logged-in user's `id` from `StorageService.userProfile` (same as the current `getAllMessengersForAdmin` call uses).

### Driver invite landing page (izinga-onboarding — new StoreInviteComponent)

**REQ-05: Create a new public Angular component `StoreInviteComponent` at the route `/indivisuals/store-invite`.**

- Route accepts a `code` query parameter: `/indivisuals/store-invite?code={invitationCode}`
- This route must NOT have `PhoneVerifiedGuard` or any other auth guard applied to it
- On component init, call `GET /invite/{code}` (no auth token — public call)
- The component must render the following states:

**State 1 — Loading:** spinner with "Loading invite details..."

**State 2 — Not Found (404):** message "This invite link is invalid or has already expired." No action buttons.

**State 3 — Already Responded:** when `status` is `ACCEPTED` or `REJECTED`, show "This invite has already been responded to." No action buttons.

**State 4 — Expired:** when `status` is `EXPIRED` or when `expiresAt` is in the past with `status = PENDING`, show "This invite has expired. Ask the store to resend a new invite." No action buttons.

**State 5 — Pending (active):** the primary state — show:
- Context display: store image if `storeImageUrl` is non-null; store name if `storeName` is non-null; if `contextType = MESSENGER_ADMIN` or `storeName` is null, show "iZinga Team"
- Masked driver phone from `driverMobileNumber`
- Expiry: "This invite expires on [human-readable date from expiresAt]"
- Two action buttons: "Accept" (primary) and "Reject" (secondary/outline)
- A brief intro: "You have been invited to join [context name] as a delivery driver."

**REQ-06: Accept and Reject actions require an authenticated driver. The auth guard must be applied at the action level, not the route level.**

When the driver clicks "Accept" or "Reject" while not logged in:
- Redirect to `/indivisuals/verify` with `returnUrl=/indivisuals/store-invite?code={code}` as a query parameter
- After OTP login completes, `PhoneVerificationComponent` must redirect back to the `returnUrl` if present

After the driver is authenticated (token exists in StorageService):
- "Accept" calls `POST /store/{contextId}/invite/{code}/accept` when `contextType = STORE`
- "Accept" calls `POST /messenger-admin/{contextId}/invite/{code}/accept` when `contextType = MESSENGER_ADMIN`
- "Reject" calls the corresponding reject endpoint
- On 200 success (accept): transition to a new **State 6 — Accepted** view: "You have joined [context name]! You can now start accepting deliveries." with a "Go to Dashboard" button navigating to `/indivisuals/dashboard`
- On 200 success (reject): transition to **State 7 — Rejected** view: "You have declined this invite." No further action required.
- On 403 (phone mismatch): show "This invite was sent to a different phone number. Please check with the store."
- On 410 (expired at accept/reject time): transition to State 4 (Expired)
- On 409 (conflict — driver already linked elsewhere): show "You are already linked to another team. Contact support if this is an error."

**REQ-07: Add `returnUrl` handling to `PhoneVerificationComponent`.**

`PhoneVerificationComponent` currently always navigates to a hardcoded route after successful OTP verification. It must be updated to:
- Read a `returnUrl` query parameter on component init
- If `returnUrl` is present and starts with `/` (internal path only — reject any external URL): after successful verification, navigate to `returnUrl` instead of the default post-verify route
- If `returnUrl` is absent: behave exactly as before (no change to existing flows)

### Angular service additions (izinga-onboarding)

**REQ-08: Add the following methods to `IzingaOrderManagementService`:**

```typescript
// POST /store/{storeId}/invite
inviteDriverToStore(storeId: string, request: DriverInviteRequest): Observable<InviteOutcomeResponse>

// POST /messenger-admin/{adminId}/invite
inviteDriverToMessengerAdmin(adminId: string, request: DriverInviteRequest): Observable<InviteOutcomeResponse>

// GET /invite/{code} — public, no auth header
getInviteByCode(code: string): Observable<InviteCodeResponse>

// POST /store/{storeId}/invite/{code}/accept
acceptStoreInvite(storeId: string, code: string): Observable<void>

// POST /store/{storeId}/invite/{code}/reject
rejectStoreInvite(storeId: string, code: string, reason?: string): Observable<void>

// POST /messenger-admin/{adminId}/invite/{code}/accept
acceptMessengerAdminInvite(adminId: string, code: string): Observable<void>

// POST /messenger-admin/{adminId}/invite/{code}/reject
rejectMessengerAdminInvite(adminId: string, code: string, reason?: string): Observable<void>
```

`getInviteByCode()` must NOT include the Authorization header — it is a public endpoint and must work before the driver is logged in.

Interface types to define (in the model layer or inline in the service):

```typescript
interface DriverInviteRequest {
  mobileNumber: string;
  name?: string;
  surname?: string;
}

interface InviteOutcomeResponse {
  outcome: 'CREATED' | 'ALREADY_LINKED' | 'INVITED';
}

interface InviteCodeResponse {
  storeId: string;             // contextId — may be a storeId or adminId
  storeName: string | null;
  storeImageUrl: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';
  expiresAt: string;           // ISO 8601 timestamp
  driverMobileNumber: string;  // masked — last 4 digits, e.g. "****1234"
  contextType: 'STORE' | 'MESSENGER_ADMIN';  // required after REQ-01 backend addendum
}
```

---

## User Stories

**US-01 (Store admin invites a new driver):** As a store admin on the Store Drivers page, I enter a driver's mobile number and optionally their name. I click "Send Invite". I see "Invite sent — they have 5 days to accept." The driver receives a WhatsApp message with a link.

**US-02 (Store admin invites a driver who is already linked):** I enter a mobile number for a driver already on my team. I see "This driver is already on your team." No invite is sent.

**US-03 (Store admin tries to invite a driver on another team):** I enter a mobile number for a driver linked to a different store. I see "This driver is already working with another team."

**US-04 (Driver receives the WhatsApp invite, has not registered):** Driver clicks the link in WhatsApp. They land on the invite page showing the store name, store logo, their masked phone number, and expiry date. They click "Accept". Because they are not logged in, they are taken to the OTP login screen. After verifying their phone, they return to the invite page, the accept completes, and they see a confirmation with a link to their dashboard.

**US-05 (Driver opens an already-expired invite):** Driver clicks a WhatsApp link that is 6 days old. They see "This invite has expired. Ask the store to resend a new invite."

**US-06 (Driver opens an invite they already accepted):** Driver clicks the same WhatsApp link again after already accepting. They see "This invite has already been responded to."

**US-07 (Messenger-admin invites a driver):** Same as US-01 and US-04 but from the messenger-admin's team page, with the identical flow on both sides.

---

## Dependencies

- **ADR-018 backend merged to `develop`**: The feature branch `feature/ADR-018-store-driver-invitation` must be merged to `develop` before the izinga-onboarding feature branch is opened.
- **REQ-01 backend addendum**: The `contextType` field addition to `InviteCodeResponse` (T-01) must be completed before `StoreInviteComponent` (T-04) starts implementation.
- **Meta WhatsApp template approval**: The `driver_store_invite` WhatsApp template must be approved on Meta before end-to-end testing. This is a test blocker, not a build blocker. Engineering may test `GET /invite/{code}` rendering and accept/reject flows with manually constructed URLs until the template is approved.
- **`PhoneVerificationComponent` `returnUrl` handling (REQ-07)**: T-07 must be complete before T-04's Accept/Reject auth flow can be verified end-to-end.

---

## Risks

**RISK-01 (AUTH — driver accept/reject requires login):** The invite landing page is public, but accepting/rejecting requires Firebase OTP auth. A driver who taps "Accept" cold from WhatsApp (never previously logged in) gets redirected to the OTP flow. If the OTP screen loses the `returnUrl` context (e.g. on page refresh), the driver returns to the home screen instead of the invite. Mitigation: REQ-07 adds `returnUrl` to `PhoneVerificationComponent`. QA must test the redirect round-trip.

**RISK-02 (NAMING — storeId field for MESSENGER_ADMIN invites):** The `InviteCodeResponse.storeId` field is named `storeId` but contains an `adminId` for MESSENGER_ADMIN invites (GAP-05 in QA test plan). The UI must treat this field as `contextId` regardless of name. The TypeScript interface defined in REQ-08 uses the accurate name `storeId` from the wire format while the code that uses it should document it as `contextId`. A future backend rename is deferred.

**RISK-03 (REGRESSION — StoreMessengersComponent change):** The current `StoreMessengersComponent` create-driver path (`registerCustomer()` with `tag.storeId` set in the payload) bypasses the backend's driver profile creation rules. Replacing it with the invite API changes who creates the profile and how. QA must verify that after the change, the drivers list refreshes correctly after an INVITED, ALREADY_LINKED, and CREATED outcome.

**RISK-04 (WhatsApp template gap):** The `driver_store_invite` Meta template is not yet approved (confirmed gap in QA test plan). The invite form in `StoreMessengersComponent` and `TeamMessengersComponent` will work and send an invite, but the driver will NOT receive the WhatsApp message until the template is approved. The "Invite sent" confirmation shown to the store admin is accurate for what the backend does; the WhatsApp delivery is separate. This must be communicated in the UI: "Invite created. A WhatsApp message will be sent once our messaging is active."
- **Resolution required before go-live**: Lindani to confirm Meta template approval status before the Release Manager authorises production deployment.

**RISK-05 (PhoneVerificationComponent `returnUrl` injection):** Adding `returnUrl` support to `PhoneVerificationComponent` affects a component used across all auth flows (driver registration, store registration, etc.). The change must be additive-only: the `returnUrl` param is used only when present; all existing nav targets remain the default when absent. QA must run a full regression on existing phone-verification flows after T-07.

---

## Task Breakdown

All tasks run on branch `feature/INVITE-DRV-01-store-driver-invitation-ui` in `izinga-onboarding`. T-01 (backend addendum) runs on the existing `feature/ADR-018-store-driver-invitation` branch in `ijudi-api` — or on a new branch `feature/INVITE-DRV-01-api-addendum` if ADR-018 has already merged to `develop` by the time this work begins (developer to confirm).

**Gate: ADR-018 must be in `develop` before this feature branch is opened.**

| ID | Task | Repo | Agent | Depends on |
|---|---|---|---|---|
| T-01 | Backend addendum: add `contextType: String` field to `InviteCodeResponse` DTO and populate it in `InviteCodeLookupController.getInvite()` from `invitation.contextType.name`; add or update unit test for the `GET /invite/{code}` STORE and MESSENGER_ADMIN response to assert `contextType` is present and correct | `ijudi-api` | iZinga Backend Developer | ADR-018 in develop |
| T-02 | Angular service: add 7 new methods to `IzingaOrderManagementService` per REQ-08; define `DriverInviteRequest`, `InviteOutcomeResponse`, `InviteCodeResponse` TypeScript interfaces; `getInviteByCode()` must NOT send the Authorization header | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | ADR-018 in develop |
| T-03 | Update `StoreMessengersComponent`: replace two-phase "Check & Add / Create" form with a single "Invite Driver" form per REQ-02; call `inviteDriverToStore()`; surface all five outcomes per the outcome table; update existing unit tests | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-02 |
| T-04 | Update `TeamMessengersComponent`: same replacement per REQ-04; call `inviteDriverToMessengerAdmin()`; surface all five outcomes | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-02 |
| T-05 | Design spec: produce a mobile-responsive design direction for `StoreInviteComponent` — States 1–7 as defined in REQ-05 and REQ-06; specify store image/fallback layout, masked phone display, expiry date formatting, primary/secondary button hierarchy, and error state card styles; confirm iZinga design system alignment | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | none |
| T-06 | Update `PhoneVerificationComponent`: add `returnUrl` query param read on init; after successful OTP verification redirect to `returnUrl` if present and starts with `/`, else to default; add unit test for returnUrl redirect path; confirm no regression on all existing verify routes | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | none |
| T-07 | Implement `StoreInviteComponent`: create component, register route `/indivisuals/store-invite` WITHOUT any auth guard, implement all 7 states per REQ-05 and REQ-06; Accept calls correct endpoint by `contextType`; Reject calls correct endpoint; unauthenticated Accept/Reject redirects to verify with `returnUrl` | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-01, T-02, T-05, T-06 |
| T-08 | QA: write full test plan covering all ACs; execute against a running backend (UAT profile); confirm no regression on existing store-messengers, team-messengers, and phone-verification flows; full unscoped Angular test suite green | `izinga-onboarding` | iZinga QA & Test Automation | T-03, T-04, T-07 |
| T-09 | AI Code Review: verify `getInviteByCode` sends no auth header; verify `returnUrl` only accepts internal paths (starts with `/`); verify no regression on existing phone-verification routes; verify all five invite outcomes are surfaced; produce PASS/FAIL verdict | Both | iZinga Code Reviewer | T-08 |
| T-10 | Release Manager: confirm Meta `driver_store_invite` WhatsApp template approval status (release gate — see RISK-04); prepare `release/*`; merge to `main` in both repos if gate is met | Both | iZinga Release Manager | T-09, Meta template gate |

**Safe implementation sequence:**

1. Confirm ADR-018 is in `develop` — do not open branches until confirmed.
2. T-01 (backend addendum) and T-02 (service methods) and T-05 (design spec) and T-06 (`returnUrl` in phone-verify) — all in parallel.
3. T-03 and T-04 (component updates) — after T-02.
4. T-07 (`StoreInviteComponent`) — after T-01, T-02, T-05, T-06.
5. T-08 (QA) — after T-03, T-04, T-07.
6. T-09 (Code Review) → T-10 (Release Manager) — sequential.

---

## Acceptance Criteria

**AC-01 (Invite form — store admin — CREATED outcome):** Given a store admin opens the Store Drivers page, when they enter a mobile number for a driver who has no existing iZinga profile, submit the invite form, and the backend returns HTTP 201 with `outcome: CREATED`, then the component displays "Driver added to your team." and the driver list reloads.

**AC-02 (Invite form — store admin — ALREADY_LINKED outcome):** Given a store admin enters the mobile number of a driver already in their store's team, when the backend returns HTTP 200 with `outcome: ALREADY_LINKED`, then the component displays "This driver is already on your team." No list reload is required.

**AC-03 (Invite form — store admin — INVITED new outcome):** Given a store admin enters a mobile number for a driver who exists but is not yet linked, when the backend returns HTTP 201 with `outcome: INVITED`, then the component displays "Invite sent — they have 5 days to accept."

**AC-04 (Invite form — store admin — INVITED idempotent outcome):** Given a valid pending invite already exists for that mobile number, when the backend returns HTTP 200 with `outcome: INVITED`, then the component displays "A pending invite already exists for this number."

**AC-05 (Invite form — store admin — CONFLICT outcome):** Given the driver is linked to a different store, when the backend returns HTTP 409, then the component displays "This driver is already working with another team."

**AC-06 (Invite form — single HTTP call):** Given a store admin submits the invite form, when the component processes the submission, then exactly one HTTP call is made (`POST /store/{storeId}/invite`) — no preliminary phone lookup call is made.

**AC-07 (Invite form — messenger-admin — all five outcomes):** ACs AC-01 through AC-05 apply identically to `TeamMessengersComponent` calling `POST /messenger-admin/{adminId}/invite`, with the adminId sourced from `StorageService.userProfile.id`.

**AC-08 (Driver landing page — public access):** Given an unauthenticated user navigates to `/indivisuals/store-invite?code={anyCode}`, when the route resolves, then the component renders without any redirect to the login screen and without a 401 error.

**AC-09 (Driver landing page — pending invite rendered):** Given the driver opens `/indivisuals/store-invite?code={validCode}` where the invite status is PENDING and not expired, when `GET /invite/{code}` returns successfully, then the component shows the context name, store image (or fallback), masked phone ending in the same 4 digits as the response, the expiry date in a human-readable format, and an "Accept" and "Reject" button.

**AC-10 (Driver landing page — expired state):** Given the driver opens the invite page where `status = EXPIRED` or `expiresAt` is in the past with `status = PENDING`, when the component renders, then the expired message is shown and no Accept/Reject buttons are rendered.

**AC-11 (Driver landing page — already responded state):** Given `status = ACCEPTED` or `status = REJECTED`, when the component renders, then "This invite has already been responded to." is shown and no action buttons are rendered.

**AC-12 (Driver landing page — not found state):** Given the code does not exist (backend returns 404), when the component renders, then the not-found message is shown and no action buttons are rendered.

**AC-13 (Accept — unauthenticated driver redirected to OTP):** Given the driver is not logged in and clicks "Accept", when the action fires, then the browser navigates to `/indivisuals/verify?returnUrl=/indivisuals/store-invite?code={code}` — the driver is NOT taken to the home screen.

**AC-14 (Accept — returnUrl restores invite page):** Given the driver completes OTP verification after being redirected from the invite page, when `PhoneVerificationComponent` processes the successful verification, then the browser navigates to `/indivisuals/store-invite?code={code}` — not to the default post-verify screen.

**AC-15 (Accept — STORE context calls correct endpoint):** Given `contextType = STORE`, when an authenticated driver clicks "Accept", then the component calls `POST /store/{storeId}/invite/{code}/accept` — not the messenger-admin endpoint.

**AC-16 (Accept — MESSENGER_ADMIN context calls correct endpoint):** Given `contextType = MESSENGER_ADMIN`, when an authenticated driver clicks "Accept", then the component calls `POST /messenger-admin/{adminId}/invite/{code}/accept` using the `storeId` field value from the `InviteCodeResponse` as the `adminId`.

**AC-17 (Accept — success state shown):** Given the accept call returns HTTP 200, when the component processes the response, then the "Accepted" state is shown with a "Go to Dashboard" button navigating to `/indivisuals/dashboard`.

**AC-18 (Reject — success state shown):** Given the reject call returns HTTP 200, then the "Rejected" state is shown.

**AC-19 (Accept — phone mismatch 403):** Given the backend returns 403, when the component processes the response, then "This invite was sent to a different phone number." is shown.

**AC-20 (Accept — expired at accept time 410):** Given the backend returns 410 (invite expired between landing and accept), when the component processes the response, then the expired state is shown.

**AC-21 (returnUrl — internal paths only):** Given `PhoneVerificationComponent` reads a `returnUrl` query param, when `returnUrl` does not start with `/` (e.g. `https://evil.com`), then the redirect is ignored and the default post-verify navigation applies.

**AC-22 (getInviteByCode — no auth header):** Given the Angular service calls `getInviteByCode()`, when the HTTP request is made, then no Authorization header is included in the request — confirmed by inspecting the network call during testing.

**AC-23 (Full unscoped test suite):** Given all tasks are complete, when `ng test` runs without scope restrictions in `izinga-onboarding`, then all tests pass and no pre-existing tests are broken.

---

## Marketing Trigger

**No** — this is infrastructure enabling store growth. A marketing trigger is appropriate only once the WhatsApp template is approved and the Meta-to-driver invite message is live. At that point, brief the Marketing Strategist to run a "grow your team" campaign targeting store owners. Minimum 2 weeks lead before the campaign date.

---

## Definition of Done

All of the following must be true before INVITE-DRV-01 is considered complete and in production:

1. ADR-018 backend is confirmed merged to `develop` before the onboarding feature branch is opened.
2. `GET /invite/{code}` response includes `contextType` field (T-01 complete); corresponding unit tests updated.
3. All seven `IzingaOrderManagementService` invite methods implemented; `getInviteByCode()` confirmed to send no Authorization header in test.
4. `StoreMessengersComponent` calls `POST /store/{storeId}/invite` — old two-step check/create form removed; all five outcomes render correctly.
5. `TeamMessengersComponent` calls `POST /messenger-admin/{adminId}/invite` — same five outcomes.
6. `PhoneVerificationComponent` handles `returnUrl` — internal paths only; all existing post-verify flows confirmed unaffected.
7. `StoreInviteComponent` exists at `/indivisuals/store-invite`; route has no auth guard; all 7 render states are implemented.
8. Accept/Reject route-to-correct-endpoint by `contextType` confirmed via manual test on UAT.
9. Unauthenticated Accept/Reject redirect to `/indivisuals/verify?returnUrl=…` and return to invite page after OTP verified — confirmed via manual test.
10. Full unscoped Angular test suite green in `izinga-onboarding`.
11. AI Code Review PASS verdict (T-09).
12. Meta `driver_store_invite` WhatsApp template approval confirmed in writing before Release Manager authorises production deployment (RISK-04 gate).
13. iZinga Growth & Analytics briefed to report on invite send rate and accept/reject ratio 2 weeks post-launch.

---

## Open Questions for Lindani

**Pending Lindani action:**

1. **Meta WhatsApp template status (release gate — RISK-04):** Confirm the current approval status of the `driver_store_invite` Meta template. This is the only release gate not controlled by engineering. If the template is not yet submitted, submit it now — approval typically takes 1–3 business days but can take longer.

2. **WhatsApp deep-link base URL:** Confirm the base URL that appears in the WhatsApp URL button template. The driver-facing invite page will live at `{base_url}/indivisuals/store-invite?code={code}`. Confirm which domain hosts izinga-onboarding in production (e.g. `biz.izinga.co.za`) so the Meta template submission can use the correct base URL. This must match what the backend's `DriverInviteNotificationPort` constructs.

3. **MESSENGER_ADMIN display name on invite landing:** When a messenger-admin (not a store) sends the invite, `storeName` is null in the landing page response. The brief proposes showing "iZinga Team" as a fallback. Confirm this is acceptable, or provide a preferred fallback.

---

*Brief authored by iZinga Product Owner. Approved for Implementation as of 12 October 2026.*
*ADR-018 (ijudi-api, feature/ADR-018-store-driver-invitation) is the backend dependency — must be in `develop` before this brief's feature branch opens.*
*Gate: Meta `driver_store_invite` WhatsApp template approval — release gate, does not block engineering.*
*3 open questions pending Lindani action (see Open Questions section).*
