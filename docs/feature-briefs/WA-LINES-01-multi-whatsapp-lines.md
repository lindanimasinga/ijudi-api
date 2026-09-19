# Feature Brief: WA-LINES-01 — Multiple WhatsApp Lines, One AI Service Agent Per Line

**Status:** Approved for Implementation
**PO-APPROVED — authorised by Lindani Masinga 2026-09-19**

**Feature name:** Multiple WhatsApp Lines, One AI Service Agent Per Line (Phase 1: Driver Service Line)

**Requested by:** Lindani Masinga

**Business objective:** Today ijudi-api routes all WhatsApp traffic — customer queries, driver support, OTP, and store notifications — through a single phone number with a single inbound AI agent (`driver_support`). Customers who are also drivers get the driver agent regardless of context; the customer-facing agent is effectively named wrong and carries driver-only tools. Separating the Driver Service onto its own number enables a dedicated driver AI agent (correct system prompt, correct MCP tool scope), protects the customer number's Meta quality rating from driver-volume spikes, and establishes the per-line registry architecture that Phase 2 (per-store agent and number) requires without a structural rewrite. The secondary business benefit is that the new Driver number can be warmed to its own tier limit independently, future-proofing driver-scale growth without risking customer-number suspension.

**Audience:** Drivers/Messengers (primary — new Driver number and agent); Customers (protected — their number and agent are unchanged); iZinga Support Staff / ADMIN (admin endpoints to manage the line registry); Store owners (unaffected in Phase 1; Phase 2 beneficiaries)

**Products affected:** `ijudi-api` (primary — all implementation); `izinga-onboarding` (Phase 2 dependency only — Firestore session rules + UI store filter; not in this release)

