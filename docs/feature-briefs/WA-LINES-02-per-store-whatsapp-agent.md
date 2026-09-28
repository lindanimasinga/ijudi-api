# Feature Brief: WA-LINES-02 — Per-Store WhatsApp Line and Dedicated AI Agent (Phase 2)

**Feature name:** Per-Store WhatsApp Line and Dedicated AI Agent

**Status:** Draft — awaiting Lindani Masinga approval before moving to Approved for Implementation

**Requested by:** Lindani Masinga

**Business objective:** WA-LINES-01 separated the Driver and Customer lines. WA-LINES-02 completes the architecture by giving each store its own WhatsApp number and its own AI agent scoped exclusively to that store's menu, hours, and order data. Today a store like RxNova24 (id `77db94fe-6cc6-4da9-bc62-5fa26045389c`) has a `mobileNumber` field on its profile — that is a display/contact field only, not a real WhatsApp capability. Phase 2 makes it real: a customer who scans RxNova24's QR or taps their WhatsApp link enters an AI conversation that knows RxNova24's products, pricing, and business hours — not a generic iZinga agent. Dedicated lines ship free for the initial pilot; billing and plan-gating are deferred to a future brief (TIER-01, undated). Pilot rollout is manual and ADMIN-driven — RxNova24 first, additional stores at Lindani's discretion.

**Audience:** Store owners (direct beneficiaries — ADMIN-provisioned, no plan gate); customers of those stores (end users); iZinga ADMIN (operators)

**Products affected:**
- `ijudi-api` — line provisioning endpoint, store agent config cloning, store-scoped MCP tool enforcement, inbound session routing (primary)
- `izinga-onboarding` — Firestore security rules, chat sessions UI query filter, reply-path store validation (secondary)

**Out of scope:**
- Store-owned Meta Business accounts (iZinga provisions and owns every store subaccount in v1)
- Self-serve store onboarding for the WhatsApp line (admin-provisioned only in v1; no store-owner self-serve UI)
- Any plan/billing/entitlement gating — TIER-01 (premium-tier billing and `StoreProfile.plan` field) is explicitly not being built now; provisioning a dedicated line has no plan check of any kind
- Conversation volume cap enforcement per store (deferred; irrelevant without billing to protect margin)
- Custom domain / custom storefront (separate scope, cut from v1 regardless of TIER-01 status)
- Any change to the Flutter `ijudi` app, `cs-lifestyle`, `furniture-delivery-app`, or `izinga-pay`
- A new chat UI in `izinga-onboarding` for STORE_ADMIN beyond scoping the existing Firestore-based chat interface to the admin's own store
- Automated Meta tier warm-up or Meta template submission automation (manual runbook only in v1)
- Changing the AI provider away from OpenAI Responses API
- OTP — OTP remains on the legacy CUSTOMER line

---

## Context: Phase 1 Foundations This Brief Builds On

WA-LINES-01 (merged to `develop`, not yet released to `main`) already established:
- The `WhatsappLine` model with a `storeId` field (nullable; null in Phase 1)
- `WhatsappSenderResolver.resolve(audience, storeId)` — the store-path resolution (step 2 in the chain) is already coded but never exercised in Phase 1
- `LineContext` record with `storeId` field — populated as null for DRIVER and CUSTOMER lines
- `AiAgentConfig` with `storeId`, `mcpServers`, and `allowedTools` fields — seeded for `driver_support` and `customer_support`; a `store_support_default` seed is specified here for Phase 2
- `WhatsappLineBootstrap` — idempotent startup seeding

Phase 2 does not rewrite any of the above. It activates the store-path branches that Phase 1 left null.

## Architectural Constraint from Gate Review ADR-020 (SA-4)

The original Phase 2 sketch in WA-LINES-01 (REQ-P2-03 and REQ-P2-04) proposed passing store scope via an `X-Store-Id` HTTP header on MCP requests. This is **not implementable** with Spring AI 1.1.5 `@Tool` methods — `@Tool` methods are invoked by the Spring AI framework without access to raw HTTP request context.

**The approved design for Phase 2 is:**
- Every store-scoped MCP tool accepts an explicit `storeId` parameter in the tool call arguments.
- The MCP server maintains a per-session `permittedStoreId` established at session initialisation.
- On every store-scoped tool call, the server validates that the `storeId` argument matches the session's `permittedStoreId`. Mismatches return a tool-call error with `errorCode = STORE_SCOPE_VIOLATION`.

This is the only acceptable enforcement design. Prompt-only store isolation is explicitly prohibited.

---

## User Stories

**US-01 (Store customer):** As a customer of RxNova24, when I message their dedicated WhatsApp number, I am answered by an AI agent that knows RxNova24's menu, prices, and business hours — not a generic iZinga agent.

**US-02 (Store customer — multi-store):** As a customer who has both placed a food order (Customer line) and messaged a store's dedicated line, my two chat sessions are independent and neither bleeds into the other.

**US-03 (Store owner):** As a store owner with a provisioned dedicated line, when a customer messages that number and the AI cannot resolve the query, the conversation can be handed off to me for a human reply. I can only see and reply to sessions from my own store's line.

