---
userType: platform / driver / customer
Feature: OpenAI API Retry, Timeout and Customer-Facing Fallback Resilience (ADR-031 + FIX-01)
App: ijudi-api (izinga-messaging, izinga-yoco-pay)
Last verified: 2026-11-11 — Live manual + full unscoped unit suite — UAT profile — PASS
---

## Scope

**Covered:**
- New `@Bean("openAiRestTemplate")` with explicit Apache HttpClient 5 timeouts — connect and response timeouts are configurable via `izinga.ai.openai.connect-timeout-seconds` and `izinga.ai.openai.response-timeout-seconds` in `application.yml` without a release (ADR-031 Option C). Defaults: 3 s connect, 15 s response.
- `@Bean("openAiRetryTemplate")` in `OpenAiRetryConfig`: `SimpleRetryPolicy` 3 max attempts, `traverseClassHierarchy=true`; retryable: `ResourceAccessException`, `HttpServerErrorException` (5xx), `HttpClientErrorException.TooManyRequests` (429); non-retryable: `HttpClientErrorException` (all other 4xx incl. 400 and 401)
- `ExponentialBackOffPolicy`: 2 s initial, x2.0, 10 s max — worst-case: 15 s x3 + 2 s + 4 s = 51 s. Acceptable — this flow is async and does NOT block Meta's webhook ack.
- Both AI agents (`AiCustomerServiceAgent` and `StoreAiAgent`) inject `@Qualifier("openAiRestTemplate")` and `@Qualifier("openAiRetryTemplate")`
- All three `postForEntity(OPENAI_CHAT_URL, ...)` call sites wrapped in `openAiRetryTemplate.execute(...)`
- `[AI-ATTEMPT] attempt=N` logging: attempt=1 at INFO (a successful call must not emit WARN), attempt >= 2 at WARN (a genuine retry warrants alerting). Both `AiCustomerServiceAgent` and `StoreAiAgent` fixed.
- Timeout property binding unit tests: `YocoConfigurationTest` verifies defaults (3 s / 15 s) and that override values take effect — guards against a silently-unbound `@Value` restoring the unlimited Apache HC5 default.
- Fallback from `WhatsappInboundEventHandler.handlePreDispatchFlows()`: AI enabled+active but agent returns null sends `AI_FALLBACK_MESSAGE` to customer via WhatsApp
- `AI_FALLBACK_MESSAGE` = "Sorry, I'm having a bit of trouble right now 🙏 Please try again in a moment, or reach us on WhatsApp at +27812815707 or email hello@curiousoft.dev."
- FIX-01 guard: `extractUserText(message)` called before AI dispatch — IMAGE and INTERACTIVE messages return null, `[AI-SKIP]` logged, NO AI dispatch, NO fallback
- LOCATION messages produce non-null text from `Location#describe()` and still reach the AI agent (regression guard confirmed)

**Out of scope:**
- Context-window-overflow HTTP 400 root cause (non-retried, fallback sent — that is correct)
- `AiAgentConfig` missing `@Builder.Default` defect (escalated)
- Broad `AiCustomerServiceAgentTest` class-level `@Disabled` re-enablement (deliberately kept)
- The 8 s response timeout value itself (escalated to Lindani — see Real Latency note below)
- `hello@curiousoft.dev` branding (escalated to Lindani)

## Repos affected

| Repo | Module | Change type |
|---|---|---|
| ijudi-api | `izinga-messaging` | Spring Retry dependency, OpenAiRetryConfig, AiCustomerServiceAgent retry wrapping, StoreAiAgent retry wrapping, WhatsappInboundEventHandler fallback + FIX-01 guard |
| ijudi-api | `izinga-yoco-pay` | openAiRestTemplate bean + @Primary on existing restTemplate |

## Known-good test fixtures

| Fixture | Value |
|---|---|
| Test sender phone | +27812815707 (only this number — standing rule) |
| Webhook URL (local uat) | `http://localhost:8080/whatsapp/webhook` |
| Auth bypass | `appSecret` not configured in UAT — webhook accepts unsigned POST |
| Port override | `--server.port=8080` to avoid needing sudo for port 80 |
| Java | Corretto 17: `export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home` |
| AI enabled in UAT | Confirmed via `isAIEnabled=true isAIAgentActive=true` in boot log (secrets from `aws-secretsmanager:izinga-uat`) |
| Non-retryable 4xx test | Pass `-Dspring-boot.run.jvmArguments=-Dopenai.api.key=INVALID_KEY_QA_TEST_401` to override key — real 401 from OpenAI |
| Transient retry test | Temporary (reverted) code change: set `OPENAI_CHAT_URL = "http://127.0.0.1:9999"` + Python stub returning 503; confirms 3 attempts with 2 s and 4 s gaps |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Boot with new beans — no NoUniqueBeanDefinitionException, timeout fields logged at startup | Live boot | uat profile start | `Started IjudiApplication` in log, no bean errors, `izinga.ai.openai.*` properties resolve | Bean wiring |
| TC-02 | Successful AI call with valid key | Live | Text message POST | `[AI-ATTEMPT] attempt=1` INFO (not WARN), OpenAI replies, wamid confirmed | Happy path |
| TC-03 | Non-retryable 4xx — 401 invalid key | Live | Text message, invalid key | Exactly `attempt=1` INFO, error `401 Unauthorized`, `[AI-FALLBACK]`, fallback wamid | Non-retryable 4xx |
| TC-04 | Transient failure — 3 attempts with backoff | Live | Text message, local 503 stub | `attempt=1` INFO at T+0, `attempt=2` WARN at T+~2s, `attempt=3` WARN at T+~6s, `[AI-FALLBACK]`, fallback wamid | Retryable 5xx |
| TC-05 | IMAGE message — AI-SKIP, no fallback | Live | IMAGE webhook | `[AI-SKIP] type=image` DEBUG, NO `[AI-ATTEMPT]`, NO `[AI-FALLBACK]` | FIX-01 E-2 |
| TC-06 | INTERACTIVE message — AI-SKIP, no fallback | Live | INTERACTIVE webhook | `[AI-SKIP] type=interactive` DEBUG, NO `[AI-ATTEMPT]`, NO `[AI-FALLBACK]` | FIX-01 E-2 |
| TC-07 | LOCATION message — still reaches AI | Live | LOCATION webhook | `[AI-ATTEMPT] attempt=1` INFO, OpenAI responds, wamid | FIX-01 regression guard |
| TC-08 | Unit: HttpStatus 5xx retried 3x, 4xx not retried | Unit | OpenAiRetryResilienceTest | PASS | RetryTemplate classification |
| TC-09 | Unit: IMAGE/INTERACTIVE no fallback, LOCATION reaches AI | Unit | OpenAiRetryResilienceTest + WhatsappInboundEventHandlerTest | PASS | FIX-01 |
| TC-10 | Full reactor build | Unit suite | `./mvnw clean test` | 918+ tests, 0 failures, 0 errors, 4 skipped | All |
| TC-11 | Unit: timeout property binding defaults (3 s / 15 s) | Unit | YocoConfigurationTest | PASS — HC5 Configurable.getConfig() returns exactly Timeout.ofSeconds(3) and Timeout.ofSeconds(15) | Property binding |
| TC-12 | Unit: timeout property binding override (7 s / 22 s) | Unit | YocoConfigurationTest | PASS — overridden fields produce 7 s / 22 s, not the default 15 s (proves not hardcoded) | Property binding override |

