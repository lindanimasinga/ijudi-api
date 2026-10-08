# Test Plan: Admin — Chat Sessions

**userType:** admin
**Feature:** Admin chat session list → session selection → message thread view → send message
**App:** `izinga-onboarding` (Angular), route `/business/chat-sessions` and `/indivisuals/chat-sessions` (biz.izinga.co.za in prod)
**Last verified:** 2026-10-08, live E2E run — PASS (with defects fixed in release 1.18.0; see Known defects)

## Scope

Covers the admin `ChatSessionsComponent`: loading the full chat session list from Firestore, filtering by Active/Recent, selecting a session to view the message thread, and composing/sending a reply. Does **not** cover order management, profile approvals (see [`pending-approvals.md`](pending-approvals.md)), or the customer-facing chat entry point.

## Repos affected

- `izinga-onboarding` — `ChatSessionsComponent`, `avatar.util`, `bootstrap5-compat.css`, `app-chat-sessions` styles

## Known-good test fixtures

**Prod login** (`biz.izinga.co.za`):
- Admin number: `0734396642` (WhatsApp OTP)
- OTP source: prod CloudWatch logs, log group `/ecs/first-run-task-definition`, command `aws logs filter-log-events --filter-pattern "<number>"`
- Note: The WhatsApp delivery webhook may report `status: failed` for this number — login still succeeds regardless; the OTP is always in the logs.

**UAT / local** (`ng serve` on port 4211, launch config `izinga-onboarding-qa`, `environment.ts izingaUrl=http://localhost`, backend run with `-Dspring-boot.run.profiles=uat`):
- OTP readable from the local backend log directly (no CloudWatch needed).
- The UAT sandbox DB has **no user with `role: ADMIN`**, so admin dashboard tiles will not show after login.
- Both admin routes (`/business/chat-sessions`, `/indivisuals/chat-sessions`) have **no route guard** — navigate directly to the route after any OTP login; the list loads regardless of role.

