# ONB-02 — Gate Review: Solution Architect (ADR-022)

**Date:** 2 October 2026
**Reviewer:** iZinga Solution Architect
**Status:** APPROVED WITH REQUIRED CHANGES — six design questions resolved below

---

## ADR-022: ONB-02 Store-Owner Onboarding Tiers — Architecture Sign-Off

**Status:** Approved (decisions below are binding; implementation blocked on Security & Compliance handoff at the end of this document)

**Requested by:** Lindani Masinga

**Context:** ONB-02 introduces a three-tier store-platform offering (FREE / PREMIUM_1 / PREMIUM_2) at biz.izinga.co.za. The onboarding review proposed adding subscription tier, merchant agreement click-wrap, domain preference, WhatsApp line request, and AI assistant flags to `StoreProfile`. Six design questions required SA sign-off before the PO finalises task breakdown.

---

## Decision 1 — Merchant Agreement Acceptance: StoreProfile, not UserProfile

**Verdict: Place icaAccepted / icaAcceptedDate / icaVersion on StoreProfile.**

ADR-017's identical fields live on `UserProfile` for drivers and ambassadors because those agreements are personal employment-style contracts with the individual. The merchant agreement is a business contract with the legal entity (the store). Three forcing functions:

1. **Ownership transfer.** When `ownerId` changes, the new owner must re-accept. If the fields were on `UserProfile`, the old user's acceptance would remain valid for a store they no longer own — silent breach. Placing them on `StoreProfile` means transfer logic must clear or re-gate acceptance, which is correct behaviour.

2. **Multiple admins.** A STORE_ADMIN who is not the ownerId must be able to trigger acceptance on behalf of the business. Acceptance on `UserProfile` would be per-person and ambiguous about business authority.

3. **Audit trail.** Per ADR-017 the audit must be insert-only. Create a new `StoreAgreementAudit` collection (one document per acceptance event: `storeId`, `acceptedByUserId`, `icaVersion`, `acceptedAt`, `ipAddress`). The `StoreProfile` fields hold the current live state; the audit collection holds the immutable history. This mirrors the `UserAgreementAudit` pattern from ADR-017 exactly.

**Required new Kotlin fields on StoreProfile (all nullable, default null — additive, no migration):**
```kotlin
var icaAccepted: Boolean? = null
var icaAcceptedDate: Date? = null
var icaVersion: String? = null
```

---

## Decision 2 — Subscription Tier: Embed on StoreProfile Now; Subscription Document Later

**Verdict: Add subscriptionTier and subscriptionTierSince as fields on StoreProfile for Phase 1. Plan a separate Subscription document for billing.**

Embedding is appropriate for Phase 1 because (a) every frontend that renders store state reads `StoreProfile` — a second API call for tier data adds latency for no gain at this stage, and (b) billing is explicitly out of scope.

When billing arrives: create a `Subscription` document (`storeId`, `tier`, `billingAnniversary`, `history[]`) linked by `storeId`. The `subscriptionTier` on `StoreProfile` becomes a denormalized read-optimised copy, updated by a `SubscriptionService` event on upgrade/downgrade. This is a standard CQRS denormalization pattern — the Subscription document is the source of truth; `StoreProfile.subscriptionTier` is the cache. Frontends never need to change: they keep reading from `StoreProfile`.

**Field name correction:** `subscriptionTierDate` is renamed to `subscriptionTierSince` — it records when the current tier was applied, not a billing date, and the name must not imply billing semantics that do not yet exist.

**Required new Kotlin fields on StoreProfile:**
```kotlin
var subscriptionTier: SubscriptionTier? = null          // null = treat as FREE
var subscriptionTierSince: Date? = null
```

---

## Decision 3 — Enum Placement and Blast Radius

**SubscriptionTier enum placement: `izinga-commons` alongside `StoreType`.**

Create `/izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/SubscriptionTier.kt`:
```kotlin
package io.curiousoft.izinga.commons.model

enum class SubscriptionTier {
    FREE, PREMIUM_1, PREMIUM_2
}
```

This makes it available to all modules that depend on `izinga-commons` (`izinga-ordermanager`, `izinga-messaging`) and matches the pattern used for `StoreType` and `ProfileRoles`.

**Blast radius — null-safety on existing documents:**

All new fields on `StoreProfile` are nullable with null defaults. MongoDB existing documents will return null for all new fields on deserialisation. The backend must treat `subscriptionTier == null` as `FREE` in all business logic. The Flutter app and Angular apps receive null for new fields and must treat null as FREE — this must be stated explicitly in the task brief for each frontend agent.

**Pre-existing TS drift (separate task spawned):** `StoreProfile.StoreTypeEnum` in `izinga-onboarding/src/app/model/storeProfile.ts` declares only FOOD, CLOTHING, SALON, CAR_WASH. The Kotlin `StoreType` enum has 8 values. The four missing values (MOVERS, TIPS, LICENSING, PARTS) must be added in the same PR as the ONB-02 TS changes — do not add new enum fields to a model with known drift.

---

## Decision 4 — PATCH /store/{id}/subscription-tier Endpoint

**Verdict: Confirmed in `izinga-ordermanager`, `StoreControler.java`.**

- **Auth:** `STORE_ADMIN` role where the authenticated user's storeId claim matches `{id}`, OR `ADMIN` role (no storeId check). Read from JWT — never from request body.
- **icaAccepted gate:** The service layer (`StoreService`) must reject the request with HTTP 422 if `StoreProfile.icaAccepted != true` or `icaVersion` is null. The controller does not implement this gate — it belongs in the service as a domain invariant. This mirrors the ambassador ICA gate pattern from ADR-017.
- **Request body:** `{ subscriptionTier: "PREMIUM_1" }` only. The service sets `subscriptionTierSince = now()` server-side — clients must not supply this date.
- **Audit:** Every tier change must write to `StoreAgreementAudit` or a separate `StoreTierChangeAudit` collection (storeId, fromTier, toTier, changedByUserId, changedAt). Decision TBD for PO brief — flag as an open item.

