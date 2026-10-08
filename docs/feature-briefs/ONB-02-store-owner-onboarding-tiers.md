# Feature Brief: ONB-02 — Store Owner Onboarding Tiers

**Feature name:** Store Owner Onboarding — Three-Tier Platform Alignment

**Status:** Approved for Implementation

**Requested by:** Lindani Masinga (co-founder)

**Business objective:** biz.izinga.co.za currently onboards store owners into a single, tier-less flow that discloses no pricing, collects no bank details, skips the merchant agreement, and omits the POPIA-required store-partner verification gate (C-04). The iZinga website now publicly promises a three-tier platform (Free / Premium Tier 1 R800/month / Premium Tier 2 R3,000/month) with specific capabilities and commercial terms. This creates a gap between what the website promises and what onboarding delivers. This brief closes that gap: onboarding must mirror the website's tier offering, disclose the 6.5% service fee and payout terms at the point of sign-up, record click-wrap acceptance of the Store/Merchant Partner Agreement (L-07) under the ADR-017 pattern, and gate store activation on the C-04 verification requirement. Revenue impact: Premium Tier 1 and Tier 2 subscriptions cannot be sold until onboarding captures tier selection and records agreement acceptance; no subscription billing can run without a complete merchant agreement acceptance record.

**Architecture sign-off:** ADR-022 approved 2 October 2026 by iZinga Solution Architect. Decisions in ADR-022 are binding on this brief.

**Audience:** Store owners / merchant partners (primary — direct users of biz.izinga.co.za)

**Products affected:**
- `izinga-onboarding` — Angular app at biz.izinga.co.za; all onboarding flow changes live here (primary)
- `ijudi-api` (`izinga-commons`) — new `SubscriptionTier.kt` enum
- `ijudi-api` (`izinga-ordermanager`) — StoreProfile model fields, PATCH endpoint, icaAccepted gate, `StoreAgreementAudit` collection
- `ijudi` (Flutter) — null-safe handling of all new `StoreProfile` fields

**Out of scope:**
- Subscription billing and payment collection for monthly subscription fees (deferred to TIER-BILLING-01)
- DNS provisioning or any automated management of custom domains
- The AI WhatsApp assistant itself — WA-LINES-02 already built; AI opt-in capture is Phase 3 (deferred by SA per ADR-022)
- `customDomainPreference` field — deferred by SA in ADR-022 Decision 6 (no infrastructure consumer exists in Phase 1)
- AI assistant fields (`aiAssistantEnabled`, `aiAdviceModeRequested`, `aiLicenceWarrantAccepted`) — deferred by SA in ADR-022 Decision 6; covered by a future brief
- Any change to `cs-lifestyle` or `furniture-delivery-app` (new optional StoreProfile fields are non-breaking; no display logic change required)
- Automated Meta WABA sub-account provisioning — WA-LINES-02 provisioning remains admin-driven; `whatsappLineRequested` is a signal field only (ADR-022 Decision 5)
- The branded storefront visual implementation (colours already captured; rendering on shop.izinga.co.za is a separate brief)
- Subscription upgrade/downgrade/cancellation billing UI (TIER-BILLING-01)

---

## Context: Existing Flow and What It Is Missing

**Current route sequence** (confirmed by onboarding review):

```
biz.izinga.co.za
  → /business               WelcomeBusinessComponent (captures ?ref=)
  → /business/verify        PhoneVerificationComponent (Firebase OTP)
  → /business/user          UserUpdateComponent
  → /business/signup-welcome/:id
  → /business/terms/:id     TermsConditionsComponent
                              (falls through to #generalTerms for STORE roles;
                               writes termsAccepted/termsAcceptedDate on UserProfile;
                               does NOT write icaAccepted/icaAcceptedDate/icaVersion)
  → /business/info          BusinessUpdateComponent
  → /business/info/:id      (registerBusinessAndStock() → createStore()/updateStore())
  → /business/dashboard
```

**Confirmed divergences from the three-tier platform promise:**

1. No tier selection — every store is created equal with no `subscriptionTier` field.
2. The 6.5% service fee is never disclosed to the merchant at any point in the flow.
3. No merchant agreement — `TermsConditionsComponent` falls through to generic terms for STORE roles, writing `termsAccepted` only. The Store/Merchant Partner Agreement (L-07) is not presented.
4. Payout cadence (daily initiation; up to 3 business days bank settlement) is not disclosed.
5. Bank account details are not captured and are not required before `createStore()` is called.
6. The C-04 verification gate (store-partner identity verification before account activation — OPEN / HIGH PRIORITY / POPIA exposure per attorney tracker 2026-08-06) is absent.
7. No Tier 1/Tier 2 capability capture fields on `StoreProfile`.
8. Dashboard has no tier badge and no upgrade CTA.
9. Storefront activation on the dashboard is not gated on `icaAccepted`.

**ADR-017 precedent used by this brief:**

The Ambassador ICA and Driver ICA flows in `TermsConditionsComponent` already implement the ADR-017 pattern: a typed version constant, a `needsXxxAcceptance` computed getter, and an `acceptTerms()` branch that writes `icaAccepted`, `icaAcceptedDate`, and `icaVersion`. ADR-022 Decision 1 resolves the key difference from those flows: for stores, the ICA fields go on `StoreProfile` (not `UserProfile`), because the merchant agreement is a business contract with the store entity — it survives ownership transfer and covers all STORE_ADMIN users, not just the individual who clicked accept.

---

## User Stories

**US-01 (Store owner — Free tier):** As a new store owner signing up on biz.izinga.co.za, I see the three pricing tiers before I commit to anything. I select Free, I see the 6.5% customer fee disclosed clearly, and I accept the Store/Merchant Partner Agreement before my store goes live.

**US-02 (Store owner — Premium Tier 1):** As a Premium Tier 1 applicant, I confirm I want a dedicated WhatsApp order line during sign-up so iZinga can action the request after agreement acceptance.

**US-03 (Store owner — dashboard):** As a signed-up store owner, my dashboard shows my current tier and a clear upgrade path if I am on the Free tier. My storefront activation button is disabled and shows a prompt to accept the merchant agreement if I have not yet done so.

**US-04 (iZinga ADMIN):** As an ADMIN, I can verify via the existing store endpoints that a given store has `icaAccepted = true` and `subscriptionTier` populated before any storefront goes live.

---

## Requirements

### Phase 1 — Blockers (must ship before merchant onboarding opens publicly)

