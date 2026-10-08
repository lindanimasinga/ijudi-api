# Test Plan: Admin — Pending Approvals

**userType:** admin
**Feature:** Driver/ambassador/store-owner profile review → approve or reject pending registrations
**App:** `izinga-onboarding` (Angular), route `/business/pending-approvals` (biz.izinga.co.za in prod); `ijudi-api` (`izinga-usermanagement` module) for the backend data source
**Last verified:** 2026-10-08 (backend — Gate 1 automated re-verification, PASS, branch `bugfix/pending-approvals-exclude-customer`, both commits abec2f6 + 0173743, 12/12 tests, 249/249 reactor); 2026-10-08 (frontend — live E2E run, PASS, release 1.18.0)

## Scope

Covers the admin `PendingApprovalsComponent`: loading the pending profile list, filtering by service type, switching between Pending List / Map View / Pending Criminal Checks tabs, selecting a user to open the Profile Review panel, and viewing the Approve Profile action. Does **not** cover actually approving profiles against real or prod data — the Approve action is a real production write and must never be clicked in prod or UAT without explicit CEO sign-off on that profile. Does **not** cover chat sessions (see [`chat-sessions.md`](chat-sessions.md)).

## Repos affected

- `izinga-commons` — `UserProfileRepo.kt` (new derived query method `findByProfileApprovedAndRoleNot`)
- `izinga-usermanagement` — `UserProfileService.pendingAproval()` (callee changed to the new method); `UserProfileServicePendingApprovalsTest` (12 unit tests: 6 from abec2f6 covering CUSTOMER exclusion + service-provider roles, 6 from 0173743 covering null-role exclusion + mixed lists)
- `izinga-onboarding` — `PendingApprovalsComponent`, `bootstrap5-compat.css`, `app-pending-approvals` styles, profile-review panel/modal markup

## Known-good test fixtures

**Prod login** (`biz.izinga.co.za`):
- Admin number: `0734396642` (WhatsApp OTP)
- OTP source: prod CloudWatch logs, log group `/ecs/first-run-task-definition`, command `aws logs filter-log-events --filter-pattern "<number>"`
- Note: WhatsApp delivery webhook may report `status: failed` for this number — login still works; OTP is always in the logs.
- Prod currently has approximately 300 pending profiles.