---

## Decision 5 — WA-LINES-02 Interaction: whatsappLineRequested

**Verdict: Field is a request signal only. Admin-driven provisioning flow is unchanged.**

`whatsappLineRequested: Boolean? = null` on `StoreProfile` indicates the merchant has requested a WhatsApp line. It must NOT trigger any automatic provisioning call.

Handoff flow: field set by merchant via onboarding UI → field visible in admin dashboard (izinga-onboarding admin view) → iZinga admin manually calls `StoreLineProvisioningService.provisionStoreLine(storeId)` in `izinga-messaging`. No event, no async job, no direct coupling between `StoreProfile.whatsappLineRequested = true` and `StoreLineProvisioningService`. This preserves the WA-LINES-02 admin-controlled provisioning contract.

---

## Decision 6 — Phase 1 Minimal Set vs Later Phases

**Phase 1 (ONB-02 delivery, tier selection + agreement gate + verification):**
- `subscriptionTier: SubscriptionTier?` — required
- `subscriptionTierSince: Date?` — required
- `icaAccepted: Boolean?` — required
- `icaAcceptedDate: Date?` — required
- `icaVersion: String?` — required
- `whatsappLineRequested: Boolean?` — include (signal only, zero provisioning coupling)

**Phase 2 (deferred — do not add in ONB-02):**
- `customDomainPreference: String?` — advisory string is inert until domain provisioning infrastructure exists; adds noise to the model with no current consumer
- AI assistant fields (`aiAssistantEnabled`, `aiAdviceModeRequested`, `aiLicenceWarrantAccepted`) — deferred to WA-LINES-02 Phase 2 or a future AI-features brief

**No fields rejected outright** — all proposed fields are structurally sound; Phase 2 fields are deferred for scope hygiene, not architectural concern.

---

## Affected Repos

- `ijudi-api` (`izinga-commons`) — new `SubscriptionTier.kt` enum; new fields on `StoreProfile.kt`
- `ijudi-api` (`izinga-ordermanager`) — new PATCH endpoint in `StoreControler.java`; new `StoreAgreementAudit` collection; icaAccepted gate in `StoreService`
- `izinga-onboarding` — TS model additions to `storeProfile.ts`; fix pre-existing StoreType drift in same PR
- `ijudi` (Flutter) — null-safe handling for all new StoreProfile fields; treat null subscriptionTier as FREE
- `cs-lifestyle` / `furniture-delivery-app` — read-only consumers; new optional fields are non-breaking; no code change required unless tier affects display logic

## Affected Agents

- iZinga Backend Developer — izinga-commons enum, StoreProfile fields, PATCH endpoint, icaAccepted gate, StoreAgreementAudit
- iZinga Onboarding UI/UX Developer — onboarding tier selection flow, ICA click-wrap UI, TS model updates, StoreType drift fix
- iZinga Flutter Developer — null-safe deserialization of new StoreProfile fields
- iZinga Security & Compliance — see handoff below

## Breaking Change

No. All field additions are nullable with null defaults. MongoDB is schemaless; existing StoreProfile documents deserialise correctly with null for new fields. The new PATCH endpoint does not replace any existing endpoint. The SubscriptionTier enum is new — no existing switch statement covers it.

## Safe Implementation Sequence

1. `ijudi-api` (`izinga-commons`): Add `SubscriptionTier.kt` enum; add new nullable fields to `StoreProfile.kt`. This is the prerequisite for all other work.
2. `ijudi-api` (`izinga-ordermanager`): Add `StoreAgreementAudit` collection/repository; add icaAccepted gate in `StoreService`; implement PATCH `/store/{id}/subscription-tier`.
3. `izinga-onboarding`: Add TS model fields; fix StoreType drift; implement tier selection and ICA click-wrap UI. Can proceed in parallel with step 2 once step 1 is deployed.
4. `ijudi` (Flutter): Add null-safe field handling. Can proceed in parallel with step 3.
5. Full unscoped test suite green before any merge to develop.

## Risks

| Risk | Mitigation |
|---|---|
| null subscriptionTier treated inconsistently across frontends | SA binding: all clients must treat null as FREE; state explicitly in PO task brief for each frontend |
| icaAccepted gate bypassed if placed in controller not service | Gate must live in StoreService; code review must verify placement |
| whatsappLineRequested misread as a provisioning trigger | Backend Developer must add a comment on the field explicitly stating it is a request signal with no provisioning side-effect |
| TS drift not fixed alongside new fields | Onboarding Developer task must include StoreType drift fix as a Definition of Done item — not a separate ticket |

## Rejected Alternatives

**ICA fields on UserProfile:** Appropriate for personal contracts (driver/ambassador ICA per ADR-017); wrong for business contracts that survive ownership transfer. Rejected.

**Subscription document from day one:** Correct eventual design; premature without billing scope. Would require a second API call for every store view. Rejected for Phase 1; deferred not rejected permanently.

**customDomainPreference in Phase 1:** No infrastructure consumer exists. Adds a free-text field with no validation, no provisioning logic, and no frontend rendering. Rejected for Phase 1.

**Approved by:** Lindani Masinga — pending review
**ADR number:** ADR-022
**Date:** 2 October 2026

---

## Security & Compliance Handoff

**To:** iZinga Security & Compliance
**From:** iZinga Solution Architect
**Re:** ONB-02 security and POPIA review items

Three specific areas require your assessment before implementation starts.

### SEC-ONB02-01: POPIA — Merchant Verification Gate (attorney tracker C-04)

The onboarding tier gate requires verifying the merchant's identity and business registration before PREMIUM tier is activated. This likely involves collecting `regNumber` (already on `StoreProfile`) and potentially ID documents.