**REQ-01: Tier selection screen with fee disclosure**

A new step must be inserted into the business onboarding route between `/business/signup-welcome/:id` and the terms route. The step presents three tier cards matching the layout and copy on store-draft.html:
- Free: "Free for merchants. 6.5% service fee added at customer checkout — paid by customer, not deducted from your payout."
- Premium Tier 1: R800/month (VAT-exclusive) — branded storefront, custom domain, dedicated WhatsApp order line, regulated-industry product filtering.
- Premium Tier 2: R3,000/month (VAT-exclusive) — all Tier 1 features plus 24/7 AI WhatsApp assistant (Default Mode included; Advice-Enabled Mode on application).

The 6.5% service fee disclosure must appear on all three cards — not only the Free card — so the merchant sees it regardless of which tier they select.

The selected tier is stored in component state and passed to the store creation call. The step cannot be bypassed by direct URL navigation — a guard must redirect to the tier selection screen if no tier has been selected in the current session.

Null handling: existing stores where `subscriptionTier` is null must be treated as `FREE` in all frontend display logic.

**REQ-02: Merchant agreement click-wrap — STORE_ADMIN branch in TermsConditionsComponent**

`TermsConditionsComponent` must gain a STORE_ADMIN branch following the ADR-017 pattern:

```typescript
/**
 * Merchant agreement version. Follows the driver-ICA convention:
 * merchant-v1, merchant-v2, … incremented on every attorney-required
 * text change. Store the string exactly; never use a date-based identifier.
 * Bump this constant (and the dashboard check) in the same commit as any
 * ICA text update.
 */
static readonly MERCHANT_ICA_VERSION = 'merchant-v2';

/**
 * ProfileRoles enum (izinga-commons): STORE_ADMIN is the owner/user person.
 * The store entity itself has role STORE — that is fixed by the StoreProfile
 * constructor and must NOT be used here. We branch on the user's role (STORE_ADMIN).
 * For belt-and-braces safety, also tolerate STORE on the user to match the
 * existing `isShop` fallthrough pattern in this component.
 */
get isStoreAdmin(): boolean {
  return this.user?.role === UserProfile.RoleEnum.STORE_ADMIN
      || this.user?.role === (UserProfile.RoleEnum as any).STORE;
}

get needsMerchantIcaAcceptance(): boolean {
  if (!this.isStoreAdmin) { return false; }
  return !this.store?.icaAccepted || this.store?.icaVersion !== TermsConditionsComponent.MERCHANT_ICA_VERSION;
}
```

When the route is `/business/terms/:id` and the user role is `STORE_ADMIN`, the component must:
1. Render the Store/Merchant Partner Agreement text (L-07, v2 draft content) in an embedded scrollable block — not a link-out. The content is served from a static asset at `src/assets/legal/store-merchant-partner-agreement-merchant-v2.pdf` (Engineering uses the markdown source until the PDF asset is ready).
2. Require the user to scroll to the bottom of the agreement before the acceptance checkbox is enabled (same UX pattern as the Ambassador and Driver ICA flows).
3. On acceptance checkbox tick and confirmation click, call `PATCH /store/{id}/ica-acceptance` (see API changes) to write `icaAccepted = true`, `icaAcceptedDate`, and `icaVersion = MERCHANT_ICA_VERSION` to `StoreProfile`. This is NOT a call to `updateCustomer()` — the acceptance fields live on `StoreProfile`, not `UserProfile` (ADR-022 Decision 1).
4. On success, navigate to `/business/info/:id`.
5. On error, display "Failed to record agreement acceptance. Please try again." without navigating away.

The click-wrap must be deployed behind an Angular feature flag (`MERCHANT_ICA_ENABLED`, defaulting to `false` in `environment.ts` and `environment.prod.ts`) until Jason van der Merwe's attorney sign-off on L-07 is received. When the flag is `false`, the `/business/terms/:id` route for STORE_ADMIN falls through to the existing general terms flow (no regression to Ambassador or Driver ICA flows). When the flag is `true`, the STORE_ADMIN branch activates.

**REQ-03: C-04 verification gate before createStore()**

Server-side: `POST /store` must reject with HTTP 403 and error body `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }` if the requesting `STORE_ADMIN`'s associated store does not carry `icaAccepted = true`. This gate must be implemented in `StoreService`, not in `StoreControler` — the SA requires business invariants to live in the service layer (ADR-022 Decision 4).

The frontend must surface a HTTP 403 `MERCHANT_ICA_NOT_ACCEPTED` response as: "Your merchant agreement acceptance could not be confirmed. Please complete the agreement step before submitting your store details."

**REQ-04: Bank account details required + payout cadence disclosure**

`BusinessUpdateComponent` must require bank details before `registerBusinessAndStock()` is called on the create path (`storeId` is null). These map to the existing `StoreProfile.bank` field (type `Bank`). The `Bank` model's confirmed fields and constraints are:

| Field | Type | UI label | Validation |
|---|---|---|---|
| `accountId` | String | Account number | Required; numeric only; 8–11 digits |
| `name` | String | Bank name | Required; select from bank name list |
| `branchCode` | String | Branch code | Required; numeric |
| `phone` | String | Bank contact number | Required |
| `type` | BankAccType | Account type | Required; options: CHEQUE, SAVINGS, TRANSMISSION, EWALLET. Do NOT offer the legacy values `wallet` or `string` in the UI |

All five fields are `@NotBlank` on the backend (confirmed from `Bank.kt`). `type` has no `@NotBlank` annotation but is required in the UI.

The form must display, directly above the bank details section, the payout cadence disclosure as a non-dismissible callout:

> "iZinga initiates payouts every Business Day. After initiation, funds reflect in your account within up to 3 Business Days — this is determined by your bank's processing time, not iZinga. iZinga does not guarantee same-day receipt."

Bank details are not validated or required on the edit path (existing stores already have them; the check must not break existing store updates).

**REQ-05: subscriptionTier, subscriptionTierSince, icaAccepted, icaAcceptedDate, icaVersion, whatsappLineRequested fields on StoreProfile**

ADR-022 Decision 6 approves these six fields for Phase 1. All six are nullable with null defaults — additive, no migration required. MongoDB existing documents return null on deserialisation; null must be treated as `FREE` for `subscriptionTier`.

