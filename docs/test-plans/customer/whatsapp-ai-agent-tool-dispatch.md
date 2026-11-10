---
userType: customer
Feature: WhatsApp AI agent tool dispatch (MCP tool invocation via OpenAI Responses API)
App: ijudi-api (WhatsApp inbound webhook / AI agent pipeline)
Last verified: 2026-11-10 | Manual live-fire + unit tests | PASS
---

## Scope

Covers the WhatsApp inbound-message → AI agent → MCP tool-call pipeline. Specifically:
- **Cause A fix (ADR-030):** Removal of the `isNewSession()` pre-dispatch shortcut that short-circuited new sessions before the AI agent was invoked. All messages now flow to `handlePreDispatchFlows → AI agent`.
- **Cause B fix:** `AiAgentConfig.useTools` annotated `@Builder.Default` so the Lombok builder no longer produces `useTools=false`. Existing MongoDB documents backfilled via `AiAgentConfigInitializer`.
- Diagnostic logging (`logPreRequest` / `logPostResponse`) gated by `izinga.ai.agent.debug.log-openai-request-response`.

**Out of scope:** Customer food ordering flow, driver quote acceptance, payment webhooks, store management. Those are covered in separate library files.

Cross-link: The driver-side AI agent shares the same `driver_support` AiAgentConfig document — see `driver/quote-acceptance-and-fulfilment.md` for driver-flow tests.

## Repos affected

| Repo | Test work |
|------|-----------|
| `ijudi-api` (izinga-messaging module) | Unit tests + live-fire |
| `ijudi-api` (izinga-ordermanager runtime host) | App boot + live-fire |

## Known-good test fixtures

| Fixture | Value |
|---------|-------|
| Sender phone | `+27812815707` (Lindani admin) |
| phoneNumberId | `720864707767294` (CUSTOMER WhatsApp line, `agentName=driver_support`) |
| AI agent config doc | `driver_support` in `ai_agent_configs` MongoDB collection |
| Spring profile | `uat` |
| OpenAI model | `gpt-4.1-mini` |
| MCP server URL | `https://api.izinga.co.za/mcp` (DEFAULT_MCP_SERVER, production) |
| Local boot port | `8082` (ports 80 and 8081 occupied by earlier test instances) |
| App boot command | `../mvnw spring-boot:run -Dspring-boot.run.profiles=uat "-Dspring-boot.run.jvmArguments=-Dserver.port=8082 -Dlogging.level.io.curiousoft.izinga.messaging.aiAgent=DEBUG"` from `izinga-ordermanager/` subdirectory |
| Pre-boot install step | `./mvnw -pl izinga-messaging -am install -DskipTests` from worktree root (required to refresh `~/.m2` cache with fix code before boot — see Known Gaps) |
| Test webhook endpoint | `POST http://localhost:<port>/whatsapp/webhook` |
| Signature bypass | `appSecret` not configured in uat → `SEC-01 BYPASS ACTIVE` — all POSTs accepted |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|----|-------------|------|-------|-----------------|----------------|
| TC-01 | Existing session — AI invoked | Live-fire | `{"type":"text","text":{"body":"Hi"}}` via webhook, `isNewSession=false` | Log: `AI customer service enabled, processing message from ... via agent=driver_support audience=CUSTOMER`; `[AI-DEBUG] toolsPresent=true`; real wamid in WhatsApp API response | `isAiCustomerServiceEnabled && isAIAgentActive` = true |
| TC-02 | Existing session — MCP tool call | Live-fire | `{"text":{"body":"Show me what services iZinga offers"}}` | Log: `[AI-DEBUG] response output: 3 items [mcp_list_tools, mcp_call, message]`; `mcp_call` with `name=send_landing_options_notification`, `status=completed`; real wamid | AI decides to call a tool |
| TC-03 | New session — no old shortcut | Live-fire | `{"text":{"body":"Hi"}}` on a NEW session (>90 min since last message) | No `New WhatsApp session started for` log; no direct `sendLandingOptions` call before AI is invoked; AI handles the message | `isNewSession=true` path (Cause A fix) |
| TC-04 | Cause B: useTools present in OpenAI request | Live-fire | Any inbound text | `[AI-DEBUG] toolsPresent=true`; `tools: [{"type":"mcp",...}]` in pre-request log | `agent.isUseTools() = true` |
| TC-05 | Cause B: pre-fix regression — useTools omitted | Unit | Build AiAgentConfig via Lombok builder, check `isUseTools()` | `isUseTools()` returns `true`; builder sets default | `@Builder.Default` branch |
| TC-06 | Backfill: existing doc with useTools=false | Unit | Mock `agentRepo` returning doc with `useTools=false` | `AiAgentConfigInitializer` logs `SA-021-4`, updates doc to `useTools=true` | Backfill dirty=true branch |
| TC-07 | Backfill: existing doc with useTools=true | Unit | Mock `agentRepo` returning doc with `useTools=true` | No SA-021-4 log, no update | Backfill dirty=false branch |
| TC-08 | Verification consent message — AI bypassed | Unit | Message body matching verification consent pattern | `verificationConsentService.handleVerificationConsentReply` called; AI NOT invoked; "Thank you. Your application is being processed." sent | `isVerificationMessage=true` branch |
| TC-09 | AI disabled — no routing | Unit | `ai.agent.enabled=false` | `isAiCustomerServiceEnabled=false`; AI block skipped; no AI log; `aiResponseToCustomer=null` | `isAiCustomerServiceEnabled=false` branch |
| TC-10 | AI session inactive — no routing | Unit | `session.isAIAgentActive()=false` | AI block skipped; no AI log | `isAIAgentActive=false` branch |