**US-04 (iZinga ADMIN):** As an ADMIN, I can provision a dedicated WhatsApp line for any store, monitor its status, and deactivate it without a code deploy.

**US-05 (iZinga ADMIN):** As an ADMIN, I can verify that a store's dedicated line is correctly configured (line active, agent config exists, last inbound timestamp) via a single API call.

---

## Requirements

### Group A — Store Line Provisioning

**REQ-01:** Implement `POST /admin/whatsapp-lines/store`.

Request body: `{ "phoneNumberId": String, "displayNumber": String, "storeId": String }`.

Server must validate:
- `storeId` must exist in the `stores` collection — return HTTP 404 if not found.
- `phoneNumberId` must not already exist in `whatsapp_lines` — return HTTP 409 (Conflict) if duplicate.

There is no plan or billing gate. Any store can receive a dedicated line via this endpoint; provisioning is a manual ADMIN decision. When TIER-01 billing is eventually built, a future brief will retrofit the entitlement check onto this endpoint and handle stores that already have a manually-provisioned line at that point.

On success, the server must:
1. Insert a `WhatsappLine` document: `audience = STORE`, `storeId` set, `active = false` (admin activates separately via the existing `PUT /admin/whatsapp-lines/{phoneNumberId}/active` endpoint), `agentName = "store_support_<storeId>"`.
2. Clone the `store_support_default` document from `ai_agent_configs` as a new document keyed `store_support_<storeId>`. The clone carries the template system prompt with placeholders intact — they are resolved at invocation time, not here.
3. Write an audit record to `whatsapp_line_audit`: `operatorUid`, `action = PROVISION`, `storeId`, `phoneNumberId`, `timestamp`.

**REQ-02:** Implement `DELETE /admin/whatsapp-lines/store/{storeId}`.

- Requires the store's `WhatsappLine` to have `active = false` before deletion is accepted — return HTTP 409 with message "deactivate line before deprovisioning" if `active = true`.
- Soft-deletes (sets `deleted = true`, `deletedAt`, `deletedBy`) both the `WhatsappLine` document and the `ai_agent_configs` clone `store_support_<storeId>`. Hard delete is not permitted — audit trail must be preserved.
- Writes an audit record: `action = DEPROVISION`.

**REQ-03:** Implement `GET /admin/whatsapp-lines/store/{storeId}`.

Returns: `{ phoneNumberId, displayNumber, active, agentName, agentConfigExists, lastInboundTimestamp (nullable) }`.

`agentConfigExists` is a boolean derived from whether a non-deleted `ai_agent_configs` document keyed `store_support_<storeId>` exists.

**REQ-04:** Seed `store_support_default` in `ai_agent_configs` on application startup (idempotent — upsert by `agentName`):

```
agentName:    "store_support_default"
audience:     STORE
storeId:      null
active:       false
mcpServers:   [{ url: "https://api.izinga.co.za/mcp" }]
allowedTools: [list of store-scoped tools — SA to confirm final list in T-01]
systemPrompt: <template with {storeName}, {storeMenu}, {businessHours}, {storeLocation} placeholders>
```

The `active = false` flag marks this as a template, not a live agent. A live agent is always a named clone (`store_support_<storeId>`), never the default template itself.

### Group B — Inbound Routing and Session Keying (extending WA-LINES-01)

**REQ-05:** When `WhatsappInboundEventHandler.handleInbound` resolves a STORE-line `WhatsappLine`, it must populate `LineContext.storeId` with `WhatsappLine.storeId`. The rest of the `LineContext` propagation chain (already in place from WA-LINES-01) carries `storeId` through to the agent.

**REQ-06:** The `upsertSession` path must write `storeId` to the `WhatsappSession` document whenever `LineContext.storeId` is non-null. This is an additive write — the field was already added to the model in WA-LINES-01.

**REQ-07:** Firestore `ChatSession` documents created for STORE-line sessions must carry a `storeId` field. The Firebase write path (wherever `ijudi-api` creates or updates a ChatSession in Firestore) must include `storeId` from `LineContext.storeId` when `LineContext.audience = STORE`.

### Group C — Store-Scoped MCP Tool Enforcement (SA-4 corrected design)

**REQ-08:** Every store-scoped MCP tool in the `/mcp` endpoint of the order manager must accept an explicit `storeId` String parameter in its tool call arguments. This applies to all tools that read or modify store-specific data (menu, orders, business hours, store info, etc.). The Solution Architect will confirm the exhaustive list of tools requiring this change as part of T-01.

**REQ-09:** The MCP server must maintain per-session state that includes a `permittedStoreId` field. When an AI agent establishes an MCP session for a STORE-line interaction, it must present the `storeId` from `LineContext.storeId` as the session's permitted store. The mechanism for binding `permittedStoreId` to the session (whether via Spring AI's MCP session API or a service-layer wrapper) is to be determined by the Solution Architect in T-01.

