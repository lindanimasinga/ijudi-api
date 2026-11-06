---
userType: driver / customer
Feature: AI Customer Service Agent — WhatsApp inbound webhook live end-to-end trace (AiCustomerServiceAgent + StoreAiAgent dispatch)
App: ijudi-api (izinga-messaging, izinga-ordermanager)
Last verified: 2026-11-01 — Live manual / UAT profile — PASS (CUSTOMER-audience path; DRIVER-audience path blocked by missing driverPhoneId in UAT secret — see Known gaps)
---

## Scope

**Covered:**
- `POST /whatsapp/webhook` receives a Meta Cloud API payload and passes HMAC validation (SEC-01 bypass active when appSecret is blank — confirmed legitimate per ADR logged 2026-09-28)
- `WhatsappInboundEventHandler` resolves `LineContext` (phoneNumberId → `WhatsappLine` → audience + agentName + storeId), creates/resumes `WhatsappSession`, and dispatches to the correct handler
- New-session flow: first inbound message triggers `sendLandingOptions` (template `izinga_landing_options`) — AI agent is NOT invoked on the first message of a new session
- Resumed-session flow (within 90-minute window): `isAIAgentActive=true` + `ai.agent.enabled=true` → `AiCustomerServiceAgent.handleWhatsappQueryForAgent()` invoked for DRIVER/CUSTOMER audience
- `AiCustomerServiceAgent` loads `AiAgentConfig` (system prompt + MCP tool definitions) for `agentName=driver_support`, retrieves conversation history from MongoDB, calls OpenAI Responses API (`https://api.openai.com/v1/responses`), stores assistant reply, and returns reply text
- Outbound WhatsApp text message sent via Meta Graph API (`https://graph.facebook.com/v23.0/{phoneNumberId}/messages`) — HTTP 200 + wamid confirmed
- Inbound and outbound messages persisted to Firestore
- ConversationHistory updated in MongoDB after each turn

**Out of scope for this pass:**
- DRIVER-audience path (no `driverPhoneId` configured in `izinga-uat` Secrets Manager — see Known gaps)
- STORE-audience path / `StoreAiAgent` (separate agent, separate dispatch branch)
- Interactive / button / location / image message types (only text exercised)
- `isAIAgentActive=false` path (disabling AI for a session)
- `ai.agent.enabled=false` path (global AI kill switch)
- MCP tool-call trace within OpenAI response (reply for this query was answered from training/system prompt; no tool calls observed in log output — tool call logging gap noted below)
- Multi-turn conversation depth beyond 2 POSTs

Cross-links:
- Customer WhatsApp location-message handling: [customer/whatsapp-location-messages.md](../customer/whatsapp-location-messages.md)
- MCP tools integration: [admin/mcp-tools.md](../admin/mcp-tools.md)

## Repos affected

| Repo | Module | Test work required |
|---|---|---|
| ijudi-api | izinga-messaging | Unit tests for `AiCustomerServiceAgent`, `WhatsappInboundEventHandler`, `WhatsAppWebhookController` |
| ijudi-api | izinga-ordermanager | Full reactor `./mvnw test` as final gate |

## Known-good test fixtures

**Endpoint:** `POST http://localhost:8080/whatsapp/webhook`

**Backend run command (Corretto 17 required):**
```bash
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
./mvnw -pl izinga-ordermanager -am spring-boot:run \
  -Dspring-boot.run.profiles=uat \
  -Dserver.port=8080 \
  2>&1 | tee /tmp/uat-run.log
```

**WhatsApp line in sandbox MongoDB (`izinga-sandbox`):**
- `phoneNumberId = 720864707767294`
- `audience = CUSTOMER`
- `agentName = driver_support` (NOTE: this is a data anomaly — see Known defects DEF-002; bootstrap seeds `customer_support` but this record was manually updated to `driver_support` in sandbox)