Backend (Kotlin): add to `StoreProfile.kt` in `izinga-commons`:
```kotlin
var subscriptionTier: SubscriptionTier? = null      // null = FREE in all business logic
var subscriptionTierSince: Date? = null
var icaAccepted: Boolean? = null
var icaAcceptedDate: Date? = null
var icaVersion: String? = null
var whatsappLineRequested: Boolean? = null           // SIGNAL ONLY — no provisioning side-effect (ADR-022 Decision 5)
```

`SubscriptionTier` enum lives in `izinga-commons` (new file):
```kotlin
package io.curiousoft.izinga.commons.model

enum class SubscriptionTier {
    FREE, PREMIUM_1, PREMIUM_2
}
```

Frontend (TypeScript): add corresponding optional fields to `izinga-onboarding/src/app/model/storeProfile.ts`. The same PR must fix the pre-existing `StoreType` enum drift — add the four missing values (`MOVERS`, `TIPS`, `LICENSING`, `PARTS`) that exist in Kotlin but are absent from the TS model (ADR-022 Decision 3). This is a required Definition of Done item, not a separate ticket.

Flutter (`ijudi`): add null-safe deserialisation for all six new fields. Treat `subscriptionTier == null` as `FREE` in all display logic.

---

### Phase 2 — Should (ship in the sprint following Phase 1)

**REQ-06: Payout cadence disclosure at tier selection**

The payout cadence disclosure from REQ-04 must also appear at the tier selection screen (REQ-01) so the merchant is informed before they select a tier. It renders as a brief "how payouts work" note below the three tier cards. Exact wording matches REQ-04.

**REQ-07: Tier 1+ WhatsApp line request checkbox**

If the merchant selects Premium Tier 1 or Tier 2 at the tier selection step, the store form in `BusinessUpdateComponent` must show a checkbox: "Request a dedicated iZinga WhatsApp order line for my store." Ticking it sets `StoreProfile.whatsappLineRequested = true`. The checkbox label must include: "(The WhatsApp number will be issued by iZinga and remains iZinga's property. It will not be transferred to you on downgrade.)" The checkbox is hidden for Free tier. Per ADR-022 Decision 5: setting this field does not trigger any backend provisioning call — the ADMIN reads the flag from the admin dashboard and manually runs the WA-LINES-02 provisioning endpoint.

**REQ-08: Dashboard tier badge and upgrade CTA**

The `/business/dashboard` component must display, in a clearly visible position near the store header:
- A tier badge showing the merchant's current `subscriptionTier` (`FREE` / `PREMIUM TIER 1` / `PREMIUM TIER 2`) in iZinga gold for Premium tiers and teal for Free. Null `subscriptionTier` renders as FREE.
- For Free tier: an "Upgrade to Premium" button that navigates to the tier selection screen pre-filled with the store's current data.
- A storefront activation control that is disabled and displays "Complete merchant agreement to activate" if `icaAccepted` is false (or null) on the store's `StoreProfile`. When `icaAccepted` is true, the existing activation behaviour applies.

---

### Phase 3 — Later (deferred by SA in ADR-022; require separate co-founder sign-off before building)

**REQ-09: customDomainPreference field (deferred by SA ADR-022 Decision 6)**

`StoreProfile.customDomainPreference: String?` — advisory string capturing the merchant's preferred custom domain. Deferred because no infrastructure consumer (DNS provisioning) exists. Add this field to the model and to the store form (visible for Tier 1+, optional, domain format validation) only when domain provisioning infrastructure is scoped and ready. Do not add to the StoreProfile model in the ONB-02 feature branch.

**REQ-10: Tier 2 AI assistant opt-in capture (deferred by SA ADR-022 Decision 6)**

AI opt-in fields (`aiAssistantEnabled`, `aiAdviceModeRequested`, `aiLicenceWarrantAccepted`) are deferred to a future brief, blocked on Jason van der Merwe's attorney sign-off on L-07 clause 12 (highest-risk clause — Advice-Enabled Mode). Do not add these fields to the StoreProfile model in this feature branch.

**REQ-11: Subscription billing** — deferred to TIER-BILLING-01.

**REQ-12: Branded storefront rendering** — deferred. Brand colour fields already on `StoreProfile`. Separate brief.

---

## Data Model Changes (ADR-022 — binding)

The following six fields are added to `StoreProfile` in Phase 1. All are nullable with null defaults.

| Field | Type | Kotlin default | TS type | Phase | Notes |
|---|---|---|---|---|---|
| `subscriptionTier` | `SubscriptionTier` enum | `null` | `SubscriptionTier \| null` | 1 | Enum in `izinga-commons`. null = FREE in all logic |
| `subscriptionTierSince` | `Date` | `null` | `Date \| null` | 1 | Set server-side; clients must not supply |
| `icaAccepted` | `Boolean` | `null` | `boolean \| null` | 1 | ADR-017 pattern; on `StoreProfile` not `UserProfile` |
| `icaAcceptedDate` | `Date` | `null` | `Date \| null` | 1 | Timestamp of acceptance click |
| `icaVersion` | `String` | `null` | `string \| null` | 1 | e.g. `"merchant-v2"` |
| `whatsappLineRequested` | `Boolean` | `null` | `boolean \| null` | 1/2 | Signal only; no provisioning side-effect |

**New `SubscriptionTier.kt` enum** in `izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/`:
```
FREE, PREMIUM_1, PREMIUM_2
```

**New `StoreAgreementAudit` collection** in MongoDB (insert-only — no update or delete operations are ever permitted on this collection):

| Field | Type | Notes |
|---|---|---|
| `storeId` | `String` | FK to `StoreProfile._id` |
| `acceptedByUserId` | `String` | UID of the user who clicked accept |
| `icaVersion` | `String` | e.g. `"merchant-v2"` |
| `acceptedAt` | `Date` | Server timestamp at time of write |
| `ipAddress` | `String` | Request IP (see RISK-03) |

The `StoreProfile` ICA fields hold the current live state. The `StoreAgreementAudit` collection holds the immutable history (prima facie proof under ECT Act per L-07 clause 20.2).

**Pre-existing TS drift fix (required in the same PR as all TS model changes):**

`izinga-onboarding/src/app/model/storeProfile.ts` `StoreTypeEnum` must have four values added: `MOVERS`, `TIPS`, `LICENSING`, `PARTS`. These exist in the Kotlin `StoreType` enum but are absent from the TS model. ADR-022 Decision 3 requires this drift to be fixed in the same PR as any new TS model field additions — do not add new enum fields to a model with known drift.