**REQ-10:** For every store-scoped tool call, the MCP server must validate that the `storeId` tool argument matches the session's `permittedStoreId`. A mismatch must:
- Return a tool-call error (not an HTTP error — Spring AI `@Tool` methods return tool errors, not HTTP responses) containing: `errorCode = STORE_SCOPE_VIOLATION`, `requestedStoreId`, `permittedStoreId`.
- Not return any data for the requested store.
- Log a `WARN` entry with the violation details for CloudWatch monitoring.

The agent must not retry the tool call with a corrected `storeId`.

**REQ-11:** Prompt-only store isolation is explicitly prohibited. A system prompt that instructs the model to "only return data for store X" does not satisfy REQ-10. The server-side enforcement in REQ-09 and REQ-10 is mandatory and is the only acceptable isolation mechanism.

**REQ-12:** `AiCustomerServiceAgent` (or a new `StoreAiAgent` per SA T-01 recommendation) must pass `LineContext.storeId` when establishing the MCP session for a STORE-line interaction. If `LineContext.storeId` is null for a STORE-line interaction (configuration error — the line exists but has no `storeId`), the agent must refuse to start the conversation, log an `ERROR`, and return a "service temporarily unavailable" message to the customer.

### Group D — Store Agent System Prompt Injection

**REQ-13:** At agent invocation time (not at agent-config seed time or clone time), the system prompt for a `store_support_<storeId>` agent must have its four placeholders resolved from live database state:

| Placeholder | Source |
|---|---|
| `{storeName}` | `StoreProfile.name` |
| `{storeMenu}` | Structured list of `StoreProduct` names, descriptions, and prices for the store's active products |
| `{businessHours}` | `StoreProfile.businessHours` (or equivalent field — SA to confirm field name) |
| `{storeLocation}` | `StoreProfile.address` or `StoreProfile.location` (SA to confirm) |

The resolved system prompt is ephemeral — it must not be persisted back to `ai_agent_configs`. The template in `ai_agent_configs` remains unchanged so every future invocation resolves fresh context.

**REQ-14:** If the store's product catalogue is unavailable at resolution time (store has no active products), `{storeMenu}` must resolve to the fallback string: `"Menu currently unavailable — please visit our store or contact us for details."` The agent must start successfully with this fallback.

**REQ-15:** If `{businessHours}` or `{storeLocation}` fields are null on the StoreProfile, each unresolvable placeholder must be replaced with an appropriate "not available" fallback string. The agent must not fail to start because a single field is null.