Assess:
1. Does the collection of business registration and owner identity data for the verification gate constitute processing of personal information under POPIA Section 11? If yes, confirm the lawful basis (contractual necessity — the merchant is entering a service agreement).
2. Does the `StoreAgreementAudit` collection (which stores `acceptedByUserId`, `ipAddress`, `icaVersion`, `acceptedAt`) require a retention and deletion policy? Flag if yes — this will require a data retention clause in the ICA and a deletion runbook.
3. Is the ICA click-wrap mechanism (checkbox + timestamp + version) legally sufficient for POPIA consent records? Flag any gap.

### SEC-ONB02-02: ICA Audit Trail Adequacy

The `StoreAgreementAudit` collection mirrors ADR-017's insert-only audit log design. Assess:
1. Confirm insert-only is enforced at the repository layer (no update or delete operations permitted on this collection — either via MongoDB role restrictions or a read-only repository interface that exposes only `save()` and `findBy*()`).
2. Confirm `ipAddress` capture is permitted under POPIA for audit purposes. If the IP address constitutes personal information, confirm a lawful basis for retention.

### SEC-ONB02-03: PATCH Endpoint Auth (MEDIUM)

The PATCH `/store/{id}/subscription-tier` endpoint:
1. Confirm the storeId claim read from JWT token (not request body) to prevent IDOR attacks.
2. Confirm ADMIN bypass of the storeId check does not leak tier change ability to service accounts that carry ADMIN role but should not be able to change commercial tiers (e.g. WhatsApp inbound service account). If ADMIN is too broad, recommend a new `BILLING_ADMIN` role or a more targeted permission.

---

*ADR-022 authored by iZinga Solution Architect · 2 October 2026*
*Implementation blocked on Security & Compliance sign-off (SEC-ONB02-01 through SEC-ONB02-03)*

---

## T-02: Security & Compliance Gate Review — ONB-02

**Date:** 2 October 2026
**Reviewer:** iZinga Security & Compliance
**Input:** ADR-022 (T-01 above), ONB-02 feature brief (store-owner-onboarding-tiers.md), WA-LINES-02 T-02 gate review (precedent format and findings)
**Codebase references:** `SecurityConfig.java` (izinga-ordermanager), `StoreControler.java` (izinga-ordermanager), `StoreProfile.kt` (izinga-commons), `TermsConditionsComponent.ts` (izinga-onboarding), `firestore.rules` (izinga-onboarding)

**Verdict: APPROVED WITH REQUIRED CHANGES**

No blocking architectural flaws. Four areas carry required changes that are binding implementation constraints — they must be incorporated into the respective task descriptions before Engineering starts those tasks. No area is outright blocked. T-03 through T-06 (ICA acceptance, C-04 gate, bank details) may proceed immediately. T-SA-07 (subscription-tier PATCH) carries the highest concentration of binding constraints and must not start until all SEC-ONB02-03-x findings are incorporated into its task specification.

---

### Summary table

| Area | ID | Verdict | Severity |
|---|---|---|---|
| C-04 verification gate (POPIA) | SEC-ONB02-01 | APPROVED WITH CHANGES | MEDIUM |
| Merchant agreement audit trail (ECT Act) | SEC-ONB02-02 | APPROVED WITH CHANGES | HIGH |
| PATCH subscription-tier IDOR | SEC-ONB02-03 | APPROVED WITH CHANGES | HIGH |
| Fee and payout disclosures (CPA) | SEC-ONB02-04 | APPROVED WITH NOTES | MEDIUM |
| Deferred AI fields (Phase 1 containment) | SEC-ONB02-05 | APPROVED WITH CONDITIONS | MEDIUM |

---

### SEC-ONB02-01: C-04 Store-Owner Verification Gate (POPIA)

**Assessment input:** REQ-03, PO brief section on C-04, attorney tracker item C-04 (OPEN/HIGH/POPIA), `StoreControler.java`, `StoreProfile.kt`.

The brief's C-04 gate as specified combines two signals: (1) Firebase OTP confirming the registrant controls the phone number; (2) `icaAccepted = true` on the `StoreProfile` as a condition on `POST /store`. This is the proposed gate.

**Finding 1 — Business identity verification scope: CLARIFICATION REQUIRED (MEDIUM)**

The C-04 attorney tracker item is titled "store-partner identity verification before account activation". The brief's implementation gates on phone ownership (OTP) and agreement acceptance (ICA click-wrap). The `regNumber` field already exists on `StoreProfile` and is collected during onboarding (`BusinessUpdateComponent`), but it is self-declared — there is no CIPC API verification or document check proposed.

This is a material gap if C-04 requires verified business identity (i.e., proof that `regNumber` corresponds to a real, registered entity and that the registrant is its authorised representative). If C-04 only requires that the registrant controls the phone number and has accepted the agreement in their own name, the current gate is sufficient.

**Required clarification from Lindani Masinga before T-05 ships to production:** Does C-04 require CIPC lookup/verification or is self-declared `regNumber` + OTP phone ownership sufficient for Phase 1? This is not a code gate — it is a legal scope question that the ICA (L-07) must address. Jason van der Merwe should confirm whether clause 3 (merchant warranties) in L-07 is sufficient to transfer liability for false business registration details onto the merchant, making self-declaration legally adequate. If yes, the gate is sufficient. If CIPC verification is required, a separate task must be scoped for Phase 1 or Phase 2.

**Finding 2 — Audit record: what was verified and by which channel (MEDIUM)**

The `store_ica_audit` collection fields specified in the brief are: `storeId`, `userId`, `icaVersion`, `icaAcceptedAt`, `requestIp`. This records WHAT was accepted and WHEN, but does not record the verification channel or what identity signal was used. For POPIA Section 18 (notification of processing) and for dispute resolution, the audit record should include:

