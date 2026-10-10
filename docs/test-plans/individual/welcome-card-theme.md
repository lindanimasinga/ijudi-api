---
userType: individual / driver / ambassador
Feature: Welcome page card theme consistency — /indivisuals route (all 3 role contexts)
App: izinga-onboarding (Angular)
Last verified: 2026-10-09 | automated unit + dev build + visual browser | PASS (with open NOTE-01 cosmetic follow-up)
---

## Scope

Covers the `WelcomeIndivisualsComponent` (`/indivisuals` route) after the `bugfix/welcome-card-theme-consistency` change: 25 inline hex/var `background-color` styles replaced with shared `.iz-card` / `.iz-icon-chip` token classes across three ng-container branches:

- **Ambassador branch** (`isAmbassador === true`): 4-step coral/teal/green/gold cards
- **Driver branch** (`isDriver === true`): 9-step coral/teal/green/gold/amber/muted/coral/teal/green cards
- **Individual branch** (default): 8-card benefits grid (coral/teal/green/gold/amber/muted/coral/teal) + 4-step Get Started (coral/teal/green/gold)

Out of scope: `welcome-business.component.html` (zero diff, deliberately untouched).

Cross-linked: driver onboarding welcome steps are tested as part of this file; no separate driver/welcome-card.md is needed.

## Repos affected

| Repo | Work |
|---|---|
| izinga-onboarding | Template-only change in `src/app/welcome-indivisuals/welcome.component.html` + CSS comment in `welcome.component.css` |

## Known-good test fixtures

| Item | Value |
|---|---|
| QA server | `izinga-onboarding-qa` on port 4211 (`ng serve`) |
| localStorage userType key | `kjsdfkjsdf_user_type` |
| Switch to individual | `localStorage.setItem('kjsdfkjsdf_user_type', '"individual"')` then reload |
| Switch to driver | `localStorage.setItem('kjsdfkjsdf_user_type', '"driver"')` then reload |
| Switch to ambassador | `localStorage.setItem('kjsdfkjsdf_user_type', '"ambassador"')` then reload |
| Trigger body.individual class | After userType = individual, reload — app.component sets `document.body.classList.add('individual')` via `environment.userTypeConfig.bodyClassByUserType` |
| Verify token collision (NOTE-02) | After body.individual active: `window.getComputedStyle(document.body).getPropertyValue('--btn-bg-color')` should equal `getPropertyValue('--btn-red-color')` — both `#D66247` |
| Muted card padding check | `window.getComputedStyle(document.querySelector('.iz-card--muted')).padding` returns `10px 14px 10px 14px` (vs 20px 16px for all colored variants) |

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| TC-WEL-01 | Component creates without error | Unit | no params | component truthy | passes |
| TC-WEL-02 | ?ref param stored in sessionStorage | Unit | `{ref: 'abc123'}` | `mockStorage.ambassadorRef === 'abc123'` | passes |
| TC-WEL-03 | No ref param: ambassadorRef stays null | Unit | `{}` | `ambassadorRef === null` | passes |
| TC-WEL-04 | Empty ref string not stored | Unit | `{ref: ''}` | `ambassadorRef === null` (falsy guard) | passes |
| TC-WEL-05 | Analytics screen view logged | Unit | `{}` | `logScreenView('welcome_individual')` called | passes |
| TC-VIS-01 | Individual context renders with tinted cards | Visual | `userType=individual` | coral/teal/green/gold/amber/muted/coral/teal cards present with distinct left borders | PASS — all tinted; muted card plain bg, NOTE-01 |
| TC-VIS-02 | Driver context: 9 cards render | Visual | `userType=driver` | cards 1–9 in sequence; card 6 is muted variant | PASS — NOTE-01 applies to card 6 |
| TC-VIS-03 | Ambassador context: 4 cards render | Visual | `userType=ambassador` | coral/teal/green/gold — no muted cards | PASS — clean |
| TC-VIS-04 | Light theme: cards show color tints | Visual | light prefers-color-scheme | colored cards tinted; muted card plain bg (#f3f2f2) | PASS — tints visible in light theme |
| TC-VIS-05 | Dark theme: cards show color tints | Visual | dark prefers-color-scheme | colored cards tinted; muted card plain bg (#2D2D2D) | PASS — tints visible in dark theme |
| TC-VIS-06 | Token collision: gold === coral in individual context | Visual | body.individual active | `iz-card--gold` and `iz-card--coral` render identical bg+border (both `rgb(214, 98, 71)`) | CONFIRMED — pre-existing NOTE-02 |
| TC-VIS-07 | welcome-business untouched | Static diff | `git diff HEAD~1 -- src/app/welcome-business/` | zero diff | PASS — confirmed no diff |

## Integration test scenarios

None required — this is a template-only change with no new service calls, route changes, or backend interactions. The component's `ngOnInit` ref-capture and analytics logic were not changed.

## Regression checks

- [x] `WelcomeIndivisualsComponent` unit tests: 5/5 pass (TC-WEL-01 through TC-WEL-05)
- [x] Full suite: 718/718 pass with `ng test --watch=false`
- [x] Dev build (`ng build`) succeeds — 5 ✔ confirmations, template warnings pre-existing (NG8107 on OrderItemHistoryComponent, not this branch)
- [x] Ambassador dashboard cards (existing) not affected — no diff in ambassador-related templates
- [x] Dashboard `iz-card` usage not affected — no diff in dashboard templates
- [x] `welcome-business.component.html` not touched — zero diff confirmed

## Known gaps / Known defects

**NOTE-01 (cosmetic, open follow-up ticket needed):**
Driver step 6 "Complete drop-off" and individual "Daily Payouts" use `iz-card--muted`, which has `padding: 10px 14px` (vs `20px 16px` for all other variants), `border-radius: 4px` (vs `8px`), no background tint (plain `var(--bkg-card-color)`), and a 2px gray border (vs 3px colored). Both original cards had `background-color: #E67E22` (orange) and should have mapped to `iz-card--amber`. In a sequential step flow and equal-weight benefits grid, the muted variant reads as an inactive/secondary card, creating a visual weight inconsistency with its siblings. Not blocking: the code review flagged it as non-blocking and the content is readable and correctly rendered. Remediation: change both `iz-card--muted` / `iz-icon-chip--muted` pairs to `iz-card--amber` / `iz-icon-chip--amber` (2 template edits). Date found: 2026-10-09.

**NOTE-02 (pre-existing, not introduced by this branch):**
When `body.individual` is active, `--btn-bg-color` is overridden to `#D66247` in `styles.css` (`body.individual { --btn-bg-color: #D66247; }`), which equals `--btn-red-color`. This makes `iz-card--gold` and `iz-card--coral` visually identical (same computed background and border color) in the individual context. Confirmed visually and via `getComputedStyle`. Pre-existing in the design token system — not introduced by this branch. Remediation: assign a distinct value for `--btn-bg-color` under `body.individual`. Date found: 2026-10-09.

## Validation commands

```bash
# Unit tests (full suite — no exclusions)
ng test --watch=false

# Dev build
ng build

# Check muted card padding after launch
# Open http://localhost:4211/indivisuals after setting localStorage key
localStorage.setItem('kjsdfkjsdf_user_type', '"driver"')
# Then in console:
window.getComputedStyle(document.querySelector('.iz-card--muted')).padding
# Expected: "10px 14px 10px 14px"
```