## Integration test scenarios

1. Transient retry success: Valid key, real OpenAI reachable. Single `[AI-ATTEMPT]` WARN. Reply delivered.
2. Transient retry x3 then fallback: 503 stub active. Three attempts with 2 s and 4 s gaps. `[AI-FALLBACK]`. Fallback wamid confirmed.
3. Non-retryable 4xx immediate fallback: 401 invalid key. Single attempt. `[AI-FALLBACK]`. Fallback wamid confirmed.
4. IMAGE doc upload — no spurious fallback: IMAGE message with field caption. `[AI-SKIP]` logged. Normal document-received flow.
5. INTERACTIVE quote acceptance — no spurious fallback: Button reply. `[AI-SKIP]` logged. No AI-related messages.
6. LOCATION — AI still engaged: Location message. `extractUserText` returns non-null description. AI called. Reply delivered.

## Regression checks

- [x] Text messages: AI still invoked and responds (TC-02)
- [x] LOCATION messages: reach AI agent (TC-07)
- [x] IMAGE messages: existing image processing flow not broken; no spurious fallback (TC-05)
- [x] INTERACTIVE messages: existing quote-acceptance flow not affected; no spurious fallback (TC-06)
- [x] Other RestTemplate consumers: `@Primary` on unqualified bean — boot confirmed no conflicts (TC-01)
- [x] Full 918-test suite: 0 failures (TC-10)

## Real latency observations — critical for 8 s timeout decision

Measured round-trip for `postForEntity` to OpenAI Responses API (MCP tool-use calls, gpt-4.1-mini):
- Text message: **6.73 s** (00:57:48.984 to 00:57:55.712 — 2026-11-11)
- LOCATION message: **4.98 s** (01:03:45.748 to 01:03:50.724 — 2026-11-11)

Both calls include `mcp_list_tools` overhead. The 6.73 s call leaves 1.27 s margin before the 8 s timeout. Under load or with multi-step tool-call chains this margin will be breached, turning working calls into retried failures and adding 2 + 4 = 6 s latency. Recommend increasing the response timeout to 15 s or making it configurable via `application.yml`. Escalated to Lindani.

## Known gaps / Known defects

| ID | Severity | Description | Date found | Status |
|---|---|---|---|---|
| GAP-001 | LOW | HTTP 429 TooManyRequests retry not live-tested (would require real OpenAI rate-limit; unit test covers the classification) | 2026-11-11 | OPEN |
| GAP-002 | MEDIUM | 8 s response timeout too tight for MCP tool-use calls — measured max 6.73 s, only 1.27 s margin. | 2026-11-11 | RESOLVED — raised to 15 s (ADR-031 Option B) and made configurable (Option C) |
| GAP-003 | LOW | `[AI-ATTEMPT] attempt=1` logged at WARN on every successful call — dilutes real alerting. | 2026-11-11 | RESOLVED — attempt=1 now INFO, attempt >= 2 now WARN in both AiCustomerServiceAgent and StoreAiAgent |

## Validation commands

```bash
# Full unscoped suite (mandatory)
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api
./mvnw clean test
# Expected: 918 tests, 0 failures, 0 errors, 4 skipped

# Boot for live-fire
./mvnw -pl izinga-ordermanager -am install -DskipTests
./mvnw -pl izinga-ordermanager spring-boot:run \
  -Dspring-boot.run.profiles=uat \
  "-Dspring-boot.run.arguments=--server.port=8080"

# Non-retryable 4xx test (override key)
./mvnw -pl izinga-ordermanager spring-boot:run \
  -Dspring-boot.run.profiles=uat \
  "-Dspring-boot.run.arguments=--server.port=8080" \
  "-Dspring-boot.run.jvmArguments=-Dopenai.api.key=INVALID_KEY_QA_TEST_401"
```
