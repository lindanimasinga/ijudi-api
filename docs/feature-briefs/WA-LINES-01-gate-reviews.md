# WA-LINES-01 — Gate reviews (Solution Architect + Security & Compliance), 19 Sep 2026

Both gates returned **APPROVED WITH CHANGES**. The items below are binding amendments to `WA-LINES-01-multi-whatsapp-lines.md`. Where they conflict with the brief, these win.

## Solution Architect (ADR-020) — required changes

| # | Change | Affects |
|---|---|---|
| SA-1 | `WhatsappLine` model, repository, service, bootstrap, admin controller live in **izinga-messaging**, not izinga-commons. `WhatsappSession` (izinga-commons) only gains plain `phoneNumberId: String?`, `agentName: String?`, `storeId: String?`. | REQ-01, REQ-13 |
| SA-2 | Index migration is a **pre-deploy runbook** against Atlas, not a startup component: (1) backfill `phoneNumberId` = legacy id on all `whatsapp_session` docs, verify zero missing; (2) `dropIndex("from_1")`; (3) deploy — Spring Data creates compound unique `(from, phoneNumberId)`; (4) legacy-adopter guard stays as non-load-bearing fallback. Runbook goes in the PR description and release checklist. Lindani runs it. | REQ-13, REQ-14 |
| SA-3 | `LineContext` is an immutable value object passed as an **explicit parameter** through `processInboundMessage → upsertSession → handlePreDispatchFlows → dispatchMessageByType → AiCustomerServiceAgent.handleWhatsappQuery`. Never `@RequestScope` (handler is `@Async`). | REQ-10 |
| SA-4 | Phase 2 store isolation via an `X-Store-Id` HTTP header is **not implementable** with Spring AI 1.1.5 `@Tool` methods. Phase 2 design: explicit `storeId` tool parameter on every store-scoped tool, validated server-side against the authenticated MCP session's permitted store. Must be designed and signed off before Phase 2 starts. | P2 |
| SA-5 | `sendLandingOptions` must be **audience-aware**: a new session on the DRIVER line must not receive the customer landing menu. | REQ-10 |
| SA-6 | `appendHumanCorrection` resolves `agentName` from the session `(from, phoneNumberId)`, never a constant. | REQ-21 |
| SA-7 | Call-site count is **20**, not 19: 17 `WhatsappNotificationService` + 2 `FirestoreToWhatsappController` + 1 `WhatsAppOtpService`. | REQ-08 |
| SA-R1 | Firestore `ChatSession.phoneNumberId` tagging is gated on `multiLineEnabled` so the izinga-onboarding chat UI is unaffected while the flag is off. Onboarding UI change is a separate, coordinated task. | REQ-05 |
| SA-R2 | `WhatsappLineBootstrap` uses find-then-upsert by `phoneNumberId` and runs after Spring Data init (`@Order` / `ApplicationReadyEvent`). | REQ-02 |

## Security & Compliance — required before Phase 1 deploy

| ID | Sev | Requirement | Affects |
|---|---|---|---|
| SEC-01 | HIGH | Verify Meta `X-Hub-Signature-256` (HMAC-SHA256 of the raw body with the app secret) on `POST /whatsapp/webhook` **before** parsing/publishing. App secret from config (`whatsapp.cloud.appSecret`, Secrets Manager). 403 on missing/invalid. Same pattern as the Yoco HMAC check. Compare raw bytes, not the re-serialised object. | WhatsAppWebhookController |
| SEC-02 | HIGH | Session isolation exactly as SA-2; `upsertSession` looks up by `(from, phoneNumberId)` and stamps new sessions with the resolved line. | REQ-13 |
| SEC-04 | HIGH | Sanitise human corrections before appending to a system prompt: strip instruction-like preambles ("ignore previous", "you are now", "new instructions", etc.), cap length (500 chars), and persist corrections as a structured list on `AiAgentConfig` (`corrections[] {text, by, at}`) rendered in a clearly delimited block, not concatenated into the raw prompt string. | REQ-21 |
| SEC-05 | MED | `/forward/**` (FirestoreToWhatsappController) must require a Bearer JWT with `ADMIN` or `STORE_ADMIN` — `@PreAuthorize` on the controller **and** an explicit matcher in `SecurityConfig`. | — |
| SEC-06 | MED | Admin line-registry endpoints: `ADMIN` only; validate `phoneNumberId` (numeric, ≤15 chars); `storeId` must exist; write an immutable audit record (`whatsapp_line_audit`: operator uid, action, before/after, timestamp) on every change. | REQ-04 |
| SEC-07 | — | Agree: `driverPhoneId` bootstraps from Secrets Manager; Mongo registry holds only the non-secret phone-number ID; token never in Mongo. | REQ-03 |

Required before Phase 2 deploy: SEC-03 (server-enforced store scoping per SA-4); Legal review of the driver broadcast template wording (must state: iZinga Driver Support line, messages may be handled by an AI assistant, link to Privacy Policy).

Noted, no action in this feature: driver conversations already flow to OpenAI (pre-existing; DPA awareness for Hloniphani).