**Chat session data:**
- Prod list loads 1,539 active sessions from Firestore.
- Safe conversation for send tests: `"theonlymlindosi"` (+27 81 281 5707, Lindani's own number). **Do NOT send messages to any other real customer conversations.**

**Viewport:**
- The Browser pane's real viewport is approximately 492px — this is the **mobile layout**, where selecting a session opens a full-screen modal.
- Use `resize_window` 1280×900 to test the **desktop two-column layout** (session list on left, message pane on right).
- `isMobileView()` evaluates `window.innerWidth <= 768` **at selection time only** — it is not reactive to a resize after the view is open; resize before selecting a session to exercise the correct layout branch.

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| TC-01 | Chat session list loads with count badge | E2E | Navigate to `/business/chat-sessions` after OTP login | Session list renders with a count badge showing the number of loaded sessions | Confirms Firestore binding is live |
| TC-02 | Active/Recent pill filter | E2E | Click "Active" pill; click "Recent" pill | List re-filters to sessions matching the selected state; count badge updates | — |
| TC-03 | Session row shows correct metadata | E2E | Inspect any row | Row displays: initials avatar (rendered by `avatar.util`), customer name, last message preview, date, and status badge with correct token class (`iz-badge--green` for active, `iz-badge--amber` for pending, `iz-badge--muted` for inactive) | No Bootstrap grey literals should appear — verify via `getComputedStyle`, not class names |
| TC-04 | Selecting a session opens thread — desktop layout | E2E | Resize to ≥1280px, select a session row | Right pane shows the message thread; gold header bar; customer speech bubbles use `--chat-bubble-incoming-bg` token (visibly distinct from page background); store/admin bubbles use gold | Requires explicit `resize_window` before selecting |
| TC-05 | Selecting a session opens thread — mobile layout | E2E | Viewport ≤768px, select a session row | Full-screen modal opens with gold header, correct bubble styling | Browser pane default viewport (~492px) exercises this path |
| TC-06 | Close button renders correctly | E2E | Open a session thread (mobile or desktop) | Close button renders as a visible × character; not an invisible icon or broken glyph | `fa fa-times` is not loaded — must be a Material ligature or plain text × |
| TC-07 | Composer layout — mobile | E2E | Viewport 375px, open a session thread | Input field + attach button + send button appear **inline on one row**; no stacking to full-width; each button is at least 44px tap target; Material `send` and `attach_file` ligatures render with width > 0 | Stacking was the defect fixed in 1.18.0 |
| TC-08 | Material icon glyphs render (non-zero width) | E2E | Open any session thread, inspect attach and send buttons via `getComputedStyle` or DOM measurement | Both icon elements have `offsetWidth > 0`; ligature text is the correct Material Icons glyph | Font Awesome (`fa fa-*`) is **not** loaded in this app — any `fa fa-*` class renders nothing |
| TC-09 | Spinner label hidden during load | E2E | Observe list while Firestore data is loading | Spinner/loading indicator shows a visual spinner; no raw label text is visible to the user while loading | — |
| TC-10 | No Bootstrap grey literals in `app-chat-sessions` | E2E | Run `getComputedStyle` sweep across all `app-chat-sessions` descendants | No computed colour equal to `rgb(73, 80, 87)`, `rgb(248, 249, 250)`, or `rgb(222, 226, 230)` found on any element inside the component | These are Bootstrap 4 defaults that leak through unpatched utilities; verify with `getComputedStyle`, not class inspection |
| TC-11 | Light theme correct | E2E | Load page without `dark-theme` class on `body` | All colours within `app-chat-sessions` use design-system tokens; no raw hex or Bootstrap grey literals | — |
| TC-12 | Dark theme correct | E2E | Add `dark-theme` class to `body` | Component re-renders using dark token values; no colours flip to light Bootstrap defaults | — |
| TC-13 | Hover/focus on session row preserves dark colours | E2E | Hover over a session row; hold sticky-hover on mobile | Row does **not** flip to light background or light text colours on hover or focus | Bootstrap `:hover` leaking `#f8f9fa` was the defect fixed in 1.18.0 |
| TC-14 | Send message (safe conversation only) | E2E | Type a message in the composer, click send (use `"theonlymlindosi"` only) | Message appears in the thread as a store/admin bubble; Firestore document updated | Never send to real customer conversations other than the designated test number |

## Integration test scenarios

- **Full admin session flow:** login (OTP) → navigate to `/business/chat-sessions` → list loads from Firestore → select `"theonlymlindosi"` → verify thread history → send a test message → verify it appears in the thread and in Firestore directly.
- **Route guard bypass (UAT):** confirm that navigating directly to `/business/chat-sessions` after a non-admin OTP login still loads the component (no redirect, no error screen) — this documents the known missing guard and must remain a regression check until a guard is added.

## Regression checks

- [ ] Both routes (`/business/chat-sessions`, `/indivisuals/chat-sessions`) load the same `ChatSessionsComponent` without error
- [ ] Route guard absence still present — if a guard is added in future, update TC integration scenario above and remove this check
- [ ] `avatar.util` still generates correct initials from customer name
- [ ] `iz-badge--green/amber/muted` token classes map to correct computed colours in both themes
- [ ] `bootstrap5-compat.css` still shims `me-*`, `gap-*`, `fw-bold`, `visually-hidden`, `btn-close` globally — if Bootstrap 5 is adopted in future, remove the shim and retest all shimmed utilities across all templates
- [ ] Material Icons font still loaded and ligatures render (width > 0) for `send` and `attach_file`
- [ ] 8 other templates using `fa fa-*` icons — tracked separately; confirm none have been added to `app-chat-sessions`

## Known defects

**Fixed in release 1.18.0, branch `bugfix/ONB-UI-chat-pending-audit-fixes` (2026-10-08):**
- Bootstrap `:hover` colours leaking — list rows going light (`#f8f9fa`) on hover/sticky-hover on mobile (fixed)
- Invisible customer speech bubbles — `--chat-bubble-incoming-bg` token was not applied (fixed)
- Composer buttons stacking full-width on mobile instead of inline (fixed)
- `fa fa-*` icons never rendered — Font Awesome is not loaded; app uses Material Icons only (fixed by replacing with Material ligatures)
- Bootstrap 5 utilities (`me-*`, `gap-*`, `fw-bold`, `visually-hidden`, `btn-close`) silently no-ops — app loads Bootstrap 4.5.0; now shimmed globally by `src/styles/bootstrap5-compat.css` (fixed)

**Still open (tracked):**
- 8 other templates across the app still use `fa fa-*` icon classes (tracked; not in `app-chat-sessions`)
- Bootstrap 5 full migration decision pending
- Dashboard/welcome local classes should alias to the new global `.iz-*` utilities

## Known gaps / environment issues

- **UAT sandbox has no ADMIN user** — admin dashboard tiles do not display on UAT/local; all chat-session testing on UAT must navigate directly to the route
- **`getComputedStyle` is the only reliable way to verify colour tokens** in this app due to the BS4/BS5 mismatch — class-name inspection will give false confidence because Bootstrap 4 classes silently no-op while the shim provides the correct visual output

## Validation commands

- `ng test --watch=false --code-coverage` (unit, `izinga-onboarding`)
- Manual E2E: `ng serve` on port 4211 (launch config `izinga-onboarding-qa`), backend with `-Dspring-boot.run.profiles=uat`, navigate to `/business/chat-sessions`
