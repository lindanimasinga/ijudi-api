# Blueprint — Onboarding a New PRO Store (custom frontend + dedicated WhatsApp line)

Generalised from the RxNova24/RxConnect build (2026-09 through 2026-10). RxNova24
is the first full run of this path; Ingalabesi, furniture-delivery-app, and
shop.izinga.co.za (cs-lifestyle) established the "custom frontend on shared iZinga
backend" pattern earlier but predate WA-LINES-02, so they don't have a dedicated
WhatsApp line yet. Steps marked **[vertical-specific]** only apply to regulated or
otherwise special categories (health, financial, age-restricted) — skip them for a
standard retail store.

Each phase names the owning agent per [[feedback_agent_chain]] — route through
them, don't jump straight to a developer agent.

---

## Phase 0 — Qualification & Scoping

**Owner:** Curiousoft BRM → iZinga Product Owner

1. Intake the client's requirement (BRM) — confirm what they actually need first,
   not the aspirational full scope. RxNova24's BRM scope doc described a full
   telehealth platform; the real MVP1 was "a store first." Don't quote the full
   estimate as the MVP cost.
2. **[vertical-specific]** Identify regulatory bodies that apply (SAPC/SAHPRA/
   POPIA for health, FSCA for financial, etc.) and get an explicit scope decision
   on whether AI-driven advice/recommendation is in or out for v1 — this one
   decision changes the compliance blast radius more than anything else.
3. iZinga Solution Architect sign-off if the store needs anything beyond the
   existing backend contract (new `StoreType`, new fields, new MCP tools). Do
   this *before* the PO finalises a task breakdown.

---

## Phase 1 — Store record (backend data, not code)

**Owner:** iZinga Store & Merchant Support / vendor portal (biz.izinga.co.za)

1. Create the store profile → get a `storeId`. This can happen early and in
   parallel with frontend work.
2. Confirm `StoreType` enum has an appropriate value for the vertical. **Gap
   found on RxNova24:** no `PHARMACY` type existed in `StoreType.kt` — this
   needs resolving (SA + Backend Developer) before go-live, not deferred.
3. Set, before go-live (orders silently fail until this is done):
   - `profileApproved: true`
   - `isStoreOffline: false`
   - `isDeliverNowAllowed` / `scheduledDeliveryAllowed` as appropriate
   - Delivery rates (not `0.0`)
4. **Set `storeWebsiteUrl`** if this store gets a dedicated custom frontend (not
   a listing under `shop.izinga.co.za`). This field already exists on
   `StoreProfile.kt` with a computed `orderUrl` getter (`$storeWebsiteUrl/order/`)
   — it's easy to forget to set it, and everything downstream (WhatsApp template
   buttons, agent redirect text) depends on it being correct.
5. **[vertical-specific]** Confirm any server-side product filtering needed
   (e.g. RxNova24's Schedule 0–1 OTC-only filter, S2 hard-excluded in
   `StockMapperService.toOtcProducts()`). This is a compliance control, not a
   nice-to-have — verify it's actually wired to the real catalogue before launch.

---

## Phase 2 — Custom frontend (only if this store needs its own branded app)

**Owner:** iZinga Solution Architect (pattern sign-off) → relevant Web Developer agent

Skip this phase entirely if the store is fine listed generically under
`shop.izinga.co.za` — it's not required for every store, only ones getting a
dedicated branded experience.

1. Fork the nearest sibling app rather than greenfield — cs-lifestyle,
   furniture-delivery-app, and ingalabesi-web are the proven precedents.
2. Wire to the shared iZinga backend via the `storeId` from Phase 1.
3. Payments: Yoco only (Ozow/PayFast are out). Follow the izinga-pay contract —
   `callback` param is the **bare origin**, not `origin/payment`; the storefront's
   `/order/:id` is the post-payment landing; never clear the cart while the order
   is still `STAGE_0`.