- `verificationChannel`: the channel by which phone ownership was confirmed — for ONB-02 this is always `'WHATSAPP_OTP'` (Firebase OTP via the `/business/verify` route). This is a static value for Phase 1 but must be explicit in the record so it is machine-readable if verification channels diversify in future.
- `userAgent`: the HTTP `User-Agent` header from the acceptance request. This is standard ECT Act prima facie evidence practice (same as ADR-017's ambassador ICA pattern should have included — see Finding 15 in WA-LINES-02). It establishes the browser/device context at acceptance time.

**Required addition to `store_ica_audit` schema (binding for T-04):**

```
{ storeId, acceptedByUserId, icaVersion, icaAcceptedAt, requestIp, userAgent, verificationChannel }
```

`userAgent` and `verificationChannel` must be captured server-side: `userAgent` from the `User-Agent` HTTP header on the `PATCH /store/{id}/ica-acceptance` request; `verificationChannel` hardcoded to `'WHATSAPP_OTP'` for Phase 1.

**Finding 3 — POPIA lawful basis: PASS**

Collection of `regNumber`, phone number, and click-wrap metadata for the verification gate constitutes processing of personal information under POPIA Section 1 (personal information includes information about an identifiable natural person, and a sole trader's business registration number may identify them). The lawful basis under Section 11(1)(c) is contractual necessity: the merchant is entering a service agreement and verification is necessary to perform that agreement. This is a sound basis. No additional consent mechanism is required beyond the ICA click-wrap, provided the ICA itself contains a POPIA-compliant data processing disclosure. Jason van der Merwe must confirm L-07 includes this disclosure clause before Gate (b) is flipped.

**Finding 4 — Data minimisation: PASS WITH NOTE**

The gate collects phone number (already held), `regNumber` (self-declared, minimal), ICA acceptance metadata, IP address, and user-agent. Each has a documented purpose. No finding of over-collection. Note: if in future a government ID document or director ID is added to the verification gate, that escalates to POPIA Section 35 (special personal information — ID numbers) and requires explicit Section 11(1)(a) consent from the data subject, which is a separate legal gate.

---

### SEC-ONB02-02: Merchant Agreement Audit Trail Adequacy (ECT Act)

**Assessment input:** ADR-022 Decision 1, brief REQ-02 and REQ-04 (API changes — `PATCH /store/{id}/ica-acceptance` + `store_ica_audit`), `TermsConditionsComponent.ts` (existing ADR-017 pattern).

**Finding 5 — Insert-only enforcement mechanism not specified: REQUIRED (HIGH)**

The brief states the audit log "must never be mutated or deleted" but does not specify the enforcement mechanism. "Never mutated or deleted" as a rule on paper is not a control. The following two-layer enforcement is required (same standard applied retrospectively to the UserAgreementAudit in ADR-017):

**Layer 1 — Repository interface constraint (mandatory for T-04):** The `StoreAgreementAuditRepository` (or equivalent) must be a Spring Data interface that exposes ONLY `save()` and `findBy*()` methods. It must not extend any repository interface that provides `deleteById()`, `delete()`, `deleteAll()`, `updateBy*()`, or any variant thereof. The Backend Developer must verify that the concrete repository interface hierarchy does not inherit a delete or update method from a parent interface. This must be checked in the T-04 PR.

**Layer 2 — MongoDB role restriction (required DevOps action before production):** The MongoDB application user credential used by `ijudi-api` in production must have `insert` and `find` permissions on the `store_ica_audit` collection (or the `StoreAgreementAudit` collection, whichever name is used) but NOT `update` or `remove`. This is a database-level backstop independent of the application layer. The Backend Developer implementing T-04 must raise a DevOps task for this MongoDB role restriction to be applied in production before `MERCHANT_ICA_ENABLED` is set to `true`. The Release Manager must confirm this restriction is in place as part of the Gate (b) checklist.

**Finding 6 — Retention period not specified in brief or ICA: REQUIRED (MEDIUM)**

No retention period for `store_ica_audit` records is specified anywhere in the brief, the ADR, or the L-07 draft references. POPIA Section 14 requires that personal data not be kept longer than necessary. ECT Act compliance and commercial contract law require that electronic contract records be retained for a period sufficient to defend a dispute — generally 5 years from termination of the service agreement under general SA prescription law (Prescription Act 68 of 1969, three-year extinctive prescription with the clock starting on demand).

**Required addition to L-07 (binding before Gate (b)):** L-07 must include a data retention clause stating that `store_ica_audit` records are retained for a minimum of 5 years from the date of termination or expiry of the merchant's service agreement with iZinga, after which they will be deleted or anonymised. Jason van der Merwe must review and confirm this clause as part of his attorney sign-off. The Release Manager must verify this clause is present before setting `MERCHANT_ICA_ENABLED = true`.

**Finding 7 — Read access not specified: REQUIRED (MEDIUM)**

The brief's description of `store_ica_audit` specifies who must NOT be able to modify it (merchant, STORE_ADMIN tooling) but does not specify who CAN read it. For completeness and to prevent accidental exposure:

- `ADMIN` role: read access permitted (dispute resolution, compliance audit).
- `STORE_ADMIN` role: no direct read access to raw audit records. The merchant can see their own agreement acceptance state via `StoreProfile.icaAccepted` / `icaVersion` / `icaAcceptedDate`, which is the derived state, not the raw audit log.
- No API endpoint should expose `store_ica_audit` documents to the merchant directly.

**Required for T-04:** The service layer must have no method that returns `StoreAgreementAudit` records to a STORE_ADMIN caller. Any future endpoint that reads audit records must be `@PreAuthorize("hasRole('ADMIN')")`.

**Finding 8 — ECT Act evidential adequacy: APPROVED WITH ADDITIONS (HIGH)**

The click-wrap mechanism as specified (version constant `"merchant-v2"`, timestamp, `acceptedByUserId`, `requestIp`) meets the core requirements for ECT Act s11 prima facie proof of electronic contract formation. The additions required by Finding 2 (`userAgent`, `verificationChannel`) are necessary to make the record complete for evidential purposes. Once those fields are added, the mechanism is adequate.

The scroll-to-bottom enforcement before the acceptance checkbox is enabled (REQ-02) is a UX control that demonstrates the merchant was presented with the full agreement text, not merely a summary. This is valuable supporting evidence. PASS, subject to Findings 2 and 6.

**Finding 9 — IP address under POPIA: NOTE ONLY (LOW)**

IP addresses of South African persons may constitute personal information under POPIA's broad definition. Collection of IP for audit purposes has a sound lawful basis under Section 11(1)(f) (legitimate interest: ECT Act compliance, fraud prevention, dispute resolution). The ICA's data processing disclosure clause (per Finding 3) should reference IP address and user-agent collection in the audit record. This is consistent with the ADR-017 pattern and is not a new concern for iZinga. No action required beyond what Finding 3 already requires.

---

### SEC-ONB02-03: PATCH /store/{id}/subscription-tier — IDOR and Auth

**Assessment input:** ADR-022 Decision 4, brief API changes section (PATCH `/store/{id}/subscription-tier`), `SecurityConfig.java`, `StoreControler.java`, SA handoff items SEC-ONB02-03.

The endpoint does not yet exist — it is T-SA-07 (PENDING SA). The following are binding implementation constraints that must be incorporated into the T-SA-07 task specification before the Backend Developer begins.

**Finding 10 — storeId from JWT, not path or body: REQUIRED (HIGH)**

The auth check for STORE_ADMIN access to this endpoint must use the `storeId` custom claim from the authenticated Firebase JWT, never the `{id}` path parameter or any field in the request body. The path `{id}` and body content are data supplied by the caller and must not be trusted as authorization signals.

The implementation pattern (identical to WA-LINES-02 SEC-WA02-03 Finding 10) is:

```java
var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getCredentials();
String jwtStoreId = jwt.getClaimAsString("storeId");
String jwtRole    = jwt.getClaimAsString("profileRole");
boolean isAdmin   = "ADMIN".equals(jwtRole);
if (!isAdmin && !id.equals(jwtStoreId)) {
    return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
}
```

The `@PreAuthorize` annotation on the controller method must be `@PreAuthorize("hasRole('STORE_ADMIN') or hasRole('ADMIN')")`. The storeId ownership check in the method body is the second gate for STORE_ADMIN. Neither gate alone is sufficient.

**Finding 11 — Dedicated DTO required — mass assignment of AI fields: REQUIRED (HIGH)**

This is the most critical finding for Phase 1 containment of AI fields. The endpoint must accept a dedicated DTO:

```kotlin
data class SubscriptionTierRequest(val subscriptionTier: SubscriptionTier)
```

It must NOT accept the full `StoreProfile` as the request body. The `StoreControler.update()` method (line 42) accepts a full `StoreProfile` for PATCH `/{id}` — if the subscription-tier endpoint follows the same pattern, a STORE_ADMIN could include `aiAssistantEnabled: true` in the request body and have it silently persisted when AI fields are eventually added to `StoreProfile` (Phase 3). With a dedicated DTO containing only `subscriptionTier`, no other field can be set via this endpoint regardless of what the caller sends in the body.

This constraint must be stated explicitly in the T-SA-07 task brief as a non-negotiable requirement. The Code Reviewer must verify the controller method signature at T-19 (AI Code Review).

**Finding 12 — ADMIN role scope: NOTED — register for TIER-BILLING-01 (MEDIUM)**

The SA raised a valid concern: if an internal service account (e.g., the WhatsApp inbound service principal) carries the `ADMIN` role, it could change commercial subscription tiers via this endpoint. For Phase 1, this risk is LOW — there is no billing consequence to a tier change and no automated provisioning triggered. The commercial impact of an erroneous tier change is limited to a flag in the database.

The risk becomes HIGH in TIER-BILLING-01 (when subscription billing is introduced), where an erroneous tier upgrade could trigger a billing event. A dedicated `BILLING_ADMIN` role should be introduced at that point, replacing the ADMIN bypass on this endpoint.

**Required for T-SA-07:** Add a comment to the endpoint method and a note in the T-SA-07 PR description: "ADMIN bypass must be reviewed when TIER-BILLING-01 is implemented. At that point, introduce a BILLING_ADMIN role and restrict commercial tier changes to BILLING_ADMIN only, removing the ADMIN bypass." The Backend Developer implementing T-SA-07 must also document which service accounts currently carry ADMIN role — this list must be provided to Lindani as an informational item before T-SA-07 ships to production.

**Finding 13 — Downgrade side effects: WhatsApp provisioning state (MEDIUM)**

The brief (REQ-08) states that a provisioned WhatsApp number "will not be transferred on downgrade" but the backend has no mechanism to handle a downgrade while a `whatsapp_provisioning_requests/{storeId}` Firestore document is in `PENDING` status. A STORE_ADMIN could:
1. Upgrade to Tier 1, set `whatsappLineRequested = true` (creates PENDING provisioning doc).
2. Downgrade back to FREE before the line is provisioned.
3. Leave the PENDING provisioning doc orphaned — iZinga admin provisions a line for a FREE merchant.

**Required for T-SA-07:** When PATCH `/store/{id}/subscription-tier` processes a downgrade (new tier is a lower-tier value than the current tier), the service must check whether a Firestore document exists at `whatsapp_provisioning_requests/{storeId}` with `status: 'PENDING'`. If it does, the service must update its status to `'CANCELLED_DOWNGRADE'` in the same operation as the tier change. This requires Firestore write access from the `izinga-ordermanager` module — the Backend Developer must confirm this Firestore write path is already established or add it as a dependency on the WA-LINES-02 REQ-10 implementation.

**Finding 14 — Audit collection name must be decided before T-SA-07 (MEDIUM — open item)**

ADR-022 Decision 4 explicitly leaves open whether tier changes are written to `StoreAgreementAudit` (the ICA audit collection) or a separate `StoreTierChangeAudit` collection. These are conceptually distinct: the ICA audit records legal agreement acceptance; the tier change audit records a commercial configuration change. Mixing them in the same collection creates an audit log where both legal and operational events must be distinguished by a type field, which is error-prone.

**Required before T-SA-07 starts:** The Solution Architect must close this open item. Recommendation: use a separate `store_tier_change_audit` collection with structure `{ storeId, fromTier, toTier, changedByUserId, changedAt, requestIp }`. The `store_ica_audit` collection remains dedicated to ICA acceptance events. This is a design decision, not a security finding — the SA must confirm it before T-SA-07 is written as a task.

**Finding 15 — SecurityConfig PATCH coverage: REQUIRED (HIGH)**

`SecurityConfig.java` line 58 currently maps `PATCH /store/*` and `PATCH /store/*/stock` as `authenticated()`. The new endpoint `PATCH /store/{id}/subscription-tier` matches the pattern `/store/*` only if the sub-path is interpreted as a single segment. Verify that `PATCH /store/{id}/subscription-tier` (three-segment path) is covered by the `authenticated()` matcher — if the matcher only covers `PATCH /store/{id}` (single wildcard after `/store/`), the new endpoint could fall through to line 59 (`.anyRequest().permitAll()`) and be unauthenticated.

**Required for T-SA-07:** The Backend Developer must add an explicit matcher for the new endpoint in `SecurityConfig.java`:

```java
.requestMatchers(PATCH, "/store/*/subscription-tier").authenticated()
```

This must be placed before the `.anyRequest().permitAll()` catch-all. The Code Reviewer must verify this in the T-19 review.

**Finding 16 — Rate limiting: NOTED (LOW)**

No rate limiting exists on store PATCH endpoints. For Phase 1 (no billing), automated tier change abuse has no financial consequence. Rate limiting is not required for Phase 1. When TIER-BILLING-01 introduces billing, rate limiting on `PATCH /store/{id}/subscription-tier` must be added (recommended: 10 requests per store per hour). Carry this forward to TIER-BILLING-01.

---

### SEC-ONB02-04: Fee and Payout Disclosures (CPA)

**Assessment input:** REQ-01, REQ-04, REQ-06, PO brief open question 4, RISK-05.

**Finding 17 — 6.5% fee disclosure timing: PASS**

The 6.5% service fee is required on all three tier cards at the tier selection step (REQ-01 / AC-02), which appears BEFORE ICA acceptance (REQ-02). This satisfies CPA s41 timing requirements for material commercial term disclosure — the merchant is informed before any commitment. PASS.

**Finding 18 — Payout cadence disclosure timing: MEDIUM**

The payout cadence disclosure ("initiates every Business Day; up to 3 Business Days bank settlement") is required at the bank details step (REQ-04 / AC-13) and at the tier selection step only in Phase 2 (REQ-06). In Phase 1, the sequence is: tier-select (6.5% fee shown) → ICA acceptance → bank details (payout cadence shown). A merchant accepts the ICA without having seen the payout cadence.

Under CPA s41, material terms must not be omitted or withheld in a way that creates a false impression. A payout timeline is a material commercial term — a merchant who depends on prompt payout timing could argue they accepted without adequate disclosure.

**Required before Gate (b) — L-07 must include payout clause:** The ICA text (L-07) must contain a clearly labelled payout terms clause stating that iZinga initiates payouts every Business Day and that bank settlement takes up to 3 Business Days, and that this timeline is determined by the receiving bank. ICA acceptance therefore constitutes informed acceptance of the payout terms even in Phase 1. Jason van der Merwe must confirm this clause is present when he reviews L-07. This is not a code gate but it IS a Gate (b) pre-condition.

**Finding 19 — VAT disclosure: REQUIRED BEFORE PRODUCTION (MEDIUM)**

PO brief open question 4 asks whether tier prices should show "(VAT-exclusive)". Under CPA s23(8), prices advertised in SA must include VAT unless the seller is not VAT-registered. Curiousoft/iZinga's VAT registration status is unconfirmed pending Unita Makhubela's input (per the brief).

**Required before Phase 1 ships to production:** Unita Makhubela must confirm VAT status. If VAT-registered, tier prices on the tier selection screen must display "(VAT-exclusive)" and the equivalent VAT-inclusive price, or alternatively the VAT-inclusive price only. The Release Manager must verify this is resolved before any public launch of the tier selection screen (regardless of whether `MERCHANT_ICA_ENABLED` is true). This is a consumer law compliance item, not a security item, but it is a production deployment gate.

**Finding 20 — CPA s14 cooling-off right: FLAG FOR JASON (MEDIUM)**

The PO brief RISK-02 flags CPA s14 (fixed-term agreement cooling-off) as an open L-07 item. REQ-02's click-wrap records acceptance but includes no cooling-off right disclosure or mechanism. CPA s14 provides a 5-business-day cooling-off right for fixed-term agreements concluded electronically in certain circumstances. Jason van der Merwe must advise whether the merchant agreement is a fixed-term agreement under CPA and, if so, whether the cooling-off right applies and how it should be disclosed and exercised. This is a legal gate for Jason, not a code gate for Engineering. The click-wrap feature flag (`MERCHANT_ICA_ENABLED`) must not be set to `true` in production until Jason has addressed this item.

---

### SEC-ONB02-05: Deferred AI Fields — Phase 1 Containment

**Assessment input:** ADR-022 Decision 6, PO brief Phase 3 (REQ-11, REQ-12), brief data model table, `StoreControler.java` lines 30–38.

**Finding 21 — Phase 1 containment via ADR-022 deferral: CONFIRMED (PASS)**

ADR-022 Decision 6 explicitly defers `aiAssistantEnabled`, `aiAdviceModeRequested`, and `aiLicenceWarrantAccepted` from Phase 1 and Phase 2. These fields will not exist on `StoreProfile` in Phase 1 code. Combined with Finding 11's DTO constraint on the subscription-tier PATCH, Phase 1 has no mechanism by which a STORE_ADMIN could enable AI features. PASS.

**Finding 22 — POST /store mass assignment risk for Phase 3: CONDITION FOR PHASE 3 (MEDIUM)**

`StoreControler.create()` (line 32) accepts the full `StoreProfile` as the request body. When AI fields are added to `StoreProfile` in Phase 3, a STORE_ADMIN could include `aiAssistantEnabled: true` in a `POST /store` payload and, if the service layer does not strip it, the AI assistant would be silently enabled at store creation without going through the intended opt-in flow (REQ-11).

**Required condition for Phase 3 task specification:** The Backend Developer implementing Phase 3 AI fields must either: (a) add explicit field-stripping in `StoreService.create()` for AI fields before persistence (strip `aiAssistantEnabled`, `aiAdviceModeRequested`, `aiLicenceWarrantAccepted` from the incoming StoreProfile, defaulting them to `false` / `null` regardless of what the caller sends); or (b) move to a dedicated `CreateStoreRequest` DTO that excludes AI fields. Option (a) is the lighter change for Phase 3. This is not a Phase 1 or Phase 2 blocker, but it must be stated as a Phase 3 pre-condition now so it is not forgotten.

**Finding 23 — No admin approval workflow or immediate disable path: CONDITION FOR PHASE 3 (MEDIUM)**

REQ-12 states that Advice-Enabled Mode is "not automatically active" and "will be reviewed by iZinga." No backend mechanism exists for: (a) admin approval of an `aiAdviceModeRequested` application, or (b) immediate disabling of `aiAssistantEnabled` for a specific store. Without an immediate disable path, iZinga cannot remediate a store AI assistant that produces harmful or illegal responses in real time.

**Required before Phase 3 is started:** The Phase 3 PO brief must include a backend endpoint `PATCH /store/{id}/ai-config` accessible only to `ADMIN` role, able to set `aiAssistantEnabled`, `aiAdviceModeApproved`, and `aiAdviceModeEnabled` independently to `true` or `false`. This endpoint must write to a dedicated `store_ai_config_audit` collection (insert-only, per the same pattern as `store_ica_audit`) so every admin enable/disable action is recorded. The Phase 3 SA assessment must include this endpoint. Phase 3 implementation is blocked until this endpoint is in the design.

**Finding 24 — Clause 12 of L-07 gate: CONFIRMED (PASS)**

The brief explicitly states REQ-12 is blocked on Jason van der Merwe's attorney sign-off on L-07 clause 12 (AI Advice-Enabled Mode), which is flagged as the highest-risk clause. Phase 3 is additionally deferred from Phase 1 and Phase 2. The Phase 3 legal gate is correctly scoped. PASS.

---

### Required changes summary — binding amendments to ONB-02

| ID | Finding | Required change | Blocks |
|---|---|---|---|
| SEC-ONB02-01-A | C-04 scope — CIPC vs self-declaration | Lindani + Jason must confirm whether C-04 requires CIPC verification or self-declared `regNumber` + OTP is sufficient; confirm L-07 clause 3 transfers liability for false registration details onto the merchant | Gate (b) checklist |
| SEC-ONB02-01-B | Audit record: missing `userAgent` and `verificationChannel` | Add `userAgent` (from HTTP `User-Agent` header) and `verificationChannel` (hardcoded `'WHATSAPP_OTP'` for Phase 1) to the `store_ica_audit` document schema | T-04 |
| SEC-ONB02-02-A | Insert-only: no enforcement mechanism specified | `StoreAgreementAuditRepository` must expose only `save()` + `findBy*()` (no delete/update methods in interface); DevOps must apply MongoDB collection-level `insert`+`find`-only permissions on the audit collection in production | T-04; DevOps action before Gate (b) |
| SEC-ONB02-02-B | Retention period not in brief or ICA | L-07 must include a data retention clause: audit records retained for minimum 5 years from agreement termination, then deleted or anonymised | Gate (b) checklist — Jason must confirm |
| SEC-ONB02-02-C | Read access not specified | `StoreAgreementAuditRepository` must have no method accessible to STORE_ADMIN callers; any read endpoint must be `@PreAuthorize("hasRole('ADMIN')")` | T-04 |
| SEC-ONB02-03-A | storeId from JWT not path/body | Controller for PATCH `/{id}/subscription-tier` must read `storeId` and `profileRole` from JWT claims and perform the storeId equality check before any service call | T-SA-07 start gate |
| SEC-ONB02-03-B | Dedicated DTO required | Controller must accept `SubscriptionTierRequest { subscriptionTier }` only — no full StoreProfile body — to block mass assignment of AI fields | T-SA-07 start gate |
| SEC-ONB02-03-C | SecurityConfig PATCH coverage | Add `.requestMatchers(PATCH, "/store/*/subscription-tier").authenticated()` to `SecurityConfig.java` before the `.anyRequest().permitAll()` catch-all | T-SA-07 |
| SEC-ONB02-03-D | Downgrade WhatsApp provisioning state | On downgrade, service must update `whatsapp_provisioning_requests/{storeId}` from `PENDING` to `CANCELLED_DOWNGRADE` atomically with the tier change | T-SA-07 |
| SEC-ONB02-03-E | Audit collection name open | SA must close the open item: use `store_tier_change_audit` (separate from `store_ica_audit`) before T-SA-07 is written as a task | T-SA-07 SA pre-condition |
| SEC-ONB02-03-F | ADMIN scope — register for TIER-BILLING-01 | Add comment in endpoint + PR noting ADMIN bypass must be replaced by `BILLING_ADMIN` role in TIER-BILLING-01; Backend Developer to document which service accounts hold ADMIN role | T-SA-07 |
| SEC-ONB02-04-A | Payout cadence in L-07 | L-07 must include a payout terms clause covering Business Day initiation and up to 3-Business-Day bank settlement; Jason must confirm before Gate (b) | Gate (b) checklist |
| SEC-ONB02-04-B | VAT disclosure | Unita Makhubela must confirm VAT status before Phase 1 launches publicly; if VAT-registered, prices must show inclusive amounts or explicit "(VAT-exclusive)" notation | Production launch gate (independent of `MERCHANT_ICA_ENABLED`) |
| SEC-ONB02-04-C | CPA s14 cooling-off right | Jason must advise whether the merchant agreement is a fixed-term agreement and whether a cooling-off right applies and how it is disclosed | Gate (b) checklist |
| SEC-ONB02-05-A | POST /store AI field mass assignment | Phase 3 PO brief must require `StoreService.create()` to strip AI fields from the incoming StoreProfile body before persistence | Phase 3 start gate |
| SEC-ONB02-05-B | No admin approve/disable path for AI | Phase 3 PO brief must include `PATCH /store/{id}/ai-config` (ADMIN only) with immediate enable/disable capability and insert-only `store_ai_config_audit` log | Phase 3 start gate |

---

### OWASP Top 10 assessment

| Risk | Status | Finding |
|---|---|---|
| A01 Broken Access Control | REQUIRES ACTION | IDOR on subscription-tier PATCH: storeId must come from JWT (SEC-ONB02-03-A); SecurityConfig coverage gap (SEC-ONB02-03-C) |
| A02 Cryptographic Failures | PASS | No new cryptographic operations introduced in Phase 1; subscription-tier PATCH adds no crypto |
| A03 Injection | PASS | No user-controlled input injected into MongoDB queries; new endpoints use Spring Data derived queries or parameterized patterns |
| A04 Insecure Design | REQUIRES ACTION | Mass assignment risk on subscription-tier PATCH (SEC-ONB02-03-B); POST /store AI field stripping needed for Phase 3 (SEC-ONB02-05-A) |
| A05 Security Misconfiguration | REQUIRES ACTION | `anyRequest().permitAll()` catch-all in SecurityConfig could cover new PATCH endpoint if not explicitly mapped (SEC-ONB02-03-C) |
| A06 Vulnerable Components | NOT ASSESSED | No new dependencies introduced; standard dependency audit applies for the release |
| A07 Auth Failures | REQUIRES ACTION | JWT claims must be used for storeId resolution on subscription-tier PATCH (SEC-ONB02-03-A); Firebase OTP already enforced at `/business/verify` |
| A08 Software Integrity | PASS | Feature flag `MERCHANT_ICA_ENABLED` correctly gates ICA click-wrap; no unsigned deployment concerns specific to this feature |
| A09 Logging Failures | PASS WITH NOTE | Brief requires WARN logging on C-04 gate rejection (HTTP 403) — Backend Developer must not log the full StoreProfile body in this path; log only `storeId` and the reason code |
| A10 SSRF | PASS | No external URL inputs introduced in Phase 1; `whatsapp_provisioning_requests` writes to Firestore (internal) with no user-controlled URL |

---

### POPIA data protection

The ONB-02 feature processes the following personal data categories: phone number (existing, primary identifier), business registration number (`regNumber` — may identify a sole trader), bank account details (collected in REQ-04 — `bankName`, `accountNumber`, `accountHolder`, `accountType`), IP address (in `store_ica_audit`), user-agent string (required by SEC-ONB02-01-B). All processing has a contractual necessity lawful basis under Section 11(1)(c) for service agreement formation and performance.

Bank account details added by REQ-04 are particularly sensitive. These must be treated with the same protective posture as the `UserProfile.bank` field — readable only by the authenticated owner and ADMIN, never returned in unauthenticated or third-party API responses. The `GET /v2/store/**` endpoints are `permitAll()` (SecurityConfig line 48) and serve public store data. The Backend Developer must confirm that bank account fields on `StoreProfile` are excluded from all public-facing GET responses (`GET /store/{id}`, `GET /v2/store/**`). If the current `StoreProfile` serialisation does not already exclude `bank`, a `@JsonIgnore` or a dedicated response DTO must be applied before REQ-04 ships to production.

The insert-only `store_ica_audit` collection contains `acceptedByUserId` and `requestIp` — both constitute personal information. Subject access requests under POPIA Section 23 must be serviceable from this collection. iZinga's POPIA manual (privacy@izinga.co.za as the Information Officer per the September 2026 privacy policy) must include `store_ica_audit` as a registered data collection with its purpose and retention period.

---

### Approved for deploy: Yes — pending all required changes incorporated before their respective task start gates

Tasks T-03, T-04, T-05, T-06 may begin immediately. T-SA-07 requires SEC-ONB02-03-A, -B, -C, -D, -E to be resolved and incorporated into the task specification before work starts. Gate (b) (`MERCHANT_ICA_ENABLED = true` in production) requires SEC-ONB02-01-A, SEC-ONB02-02-B, SEC-ONB02-04-A, SEC-ONB02-04-B, SEC-ONB02-04-C to be confirmed by Jason van der Merwe and Unita Makhubela. Phase 3 requires SEC-ONB02-05-A and SEC-ONB02-05-B to be in the Phase 3 PO brief before that brief is issued.

*Security & Compliance gate completed · 2 October 2026*