**Deferred to Phase 3 (not added in this feature branch):**
- `customDomainPreference` (ADR-022 Decision 6)
- `aiAssistantEnabled`, `aiAdviceModeRequested`, `aiLicenceWarrantAccepted` (ADR-022 Decision 6)

---

## API Changes

**New endpoint — `PATCH /store/{id}/ica-acceptance`**

Module: `izinga-ordermanager`, `StoreControler.java`

Request body:
```json
{
  "icaAccepted": true,
  "icaVersion": "merchant-v2"
}
```

Auth: `STORE_ADMIN` role where the JWT `storeId` claim matches `{id}`, OR `ADMIN` role (auth read from JWT token — never from request body; see ADR-022 Decision 4).

Service-layer validation (`StoreService`):
- `icaAccepted` must be `true` — return HTTP 400 if `false`.
- `icaVersion` must be a non-empty string — return HTTP 400 if blank.
- If `icaAccepted` is already `true` on the store and the version matches, return HTTP 200 (idempotent).
- If the version differs (re-acceptance after agreement update), update `icaVersion` and `icaAcceptedDate`; do not clear `icaAccepted`.

On success:
1. Write `icaAccepted = true`, `icaAcceptedDate = now()`, `icaVersion` to `StoreProfile`.
2. Write an insert-only document to `StoreAgreementAudit` collection.
3. Return the full updated `StoreProfile` (HTTP 200).

Audit log invariant: the `StoreAgreementAudit` write must never fail silently. If the audit write fails, the endpoint must return HTTP 500 and roll back the `StoreProfile` write. The audit record is a legal requirement (ECT Act, L-07 clause 20.2).

**New endpoint — `PATCH /store/{id}/subscription-tier`**

Module: `izinga-ordermanager`, `StoreControler.java`

Request body:
```json
{
  "subscriptionTier": "PREMIUM_1"
}
```

Auth: `STORE_ADMIN` role where JWT `storeId` claim matches `{id}`, OR `ADMIN` role. Auth read from JWT — never from request body (IDOR prevention; ADR-022 Decision 4).

Service-layer validation (`StoreService`):
- `subscriptionTier` must be a valid `SubscriptionTier` enum value — return HTTP 400 otherwise.
- `StoreProfile.icaAccepted` must be `true` and `icaVersion` non-null — return HTTP 422 if not (ADR-022 Decision 4 binding). Error body: `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }`.
- `subscriptionTierSince` is set server-side to `now()` — clients must not supply this field.

Tier change audit: see Open Question 3 below — SA has flagged this as a decision pending PO input.

Response: the full updated `StoreProfile` (HTTP 200).

**Modified endpoint — `POST /store`**

Add service-layer C-04 gate in `StoreService`: reject HTTP 403 `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }` if the creating user has role `STORE_ADMIN` and the request body does not carry `icaAccepted: true`. Gate is in the service layer, not the controller (ADR-022 Decision 4).

**Modified endpoints — `GET /store/{id}`, `GET /store/admin`, `GET /stores`**

Must return all six new `StoreProfile` fields in the response body. All are nullable — existing callers receive null for all new fields and must not break (non-breaking change confirmed by SA).

---

## UX Flow (Target — matches review proposal)

```
biz.izinga.co.za
  → /business                  WelcomeBusinessComponent (unchanged; captures ?ref=)
  → /business/verify           PhoneVerificationComponent (unchanged; Firebase OTP)
  → /business/user             UserUpdateComponent (unchanged)
  → /business/signup-welcome/:id  (unchanged)
  → /business/tier-select/:id  [NEW] TierSelectionComponent
                                  — three tier cards (Free, Premium Tier 1, Premium Tier 2)
                                  — 6.5% fee disclosure on all three cards
                                  — payout cadence note below cards (Phase 2: move to this
                                    screen; Phase 1: note may appear at bank details step only)
                                  — on tier selection: store tier in session state;
                                    navigate to /business/terms/:id
  → /business/terms/:id        TermsConditionsComponent
                                  — STORE_ADMIN branch (REQ-02):
                                    if MERCHANT_ICA_ENABLED feature flag:
                                      render L-07 agreement, require scroll,
                                      checkbox + confirm → PATCH /store/{id}/ica-acceptance
                                    else:
                                      existing general terms flow (no regression)
  → /business/info/:id         BusinessUpdateComponent (extended per REQ-03, REQ-04, REQ-07)
                                  — bank details required on create path (REQ-04)
                                  — payout cadence callout above bank section (REQ-04)
                                  — whatsappLineRequested checkbox if Tier 1+ (Phase 2 REQ-07)
                                  — on submit: service-layer C-04 gate enforced (REQ-03)
  → /business/dashboard         (existing; extended per Phase 2 REQ-08)
                                  — tier badge
                                  — upgrade CTA if FREE or null tier
                                  — storefront activation blocked if icaAccepted = false/null
```

Route guard: if a user navigates directly to `/business/info/:id` without having passed through `/business/tier-select/:id` in the current session, redirect to `/business/tier-select/:id`. The guard checks component state (not persisted; loss on refresh is acceptable — user restarts the step).

---

## Dependencies and Gates

### Gate (a) — Security & Compliance review (blocks implementation start)

Three specific areas require iZinga Security & Compliance assessment before implementation starts. This gate is structured identically to the WA-LINES-02 gate review process.

**SEC-ONB02-01:** POPIA compliance of the C-04 merchant verification gate and `StoreAgreementAudit` data collection.
**SEC-ONB02-02:** Insert-only enforcement of `StoreAgreementAudit` at the repository layer; POPIA lawful basis for IP address retention.
**SEC-ONB02-03:** PATCH endpoint auth — confirm JWT storeId claim prevents IDOR; confirm `ADMIN` role is not overly broad (SA flagged: does the `ADMIN` role include service accounts like the WhatsApp inbound service account that should not be able to change commercial tiers?).

Engineering does not start work on any task until Security & Compliance returns a PASS or CONDITIONAL-PASS verdict for all three areas. (Exception: T-10 `TierSelectionComponent` UI work carries no backend risk and may proceed in parallel with the Security & Compliance review.)

### Gate (b) — Attorney sign-off before click-wrap ships to production