**REQ-16:** The product list injected into `{storeMenu}` must be capped at a maximum product count determined by the Solution Architect in T-01 (to prevent system prompt overflow beyond the model's context window). If the store has more active products than the cap, the injected list must prioritise by `displayOrder` or `mostOrderedLast30Days` (SA to recommend the sort key). A `DEBUG` log must record when the cap is applied and how many products were omitted.

### Group E — izinga-onboarding: Store Admin Chat Session Isolation

**REQ-17:** `izinga-onboarding` Firestore security rules must be updated so that:
- A user with Firebase custom claim `profileRole = STORE_ADMIN` can only read and write `ChatSession` documents where `chatSession.storeId == request.auth.token.storeId`.
- A user with `profileRole = ADMIN` retains full read/write access to all `ChatSession` documents.
- All other roles are unchanged.

The rules change must be validated in a Firestore emulator environment before production deployment. Testing in emulator is mandatory — this is not a code change that can be verified only in production.

**REQ-18:** The `izinga-onboarding` chat sessions UI must add a `storeId` query filter for STORE_ADMIN users. The filter must be a Firestore server-side query clause (`where("storeId", "==", userProfile.storeId)`), not a client-side filter applied after fetching all sessions. A client-side-only filter does not meet this requirement.

**REQ-19:** The reply path in `FirestoreToWhatsappController` must validate that the replying user's `storeId` matches `chatSession.storeId` before sending a reply:
- If the user's role is `STORE_ADMIN` and their `storeId` does not match `chatSession.storeId`, return HTTP 403.
- If the user's role is `ADMIN`, the check is bypassed.
- The storeId for the replying user must be read from the authenticated JWT, not from a request body parameter.

### Group F — Monitoring

**REQ-20:** Extend the `account_update` webhook log entry (from WA-LINES-01 REQ-22) to include `storeId` when the affected `phone_number_id` belongs to a STORE line. This enables per-store quality monitoring in CloudWatch.

---

## Acceptance Criteria

**AC-01 (provision — happy path):** Given a valid `storeId` that exists in the `stores` collection and a `phoneNumberId` not yet in the registry, when an ADMIN calls `POST /admin/whatsapp-lines/store`, then: (a) a `WhatsappLine` document exists with `audience = STORE`, `storeId` set, `active = false`, `agentName = "store_support_<storeId>"`; (b) an `ai_agent_configs` document keyed `store_support_<storeId>` exists as a clone of `store_support_default` with placeholders intact; (c) an audit record exists in `whatsapp_line_audit`. No plan or billing gate is applied.

**AC-02 (provision — duplicate guard):** Given a `phoneNumberId` already in `whatsapp_lines`, when an ADMIN calls `POST /admin/whatsapp-lines/store` with that `phoneNumberId`, then the API returns HTTP 409 and no new documents are created.

**AC-03 (provision — storeId not found):** Given a `storeId` that does not exist in the `stores` collection, when an ADMIN calls `POST /admin/whatsapp-lines/store`, then the API returns HTTP 404.

**AC-04 (inbound routing — STORE line):** Given `multiLineEnabled = true` and a webhook payload whose `metadata.phone_number_id` matches a STORE line with `storeId = X`, when `handleInbound` processes the message, then `LineContext.storeId = X`, `LineContext.audience = STORE`, `LineContext.agentName = "store_support_X"`, and a `WhatsappSession` is created or retrieved using the compound key `(from, phoneNumberId)` inherited from WA-LINES-01.

**AC-05 (sender resolver — store path):** Given `multiLineEnabled = true` and a STORE line with `storeId = X` and `active = true`, when `WhatsappSenderResolver.resolve(STORE, X)` is called, then it returns the `phoneNumberId` of that store's dedicated line.

**AC-06 (MCP tool scope — permitted store):** Given an MCP session established with `permittedStoreId = X`, when the agent calls a store-scoped tool with `storeId = X`, then the tool executes and returns data for store X.

**AC-07 (MCP tool scope — violation):** Given an MCP session established with `permittedStoreId = X`, when the agent calls a store-scoped tool with `storeId = Y` (Y ≠ X), then the MCP server returns a tool-call error with `errorCode = STORE_SCOPE_VIOLATION` and no data for store Y is returned or logged at the agent level.

**AC-08 (MCP tool scope — prompt cannot bypass server):** Given a system prompt instructing the model to "act as the agent for store Y" and a session with `permittedStoreId = X`, when the agent calls a store-scoped tool with `storeId = Y`, then the MCP server still rejects the call with `STORE_SCOPE_VIOLATION`. The system prompt cannot override server-side enforcement.

**AC-09 (system prompt injection — all placeholders resolved):** Given a `store_support_<storeId>` agent invoked for store X, when the system prompt is resolved at invocation time, then `{storeName}`, `{storeMenu}`, `{businessHours}`, and `{storeLocation}` are all replaced with store X's live database values. The `ai_agent_configs` template document is unchanged after the invocation.

**AC-10 (system prompt injection — empty catalogue fallback):** Given store X has no active products, when the agent is invoked, then `{storeMenu}` resolves to the defined fallback string and the agent starts without error.

**AC-11 (Firestore — storeId field populated):** Given an inbound message on a STORE line with `storeId = X`, when the `ChatSession` document is written to Firestore, then the document carries `storeId = X`.

**AC-12 (Firestore rules — STORE_ADMIN isolation):** Given a user with `profileRole = STORE_ADMIN` and `storeId = X`, when they attempt to read a `ChatSession` document with `storeId = Y` (Y ≠ X) directly via the Firestore SDK, then Firestore returns a permission-denied error. Verified in the Firestore emulator.

**AC-13 (onboarding UI — server-side query filter):** Given a STORE_ADMIN for store X is logged into the `izinga-onboarding` chat sessions screen, when the session list loads, then the Firestore query includes `where("storeId", "==", "X")` as a server-side clause, and only sessions with `storeId = X` are returned.

**AC-14 (reply path — storeId mismatch):** Given a STORE_ADMIN for store X sends a reply request to a `ChatSession` with `storeId = Y` (Y ≠ X), when `FirestoreToWhatsappController` processes the request, then the API returns HTTP 403 and no message is sent.

**AC-15 (store_support_default seed):** Given the application starts, when the `ai_agent_configs` collection is seeded, then a document with `agentName = "store_support_default"` exists, has `active = false`, and contains all four placeholders (`{storeName}`, `{storeMenu}`, `{businessHours}`, `{storeLocation}`) in the `systemPrompt` field. Running startup twice does not create duplicate documents.

**AC-16 (line status endpoint):** Given a store line has been provisioned for store X, when an ADMIN calls `GET /admin/whatsapp-lines/store/X`, then the response includes `phoneNumberId`, `displayNumber`, `active`, `agentName`, and `agentConfigExists = true`.

**AC-17 (deprovision — requires inactive):** Given a store line with `active = true`, when an ADMIN calls `DELETE /admin/whatsapp-lines/store/{storeId}`, then the API returns HTTP 409 with message "deactivate line before deprovisioning". No documents are deleted.

**AC-18 (deprovision — soft delete):** Given a store line with `active = false`, when an ADMIN calls `DELETE /admin/whatsapp-lines/store/{storeId}`, then the `WhatsappLine` document is soft-deleted (`deleted = true`), the `ai_agent_configs` clone is soft-deleted, and an audit record with `action = DEPROVISION` is written. No document is hard-deleted.

---

## Unit Tests Expected

| Test class | Scenario |
|---|---|
| `StoreLineProvisioningServiceTest` | AC-01 (happy path — all three writes, no plan gate); AC-02 (duplicate phoneNumberId); AC-03 (storeId not found) |
| `StoreScopedMcpToolTest` | AC-06 (permitted storeId passes); AC-07 (wrong storeId rejected); AC-08 (prompt cannot bypass) |
| `StoreContextResolverTest` | AC-09 (all four placeholders resolved from DB); AC-10 (empty catalogue fallback); null businessHours fallback (REQ-15); product cap applied and logged (REQ-16) |
| `WhatsappSenderResolverTest` (extend from WA-LINES-01) | AC-05 (STORE path returns store's phoneNumberId) |
| `FirestoreToWhatsappControllerTest` | AC-14 (STORE_ADMIN storeId mismatch → 403); ADMIN role bypasses check |
| `AiAgentConfigServiceTest` (T-03 change) | `getMcpToolsForAgent("store_support_X")` returns the `mcpServers` list from the cloned config document, not a hardcoded value; `getMcpToolsForAgent("driver_support")` still returns the correct server after the model change |

## Integration Tests Expected

| Test class | Scenario |
|---|---|
| `StoreLineWebhookIntegrationTest` | Two-entry fixture: one STORE-line payload (storeId = X) + one CUSTOMER-line payload → distinct sessions, `LineContext.storeId = X` for store payload, `null` for customer payload; MCP tool call with wrong storeId returns STORE_SCOPE_VIOLATION; ChatSession Firestore write carries storeId. Full unscoped `mvn test` must pass. |

---

## Data Migration Requirements

**DM-01 (no new index migration):** WA-LINES-01 already established the compound unique index `(from, phoneNumberId)` on `whatsapp_sessions`. No new index migrations are required for WA-LINES-02. The `storeId` field on `WhatsappSession`, `WhatsappLine`, and `AiAgentConfig` was already added in WA-LINES-01.

**DM-02 (store_support_default seed is additive):** The `store_support_default` seed is an upsert on `agentName` — it is additive to the existing `driver_support` and `customer_support` seed. No migration of existing documents is needed.

**DM-03 (Firestore ChatSession backfill — not required):** Existing ChatSession documents in Firestore were created before STORE lines existed and do not carry `storeId`. The Firestore rules change (REQ-17) must not apply retroactively to existing sessions in a way that breaks the ADMIN user's ability to read them. Confirm with SA that the rules for ADMIN role retain full access regardless of the `storeId` field's presence.

---

## Meta-Side Prerequisites Per Store (owned by Hloniphani Manzi / Operations — must be complete before any store line is activated)

These are not code tasks. They must be completed per store in Meta Business Manager before calling `PUT /admin/whatsapp-lines/{phoneNumberId}/active` for that store.

| # | Action |
|---|---|
| M-01 | Add the store's phone number to WABA as a subaccount (iZinga-owned). Complete verification and 2-step PIN. |
| M-02 | Note the `phone_number_id` assigned by Meta; provide to ADMIN for the provisioning API call. |
| M-03 | Submit display name for Meta review (e.g. "RxNova24 by iZinga"). Approval may take 2–5 business days. |
| M-04 | Register the number for Cloud API if required by the WABA setup flow. |
| M-05 | Confirm the iZinga WABA-scoped templates are visible from the new number; perform one UAT test send before activation. |
| M-06 | Note: each new store number starts at Meta's 250 BICU/24h default tier. Plan a warm-up week before any high-volume broadcast from the store line. |
| M-07 | The first outbound from the store line to any customer must be a template — there is no session window until the customer has replied. The store agent must not send a free-form first message. |

**DevOps runbook (REQ-20 / T-17)** must document steps M-01 through M-07 as the per-store line provisioning runbook, analogous to the WA-LINES-01 pre-deploy runbook.

---

## Dependencies

| Dependency | Status | Owner | Blocking? |
|---|---|---|---|
| WA-LINES-01 deployed to production with `multiLineEnabled = true` | On `develop`, not yet released. Blocked on Driver line phone ID and app secret provisioning. | Backend Developer + DevOps + Lindani (Meta) | YES — WA-LINES-02 cannot be activated before WA-LINES-01 is live |
| TIER-01 (plan field / billing) | NOT being built now — explicitly descoped by Lindani 26 Sep 2026. No plan check on the provisioning endpoint. When TIER-01 is eventually built, it will need to retrofit entitlement enforcement onto stores that already have a manually-provisioned line. | Future brief | NO — removed as a dependency of this brief |
| Solution Architect blast-radius sign-off | Not started | iZinga Solution Architect | YES — MCP session scoping design (REQ-09) is unresolved; SA T-01 must complete before implementation |
| Security & Compliance sign-off | Not started | iZinga Security & Compliance | YES |
| Spring AI 1.1.5 MCP session state API | Not yet assessed | iZinga Solution Architect | YES — per-session `permittedStoreId` binding mechanism is the highest architectural risk in this brief |
| `AiAgentConfig` model fields (`audience`, `storeId`, `mcpServers`, `allowedTools`) and `getMcpToolsForAgent()` rewrite (ADR-020A) | NOT in production. Fields were specified in WA-LINES-01 REQ-17 but were never consumed at runtime and are absent from the live `driver_support` document. Phase 2 implementation cannot proceed without this change — T-04 (seed) cannot write `mcpServers`, and T-08 (MCP session binding) cannot read per-agent server config. Covered by T-03. | iZinga Backend Developer | YES — blocks T-04 and T-08 |
| Meta WABA subaccount per store | Not started | Hloniphani / Operations | YES (for activation, not for implementation) |
| `customer_support` agent config in production | NOT IN PRODUCTION — `ai_agent_configs` currently has exactly one document (`driver_support`). `customer_support` was specified in WA-LINES-01 but has not been seeded because WA-LINES-01 has not deployed to production yet (bootstrap only runs on startup). This is most likely expected behaviour, not a bug. Non-blocking for WA-LINES-02: `store_support_default` uses the same bootstrap mechanism and will be seeded on first WA-LINES-02 startup. Lindani has not requested investigation; resolve naturally when WA-LINES-01 ships. | Backend Developer / WA-LINES-01 deploy | NO — but must be verified after WA-LINES-01 deploys to production before treating `ai_agent_configs` seeding as fully proven for all agent types |

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Spring AI 1.1.5 does not expose per-session custom state on its McpServer | HIGH | SA T-01 must assess before implementation. If the Spring AI McpServer API does not support per-session custom fields, the enforcement must move to a service-layer wrapper above the MCP call, or the project must evaluate upgrading to a Spring AI version that supports it. Do not start REQ-08 through REQ-12 without SA sign-off. |
| Meta WABA operational overhead at scale | MEDIUM | At 10+ stores the Meta management overhead (quality monitoring, tier limits, template approvals per subaccount) is non-trivial. Pilot is manual and ad-hoc at Lindani's discretion. At 20+ stores, a dedicated ops task owner is needed regardless of whether billing is active. |
| Store product catalogue size exceeding model context window | MEDIUM | REQ-16 mandates a product cap. SA must advise on the cap value and sort key in T-01. Mitigation: cap + sort by `mostOrderedLast30Days`; a future brief can add a RAG-based menu lookup for large catalogues. |
| Firestore rules change breaks existing ADMIN view | MEDIUM | Rules must be validated in Firestore emulator before production deploy (REQ-17). Test scenario: ADMIN reads a session with no `storeId` field — confirm rules do not block this. |
| `store_support_default` prompt quality | MEDIUM | If the template prompt is weak, every store agent is weak from day one. Recommend Lindani reviews and approves the `store_support_default` system prompt template before it is seeded in production. This is a content review, not a code gate, but it affects customer-facing quality for every PRO store. |
| STORE_ADMIN reply 403 regression | LOW | REQ-19 adds a new 403 path to `FirestoreToWhatsappController`. The existing ADMIN role must be verified to bypass this check. Covered in `FirestoreToWhatsappControllerTest`. |

**Flag to Solution Architect (required before implementation starts):**

1. **REQ-09 / REQ-10 — MCP per-session permittedStoreId binding**: confirm the mechanism in Spring AI 1.1.5. If the McpServer API does not support custom per-session state, propose the enforcement design (service-layer wrapper, JWT-scoped tool call validation, or another pattern). This is the single highest-risk design point in the brief.

2. **REQ-13 / REQ-16 — product catalogue injection + context window sizing**: advise on the maximum product count cap and the preferred sort key for large catalogues. Assess whether the current `StoreProduct` schema has a `displayOrder` or order-frequency field usable as the sort key.

3. **izinga-onboarding Firestore rules blast radius**: confirm that changing STORE_ADMIN read scope does not affect the existing behaviour of the `izinga-onboarding` chat UI for ADMIN users, or any other role that reads ChatSession documents.

4. **StoreAiAgent vs AiCustomerServiceAgent extension**: advise whether a new `StoreAiAgent` class is cleaner or whether `AiCustomerServiceAgent` should be extended with store-path logic. Recommendation to SA: a new class is cleaner and avoids making the CUSTOMER-path code bear store-specific branching.

---

## Agent Task Breakdown

| # | Task | Agent | Depends on |
|---|---|---|---|
| T-01 | SA review: (a) MCP per-session permittedStoreId binding design in Spring AI 1.1.5; (b) StoreAiAgent vs AiCustomerServiceAgent extension; (c) product catalogue injection cap and sort key; (d) Firestore rules blast-radius on izinga-onboarding. Produce implementation plan sign-off (ADR). | iZinga Solution Architect | none |
| T-02 | Security & Compliance review: MCP store-scope enforcement (REQ-08–REQ-12); Firestore STORE_ADMIN rules (REQ-17); reply-path 403 guard (REQ-19); store system prompt injection (SEC-04 prompt-injection risk applies). Produce sign-off. | iZinga Security & Compliance | T-01 |
| T-03 | **[ADR-020A prerequisite — must complete before any store agent code]** Add `audience`, `storeId`, `mcpServers` (List), and `allowedTools` (List) fields to `AiAgentConfig` model. Rewrite `getMcpToolsForAgent()` — currently returns a hardcoded single-server list — to read from `AiAgentConfig.mcpServers` for the named agent. Update `driver_support` and `customer_support` seed upserts to populate `mcpServers = [{url: "https://api.izinga.co.za/mcp"}]` and `audience` values (these were specified in WA-LINES-01 REQ-17 but were never consumed at runtime and were left off the live seed; Phase 2 requires them). WA-LINES-01 is unaffected — `audience` routing lives entirely on `WhatsappLine`/`LineContext` for Phase 1. Phase 2 store agents require this change because each store agent will have its own MCP server entry and the hardcoded implementation cannot support that at all. | iZinga Backend Developer | T-01, T-02 |
| T-04 | Seed `store_support_default` in `ai_agent_configs`. **Mechanism:** model the upsert/idempotency pattern after whatever mechanism keeps `driver_support` alive and updatable in production — that infrastructure is proven (9 in-place version updates confirmed). Do not rebuild it; extend it. **Content:** the `store_support_default` document must be authored from scratch. The production `driver_support` document is not a reusable base — it has a fully hardcoded system prompt with no placeholder variables, and no `audience` or `storeId` fields. The new document is structurally different: `active = false` (template, not live), `audience = STORE`, `storeId = null`, `mcpServers = [{url: "https://api.izinga.co.za/mcp"}]` (field now available on the model after T-03), and a `systemPrompt` containing the four placeholder variables (`{storeName}`, `{storeMenu}`, `{businessHours}`, `{storeLocation}`) in clearly delimited blocks. The Backend Developer must author the prompt template text from scratch, then route the draft to Lindani for content approval (Decision 4) before the document is seeded in production. | iZinga Backend Developer | T-01, T-02, T-03 |
| T-05 | Implement `POST /admin/whatsapp-lines/store` (REQ-01): storeId existence check (404 if not found), duplicate phoneNumberId check (409), WhatsappLine insert, ai_agent_configs clone, audit record. No plan or billing gate — any store is provisionable by ADMIN action. | iZinga Backend Developer | T-04 |
| T-06 | Implement `DELETE /admin/whatsapp-lines/store/{storeId}` (REQ-02): active check, soft-delete of line + agent config clone, audit record | iZinga Backend Developer | T-05 |
| T-07 | Implement `GET /admin/whatsapp-lines/store/{storeId}` (REQ-03): status response | iZinga Backend Developer | T-05 |
| T-08 | Implement MCP per-session permittedStoreId binding (REQ-09) and store-scope validation on all store-scoped `@Tool` methods or service-layer wrapper (per SA T-01 plan) (REQ-10, REQ-11) | iZinga Backend Developer | T-01, T-02, T-03 |
| T-09 | Implement `StoreContextResolver`: resolves `{storeName}`, `{storeMenu}`, `{businessHours}`, `{storeLocation}` from DB at invocation time; product cap enforced; fallbacks for null/empty fields (REQ-13–REQ-16) | iZinga Backend Developer | T-04 |
| T-10 | Implement `StoreAiAgent` (or extend `AiCustomerServiceAgent` per SA T-01 plan): reads `storeId` from `LineContext`, passes `permittedStoreId` to MCP session, calls `StoreContextResolver` for prompt injection, guard on null `storeId` (REQ-12) | iZinga Backend Developer | T-08, T-09 |
| T-11 | Update `WhatsappInboundEventHandler`: populate `LineContext.storeId` for STORE lines (REQ-05); route to `StoreAiAgent` / extended path | iZinga Backend Developer | T-10 |
| T-12 | Update `upsertSession`: write `storeId` to `WhatsappSession` when `LineContext.storeId` is non-null (REQ-06) | iZinga Backend Developer | T-11 |
| T-13 | Update Firestore ChatSession write path: include `storeId` field for STORE-line sessions (REQ-07) | iZinga Backend Developer | T-11 |
| T-14 | Update `FirestoreToWhatsappController`: add STORE_ADMIN storeId mismatch guard returning HTTP 403 (REQ-19) | iZinga Backend Developer | T-13 |
| T-15 | Extend `account_update` webhook log: include `storeId` for STORE-line quality events (REQ-20) | iZinga Backend Developer | T-11 |
| T-16 | Write unit tests: `StoreLineProvisioningServiceTest`, `StoreScopedMcpToolTest`, `StoreContextResolverTest`, `WhatsappSenderResolverTest` store-path extension, `FirestoreToWhatsappControllerTest`, `AiAgentConfigServiceTest` for `getMcpToolsForAgent()` reading from model (T-03 change) — see "Unit Tests Expected" for full list | iZinga QA & Test Automation | T-05 through T-15 |
| T-17 | Write `StoreLineWebhookIntegrationTest` (full scenario per "Integration Tests Expected"); full unscoped `mvn test` green | iZinga QA & Test Automation | T-16 |
| T-18 | Produce per-store Meta WABA provisioning runbook (REQ-20, steps M-01 through M-07): WABA subaccount add, phone number verification, display name approval, Cloud API registration, Secrets Manager entry pattern, warm-up plan, first-outbound template requirement. **RxNova24 (store id `77db94fe-6cc6-4da9-bc62-5fa26045389c`) is a confirmed first-wave pilot store — include it explicitly in the runbook timeline and WABA provisioning sequence.** | iZinga DevOps & Infrastructure | T-01 |
| T-19 | izinga-onboarding: update Firestore security rules for STORE_ADMIN isolation (REQ-17); validate in Firestore emulator before deploy | iZinga Onboarding UI/UX Developer | T-01, T-02, T-13 |
| T-20 | izinga-onboarding: update chat sessions UI with server-side storeId query filter for STORE_ADMIN (REQ-18) | iZinga Onboarding UI/UX Developer | T-19 |
| T-21 | Code review — `ijudi-api`: all REQs implemented, `getMcpToolsForAgent()` reads from model not hardcoded (T-03), MCP scoping enforced (no prompt bypass), build clean, full test suite green | iZinga Code Reviewer | T-17 |
| T-22 | Code review — `izinga-onboarding`: Firestore rules correct and emulator-validated, UI query filter is server-side | iZinga Code Reviewer | T-20 |

---

## Business Decisions Required Before Approved for Implementation

**Decision 1 — TIER-01 sequencing:** RESOLVED BY DESCOPING — 26 September 2026. TIER-01 (the premium-tier brief and `StoreProfile.plan` field) is not being built now. Lindani's direction: "don't build the plans yet." WA-LINES-02 has no dependency on TIER-01 whatsoever. The provisioning endpoint has no plan check. This brief can move to Approved for Implementation and through the SA/Security gate without waiting on TIER-01 at any point.

*Forward notice:* when TIER-01 is eventually built, it will need to retrofit an entitlement gate onto `POST /admin/whatsapp-lines/store` and handle stores that already hold a manually-provisioned line at that time. The entitlement check was intentionally omitted here — not forgotten. The TIER-01 brief author must account for this migration at that point.

**Decision 2 — Per-store conversation cap:** RESOLVED BY DESCOPING — 26 September 2026. No conversation cap. Deferred — irrelevant without billing to protect margin. Revisit when TIER-01 is eventually scoped.

**Decision 3 — Pilot cohort selection:** RESOLVED — 25 September 2026 (updated 26 September 2026). Pilot rollout is manual and ad-hoc. RxNova24 (store id `77db94fe-6cc6-4da9-bc62-5fa26045389c`) is the first store. Additional pilot stores are added at Lindani's own discretion — no formal process, no volume-based selection, no Data Intelligence ranking required. DevOps must include RxNova24 explicitly in the T-18 Meta WABA provisioning runbook and timeline.

**Decision 4 — `store_support_default` prompt sign-off:** OPEN. The template system prompt in `ai_agent_configs` is the quality foundation for every store agent. Lindani must review and approve the draft template text before it is seeded in production. The Backend Developer authors the draft in T-04 and routes it to Lindani for approval before the production seed runs. This is a content approval, not a code gate, but it is in the Definition of Done.

---

## Marketing Trigger

**Deferred — no Marketing trigger at this time.** Dedicated lines are shipping free for the initial pilot. There is no Pro tier to market, no upsell copy, and no conversion funnel yet. When TIER-01 billing is eventually built and a paid tier is live, brief the Marketing Strategist at that point on a store-owner campaign leading with the WhatsApp-agent benefit. Do not brief Marketing before then.

---

## Definition of Done

This feature is complete when ALL of the following are true:

1. All REQ-01 through REQ-20 have been implemented and all ACs pass.
2. Full unscoped `mvn test` suite passes with zero failures and zero test exclusions.
3. `mvn package -DskipTests` completes with zero errors.
4. Solution Architect has issued sign-off (T-01 ADR).
5. Security & Compliance has issued sign-off (T-02).
6. iZinga Code Reviewer has issued PASS for `ijudi-api` (T-21) and `izinga-onboarding` (T-22).
7. `AiAgentConfig` model carries `audience`, `storeId`, `mcpServers`, and `allowedTools` fields; `getMcpToolsForAgent()` reads from the model — confirmed by `AiAgentConfigServiceTest` passing (T-03 change).
8. `store_support_default` agent config exists in production `ai_agent_configs` with `active = false`, `mcpServers` populated, and the template prompt reviewed and approved by Lindani (Decision 4).
9. At least one store line (RxNova24) has been provisioned and activated in staging against a real Meta WABA subaccount, and a test inbound message has been answered by the store-scoped agent using the store's actual menu and business hours.
10. MCP scope violation has been confirmed in staging: a store-scoped tool call with an incorrect `storeId` returns `STORE_SCOPE_VIOLATION` and no data leaks to the requesting agent.
11. `izinga-onboarding` Firestore rules have been validated in the Firestore emulator: a STORE_ADMIN can only read their own store's sessions; ADMIN role retains full access.
12. WA-LINES-01 must be live in production with `multiLineEnabled = true` before any store line is activated.
13. Per-store Meta WABA provisioning runbook (T-18) exists and has been successfully executed for at least one store in staging.
14. Feature branches merged to `develop` via pull requests with Code Reviewer PASS, SA sign-off, and Security & Compliance sign-off attached.

---

*Brief authored by iZinga Product Owner · Draft — awaiting Lindani Masinga approval · last updated 26 September 2026*