**From number used (safe — Lindani's own admin number, confirmed acceptable for UAT outbound):**
- `27812815707`

**POST 1 — session creation (new-session flow, no AI invocation):**
```bash
curl -s -X POST http://localhost:8080/whatsapp/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "test_entry_001",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "metadata": {
            "display_phone_number": "27600123456",
            "phone_number_id": "720864707767294"
          },
          "contacts": [{"profile": {"name": "Test Driver"}, "wa_id": "27812815707"}],
          "messages": [{
            "from": "27812815707",
            "id": "test_msg_session_init_001",
            "timestamp": "1730444800",
            "type": "text",
            "text": {"body": "Hi"}
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```
Expected: HTTP 200, landing options template `izinga_landing_options` sent to `27812815707`, wamid logged.

**POST 2 — AI invocation (must follow POST 1 within 90 minutes):**
```bash
curl -s -X POST http://localhost:8080/whatsapp/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "test_entry_002",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "metadata": {
            "display_phone_number": "27600123456",
            "phone_number_id": "720864707767294"
          },
          "contacts": [{"profile": {"name": "Test Driver"}, "wa_id": "27812815707"}],
          "messages": [{
            "from": "27812815707",
            "id": "test_msg_driver_signup_001",
            "timestamp": "1730444880",
            "type": "text",
            "text": {"body": "Hi, I want to become an iZinga driver. What do I need to do to sign up?"}
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```
Expected: HTTP 200, AI-generated reply delivered to `27812815707` via Meta Graph API, Firestore writes logged.

**Confirmed AI reply text (2026-11-01 run):**
```
To become an iZinga driver:

1. Sign up with your mobile number and verify via OTP.
2. Complete your profile at https://driver.izinga.co.za.
3. Select the appropriate driver service.
4. Upload all required documents clearly.
5. Add your payout details (bank or cellphone payout).
6. Submit your profile for review and wait for approval.

If you need help with any step, just ask!
```

**Confirmed wamids (sandbox, 2026-11-01):**
- POST 1 (landing template): `wamid.HBgLMjc4MTI4MTU3MDcVAgARGBI4N0M5QzI1MjVDRUI2OEY3MTMA`
- POST 2 (AI reply): `wamid.HBgLMjc4MTI4MTU3MDcVAgARGBI5NzUyQjE4RDdBODAxMTI3QkMA`

**Confirmed Firestore message IDs (sandbox, 2026-11-01):**
- Inbound user message: `jX5pPftqQIcYZFCL7iF5`
- AI response: `tTthrvoOWtTioPpQYBEk`

**OpenAI latency observed:** ~8.4 seconds (09:31:23.436 → 09:31:31.805) for a driver sign-up query answered from system prompt without tool calls.

## Test cases

| ID | Description | Type | Input | Expected output | Notes |
|---|---|---|---|---|---|
| AI-01 | New session — landing options sent, AI not invoked | Live E2E | POST 1 payload above; `from` not in active session | HTTP 200; template `izinga_landing_options` sent; no AiCustomerServiceAgent log | Confirmed 2026-11-01 |
| AI-02 | Resumed session — AI agent invoked for driver sign-up query | Live E2E | POST 2 payload above; active session within 90 min | HTTP 200; OpenAI called; reply delivered to `from`; Firestore writes logged | Confirmed 2026-11-01 |
| AI-03 | HMAC bypass when appSecret blank | Unit | `X-Hub-Signature-256` absent; `appSecret=null` | SEC-01 BYPASS ACTIVE logged; request accepted | See SEC-01 in `WhatsAppWebhookController` |
| AI-04 | HMAC validation when appSecret configured | Unit | Valid HMAC in header | Request accepted | Inverse: invalid HMAC → 403 |
| AI-05 | Session expiry — AI not invoked after 90 min gap | Unit/Live | `lastSeen` > 90 min ago | `isNewSession=true`; landing options re-sent | Not exercised in this pass |
| AI-06 | `isAIAgentActive=false` — AI skipped | Unit | Session with `isAIAgentActive=false` | AI not invoked; landing options or no-op | Not exercised in this pass |
| AI-07 | `ai.agent.enabled=false` (kill switch) | Unit | `ai.agent.enabled=false` in config | `aiCustomerService.isEnabled()=false`; AI not invoked | Toggle in Secrets Manager |
| AI-08 | STORE audience → StoreAiAgent | Unit | `phoneNumberId` mapped to STORE audience line | `storeAiAgent.handleWhatsappQuery()` invoked, not `aiCustomerService` | Not exercised in this pass |
| AI-09 | DRIVER audience → AiCustomerServiceAgent | Live | `phoneNumberId` mapped to DRIVER audience line | Same AI path as CUSTOMER; `audience=DRIVER` in LineContext log | Blocked — no driverPhoneId in UAT Secrets |
| AI-10 | Unknown phoneNumberId — null LineContext | Unit | `phoneNumberId` not in WhatsappLine collection | NullPointerException guard; no crash; graceful drop | Check `resolveLineContext` null path |
| AI-11 | OpenAI error response — agent fallback | Unit | OpenAI returns 4xx/5xx | Exception caught; error logged; no outbound WhatsApp crash | RestTemplate error handling in AiCustomerServiceAgent |
| AI-12 | ConversationHistory not found — creates new | Unit | First-ever message from `from` for `agentName` | New ConversationHistory doc created; user message added | Confirmed by "Found existing conversation" log on second run |
| AI-13 | Firestore persistence — inbound message written | Live | Any inbound text message | Firestore doc created with message body and metadata | Confirmed 2026-11-01 |
| AI-14 | Firestore persistence — outbound AI reply written | Live | AI reply returned | Firestore doc created with AI reply body and metadata | Confirmed 2026-11-01 |

## Integration test scenarios

**Scenario INT-01: Full two-turn driver sign-up query (confirmed 2026-11-01)**

1. Webhook POST 1 received → session created → landing template delivered to WhatsApp
2. Webhook POST 2 received (within 90 min) → AiCustomerServiceAgent invoked → OpenAI called → reply delivered to WhatsApp → Firestore writes confirmed

**Scenario INT-02: Multi-line routing (not yet tested)**

Run POST 2 payload with a `phoneNumberId` mapped to STORE audience → confirm `StoreAiAgent` is called, not `AiCustomerServiceAgent`. Requires a STORE WhatsappLine with a distinct `phoneNumberId` in sandbox.

**Scenario INT-03: Conversation context carried across turns (partially tested)**

The live run confirmed that the ConversationHistory doc already had 3 context messages when POST 2 fired, confirming that prior conversation state was loaded and passed to OpenAI. Full multi-turn coherence (asking a follow-up question) was not tested.

## Regression checks

- [ ] `POST /whatsapp/webhook` still returns 200 for well-formed payloads
- [ ] Landing options template still fires on new sessions
- [ ] `WhatsappSession` compound index `(from, phoneNumberId)` prevents duplicate session creation
- [ ] `WhatsappLineBootstrap` still seeds CUSTOMER line from `whatsapp.cloud.phoneId` on startup
- [ ] Firestore message persistence still writes inbound and outbound messages
- [ ] `ConversationHistoryService` still creates/updates MongoDB docs correctly
- [ ] `StoreScopeJwtService.buildScopeToken()` does not throw when `storeId=null`

## Known gaps / Known defects

**DEF-001 — SCHEDULER: Deprecated `dall-e-2` model**
- Severity: Medium (causes startup noise and image generation failures for affected stores)
- Location: `izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanager/scheduler/SchedulerService.java` (method `generateMissingImagesForStores`)
- Error: `400 Bad Request: "The model 'dall-e-2' does not exist."` — observed at startup for RxNova24 stock items
- Date found: 2026-11-01
- Status: Open, pre-existing, not fixed in this session. Does not block AI agent flow.

**DEF-002 — DATA: CUSTOMER WhatsappLine has `agentName=driver_support`**
- Severity: Low (functional — AI invoked correctly; but agent name implies wrong audience intent)
- Location: `izinga-sandbox` MongoDB, `whatsapplines` collection, `phoneNumberId=720864707767294`
- Detail: `WhatsappLineBootstrap` seeds a `customer_support` agent name for the CUSTOMER line, but the sandbox record was manually updated to `driver_support`. The AI agent dispatched correctly using `driver_support`, but this is a data state inconsistency.
- Date found: 2026-11-01
- Status: Sandbox data anomaly. Production data must be verified to ensure the correct `agentName` mapping.

**DEF-003 — LOG: "replied with N chars of context" is misleading**
- Severity: Low (cosmetic)
- Location: `izinga-messaging/.../AiCustomerServiceAgent.java`, log statement: `"AI agent '{}' replied to {} with {} chars of context"`
- Detail: The format arg is `contextMessages.size()` (number of conversation message objects), not a character count. The word "chars" is wrong — should be "messages" or "context messages".
- Date found: 2026-11-01
- Status: Open, minor. Developer fix is a one-line log message change.

**GAP-001 — No DRIVER-audience WhatsApp line in UAT sandbox**
- `whatsapp.cloud.driverPhoneId` is blank in `izinga-uat` Secrets Manager
- `WhatsappLineBootstrap` skips the DRIVER line seed when `driverPhoneId` is blank
- DRIVER-audience path (AI-09) could not be exercised
- Remediation: configure `driverPhoneId` in Secrets Manager or add a DRIVER WhatsappLine document directly to sandbox MongoDB

**GAP-002 — No OpenAI request/response logging**
- `AiCustomerServiceAgent` uses a plain `RestTemplate` with no interceptor
- The model name, tools array, and raw OpenAI response are invisible in logs
- When tool calls occur, there is no log of which tools were invoked or what they returned
- Remediation: add a logging interceptor to the OpenAI `RestTemplate` (similar to `WhatsAppFactory`'s OkHttp interceptor)

**GAP-003 — `isAIAgentActive=false` path not tested**
- No test exercised the flow where a user disables the AI agent mid-session
- Cover in AI-06 unit test

## Validation commands

```bash
# Backend — scoped (development iteration only)
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
./mvnw -pl izinga-messaging -am test

# Backend — full reactor (mandatory before any PASS verdict)
./mvnw test
```