The click-wrap STORE_ADMIN branch (REQ-02) may be built on the feature branch using the v2 draft text of L-07 behind the `MERCHANT_ICA_ENABLED` feature flag (`false` in all environment files). The feature flag must remain `false` in `environment.prod.ts` until:
1. Jason van der Merwe provides written sign-off on L-07 (all Part C open items including CPA s14/s44, regulated-industry platform liability, and co-founder sign-off requirement resolved).
2. Lindani Masinga provides co-founder sign-off on the final L-07 text.
3. Release Manager sets `MERCHANT_ICA_ENABLED = true` in `environment.prod.ts` as part of the production release.

This gate does NOT block building the click-wrap on the feature branch. Implementation proceeds under the flag.

### Gate (c) — AI Code Review PASS before merge to develop

All pull requests from the feature branch into `develop` must carry an AI Code Review PASS verdict. No merge without the verdict.

### Gate (d) — Release Manager owns merge to main

The Release Manager merges `release/*` to `main`. No direct merges to `main`. The Release Manager must confirm Gate (b) is satisfied before setting `MERCHANT_ICA_ENABLED = true` in `environment.prod.ts`.

### Other dependencies

- WA-LINES-02 must be in `develop` before REQ-07 Phase 2 (WhatsApp request checkbox) is implemented, to confirm the admin dashboard signal flow. WA-LINES-02 is in `develop` as of this brief's issue date.
- The static PDF asset `store-merchant-partner-agreement-merchant-v2.pdf` must be built from `izinga-legal/drafts/store-partner-agreement-draft-v2.md` and placed at `izinga-onboarding/src/assets/legal/` before Gate (b) flips. Until then, Engineering renders the markdown source inline for testing.
- Attorney tracker item C-04 (OPEN/HIGH PRIORITY/POPIA) is addressed by REQ-03. This brief is the Product Owner's formal response to that tracker item.

---

## Risks

**RISK-01 (LEGAL — attorney gate):** The Store/Merchant Partner Agreement draft v2 (L-07) has not been reviewed by Jason van der Merwe. CPA s14/s44 (fixed-term agreement / online cooling-off), the regulated-industry platform liability question, and the AI Advice-Enabled Mode clause are flagged as open items. The click-wrap must not go to production until Jason signs off. The `MERCHANT_ICA_ENABLED` feature flag is the engineering enforcement mechanism.

**RISK-02 (POPIA — C-04):** Activating a merchant storefront without verification creates POPIA exposure (attorney tracker C-04, HIGH PRIORITY). REQ-03 closes this gap. The service-layer gate on `POST /store` is the enforcement point. If Engineering removes or bypasses this gate for any reason, it must be escalated to Lindani Masinga and the iZinga Legal & Compliance agent immediately.

**RISK-03 (AUDIT TRAIL — IP address):** The `StoreAgreementAudit` collection captures `ipAddress` from the incoming request. IP addresses may constitute personal information under POPIA. Security & Compliance (Gate (a), SEC-ONB02-02) must confirm the lawful basis for retention before the endpoint is deployed. If Security & Compliance rules that IP address cannot be stored, remove the field from the audit document and from the endpoint implementation.

**RISK-04 (REGRESSION — TermsConditionsComponent):** The existing Ambassador and Driver ICA branches must not be modified. The new STORE_ADMIN branch is an `else if` addition. The `isStoreAdmin` getter uses `UserProfile.RoleEnum.STORE_ADMIN` (confirmed from `ProfileRoles.kt`); a STORE-role fallthrough is included for safety (matching the existing `isShop` pattern). The Code Reviewer must verify neither the Ambassador branch (`isAmbassador`) nor the Driver branch (`isDriver`) is altered in the PR.

**RISK-05 (NULL HANDLING — existing stores):** All new `StoreProfile` fields are nullable. Every client (Angular, Flutter) must treat null `subscriptionTier` as `FREE` and null `icaAccepted` as `false`. Failure to handle nulls will cause NPE crashes on screens that read these fields from existing stores. This must be a mandatory checklist item in the Code Review gate.

**RISK-06 (TS DRIFT — StoreTypeEnum):** The `StoreTypeEnum` in `izinga-onboarding/src/app/model/storeProfile.ts` has 4 missing values. The Code Reviewer must fail any PR that adds new fields to this model without also fixing the drift (ADR-022 Decision 3 binding).

**RISK-07 (IDOR — PATCH endpoint):** The `PATCH /store/{id}/subscription-tier` endpoint must read the storeId from the JWT token, not from the request body, to prevent IDOR. Security & Compliance SEC-ONB02-03 covers this. Engineering must not implement this endpoint until SEC-ONB02-03 returns a verdict.

---

## Task Breakdown

All tasks run on `feature/ONB-02-store-owner-onboarding-tiers` branched from `develop` in the respective repos.