## Integration test scenarios

**E2E-01: New customer first message → AI sends landing options via MCP**
1. Customer sends "Hi" on a session with `lastMessageDate=null` (new session)
2. `upsertSession` sets `isNewSession=true`
3. `handlePreDispatchFlows` checks AI flags, routes to `aiCustomerService.handleWhatsappQueryForAgent`
4. AI loads `driver_support` agent config (`useTools=true`, `mcpServers=[DEFAULT_MCP_SERVER]`)
5. OpenAI Responses API: lists tools from `https://api.izinga.co.za/mcp`, then calls `send_landing_options_notification`
6. Production MCP server processes tool call, sends `izinga_landing_options` template via WhatsApp API to customer
7. AI sends text follow-up via local app
8. Expected wamid from production MCP server send + wamid from local app send

**E2E-02: Returning customer query requiring data lookup**
1. Customer sends "What is my order status?"
2. AI calls `find_orders_by_phone_number` via MCP, gets real order data from production
3. AI replies with order status text, local app sends via WhatsApp API

## Regression checks

- [ ] `WhatsappInboundEventHandler` test suite still passes (no regressions in `handleTextMessage`, `handleInteractiveMessage`, `handleLocationMessage`, `handleImageMessage`)
- [ ] `AiCustomerServiceAgent` test suite: tool-dispatch tests, `buildScopedMcpTools`, `logPreRequest`/`logPostResponse`
- [ ] `AiAgentConfigInitializer` test suite: backfill for `useTools`, `audience`, `mcpServers`
- [ ] Full reactor `./mvnw clean test` — 918 tests, 0 failures, 0 errors, 4 skipped (confirmed 2026-11-10)
- [ ] Verification consent flow not broken (TC-08)
- [ ] Store AI agent routing (`lineContext.audience() == STORE`) not broken

## Known gaps / Known defects

| Severity | Description | Date found |
|----------|-------------|------------|
| LOW | TC-03 (new session, `isNewSession=true`) cannot be tested against the same sender number within 90 minutes of a prior test. Requires either a 90-minute wait or a fresh session (different phoneNumberId). | 2026-11-10 |
| LOW | The `spring-boot:run` command documented in QA agent (`./mvnw -pl izinga-ordermanager -am spring-boot:run`) fails on this monorepo because `spring-boot-maven-plugin` is configured on the parent POM and executes on it first. Workaround: run `./mvnw -pl izinga-messaging -am install -DskipTests` from root first, then run from subdirectory without `-am`. | 2026-11-10 |
| INFO | `ai.agent.enabled` defaults to `false` if not set in AWS Secrets Manager. Live-fire tests require the uat secret to have `ai.agent.enabled=true`. Confirmed true for `izinga-uat` secret as of 2026-11-10. | 2026-11-10 |
| INFO | The `mcp_call` for `send_landing_options_notification` dispatches to the PRODUCTION MCP server (`https://api.izinga.co.za/mcp`). The WhatsApp notification is sent from the production server's WhatsApp token, not the local instance. This is by design (OpenAI hosted MCP). | 2026-11-10 |

## Validation commands

```bash
# Full reactor test (mandatory before any PASS verdict)
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api-mcp-dispatch-fix
./mvnw clean test

# Install fixed izinga-messaging JAR to ~/.m2 (required before local UAT boot)
./mvnw -pl izinga-messaging -am install -DskipTests

# Boot locally on uat profile (from ordermanager subdir, after install step above)
cd izinga-ordermanager
../mvnw spring-boot:run \
  -Dspring-boot.run.profiles=uat \
  "-Dspring-boot.run.jvmArguments=-Dserver.port=8082 -Dlogging.level.io.curiousoft.izinga.messaging.aiAgent=DEBUG"
```