4. Brand/design pass, including a real mobile + desktop responsiveness audit —
   budget for at least two audit rounds; real bugs (overflow, phantom reCAPTCHA
   badge width, broken sticky CTA margins) showed up on the *second* pass, not
   the first, on RxNova24.
5. Build gate: both `ng build` (dev) **and** `ng build --configuration
   production` must pass. Prod-only passed twice on a hard compile error that
   dev-mode webpack caught immediately — see [[feedback_angular_review_dev_build]].
6. Gitflow: `feature/*` → `develop` → `release/x.y.z` → `main`, tagged. No
   direct commits to `develop`/`main`.
7. Deploy via Firebase Hosting REST API if the CLI isn't authenticated on the
   build machine (`gcloud auth application-default print-access-token`).

---

## Phase 3 — Domain & hosting

**Owner:** iZinga DevOps & Infrastructure

1. **Decide the production domain up front**, not after the prototype is built.
   RxNova24 built straight onto a Firebase project literally named
   `rxconnect-prototype` with `demo.rxnova24.co.za` — fine for a pharmacist/legal
   review artifact, but it means a second, deliberate domain + possibly a second
   Firebase project decision is still owed before real go-live. Decide this
   during Phase 0, even if prototype work starts on a demo subdomain.
2. Connect the custom domain to Firebase Hosting; confirm `DOMAIN_ACTIVE`.
3. Add the domain to Firebase Auth's authorized domains list.
4. **GCP API key — two separate restriction tabs, both must be updated:**
   - *Application restrictions* (HTTP referrers): add the new domain, additively
     (`https://*.rxnova24.co.za/*`) — don't replace the existing list.
   - *API restrictions*: confirm **Identity Toolkit API** is included. This is
     the one that's easy to miss — referrer restriction alone looks like enough
     but login still fails 403 until Identity Toolkit is added too. This was the
     actual root cause of RxNova24's live login failure, found only after fixing
     the referrer restriction didn't resolve it.

---

## Phase 4 — WhatsApp AI agent (WA-LINES-02)

**Owner:** iZinga Backend Developer (provisioning call) + Lindani (content approval)

The platform mechanism is already built (merged to `develop` — `StoreAiAgent`,
`StoreLineProvisioningService`, JWT store-scope + audience enforcement). Per-store
work is:

1. Get a WhatsApp Business phone number assigned for this store's dedicated line.
2. Call `StoreLineProvisioningService.provisionStoreLine(phoneNumberId, displayNumber, storeId, operatorUid)`
   — this clones `store_support_default` → `store_support_<storeId>` automatically.