| ID | Task | Repo | Agent | Depends on | Phase |
|---|---|---|---|---|---|
| T-01 | Create feature branch `feature/ONB-02-store-owner-onboarding-tiers` from `develop` in `izinga-onboarding` and `ijudi-api` | Both | iZinga Onboarding UI/UX Developer + iZinga Backend Developer | none | 1 |
| T-02 | Security & Compliance review: assess SEC-ONB02-01 (POPIA / C-04 / audit collection), SEC-ONB02-02 (insert-only enforcement + IP retention), SEC-ONB02-03 (PATCH IDOR + ADMIN role scope); return PASS or CONDITIONAL-PASS before any backend implementation starts | `ijudi-api` | iZinga Security & Compliance | T-01 | 1 |
| T-03 | Backend: create `SubscriptionTier.kt` enum (`FREE`, `PREMIUM_1`, `PREMIUM_2`) in `izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/` | `ijudi-api` | iZinga Backend Developer | T-02 | 1 |
| T-04 | Backend: add six nullable fields to `StoreProfile.kt` in `izinga-commons` (`subscriptionTier`, `subscriptionTierSince`, `icaAccepted`, `icaAcceptedDate`, `icaVersion`, `whatsappLineRequested`); add `whatsappLineRequested` field comment: "Request signal only — no provisioning side-effect (ADR-022 Decision 5)" | `ijudi-api` | iZinga Backend Developer | T-03 | 1 |
| T-05 | Backend: create `StoreAgreementAudit` MongoDB collection and a repository interface that exposes only `save()` and `findByStoreId()` — no update or delete operations. Fields: `storeId`, `acceptedByUserId`, `icaVersion`, `acceptedAt`, `ipAddress` | `ijudi-api` | iZinga Backend Developer | T-02, T-04 | 1 |
| T-06 | Backend: implement `PATCH /store/{id}/ica-acceptance` in `StoreControler.java`; service-layer validation in `StoreService`; audit write to `StoreAgreementAudit` must be atomic with `StoreProfile` write (if audit write fails, roll back StoreProfile write and return HTTP 500) | `ijudi-api` | iZinga Backend Developer | T-05 | 1 |
| T-07 | Backend: add service-layer C-04 gate to `StoreService.createStore()` — reject HTTP 403 `MERCHANT_ICA_NOT_ACCEPTED` if STORE_ADMIN and `icaAccepted != true` | `ijudi-api` | iZinga Backend Developer | T-04 | 1 |
| T-08 | Backend: add `bank` required validation to `StoreService.createStore()` — reject HTTP 400 if `bank` is null or any of the five required `Bank` fields (`accountId`, `name`, `branchCode`, `phone`, `type`) is missing/blank/invalid on the create path | `ijudi-api` | iZinga Backend Developer | T-01 | 1 |
| T-09 | Backend: implement `PATCH /store/{id}/subscription-tier` in `StoreControler.java` + `StoreService`; auth from JWT storeId claim (never request body); icaAccepted gate (HTTP 422 if not accepted); `subscriptionTierSince = now()` set server-side; tier change audit write (method TBD pending Open Question 3 resolution) | `ijudi-api` | iZinga Backend Developer | T-02, T-04, T-05 | 1 |
| T-10 | Frontend: create `TierSelectionComponent` at route `/business/tier-select/:id` — three tier cards matching store-draft.html copy; 6.5% fee disclosure on all three cards; payout cadence note below cards; tier selection stores value in component state; route guard on `/business/info/:id` redirects to tier select if no tier in session state; null `subscriptionTier` displays as FREE | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-01 | 1 |
| T-11 | Frontend: add STORE_ADMIN branch to `TermsConditionsComponent` — `MERCHANT_ICA_VERSION = 'merchant-v2'` constant; `isStoreAdmin` getter using `UserProfile.RoleEnum.STORE_ADMIN` (confirmed enum value; STORE fallthrough for safety per REQ-02 note); `needsMerchantIcaAcceptance` getter; L-07 agreement rendering in scrollable block; scroll-to-bottom required before checkbox enables; acceptance flow calls `PATCH /store/{id}/ica-acceptance`; `MERCHANT_ICA_ENABLED` feature flag defaults `false` in both environment files | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-06 | 1 |
| T-12 | Frontend: add TS model fields to `storeProfile.ts` — six new optional fields + `SubscriptionTier` union type; in the same PR fix `StoreTypeEnum` drift by adding `MOVERS`, `TIPS`, `LICENSING`, `PARTS` (ADR-022 Decision 3; this is a Definition of Done item, not optional) | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-04 | 1 |
| T-13 | Frontend: add `Bank` details required section to `BusinessUpdateComponent` create path — `accountId` (numeric, 8–11 digits), `name` (bank name select), `branchCode` (numeric), `phone`, `type` (select: CHEQUE, SAVINGS, TRANSMISSION, EWALLET — do NOT include legacy `wallet` or `string` options); payout cadence callout directly above bank fields; skip validation on edit path if `StoreProfile.bank` already populated | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-08 | 1 |
| T-14 | Frontend: wire tier selection session state into `BusinessUpdateComponent.registerBusinessAndStock()` so `subscriptionTier` is included in the `POST /store` payload | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-09, T-10 | 1 |
| T-15 | Flutter: add null-safe deserialisation for all six new `StoreProfile` fields; treat `subscriptionTier == null` as `FREE` in all display logic; no UI changes required unless tier affects an existing rendered field | `ijudi` | iZinga Flutter Developer | T-04 | 1 |
| T-16 | Frontend (Phase 2): add `whatsappLineRequested` checkbox to `BusinessUpdateComponent` — visible for Tier 1+ only; disclosure label per REQ-07; signal only (no backend event triggered by the checkbox itself) | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-12, T-10 | 2 |
| T-17 | Frontend (Phase 2): add tier badge, upgrade CTA, and storefront activation gate to `/business/dashboard` component — null tier renders as FREE badge; upgrade CTA for FREE; storefront activation disabled with message if `icaAccepted` is false or null | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-09, T-11 | 2 |
| T-18 | QA: write test plan and execute against all Phase 1 ACs (AC-01 through AC-13); full unscoped test suite must be green; block merge to `develop` until all ACs pass | Both | iZinga QA & Test Automation | T-07, T-08, T-11, T-13, T-14, T-15 | 1 |
| T-19 | AI Code Review: review all Phase 1 changes across both repos; verify null handling on all new fields; verify C-04 gate is in service layer not controller; verify STORE_ADMIN branch does not regress Ambassador/Driver ICA flows; verify `StoreTypeEnum` drift is fixed; produce PASS/FAIL verdict | Both | iZinga Code Reviewer | T-18 | 1 |
| T-20 | QA: write test plan and execute against Phase 2 ACs (AC-14 through AC-17) | Both | iZinga QA & Test Automation | T-16, T-17 | 2 |
| T-21 | AI Code Review: review Phase 2 changes; produce PASS/FAIL verdict | Both | iZinga Code Reviewer | T-20 | 2 |
| T-22 | Release Manager: prepare `release/*` for Phase 1 + 2; confirm Gate (b) attorney sign-off on file; set `MERCHANT_ICA_ENABLED = true` in `environment.prod.ts` intentionally and explicitly; merge to `main` | Both | iZinga Release Manager | T-19, T-21, Gate (b) | 1+2 |
| T-23 | Frontend investigation: trace `UserUpdateComponent` and `BusinessUpdateComponent` to confirm exactly where the owner's role is set to `STORE_ADMIN` during biz.izinga.co.za signup. The `TermsConditionsComponent` STORE_ADMIN branch depends on the user's role being `STORE_ADMIN` at the point the `/business/terms/:id` route is hit — confirm this is always true before the terms step is reached, and confirm no path exists where a user arrives at that route with a non-STORE_ADMIN role in a business onboarding session. Document the finding as a comment on the T-11 PR. | `izinga-onboarding` | iZinga Onboarding UI/UX Developer | T-01 | 1 |

**Safe implementation sequence (ADR-022 binding):**

