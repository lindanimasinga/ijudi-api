# iZinga Test Plan Library

Persistent, reusable test plans for manual and automated QA passes across iZinga repos, structured **per userType then per feature** so a test plan can be found and reused without re-deriving flow knowledge from code each time.

## Structure

```
test-plans/
  customer/
    <feature>.md
  driver/
    <feature>.md
  merchant/
    <feature>.md
  admin/
    <feature>.md
```

Each userType directory holds one file per feature. A feature that spans two userTypes (e.g. a delivery that involves both a customer and a driver) gets a primary file under the userType that initiates it, cross-linked from the other userType's directory.

## Index

| userType | Feature | File | Last verified |
|---|---|---|---|
| customer | Furniture/parcel booking | [customer/furniture-booking.md](customer/furniture-booking.md) | 2026-10-08 |
| driver | Quote acceptance & delivery fulfilment | [driver/quote-acceptance-and-fulfilment.md](driver/quote-acceptance-and-fulfilment.md) | 2026-10-08 |
| store | PayFast subscription billing — tier selection, checkout, ITN activation | [store/payfast-subscription-billing.md](store/payfast-subscription-billing.md) | 2026-10-08 |
| store | EWALLET bank validation carve-out + STORE-BANK-01 fallback fix (POST /user, PATCH /user/{id}, POST /store) | [store/ewallet-bank-validation.md](store/ewallet-bank-validation.md) | 2026-10-08 |
| admin | Chat sessions — list, filter, thread view, send message | [admin/chat-sessions.md](admin/chat-sessions.md) | 2026-10-08 |
| admin | Pending approvals — profile review, service-type filter, approve gate | [admin/pending-approvals.md](admin/pending-approvals.md) | 2026-10-08 |
| customer (all roles) | Terms & ICA acceptance — all 4 branches, proactive dashboard profile-completeness gate (TC-PROF-01–06), blank-imageUrl stripping, profile-incomplete CTA, fixed-bottom button translucency fix (BUG-1), post-ICA training invisible button fix (BUG-2), Ambassador ICA v2→v3 version bump (TC-55, TC-56, TC-DASH-10b) | [customer/terms-acceptance.md](customer/terms-acceptance.md) | 2026-10-09 |
| driver (all roles) | Phone verification & WhatsApp OTP login — OTP send/verify, double-submit guard (ONB-BUG-03), SMS fallback via triple-tap, phone normalisation, returnUrl redirect | [driver/phone-verification.md](driver/phone-verification.md) | 2026-10-09 |
| individual / driver / ambassador | Welcome page card theme consistency — /indivisuals route, all 3 role contexts (individual benefits grid, driver 9-step flow, ambassador 4-step flow) | [individual/welcome-card-theme.md](individual/welcome-card-theme.md) | 2026-10-09 |
| store / messenger-admin / driver | Store & messenger-admin driver invitation — 5-outcome invite form (StoreMessengersComponent, TeamMessengersComponent), StoreInviteComponent 7-state landing page, PhoneVerificationComponent returnUrl, public deep-link, accept/reject by contextType, AC-21 open-redirect guard (INVITE-DRV-01 / ADR-018) | [store/store-driver-invitation.md](store/store-driver-invitation.md) | 2026-10-12 |
| customer | WhatsApp inbound location-message handling — describe() all null/blank variants, Firestore persistence with meta, AI-agent routing (CUSTOMER vs STORE audience), AC-07 StoreAiAgent guard | [customer/whatsapp-location-messages.md](customer/whatsapp-location-messages.md) | 2026-10-20 |
| admin (platform integration) | MCP tools — tokenless auth bypass fix (SEC-MCP-01/02, 18 tools, FIX-03/04/05); find_stores + start_order; SA-022-01 guards; idempotent duplicate detection; domain exception sanitization | [admin/mcp-tools.md](admin/mcp-tools.md) | 2026-11-03 |
| driver / customer | AI Customer Service Agent — WhatsApp inbound webhook live E2E trace; AiCustomerServiceAgent dispatch; new-session landing flow; resumed-session OpenAI call; Firestore + MongoDB conversation history; outbound WhatsApp delivery (CUSTOMER-audience confirmed; DRIVER-audience blocked by UAT gap) | [driver/ai-agent-whatsapp-live-trace.md](driver/ai-agent-whatsapp-live-trace.md) | 2026-11-01 |
| driver / customer | AI Customer Service Agent — boundary discipline & identity integrity; 18-case live suite; cross-identity attack resistance; privacy guardrails; role-claim bypass; payout lookup scoping; 4 FAIL + 2 PARTIAL findings | [driver/ai-agent-boundary-discipline.md](driver/ai-agent-boundary-discipline.md) | 2026-11-01 |
| admin (platform / engineering) | JUnit 4 engine fix (vintage-engine, Surefire 3.2.5) + Device.kt @field:NotBlank + OrderServiceImpl shippingData null guards + StoreService partial-PATCH OR guard + DeliveryCalculationService extraction | [admin/junit4-engine-and-production-null-guards.md](admin/junit4-engine-and-production-null-guards.md) | 2026-11-02 |
| driver / admin / store | Payout disbursement — full pipeline: BUG-3 (null-bank NPE guard + WhatsApp notify, driver path), GAP-7 (same for store path), GAP-3 (PROCESSING→PENDING reversion with failureReason + attemptCount on paid=false), BUG-7 (RFC 4180 CSV quoting). 258/0/3-skipped full reactor PASS. | [driver/payout-disbursement.md](driver/payout-disbursement.md) | 2026-11-03 |
| admin (platform integration) | MCP tools controller-layer separation — ADR-026 / MCP-SEC-03 Phases 0–6 + Phase 2b; 17 @Tool methods on 6 @McpToolController classes (create_user removed from MCP surface Phase 2b, Lindani 2026-11-05); services de-gated for REST; bank masking at controller layer; ArchUnit R1–R6; single-provider McpConfig; @RequiresAudience retired; contract snapshot + access matrix safety nets | [admin/mcp-controller-layer-separation.md](admin/mcp-controller-layer-separation.md) | 2026-11-05 |
| platform (MCP / AI agent surface) | MCP WhatsApp outbound notification tools — ADR-029; send_landing_options_notification (gated, 5 audiences) + send_shop_options_notification (@McpPublicTool, public); sendShopOptions boolean return + catch(Exception) + null guard (DEF-1/AC-09 fix); SEC-02 false Javadoc sweep; SEC-03 frozen allow-list ArchTest; SEC-05 phone masking to last 4 digits | [platform/mcp-whatsapp-outbound-notifications.md](platform/mcp-whatsapp-outbound-notifications.md) | 2026-11-07 |
| customer | WhatsApp AI agent tool dispatch — ADR-030 Cause A (removed isNewSession shortcut) + Cause B (@Builder.Default useTools=true) + AiAgentConfigInitializer backfill; toolsPresent=true confirmed; mcp_call confirmed (send_landing_options_notification, status=completed); SEC-01 bypass; build methodology (install step required) documented | [customer/whatsapp-ai-agent-tool-dispatch.md](customer/whatsapp-ai-agent-tool-dispatch.md) | 2026-11-10 |