**UAT / local** (`ng serve` on port 4211, launch config `izinga-onboarding-qa`, `environment.ts izingaUrl=http://localhost`, backend run with `-Dspring-boot.run.profiles=uat`):
- OTP readable from the local backend log directly.
- UAT sandbox has approximately 14 pending profiles (today's test accounts).
- The UAT sandbox DB has **no user with `role: ADMIN`** — admin dashboard tiles will not show; route has **no route guard** so navigate directly to `/business/pending-approvals` after any OTP login.

**Service type filter values** (dropdown options):
- `All service types`
- `Bakkie Delivery Driver`
- `Bike Delivery Driver`
- `Truck Delivery Driver`
- `Small-Medium Vehicle Driver`
- `Driver / Fleet Manager`
- `Ambassador`
- `Referral Partner`
- `Store Owner`

**Approve Profile action:**
- The fixed-bottom Approve bar is the app-wide approval pattern.
- **Never click "Approve Profile" against prod data** in automated or exploratory testing. This is a real production write that changes the driver's `profileApproved` field and makes them eligible for job matching.

## Backend test cases (automated — `UserProfileServicePendingApprovalsTest`)

These 12 unit tests cover the two-layer exclusion in `pendingAproval()`: DB-layer CUSTOMER exclusion (commit abec2f6) and in-memory null-role exclusion (commit 0173743). All 12 passed in the full reactor run on 2026-10-08 (249 tests, 0 failures).

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| BTC-01 | MESSENGER profiles returned | Unit | repo returns one MESSENGER profile | list contains that profile | happy path — non-CUSTOMER role |
| BTC-02 | STORE_ADMIN profiles returned | Unit | repo returns one STORE_ADMIN profile | list contains that profile | service-provider role variant |
| BTC-03 | AMBASSADOR profiles returned | Unit | repo returns one AMBASSADOR profile | list contains that profile | ambassador role variant |
| BTC-04 | STORE profiles returned | Unit | repo returns one STORE profile | list contains that profile | STORE role variant |
| BTC-05 | MESSENGER_ADMIN profiles returned | Unit | repo returns one MESSENGER_ADMIN profile | list contains that profile | MESSENGER_ADMIN role variant |
| BTC-06 | REFERRAL_PARTNER profiles returned | Unit | repo returns one REFERRAL_PARTNER profile | list contains that profile | REFERRAL_PARTNER role variant |
| BTC-07 | Null-role profile excluded | Unit | repo returns one null-role profile | result is empty | null-role exclusion (in-memory filter) |
| BTC-08 | Null-role excluded from mixed list | Unit | repo returns null-role + MESSENGER profiles | result has 1 entry with role MESSENGER | mixed-list null-role exclusion |
| BTC-09 | Result contains no null-role entries (purity assertion) | Unit | repo returns null-role + STORE_ADMIN profiles | `result.none { it.role == null }` | null-role exclusion — data-purity guard |
| BTC-10 | Old method `findByProfileApproved` is never called | Unit | repo stub on new method only | `verifyNoMoreInteractions(profileRepo)` passes | regression guard — confirms old method is dead |
| BTC-11 | Result contains no CUSTOMER profiles | Unit | repo returns only MESSENGER profile | `result.none { it.role == CUSTOMER }` | data-purity assertion on CUSTOMER exclusion |
| BTC-12 | Empty list when no unapproved service-provider profiles | Unit | repo returns empty list | result is empty | empty-collection branch |

## Frontend E2E test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| TC-01 | Header and subtitle render | E2E | Navigate to `/business/pending-approvals` after OTP login | Page title and subtitle are visible with correct text; no layout overflow | — |
| TC-02 | Count badge on Pending List tab | E2E | Navigate to page | Pending List tab shows a count badge matching the number of pending profiles loaded | — |
| TC-03 | Tabs scroll horizontally on mobile | E2E | Viewport 375px | Three tabs (Pending List / Map View / Pending Criminal Checks) are all reachable by horizontal scroll; no tab text overlaps another; tab-strip bottom border uses the hairline design-system token, not `#dee2e6` | Verify border colour with `getComputedStyle`, not class inspection |
| TC-04 | Service type filter narrows list | E2E | Select each filter value from the dropdown | List re-renders with only profiles matching the selected type; count badge updates | Test at least `All service types`, `Bakkie Delivery Driver`, and `Ambassador` |
| TC-05 | Row shows initials avatar for no-photo users | E2E | Find a profile with no profile photo | Avatar circle renders with the correct initials and design-system background colour; no white/empty circle | White `bg-light` circle was the defect fixed in 1.18.0 |
| TC-06 | Row shows nationality badge | E2E | Inspect rows with various nationalities | ZAF nationality uses `iz-badge--green`; no nationality uses `iz-badge--muted` with text "No nationality"; other nationalities use `iz-badge--amber` | Verify token class is applied, then verify computed colour |
| TC-07 | "Ready For App" marker shown where applicable | E2E | Find a profile flagged as ready for the app | "Ready For App" marker is visible on the row | — |
| TC-08 | Selecting a row opens Profile Review — desktop | E2E | Viewport ≥1024px, click a profile row | Inline right panel opens; does **not** open a modal | Markup is duplicated in the template for desktop and mobile (see Known gaps) |
| TC-09 | Selecting a row opens Profile Review — mobile | E2E | Viewport 375px, tap a profile row | Full-screen modal opens | Markup duplicate must match desktop panel — keep both in sync |
| TC-10 | Profile Review: single teal inset accent | E2E | Open any profile review | One teal/primary-colour inset accent visible; no additional accent splashes | — |
| TC-11 | Profile Review: section labels use `iz-section-label` | E2E | Inspect profile review panel/modal | Section headings styled with `iz-section-label` class; no raw Bootstrap heading styles | Verify with `getComputedStyle` |
| TC-12 | Profile Review: Basic and Bank info tables with hairline borders | E2E | Open a profile with bank information | Basic Info and Bank Information tables render with hairline borders; no thick Bootstrap-default borders | Verify computed border width |
| TC-13 | Profile Review: "Bank Information" hidden when no bank data | E2E | Open a profile with no bank information | The "Bank Information" section does not render; no empty label or empty table displayed | Empty bank label was a defect fixed in 1.18.0 |
| TC-14 | Profile Review: coordinates line hidden when lat/long are 0/0 | E2E | Open a profile where lat and long are both `0` | Coordinates line is not shown in the profile review | "0, 0" coordinates displaying was a defect fixed in 1.18.0 |
| TC-15 | Pending Approval badge is amber | E2E | Open any pending profile | Approval status badge uses `iz-badge--amber` token | — |
| TC-16 | Chat `bg-dark` uses gold token | E2E | Open a profile that has chat activity | Chat section background uses the gold design-system token, not a raw Bootstrap `bg-dark` class value | Verify with `getComputedStyle` |
| TC-17 | Refresh icon renders | E2E | Inspect the refresh button on the list | Icon has `offsetWidth > 0`; renders as a visible Material ligature (not a `fa fa-*` glyph) | Font Awesome is not loaded in this app |
| TC-18 | No `alert-*` classes in use | E2E | Inspect all visible notices and banners in `app-pending-approvals` | No `alert-success`, `alert-warning`, `alert-danger`, or other Bootstrap `alert-*` classes; only `iz-notice` design-system components | — |
| TC-19 | No Bootstrap grey literals in `app-pending-approvals` | E2E | Run `getComputedStyle` sweep across all `app-pending-approvals` descendants | No computed colour equal to `rgb(73, 80, 87)`, `rgb(248, 249, 250)`, or `rgb(222, 226, 230)` | Verify with `getComputedStyle`, never class names |
| TC-20 | Light theme correct | E2E | Load without `dark-theme` class on `body` | All colours use design-system tokens; no Bootstrap grey literals | — |
| TC-21 | Dark theme correct | E2E | Add `dark-theme` class to `body` | Component re-renders using dark token values; no colours flip to light Bootstrap defaults | — |
| TC-22 | Viewport 375px layout | E2E | Set viewport to 375px | List renders without horizontal overflow; tabs scroll; row content does not clip | — |
| TC-23 | Viewport ≥1024px layout | E2E | Set viewport to 1024px or wider | Two-column layout (list + inline panel) without overflow; no stray gutters | — |

## Integration test scenarios

- **Full pending approval review flow (read-only):** login (OTP) → navigate to `/business/pending-approvals` → filter by `Ambassador` → select a profile → verify review panel shows all sections correctly → verify Approve bar renders but **do not click** → close the panel.
- **Route guard bypass (UAT):** confirm that navigating directly to `/business/pending-approvals` after a non-admin OTP login still loads the component (no redirect, no error) — documents the known missing guard until one is added.
- **Profile Review markup parity:** open the same profile at 375px (modal) and at ≥1024px (inline panel); both views must show identical field sets, section labels, and data — the template duplication (Issue 6) means changes to one copy can silently diverge from the other.

## Regression checks

**Backend:**
- [ ] `GET /pending-approvals` response contains no profiles with `role: CUSTOMER` — verify by inspecting JSON payload in network tab
- [ ] `GET /pending-approvals` response still contains MESSENGER, STORE_ADMIN, STORE, MESSENGER_ADMIN, AMBASSADOR, REFERRAL_PARTNER, and ADMIN profiles when unapproved ones exist
- [ ] `SchedulerService.newDrivers()` still processes unapproved MESSENGER profiles correctly — the call to `findByProfileApproved(false)` in that method is intentionally unchanged; an in-memory `.filter(role == MESSENGER)` applies immediately after
- [ ] `./mvnw test` runs green (249 tests, 0 failures, 3 pre-existing skips)

**Frontend:**
- [ ] Route guard absence still present — if a guard is added, update integration scenario above and remove this check
- [ ] Service type filter options match the list in the Known-good fixtures section above; update if new types are added
- [ ] Desktop panel markup and mobile modal markup remain in sync (Issue 6 — template duplication); any edit to one must be applied to the other
- [ ] `iz-badge--green/amber/muted` token classes map to correct computed colours in both themes
- [ ] `bootstrap5-compat.css` shim covers all utilities in use on this page — if Bootstrap 5 is adopted, remove shim and retest
- [ ] Material Icons ligature renders for refresh icon (and any other icon on this page)
- [ ] `iz-notice` used for all notices, no `alert-*` classes re-introduced

## Known defects

**Fixed in release 1.18.0, branch `bugfix/ONB-UI-chat-pending-audit-fixes` (2026-10-08):**
- `.list-group-item:hover` hardcoded `#f8f9fa` background (fixed)
- White avatar placeholder circle (`bg-light`) for profiles without a photo (fixed)
- Nav tab overlap on mobile (fixed)
- Selected row was a solid gold fill — now uses an accent border only (fixed)
- Empty "Bank Information" section label shown when no bank data (fixed)
- Coordinates displayed as "0, 0" when lat/long were both zero (fixed)
- Bootstrap `:hover` colours leaking — same root cause as chat sessions (fixed by `bootstrap5-compat.css`)
- Bootstrap 5 utilities silently no-ops (`me-*`, `gap-*`, `fw-bold`, `visually-hidden`, `btn-close`) — shimmed globally (fixed)

**Still open (tracked):**
- **[Issue 6] Profile Review template duplicated** — the markup exists in two separate blocks in the template (desktop inline panel + mobile modal); changes to one block must be manually mirrored to the other; no single-source-of-truth component yet
- 8 other templates across the app using `fa fa-*` icon classes (tracked; not specific to this component)
- Bootstrap 5 full migration decision pending
- Dashboard/welcome local classes should alias to the new global `.iz-*` utilities

## Known gaps / environment issues

- **Approve action must not be used in testing** — there is no soft-delete or undo on profile approval; any accidental approval in prod must be corrected manually in the database
- **UAT sandbox has no ADMIN user** — admin dashboard tiles do not display; navigate directly to the route
- **`getComputedStyle` is the only reliable verification method** for colours due to the BS4/BS5 mismatch — class-name inspection gives false confidence

## Validation commands

- `export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home && ./mvnw test` (full unscoped reactor, `ijudi-api` root — pin Corretto 17 or ordermanager tests fail spuriously on this Mac)
- `./mvnw -pl izinga-usermanagement -am test` (scoped iteration during development only — not a substitute for the full reactor above)
- `ng test --watch=false --code-coverage` (unit, `izinga-onboarding`)
- Manual E2E: `ng serve` on port 4211 (launch config `izinga-onboarding-qa`), backend with `-Dspring-boot.run.profiles=uat`, navigate to `/business/pending-approvals`