1. T-01, T-02, T-23 (branch creation + Security & Compliance review + role-set investigation — all parallel)
2. T-03 (enum) → T-04 (StoreProfile fields) — prerequisite for all other backend work
3. T-05, T-07, T-08 (audit collection + C-04 gate + bank validation — parallel, all depend on T-04)
4. T-06, T-09 (PATCH endpoints — parallel, depend on T-05)
5. T-10, T-12 (frontend tier selection + TS model — can start after T-01; T-12 needs T-04 for field names)
6. T-11, T-13, T-14, T-15 (frontend ICA branch + bank fields + tier wiring + Flutter — parallel once T-06, T-08, T-09, T-12, T-23 complete)
7. T-18, T-19 (QA + Code Review — sequential)
8. T-16, T-17 (Phase 2 frontend — after Phase 1 ships to develop)
9. T-20, T-21, T-22 (Phase 2 QA + Code Review + Release)

---

## Acceptance Criteria

### Phase 1

**AC-01 (Tier selection — presence):** Given a new store owner who has completed OTP verification and user profile, when they reach `/business/signup-welcome/:id` and click Continue, then they are navigated to `/business/tier-select/:id` showing three tier cards (Free, Premium Tier 1, Premium Tier 2) before any store details form is shown.

**AC-02 (Fee disclosure — all tiers):** Given the tier selection screen is visible, when the merchant views any of the three tier cards, then the text communicating that a 6.5% service fee is added at customer checkout and paid by the customer (not deducted from the payout) is visible on each of the three cards without scrolling on a 1280px viewport.

**AC-03 (Tier selection — route guard):** Given a merchant navigates directly to `/business/info/:id` without having passed through `/business/tier-select/:id` in the current session, when the route resolves, then the merchant is redirected to `/business/tier-select/:id`.

**AC-04 (Click-wrap — feature flag off, no regression):** Given `MERCHANT_ICA_ENABLED` is `false` (the default in all environment files), when a STORE_ADMIN user reaches `/business/terms/:id`, then the existing general terms flow renders unchanged; the Ambassador ICA flow and the Driver ICA flow are unaffected.

**AC-05 (Click-wrap — feature flag on, STORE_ADMIN):** Given `MERCHANT_ICA_ENABLED` is `true` and the user has role `STORE_ADMIN`, when they reach `/business/terms/:id`, then the Store/Merchant Partner Agreement content is rendered in a scrollable block; the acceptance checkbox is disabled until the merchant has scrolled to the bottom; and ticking the checkbox and clicking Confirm calls `PATCH /store/{id}/ica-acceptance` with `{ "icaAccepted": true, "icaVersion": "merchant-v2" }`.

**AC-06 (Click-wrap — ICA on StoreProfile, not UserProfile):** Given the PATCH in AC-05 returns HTTP 200, when the `StoreProfile` document for that store is fetched from the database, then `icaAccepted = true`, `icaVersion = "merchant-v2"`, and `icaAcceptedDate` is a non-null timestamp; and the `UserProfile` for the accepting user is unchanged (no ICA fields written there).

**AC-07 (Click-wrap — audit log, insert-only):** Given the PATCH in AC-05 returns HTTP 200, then a document exists in the `StoreAgreementAudit` collection with the correct `storeId`, `acceptedByUserId`, `icaVersion`, `acceptedAt` timestamp, and `ipAddress`; issuing an update or delete operation against this collection returns an error (insert-only enforcement confirmed).

**AC-08 (Click-wrap — navigation):** Given the PATCH in AC-05 returns HTTP 200, when the response is received by the frontend, then the app navigates to `/business/info/:id`.

**AC-09 (C-04 gate — server-side):** Given `MERCHANT_ICA_ENABLED` is `true` and a STORE_ADMIN calls `POST /store` with `icaAccepted` absent or `false`, when the server processes the request, then it returns HTTP 403 with body `{ "error": "MERCHANT_ICA_NOT_ACCEPTED" }` and no store record is created in the database.

**AC-10 (C-04 gate — frontend message):** Given the server returns HTTP 403 `MERCHANT_ICA_NOT_ACCEPTED` during store creation, when the frontend handles the error, then the user sees the message "Your merchant agreement acceptance could not be confirmed. Please complete the agreement step before submitting your store details." and remains on the current form screen.

**AC-11 (Bank details — required on create):** Given a merchant is on the store creation form (`storeId` is null in session), when they attempt to submit without completing all five `Bank` fields (`accountId`, `name`, `branchCode`, `phone`, `type`), then field-level validation errors are displayed for the missing fields and `registerBusinessAndStock()` is not called.

**AC-12 (Bank details — account number format):** Given the `accountId` field is visible, when the merchant enters a value that is not 8–11 numeric digits (e.g. "ABCD", "123", or a 12-digit string), then a validation error is displayed inline and form submission is blocked.

**AC-13 (Payout disclosure — bank section):** Given the merchant is on the store creation form, when the bank details section is visible, then the payout cadence callout is rendered directly above the bank fields stating that iZinga initiates payouts every Business Day and that bank settlement takes up to 3 Business Days (paid by the bank, not guaranteed by iZinga).

**AC-14 (SubscriptionTier — stored on create):** Given a merchant selects Premium Tier 1 at the tier selection step and completes store creation, when the resulting `StoreProfile` is retrieved from the backend, then `subscriptionTier = "PREMIUM_1"` and `subscriptionTierSince` is a non-null timestamp.

**AC-15 (Null tier — treated as FREE):** Given an existing store whose `StoreProfile` has `subscriptionTier = null`, when any frontend (Angular or Flutter) renders a screen that displays the tier, then "FREE" is displayed rather than null, an empty string, or an error.

**AC-16 (StoreType drift fix):** Given `izinga-onboarding/src/app/model/storeProfile.ts` is inspected after the ONB-02 PR merges to develop, then `StoreTypeEnum` contains `MOVERS`, `TIPS`, `LICENSING`, and `PARTS` in addition to the pre-existing values.

**AC-17 (PATCH subscription-tier — IDOR prevention):** Given an authenticated STORE_ADMIN user whose JWT `storeId` claim is `"store-A"` attempts to call `PATCH /store/store-B/subscription-tier` (a different store), when the server processes the request, then it returns HTTP 403 and the `StoreProfile` for store-B is unchanged.

### Phase 2

**AC-18 (Dashboard tier badge):** Given a store owner is logged in and their `StoreProfile.subscriptionTier` is `FREE` (or null), when they view `/business/dashboard`, then a badge displaying "FREE" in teal and an "Upgrade to Premium" button are both visible in the store header section without scrolling.