## Maintenance rule

Whenever a live or regression test is run against a feature covered here, **update the corresponding file** (test cases, known-good values, last-verified date, any newly discovered defects) instead of creating a throwaway report only. A one-off test *report* (dated, point-in-time, with pass/fail verdict and defects found) is still worth writing separately — but the reusable *test plan* in this library must be kept current so the next QA pass starts from here instead of re-reading application code from scratch.

When a feature is tested for the first time and has no file here yet, create one following the format in [`../../../.claude/agents` — iZinga QA & Test Automation agent](../../../README.md) (Scope, Repos affected, Risk areas, Test cases table, Integration scenarios, Regression checks, Validation commands), plus a "Known-good test fixtures" section recording real values (test accounts, coordinates, category/driver-type mappings) that worked, so future runs don't have to rediscover them.

## Admin pages — shared gotchas

These apply to every file in `admin/` and must be understood before testing any admin route in `izinga-onboarding`:

- **No route guard on admin routes.** Both `/business/chat-sessions` and `/business/pending-approvals` can be reached after any OTP login — no `role: ADMIN` check is enforced at the Angular router level. On UAT/local the sandbox DB has no ADMIN user, so the admin dashboard tiles do not render, but navigating directly to the route loads the component.
- **Sandbox has no ADMIN user.** UAT/local testing must navigate directly to the admin route after any OTP login. Do not wait for an admin dashboard tile that will never appear.
- **Verify colours with `getComputedStyle`, never by reading class names.** The app loads Bootstrap 4.5.0 but uses Bootstrap 5 utility classes (`me-*`, `gap-*`, `fw-bold`, `visually-hidden`, `btn-close`). These are now shimmed globally by `src/styles/bootstrap5-compat.css`, but class-name inspection is unreliable — a class can be present and still have no visual effect if the shim is incomplete or reverted. Always confirm colours, spacing, and visibility using `getComputedStyle` in the browser.
- **Icons must be Material ligatures.** Font Awesome is **not** loaded in `izinga-onboarding`. Any element using `fa fa-*` classes renders nothing (zero width). All icon assertions must confirm `offsetWidth > 0` on the icon element; a non-zero width confirms a Material ligature is rendering correctly.