**Out of scope (Phase 1 — do not implement):**
- Per-store WhatsApp number and per-store AI agent (Phase 2; requirements captured in the Phase 2 section below — read them before starting Phase 1 to avoid painting into a corner)
- Any change to the Flutter `ijudi` app, `cs-lifestyle`, `furniture-delivery-app`, or `izinga-pay`
- Switching the AI provider away from OpenAI Responses API
- Any change to the WhatsApp webhook URL, verify token, or Meta App configuration (WABA is already scoped to iZinga's single app; only `phone_number_id` routing changes in code)
- Sending new driver templates — existing templates are WABA-scoped and work from either number; confirm via UAT send only
- Any new Meta Business Manager template submissions
- Changing the OTP number — OTP remains on the legacy number for Phase 1 and Phase 2
- Building a UI in `izinga-onboarding` for managing the line registry (ADMIN uses the REST endpoint directly in Phase 1)

---

## User Stories

**US-01 (Driver):** As a driver, when I send a WhatsApp message to iZinga's Driver Support number, I am answered by the Driver Service AI agent — which knows my driver context and has access to the correct driver tools — not a generic or customer-focused agent.

**US-02 (Driver):** As a driver, when I receive a WhatsApp notification from iZinga (order assignment, payout, daily limit alert), it arrives from the Driver Support number so my contacts list and WhatsApp unread state are consistent.

**US-03 (Customer):** As a customer, my experience on the iZinga customer support number is unchanged — the same agent, same tools, same response behaviour as before this release.

**US-04 (Customer — dual role):** As a person who is both a driver and a customer, when I message the Driver number I get the Driver agent; when I message the Customer number I get the Customer agent. My two chat sessions are independent.

**US-05 (iZinga Support Staff / ADMIN):** As an ADMIN, I can activate or deactivate a WhatsApp line from the admin API without a code deploy, and I can view the current line registry to audit which number handles which audience.

**US-06 (iZinga Support Staff — human handoff):** As a support agent replying to a customer or driver via the Firestore human-handoff interface, my reply is sent from the same number the person wrote to — never crossed to the wrong number.

**US-07 (iZinga ADMIN — rollback):** As an ADMIN, if the Driver line experiences issues post-launch I can deactivate it via the admin API and all driver traffic immediately falls back to the legacy number without a code deploy.

---

## Requirements

### Group A — Line Registry

**REQ-01:** Create a new Mongo collection `whatsapp_lines`. Each document is a `WhatsappLine` with fields:
- `phoneNumberId` (String, unique index) — the Meta `phone_number_id` value
- `displayNumber` (String) — human-readable number, e.g. "+27 xx xxx xxxx"
- `audience` (enum: `DRIVER | CUSTOMER | STORE | OTP`) — which traffic this line handles
- `agentName` (String, nullable) — references a document key in `ai_agent_configs`; null means no AI agent (OTP line)
- `storeId` (String, sparse unique index, nullable) — Phase 2 store association; null in Phase 1
- `active` (boolean) — whether this line is currently live
- `isDefault` (boolean) — the fallback line when no audience match is found; exactly one document may have `isDefault = true`

Model class lives in `izinga-commons` (or the messaging module's model package — follow the `BankConfig` pattern in `izinga-recon`). Repository, service, and ADMIN REST controller live in `izinga-messaging`.

**REQ-02:** Create `WhatsappLineBootstrap`, a Spring `@Component` that seeds the two Phase 1 rows on application startup if they do not already exist:
- Row 1: `phoneNumberId` = value of Secrets Manager key `whatsapp.cloud.phoneId`; `audience = CUSTOMER`; `agentName = customer_support`; `active = true`; `isDefault = true`
- Row 2: `phoneNumberId` = value of Secrets Manager key `whatsapp.cloud.driverPhoneId`; `audience = DRIVER`; `agentName = driver_support`; `active = true`; `isDefault = false`

After the first startup the registry is the source of truth. Bootstrap is idempotent — re-running must not create duplicate rows (use `upsert` or an existence check on `phoneNumberId`).

**REQ-03:** Add a new config property `whatsapp.cloud.driverPhoneId` (read from Secrets Manager `izinga-prod`) alongside the existing `whatsapp.cloud.phoneId`. Add `whatsapp.cloud.multiLineEnabled` (boolean, default `false`) to the same config class.

**REQ-04:** Expose a ADMIN-only REST controller (`/admin/whatsapp-lines`) with:
- `GET /admin/whatsapp-lines` — list all lines
- `PUT /admin/whatsapp-lines/{phoneNumberId}/active` — set `active = true/false`

No create or delete endpoint is needed in Phase 1 (bootstrap handles seeding; Phase 2 will add store-line provisioning).

### Group B — Feature Flag and Fallback

**REQ-05:** Implement a feature flag (`whatsapp.cloud.multiLineEnabled`). When the flag is `false` (the default), the system must behave byte-for-byte identically to the pre-feature code: outbound always uses `whatsappConfig.phoneId()`; inbound ignores `metadata.phone_number_id` and uses the hardcoded legacy agent name. No functional change is observable with the flag off.

**REQ-06:** When the flag is `true` but the `WhatsappLine` registry returns no match (e.g. a phone number ID not yet in the registry), the system must fall back to the `isDefault = true` line, log a `WARN` with the unrecognised `phone_number_id`, and continue processing. The call must not throw or drop the message.

### Group C — Outbound Resolver

**REQ-07:** Create `WhatsappSenderResolver` with method `resolve(audience: Audience, storeId: String?): String`. Resolution order:
1. If `multiLineEnabled = false`, return legacy `whatsappConfig.phoneId()`.
2. Find the active line where `audience` matches and `storeId` matches (Phase 2 path; in Phase 1 `storeId` is always null).
3. Find the active line where `audience` matches and `storeId` is null.
4. Find the active line where `isDefault = true`.
5. Fall back to legacy `whatsappConfig.phoneId()` with a `WARN` log.

**REQ-08:** Replace all 19 `whatsappConfig.phoneId()` call sites in `WhatsappNotificationService` (17 sites) and `WhatsAppOtpService` (1 site) and `FirestoreToWhatsappController` (1 site) with `WhatsappSenderResolver.resolve(audience, storeId)`. Assignment rules:
- All driver-facing templates (order assignment, payout, daily limit, driver nudges, etc.) → `DRIVER`
- All customer/shop/landing templates → `CUSTOMER`
- OTP → `OTP` (resolves to the legacy number in Phase 1, which is the CUSTOMER / default line)
- Human reply in `FirestoreToWhatsappController` → use the `phoneNumberId` stored on the `WhatsappSession` (the reply must leave from the number the person wrote to, not from an audience resolution)

**REQ-09:** While touching all 19 call sites, consolidate the duplicated `0XXXXXXXXX → +27XXXXXXXXX` phone number normalisation into a single static util method. Do not leave inline normalisation logic scattered across the file.

### Group D — Inbound Routing

**REQ-10:** In `WhatsappInboundEventHandler.handleInbound`, read `value.getMetadata().getPhoneNumberId()`. Resolve the corresponding `WhatsappLine` (using the fallback logic in REQ-06). Thread a `LineContext` record (fields: `line: WhatsappLine`, `agentName: String`, `audience: Audience`, `storeId: String?`) through the call chain: `upsertSession` → `handlePreDispatchFlows` → `dispatchMessageByType` → `AiCustomerServiceAgent` (and future per-store agents).

**REQ-11:** Remove the hardcoded `AGENT_NAME` constant from the inbound handler. The agent name is always sourced from `LineContext.agentName` (which comes from `WhatsappLine.agentName`).

**REQ-12:** Agent selection is by line, not by the sender's `ProfileRole`. A driver who is also a customer gets the Driver agent when they write to the Driver number and the Customer agent when they write to the Customer number. The inbound handler must never inspect `ProfileRole` to choose the agent.

### Group E — Session and Conversation Keying

**REQ-13:** Add `phoneNumberId` (String), `agentName` (String), and `storeId` (String, nullable) fields to `WhatsappSession`. Replace the unique Mongo index on `from` alone with a compound unique index on `(from, phoneNumberId)`. Migration order is mandatory: backfill `phoneNumberId = legacy phone number id` on all existing documents **before** the index operation in the same migration component or startup hook, so no existing session document is orphaned.

**REQ-14:** When `upsertSession` encounters a legacy session document that lacks a `phoneNumberId` field, stamp it with the legacy phone number id and save before any further processing (one-release compatibility adopter). This prevents double-session creation during the rollout window when old sessions without the field are still in the database.

**REQ-15:** Add `agentName` (String) to `ConversationHistory`. Add a Mongo index on `(driverPhoneNumber, agentName)`. Backfill `agentName = 'driver_support'` on all existing documents as part of the migration step.

**REQ-16:** Human handoff state (`isAIAgentActive`) becomes scoped to `(person, line)`. Pausing the agent on the Driver line for a driver must not pause the Customer agent for that same person on the Customer line. The session lookup and human-handoff check must use the compound key `(from, phoneNumberId)`.

### Group F — Agent Config

**REQ-17:** Add `audience` (Audience enum, nullable), `storeId` (String, nullable), `mcpServers` (List of MCP server config, persisted), and `allowedTools` (List of String, persisted) fields to `AiAgentConfig`. The hardcoded `https://api.izinga.co.za/mcp` URL becomes the seeded default value in the `driver_support` and `customer_support` seed documents; it is no longer hardcoded in the agent execution code.

**REQ-18:** Replace the single-entry `Optional<AiAgentConfig>` cache in `AiAgentConfigService` with a `Map<String, AiAgentConfig>` keyed by `agentName`. The old cache caused a correctness defect the moment two agents exist. The new cache must refresh on TTL or on explicit invalidation (follow whatever refresh pattern the existing single-entry cache uses, extended to all entries).

**REQ-19:** Seed (upsert on startup) two `ai_agent_configs` documents:
- `driver_support`: `audience = DRIVER`, `mcpServers = [{url: "https://api.izinga.co.za/mcp"}]`, `active = true`
- `customer_support`: `audience = CUSTOMER`, `mcpServers = [{url: "https://api.izinga.co.za/mcp"}]`, `active = true`

Phase 2 will add `store_support_default` as a template cloned per store. The seed must be idempotent.

**REQ-20:** `AiCustomerServiceAgent` (and any other agent executor) must read the agent name from `LineContext.agentName` when building the OpenAI Responses API call. It must not use a hardcoded constant.

**REQ-21:** Human corrections (training messages appended to conversation history) must be appended to the `agentName` field sourced from the session, not hardcoded to `driver_support`.

### Group G — Monitoring

**REQ-22:** Subscribe to Meta `account_update` webhook events. For each event that carries a `phone_number_id` and a messaging tier or quality rating payload, log a structured entry at `INFO` level including: `phoneNumberId`, `displayNumber`, `newTier`, `qualityRating`, `timestamp`. This log line must be machine-parseable (use a structured logging framework or a fixed JSON format) so it can be queried in CloudWatch. No alerting threshold is set in Phase 1 — logging is the requirement.

**REQ-23:** For every outbound `sendMessage` call, log at `DEBUG` level: `phoneNumberId` (the sender line), `toNumber` (masked to last 4 digits), `templateName or messageType`, `audience`. For every inbound message processed, log at `DEBUG` level: `phoneNumberId` (the receiving line), `agentName`, `audience`. These per-number metrics allow error rate and volume to be tracked per line in CloudWatch.

---

## Acceptance Criteria

**AC-01 (registry seeding):** Given the application starts with `multiLineEnabled = false` and `whatsapp.cloud.driverPhoneId` set in Secrets Manager, when the application context loads, then the `whatsapp_lines` collection contains exactly two documents (one CUSTOMER, one DRIVER). Re-running startup does not create duplicate documents.

**AC-02 (flag-off byte identity):** Given `whatsapp.cloud.multiLineEnabled = false`, when a driver-facing outbound template is triggered and when an inbound driver message is processed, then `WhatsappSenderResolver` returns the legacy phone number id for all audiences and `WhatsappInboundEventHandler` uses the legacy hardcoded agent name. No behaviour change from the pre-feature baseline.

**AC-03 (outbound — driver templates go to Driver line):** Given `multiLineEnabled = true` and the Driver line is active, when `WhatsappNotificationService.sendDriverNotification` (or equivalent driver template method) is called, then the `@Path phoneId` passed to `WhatsAppService.sendMessage` equals the value of `whatsapp.cloud.driverPhoneId`.

**AC-04 (outbound — OTP stays on legacy number):** Given `multiLineEnabled = true`, when `WhatsAppOtpService` sends an OTP, then the `@Path phoneId` equals the value of `whatsapp.cloud.phoneId` (the CUSTOMER / default / legacy line).

**AC-05 (outbound — human reply leaves from the session's line):** Given a `WhatsappSession` with `phoneNumberId = driverPhoneId` and human handoff active, when `FirestoreToWhatsappController` sends a reply, then the `@Path phoneId` equals `driverPhoneId`, not the customer number.

**AC-06 (inbound — Driver number → Driver agent):** Given `multiLineEnabled = true` and a webhook payload whose `metadata.phone_number_id` equals `driverPhoneId`, when `handleInbound` processes the message, then the `LineContext.agentName` is `driver_support` and a `WhatsappSession` is created or retrieved using the compound key `(from, driverPhoneId)`.

**AC-07 (inbound — Customer number → Customer agent):** Given a webhook payload whose `metadata.phone_number_id` equals the legacy `phoneId`, when `handleInbound` processes the message, then `LineContext.agentName` is `customer_support`.

**AC-08 (inbound — dual role, two independent sessions):** Given a user whose phone number appears in both a Driver session (on the Driver line) and a Customer session (on the Customer line), when each line receives a message from that number, then two separate `WhatsappSession` documents exist and neither session's `isAIAgentActive` state bleeds into the other.

**AC-09 (inbound — unknown phone_number_id fallback):** Given `multiLineEnabled = true` and a webhook payload with an unrecognised `phone_number_id`, when `handleInbound` processes it, then the system logs a `WARN` containing the unrecognised id, falls back to the `isDefault = true` line, and continues processing without throwing an exception.

**AC-10 (session migration — legacy doc adoption):** Given an existing `WhatsappSession` document with no `phoneNumberId` field, when `upsertSession` processes a message for that sender, then the document is stamped with the legacy `phoneId`, saved, and the call proceeds without creating a duplicate session.

**AC-11 (admin — deactivate line):** Given the Driver line is active, when an ADMIN calls `PUT /admin/whatsapp-lines/{driverPhoneId}/active` with body `{"active": false}`, then `WhatsappSenderResolver.resolve(DRIVER, null)` falls back to the default line and returns the legacy `phoneId`. No code deploy is required.

**AC-12 (agent config cache — two agents):** Given both `driver_support` and `customer_support` configs exist in `ai_agent_configs`, when `AiAgentConfigService` is asked for each agent name, then it returns the correct config for each without one overwriting the other.

**AC-13 (monitoring — account_update webhook):** Given Meta sends an `account_update` event carrying a `phone_number_id` and quality rating, when the webhook handler processes it, then a structured log line at `INFO` is written containing `phoneNumberId`, `qualityRating`, and `timestamp`.

---

### Unit Tests Expected (per the Verification section of the design plan)

| Test class | Scenario |
|---|---|
| `WhatsappSenderResolverTest` | resolve with store match (Phase 2 path — stub); resolve by audience match; resolve by isDefault; resolve with flag off returns legacy id |
| `WhatsappInboundEventHandlerTest` | Two webhook fixtures differing only in `phone_number_id` → different `agentName` in context + different session keyed; unknown `phone_number_id` falls back to default + WARN logged |
| `WhatsappSessionRepositoryTest` | Same `from`, two different `phoneNumberId` values → two distinct documents; legacy doc (no `phoneNumberId`) is adopted and stamped on upsert |
| `AiAgentConfigServiceTest` | Cache returns correct config when keyed per agent name; two agents do not overwrite each other |
| `WhatsappLineBootstrapTest` | Idempotent: running bootstrap twice produces exactly two documents, not four |

### Integration Tests Expected (`@SpringBootTest`, embedded Mongo)

| Test class | Scenario |
|---|---|
| `MultiLineWebhookIntegrationTest` | Two-entry webhook fixture (one Driver payload, one Customer payload) → distinct sessions in Mongo, distinct `agentName` in each, correct `@Path phoneId` on mocked `WhatsAppService.sendMessage` calls. Full unscoped module test run (`mvn test` with no `-pl` scoping). |

---

## Data Migration Requirements

**DM-01 (session backfill):** Before the compound unique index `(from, phoneNumberId)` is created on `whatsapp_sessions`, all existing documents must have `phoneNumberId` set to the legacy `whatsappConfig.phoneId()` value. This must happen in the same deployment — implement as a Spring `@Component` with `@EventListener(ApplicationReadyEvent)` that runs the backfill before Bootstrap seeds the line registry, or as a Mongo migration script executed before the application starts. The migration must be idempotent.

**DM-02 (conversation history backfill):** All existing `conversation_histories` documents must have `agentName` set to `driver_support` before the new index `(driverPhoneNumber, agentName)` is created. Same idempotency requirement.

**DM-03 (index ordering):** The deployment sequence must be: (1) backfill `phoneNumberId` on sessions, (2) create compound unique index, (3) run bootstrap. If step 1 is skipped and step 2 runs against documents with a null `phoneNumberId`, the index will reject inserts. The migration component must enforce this order.

**DM-04 (zero-downtime):** The legacy unique index on `from` alone must not be dropped until all documents have been backfilled and the compound index is confirmed live. During the rollout window both indexes may coexist; drop the old `from_1` index in a subsequent release after the compound index is confirmed stable.

---

## Feature Flag and Rollout / Rollback Criteria

**Rollout stages:**

| Stage | `multiLineEnabled` | Driver line `active` | Description |
|---|---|---|---|
| 1 — Deploy off | `false` | `false` | Deploy to production with flag off. Zero behaviour change. Verify startup logs show both lines seeded. |
| 2 — Shadow | `true` | `false` | Enable flag. Driver line is inactive; resolver falls back to default for DRIVER audience. Observe logs to confirm resolver is called and falls back cleanly. No driver traffic moves yet. |
| 3 — Activate | `true` | `true` | Activate Driver line via admin API. All driver outbound templates now use the new number. Inbound driver messages are routed to `driver_support`. Broadcast "Save our new driver support number" template from the new number. |
| 4 — Legacy grace | `true` | `true` | Keep legacy number answering driver messages for 2 weeks with an auto-reply nudge directing drivers to the new number. At end of grace period, remove driver routing from legacy number by audience (the legacy number becomes CUSTOMER + OTP only). |

**Rollback (no deploy required):** Call `PUT /admin/whatsapp-lines/{driverPhoneId}/active` with `{"active": false}`. The resolver immediately falls back to the default line for DRIVER audience. If a code-level rollback is needed, set `whatsapp.cloud.multiLineEnabled = false` in application config or Secrets Manager and redeploy.

**Rollback criteria (trigger if any of these occur):**
- Driver-facing message delivery failure rate on the new number exceeds 5% within the first 24 hours after Stage 3
- Meta quality rating for the Driver number drops to RED within the first week
- Any inbound driver session is routed to the Customer agent (silent routing failure)
- Session duplication or data loss detected in `whatsapp_sessions`

---

## Meta-Side Prerequisites (owned by Lindani Masinga — must be complete before Stage 3 rollout)

These are not code tasks. They are WABA configuration steps that only Lindani can execute in Meta Business Manager (WABA 1938989483522995).

| # | Action | Done? |
|---|---|---|
| M-01 | Add the Driver phone number to WABA 1938989483522995 (verify the number, complete two-step PIN) | — |
| M-02 | Note the `phone_number_id` assigned by Meta to the Driver number; provide to Backend Developer for Secrets Manager entry | — |
| M-03 | Submit display name "iZinga Driver Support" for Meta review | — |
| M-04 | Register the Driver number for Cloud API if required by the WABA setup flow | — |
| M-05 | Confirm existing WABA-scoped driver templates are visible from the new number (one UAT test send before Stage 3) | — |
| M-06 | Add `whatsapp.cloud.driverPhoneId` to Secrets Manager path `izinga-prod` with the `phone_number_id` value from M-02 | — |

The new number starts at 250 business-initiated conversations per 24 hours (Meta default tier for a new number). Plan a warm-up week after Stage 3 before high-volume driver broadcasts.

---

## Dependencies

- `whatsapp.cloud.driverPhoneId` in Secrets Manager `izinga-prod` (Meta-side prerequisite M-06 — blocks Stage 3 but not Stage 1 or 2; bootstrap skips the Driver line row if the key is absent)
- Solution Architect sign-off required before implementation starts: this brief touches `WhatsappSession` (shared model), `ConversationHistory` (shared model), `AiAgentConfig` (shared model), Mongo index changes, and the `WhatsappInboundEventHandler` entry point. Blast radius check is mandatory.
- Security & Compliance review required: session isolation change (compound key), human-handoff scoping change, inbound routing change.
- No changes to `ijudi` Flutter app, `cs-lifestyle`, `izinga-onboarding`, or `izinga-pay` in Phase 1.

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Index migration ordering: if backfill runs after index creation, null `phoneNumberId` values conflict with compound unique constraint | HIGH | DM-03 enforces backfill-first order; test in staging with a non-empty session collection |
| `AiAgentConfigService` cache thrash: with two agents, the old single-entry Optional cache is a correctness defect, not a performance concern | HIGH (must-fix in Phase 1) | REQ-18 mandates a per-agent Map cache |
| Mid-conversation sessions at cutover: drivers with an in-flight conversation at the moment Stage 3 activates will have a session without `phoneNumberId` or with the legacy id | MEDIUM | REQ-14 adoption logic handles this; legacy grace period (Stage 4) means no hard cutover |
| New Driver number starts at 250 BICU/24h tier limit | MEDIUM | Warm-up plan in Rollout; monitor via REQ-22 account_update webhook logs |
| First contact from the new number to a driver must be a template (24-hour service window does not apply to a new conversation from a new number) | MEDIUM | Broadcast template from Driver number as the first outbound in Stage 3; UAT prerequisite M-05 |
| Session isolation regression: incorrect compound key lookup creates duplicate sessions or merges two users' sessions | HIGH | WhatsappSessionRepositoryTest covers same-from-two-lines scenario; integration test in MultiLineWebhookIntegrationTest |
| Phase 2 store isolation: the `X-Store-Id` header on MCP requests must be server-enforced (prompt-only isolation is not acceptable) | HIGH (Phase 2 gate) | Captured in Phase 2 REQ-P2-10; flag to Solution Architect before Phase 2 starts |

**Flag to Solution Architect:** This brief touches `WhatsappSession` unique index, `ConversationHistory` index, `AiAgentConfig` model fields, the `WhatsappInboundEventHandler` routing entry point, and introduces a new Mongo collection `whatsapp_lines`. These are all cross-cutting or shared-model changes. Solution Architect sign-off on the implementation plan is required before the feature branch is opened.

---

## Agent Task Breakdown

| # | Task | Agent | Depends on |
|---|---|---|---|
| T-01 | Solution Architect review: blast-radius check on session model, conversation history model, agent config model, inbound handler, new collection. Produce implementation plan sign-off. | iZinga Solution Architect | none |
| T-02 | Security & Compliance review: session isolation change (compound key), handoff scoping, inbound routing. Produce sign-off. | iZinga Security & Compliance | T-01 |
| T-03 | Add `whatsapp.cloud.driverPhoneId` + `whatsapp.cloud.multiLineEnabled` to config class; add model `WhatsappLine` in izinga-commons (or messaging module per SA plan); add `WhatsappLineRepository` | iZinga Backend Developer | T-01, T-02 |
| T-04 | Implement `WhatsappLineBootstrap` (idempotent seed of two rows from Secrets Manager) | iZinga Backend Developer | T-03 |
| T-05 | Implement `WhatsappSenderResolver` (full resolution chain: store → audience → default → legacy fallback; flag-off short-circuit) | iZinga Backend Developer | T-03, T-04 |
| T-06 | Replace all 19 `whatsappConfig.phoneId()` call sites with `WhatsappSenderResolver.resolve(...)`; consolidate phone normalisation util (REQ-09) | iZinga Backend Developer | T-05 |
| T-07 | Implement migration component: backfill `phoneNumberId` on all `whatsapp_sessions` docs; backfill `agentName` on all `conversation_histories` docs; create compound unique index `(from, phoneNumberId)` on sessions; add `(driverPhoneNumber, agentName)` index on histories | iZinga Backend Developer | T-03 |
| T-08 | Update `WhatsappSession`: add `phoneNumberId`, `agentName`, `storeId` fields; update `upsertSession` to use compound key lookup and adopt legacy docs (REQ-14) | iZinga Backend Developer | T-07 |
| T-09 | Implement `LineContext` record; update `WhatsappInboundEventHandler.handleInbound` to read `metadata.phone_number_id`, resolve line, thread `LineContext`; remove hardcoded `AGENT_NAME` | iZinga Backend Developer | T-05, T-08 |
| T-10 | Update `AiAgentConfig` model: add `audience`, `storeId`, `mcpServers`, `allowedTools`; replace single-entry cache in `AiAgentConfigService` with per-agent Map cache; update seeds for `driver_support` + `customer_support` | iZinga Backend Developer | T-03 |
| T-11 | Update `AiCustomerServiceAgent` to read agent name from `LineContext.agentName`; update human-correction append to use session's `agentName`; move hardcoded MCP URL to config/seed | iZinga Backend Developer | T-09, T-10 |
| T-12 | Implement ADMIN REST controller `GET /admin/whatsapp-lines` + `PUT /admin/whatsapp-lines/{phoneNumberId}/active` | iZinga Backend Developer | T-03, T-04 |
| T-13 | Implement `account_update` webhook handler for monitoring (REQ-22); add per-line structured DEBUG logging to outbound + inbound paths (REQ-23) | iZinga Backend Developer | T-09 |
| T-14 | Update `ConversationHistory`: add `agentName` field (backfill covered by T-07) | iZinga Backend Developer | T-07 |
| T-15 | Write all unit tests (WhatsappSenderResolverTest, WhatsappInboundEventHandlerTest, WhatsappSessionRepositoryTest, AiAgentConfigServiceTest, WhatsappLineBootstrapTest) | iZinga QA & Test Automation | T-05 through T-14 |
| T-16 | Write MultiLineWebhookIntegrationTest (`@SpringBootTest`, embedded Mongo, two-payload fixture, mocked WhatsAppService) | iZinga QA & Test Automation | T-15 |
| T-17 | Full unscoped `mvn test` suite to green (no `-pl` scoping, no exclusions) | iZinga QA & Test Automation | T-16 |
| T-18 | Code review: verify all REQs implemented per brief, build clean, full test suite green, blast-radius compliance | iZinga Code Reviewer | T-17 |

---

## Phase 2 — Per-Store Agent and Number (Not in This Release)

The following requirements are specified here so Phase 1 developers do not make structural decisions that block Phase 2. Do not implement any of these in Phase 1. A separate Feature Brief (WA-LINES-02) will be issued before Phase 2 starts.

**REQ-P2-01:** Add an admin endpoint `POST /admin/whatsapp-lines/store` that provisions a new `WhatsappLine` row with `audience = STORE` and a given `storeId`, and clones the `store_support_default` agent config as a new `ai_agent_configs` document keyed `store_support_<storeId>`, with the store's name, menu summary, and business hours injected into the system prompt.

**REQ-P2-02:** `WhatsappSenderResolver.resolve(STORE, storeId)` must return the store-specific line when one exists with a matching `storeId` and `active = true`.

**REQ-P2-03:** Inbound messages arriving on a STORE line must have `LineContext.storeId` set. `AiCustomerServiceAgent` (or a new `StoreAiAgent`) must include `storeId` in every MCP request as header `X-Store-Id`.

**REQ-P2-04:** The `/mcp` endpoint in the order manager must enforce `X-Store-Id` at the server level — reject or scope all tool calls to the store identified in the header. Prompt-only store isolation is not acceptable.

**REQ-P2-05:** Firestore `ChatSession` documents for store lines must carry `storeId` and `phoneNumberId`. Session lookup by the human-handoff UI must be keyed `(customerId, phoneNumberId)`.

**REQ-P2-06:** `izinga-onboarding` Firestore rules must be updated so `STORE_ADMIN` can only read and write `ChatSession` documents where `chatSession.storeId == storageService.userProfile.storeId`. This is an `izinga-onboarding` task, not an `ijudi-api` task.

**REQ-P2-07:** The `izinga-onboarding` chat sessions UI must filter by `storeId` so a `STORE_ADMIN` only sees sessions from customers who wrote to their store's number.

**REQ-P2-08:** Reply path in `FirestoreToWhatsappController` must check that the replying `STORE_ADMIN`'s `storeId` matches `chatSession.storeId` before sending the reply. Mismatched `storeId` must return HTTP 403.

**REQ-P2-09:** Store agent system prompt template must inject `{storeName}`, `{storeMenu}`, `{businessHours}`, and `{storeLocation}` at agent resolution time, not at seed time.

**REQ-P2-10:** A Meta number-onboarding runbook per store must be produced by the DevOps agent before Phase 2 goes live. Each store number requires its own WABA registration, display name approval, and tier warm-up.

---

## Marketing Trigger

No — Phase 1 is an infrastructure and routing change with no customer-visible UI. The driver-facing change (new WhatsApp number) requires a targeted in-app or WhatsApp broadcast to drivers notifying them of the new Driver Support number. This is an operational communication owned by Hloniphani, not a marketing campaign. Brief the Marketing Strategist only if a broader "we improved driver support" campaign is desired post-launch.

---

## Definition of Done

This feature is complete when ALL of the following are true:

1. All REQ-01 through REQ-23 have been implemented and all acceptance criteria AC-01 through AC-13 pass.
2. All unit tests listed in the "Unit Tests Expected" table pass.
3. `MultiLineWebhookIntegrationTest` passes against embedded Mongo with the two-payload fixture.
4. Full unscoped `mvn test` suite passes with zero failures and zero test exclusions.
5. `mvn package -DskipTests` (or equivalent build command) completes with zero errors.
6. The iZinga Code Reviewer has issued a PASS verdict.
7. The iZinga Solution Architect has issued a sign-off (T-01).
8. The iZinga Security & Compliance agent has issued a sign-off (T-02).
9. All Meta-side prerequisites M-01 through M-06 are confirmed complete by Lindani before Stage 3 (line activation) proceeds.
10. Staging deployment with `multiLineEnabled = true` and the Driver line active has been validated: a test driver message received on the Driver number is answered by `driver_support`; an OTP send uses the legacy number; a customer message on the Customer number is answered by `customer_support`.
11. The migration component has been confirmed idempotent on staging: two application restarts produce exactly two `whatsapp_lines` rows, not four or more.
12. The feature branch has been merged to `develop` via a pull request with Code Reviewer PASS, Solution Architect sign-off, and Security & Compliance sign-off attached.

---

*Brief authored by iZinga Product Owner · PO-APPROVED — authorised by Lindani Masinga 2026-09-19*