3. Write store-specific prompt overrides as a `.claude/agents/` reference file
   first (e.g. [izinga-store-support-rxnova24.md](../../../izinga-onboarding/.claude/agents/izinga-store-support-rxnova24.md))
   — gives Lindani something concrete to review/approve before it becomes the
   real `systemPrompt`, per ADR-021 Decision 4 ("Lindani must approve the
   template text before production seed").
4. **[vertical-specific]** Add any guardrail section the generic template
   doesn't cover — RxNova24 needed an explicit "never give medical/dosage/
   symptom advice, always redirect to the pharmacist team" section that doesn't
   exist in `store_support_default` because it was written for food/furniture
   stores.
5. Confirm `allowedTools` stays at `["find_store_or_shops_by_id"]` unless a new
   tool has been through SA + Security sign-off — this is a security-approved
   minimum, not a default to casually expand.
6. Fix check (platform-level, not per-store, but verify it's landed before
   relying on it): `store_support_default`'s seeded prompt currently hardcodes
   `https://shop.izinga.co.za` as literal text rather than resolving
   `StoreProfile.orderUrl` — wrong for any store with its own `storeWebsiteUrl`.
   Needs a `{storeOrderUrl}` placeholder fix once, benefits every future
   custom-domain store.

---

## Phase 5 — WhatsApp Business template (Meta)

**Owner:** iZinga DevOps & Infrastructure (Meta submission) + Backend Developer (send-call wiring)

1. A new number's first message must be a pre-approved template — the 24-hour
   free-form window doesn't apply to a brand-new conversation from a new number.
2. **Decide shared vs. store-specific template** based on whether this store has
   its own custom domain:
   - Store lives under a shared iZinga domain → can reuse a generic
     parameterized template (`{{1}}`=name, `{{2}}`=store name) — Meta template
     review only needs doing once, ever.
   - Store has its own domain (like RxNova24) → needs its own template, because
     a WhatsApp URL button's domain must be static text at submission time —
     Meta doesn't allow the whole domain to be a variable, only a trailing path
     segment on a fixed base.
3. Button design (if using buttons): max 3 total, CTA buttons (URL/phone) listed
   before quick-reply buttons, max 2 CTA + up to 3 quick-reply combined ≤ 3
   total. RxNova24's pattern — 1 URL ("Order Now" → store homepage) + 2 quick
   replies ("Track Order", "Help") — is a reasonable default: quick-reply
   buttons just re-enter the same WhatsApp conversation and get handled by the
   store agent's existing redirect/escalation rules, no new capability needed.
4. Submit via Graph API (`POST /{waba-id}/message_templates`) or WhatsApp
   Manager UI. Needs the live Meta access token and WABA ID — these are
   deliberately not in the repo (`whatsapp.cloud.api.key` is externally
   injected), so this step needs whoever holds those credentials.
5. Wire the send call (new store-aware method next to
   `WhatsappNotificationService.sendLandingOptions()`), sourcing the store name
   from `StoreProfile.name` and the line from the store's own `phoneNumberId` —
   never hardcode per store in Java.

---

## Phase 6 — Release & QA gates

**Owner:** iZinga QA & Test Automation → iZinga Code Reviewer → iZinga Release Manager

1. Full **unscoped** test suite — never report green off a scoped/excluded run
   (see [[feedback_no_scoped_build_exclusions]]).
2. On this Mac specifically: `ijudi-api` needs `JAVA_HOME` pointed at Corretto
   17, not the Homebrew-default JDK 23, or ordermanager tests fail spuriously
   (see [[feedback_ijudi_api_jdk17_pin]]).
3. iZinga Code Reviewer PASS required before merge to `develop`.
4. iZinga Security & Compliance review for anything touching auth, payments, or
   personal/health data — mandatory given how much of this path touches exactly
   those three.
5. Release Manager merges `release/*` → `main`, tags, back-merges to `develop`.

---

## Phase 7 — Go-live checklist

**Owner:** Lindani + relevant Product Owner

- [ ] **[vertical-specific]** Pharmacist/legal/compliance sign-off on the
      prototype/demo build
- [ ] Production domain decided and connected (Phase 3, if not already the
      prototype domain)
- [ ] Store flipped live: `profileApproved: true`, `isStoreOffline: false`
- [ ] WhatsApp line provisioned and template live
- [ ] WhatsApp Business profile photo set (brand wordmark, properly centered —
      measure from the live site's actual rendered bounding box, not the SVG's
      nominal viewBox, if generating one locally)
- [ ] End-to-end smoke test as a real customer: login → browse → order → pay →
      track → WhatsApp bot reply — all against production, not demo

---

## Open items carried over from RxNova24 specifically (not yet resolved as of 2026-10-02)

These aren't blueprint steps — they're this store's actual unfinished Phase 1–5
items, kept here so the blueprint and the live status don't drift apart:

- Store profile fields (Phase 1.3, 1.4) — never confirmed set
- Identity Toolkit API on the GCP key (Phase 3.4) — never confirmed done
- WhatsApp AI agent provisioning call (Phase 4.2) — not yet made
- `rxnova24_landing_options` Meta template (Phase 5.4) — drafted, not submitted
- Production domain decision (Phase 3.1) — not yet made; only the demo subdomain exists
- Pharmacist/legal sign-off on the prototype (Phase 7) — status unknown, worth
  confirming with Ndumiso before investing further in production setup