**AC-19 (Dashboard storefront activation gate):** Given `StoreProfile.icaAccepted` is `false` or null for a store, when the store owner views `/business/dashboard`, then the storefront activation control is disabled and the label "Complete merchant agreement to activate" is displayed adjacent to the control.

**AC-20 (WhatsApp request checkbox — Tier 1 visible):** Given the merchant selected Premium Tier 1 or Tier 2 at the tier selection step, when they reach the store form in `BusinessUpdateComponent`, then the `whatsappLineRequested` checkbox is visible and its label includes the disclosure that the WhatsApp number remains iZinga's property and will not be transferred on downgrade.

**AC-21 (WhatsApp request checkbox — Free hidden):** Given the merchant selected Free tier, when they reach the store form, then the `whatsappLineRequested` checkbox is not rendered on the page.

---

## Marketing Trigger

**No** — store owner onboarding alignment is product infrastructure. Marketing messaging about the tier offering is already live on the website. A separate marketing trigger for a Premium tier launch campaign (email blast, Meta ads targeting store owner acquisition) should be raised once Phase 1 ships to production and the click-wrap gate is open. Brief the Marketing Strategist at least 2 weeks before the planned campaign date.

---

## Definition of Done

All of the following must be true before this feature is considered complete and in production:

1. Security & Compliance has returned PASS or CONDITIONAL-PASS on SEC-ONB02-01 through SEC-ONB02-03 (Gate (a)).
2. Phase 1 is deployed to production with `MERCHANT_ICA_ENABLED = false` (click-wrap built but flagged off).
3. The tier selection screen is live at `/business/tier-select/:id`; the route guard is enforced.
4. Bank account details are required on the store create path; payout cadence disclosure is visible.
5. The service-layer C-04 gate returns HTTP 403 for any STORE_ADMIN store creation without `icaAccepted = true`.
6. The `StoreTypeEnum` drift fix is confirmed in `storeProfile.ts`.
7. Phase 2 is deployed: tier badge on dashboard; WhatsApp request checkbox visible for Tier 1+.
8. Jason van der Merwe attorney sign-off on L-07 is received and on file in `izinga-legal/attorney-correspondence/`.
9. Lindani Masinga co-founder sign-off on the final L-07 text is on file.
10. `MERCHANT_ICA_ENABLED` is set to `true` in `environment.prod.ts` by the Release Manager (Gate (b) confirmed).
11. End-to-end test confirms a new store owner can complete: tier selection → agreement acceptance → store creation with bank details → dashboard showing tier badge. The resulting `StoreProfile` has `icaAccepted = true`, `icaVersion = "merchant-v2"`, `subscriptionTier` set, `subscriptionTierSince` non-null.
12. The `StoreAgreementAudit` collection contains at least one record from the end-to-end test confirming insert-only behaviour.
13. iZinga Growth & Analytics briefed to report on Phase 1 + 2 performance (tier distribution, agreement completion rate, drop-off points) 2 weeks after production launch.

---

## Open Questions for Lindani

The following items were resolved from the codebase and do not require Lindani's input. Three items remain as decisions for Lindani to confirm. Proposed defaults are stated — confirm or amend.

**RESOLVED — no action required:**

- `STORE_ADMIN` enum value: confirmed as `UserProfile.RoleEnum.STORE_ADMIN` from `ProfileRoles.kt` in `izinga-commons`. The store entity itself has role `STORE` (fixed by the `StoreProfile` constructor). The ICA acceptance record goes on `StoreProfile` (role STORE) per ADR-022. The acceptance branch in `TermsConditionsComponent` fires on the user's role (`STORE_ADMIN`).
- `bank` field: confirmed as `StoreProfile.bank` (type `Bank`) from `Bank.kt`. Fields: `accountId`, `name`, `branchCode`, `phone`, `type` (BankAccType: CHEQUE, SAVINGS, TRANSMISSION, EWALLET). Legacy values `wallet` and `string` must not be offered in the UI. All except `type` are `@NotBlank` on the backend.

**Pending Lindani confirmation (proposed defaults below):**

1. **Tier change audit collection** (PROPOSED: separate `StoreTierChangeAudit` collection — confirm or amend): ADR-022 Decision 4 leaves this as a PO decision. The proposed default is a separate `StoreTierChangeAudit` collection distinct from `StoreAgreementAudit`. Fields: `storeId`, `fromTier`, `toTier`, `changedByUserId`, `changedAt`. Rationale: keeps agreement acceptance history and tier change history queryable independently; simpler to join against a future `Subscription` document for billing reconciliation. T-09 is blocked on this confirmation — the Backend Developer needs to know which collection to write to.

2. **Attorney sign-off process** (PROPOSED: Legal & Compliance drafts the briefing; Lindani sends to Jason directly — confirm or amend): Attorney tracker Part C item 1 marks L-07 as "OPEN — Feature Brief required; deployment blocked until built." This brief is that Feature Brief. The proposed default: the iZinga Legal & Compliance agent drafts the attorney briefing package for L-07; Lindani sends it to Jason van der Merwe directly (Lindani is Jason's client). Gate (b) is on the critical path to production — this should be triggered promptly.

3. **Merchant ICA version string convention** (PROPOSED: `merchant-v1`, `merchant-v2`, ... incrementing — confirm or amend): The proposed convention follows the driver-ICA pattern (`driver-v2`): increment the number on every attorney-required text change, never use a date-based identifier. Current brief uses `"merchant-v2"` to match L-07 v2. If Jason requires material text changes before signing off, the version becomes `"merchant-v3"`, and `MERCHANT_ICA_VERSION` is bumped in the same commit as the updated static asset. This convention is already written into REQ-02 and T-11.

---

*Brief authored by iZinga Product Owner. Approved for Implementation as of 2 October 2026.*
*ADR-022 (iZinga Solution Architect, 2 October 2026) incorporated — all SA decisions binding.*
*Codebase answers incorporated 2 October 2026: STORE_ADMIN enum confirmed from ProfileRoles.kt; bank field confirmed as StoreProfile.bank (Bank.kt) with fields accountId / name / branchCode / phone / type.*
*Gate (a) — Security & Compliance review — blocks implementation start. Gate (b) — attorney sign-off — blocks production release of the click-wrap.*
*3 open questions remain pending Lindani confirmation (see Open Questions section above).*
