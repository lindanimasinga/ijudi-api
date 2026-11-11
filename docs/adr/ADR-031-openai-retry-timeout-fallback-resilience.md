# ADR-031: OpenAI API Retry, Timeout and Fallback Resilience

**Date:** 2026-11-11
**Status:** Accepted
**Deciders:** Lindani Masinga (CEO), iZinga Backend Developer, iZinga Code Reviewer, iZinga QA
**Modules affected:** `izinga-messaging`, `izinga-yoco-pay`, `izinga-ordermanager`

---

## Context

`AiCustomerServiceAgent` and `StoreAiAgent` called the OpenAI chat-completions API through a shared,
unqualified `RestTemplate` bean with no connect timeout, no read timeout, and no retry logic. Any
transient OpenAI failure — a brief 503, a network hiccup, an upstream rate limit — returned `null`
from the agent and the customer received **total WhatsApp silence**. A 2-second OpenAI hiccup was
indistinguishable from a broken product.

`handleWhatsappQueryForAgent` in `AiCustomerServiceAgent` contained nine separate `return null` exits,
meaning a fallback attached only to the exception `catch` block would still have produced silence on
the other eight exit paths.

No retry library was present anywhere in the codebase prior to this change.

---

## Decision 1 — `RetryTemplate` over `@Retryable`

Adopted `spring-retry` (BOM-managed 2.0.10 via Spring Boot 3.3.6) using the **programmatic
`RetryTemplate`**, declared as a bean in `OpenAiRetryConfig.java` (new class, `izinga-messaging`),
with the dependency added to `izinga-messaging/pom.xml` only.

`@Retryable` was rejected because both agents wrap the OpenAI call inside a broad
`catch (Exception e)` block that returns `null`. Exceptions never propagate to a CGLIB proxy, so
`@Retryable` would have been silently ineffective without restructuring the try-catch to re-throw —
a larger refactor than the fix warranted. `RetryTemplate.execute()` wraps only the network call and
leaves the surrounding structure intact.

**Consequence:** no `spring-aspects`, no `@EnableRetry`, no AOP weaving anywhere in the codebase.

Rejected also: a hand-rolled retry loop — same semantics, more code, lower readability.

---

## Decision 2 — Dedicated `@Bean("openAiRestTemplate")` with `@Primary` on the shared bean

A new `@Bean("openAiRestTemplate")` is added in `YocoHeaderConfig` (`izinga-yoco-pay`), injected via
`@Qualifier("openAiRestTemplate")` at exactly two sites: `AiCustomerServiceAgent` and `StoreAiAgent`.

Modifying the existing shared `restTemplate()` bean was rejected. That bean also serves live payment
calls, payout-email dispatch, and Firestore interactions. Imposing timeouts there carried an
unacceptable cross-cutting regression risk.

`@Primary` was added to the existing shared `restTemplate()` bean (annotation only — bean body
untouched). This is the boot-safety mechanism: with two `RestTemplate` candidates in the application
context, all pre-existing unqualified single-parameter constructor injections would otherwise have
hit `NoUniqueBeanDefinitionException` at startup. Relying on Spring's parameter-name-to-bean-name
fallback would have been fragile.

**Consumer count correction:** an initial estimate identified 13 consumers of the shared bean. On
inspection, only 5 are genuine Spring DI consumers: `AiCustomerServiceAgent`, `StoreAiAgent`,
`FirestoreService`, `WhatsappTemplateReplyEventHandler`, and `WhatsappMediaDownloader`. The
remaining 8 construct `new RestTemplate()` inline and were structurally immune to the `@Primary`
change.

Verified: exactly one `@Primary RestTemplate` bean exists repo-wide. No competing test-context bean.

---

## Decision 3 — Retry policy and exception classification

`SimpleRetryPolicy(3, map, traverseClassHierarchy=true)` — 3 total attempts (2 retries).

| Exception | Treatment | Rationale |
|---|---|---|
| `ResourceAccessException` | Retryable | Connect/read timeout, connection refused |
| `HttpServerErrorException` | Retryable | 5xx — transient upstream failure |
| `HttpClientErrorException.TooManyRequests` | Retryable | 429 — rate limit, worth waiting |
| `HttpClientErrorException` (all other 4xx) | **Non-retryable** | Includes HTTP 400 context-window overflow — identical re-attempt wastes budget |

`ExponentialBackOffPolicy`: 2 000 ms initial, ×2.0 multiplier, 10 000 ms cap. Wait sequence:
2 s before attempt 2, 4 s before attempt 3.

**Subclass ordering note:** `TooManyRequests` is a subclass of the non-retryable
`HttpClientErrorException`. With `traverseClassHierarchy=true`, Spring Retry resolves to the most
specific declared entry first — making `TooManyRequests` retryable as intended. This is verified by
a unit test against the production bean itself, not a mirrored policy.

---

## Decision 4 — Timeouts: 3 s connect / 15 s response, configurable (Lindani's call)

The initial implementation shipped connect=3 s / response=**8 s**, chosen to fit a presumed 30 s
worst-case cap.

**QA live-fire measured real OpenAI round-trips of 6.73 s** (text + MCP tool-use calls) and 4.98 s
(location query) on the `uat` profile. At 8 s, the working calls had only 1.27 s of margin; under
load or with longer tool-call chains they would have timed out, burned 6 s in retries, and delivered
an apology for a request that would have succeeded.

Lindani approved **Option B+C**: raise the response timeout to **15 s** and make both timeouts
configurable without a release, via `application.yml` properties:

```yaml
izinga:
  ai:
    openai:
      connect-timeout-seconds: 3    # default; adjust at runtime without rebuild
      response-timeout-seconds: 15  # default; raised from 8s after live-fire measurement
```

Bound with `@Value` on `YocoHeaderConfig` fields with Kotlin-declared defaults (3 and 15
respectively) so an unbound property falls back to the correct value rather than Apache HttpClient
5's `Timeout.DISABLED` (unlimited). Both values are verified in `YocoConfigurationTest`.

Connect timeout was deliberately left at 3 s. A longer connect timeout only delays fail-fast on
DNS and routing failures.

**New worst-case path:** 15 s × 3 attempts + 2 s + 4 s = **51 s total**.

**30 s was never a hard constraint.** This flow is asynchronous — it does not block Meta's webhook
acknowledgement. 51 s is a UX/latency consideration, not an engineering limit.

Retry attempt count and backoff intervals remain hardcoded in `OpenAiRetryConfig.java`. Externalising
them was judged not worth the invasiveness in this tranche (see follow-up 5 below).

---

## Decision 5 — Fallback dispatched from the handler, one choke point

The customer-facing fallback is sent from `WhatsappInboundEventHandler.handlePreDispatchFlows()`,
not from inside the agents.

Rationale: the correct WhatsApp line (determined by audience and storeId from `LineContext`) is only
available in the handler. A fallback inside the agent would have required passing line-routing state
into it. More importantly, a single choke point is what actually covers all nine null-return exits
rather than nine separate patches.

Fallback copy:

```
Sorry, I'm having a bit of trouble right now 🙏 Please try again in a moment, or reach us on
WhatsApp at +27812815707 or email hello@curiousoft.dev.
```

The contact string matches the established house convention already used in five existing
customer-facing AI templates in `AiAgentConfigInitializer.java`.

---

## Decision 6 — AC-07 amended: selected null exits do NOT send the fallback (FIX-01)

The original acceptance criterion required all nine null exits from `handleWhatsappQueryForAgent`
to dispatch the fallback. The first implementation did exactly that and **Code Review FAILED it**:

`extractUserText` legitimately returns `null` for IMAGE and INTERACTIVE messages. With the original
implementation, every driver document upload would have received **both** the apology and the real
confirmation (double message), and every delivery-quote-acceptance tap would have been answered with
an erroneous apology — the inverse of the bug being fixed, on two critical flows.

**Amendment:** exits E-1 (`message == null`), E-2 (no processable text), and E-3 (`!enabled`)
deliberately send **no** fallback. All genuine-failure exits — E-4 (misconfigured system prompt),
E-5/6/7 (exhausted retries), E-8 (non-retryable 4xx), E-9 (catch-all), and the equivalent exits
in `StoreAiAgent` — all dispatch the fallback.

Implemented as a guard on `aiCustomerService.extractUserText(message)` as single source of truth.
A hardcoded message-type enumeration was explicitly rejected; it would drift from the agents' own
classification logic.

The guard sits after the verification-consent early return so consent replies are unaffected.
LOCATION messages continue to reach the AI agent via `Location#describe()`.

**Governing principle:** no silence on genuine failure, and no spurious apology on input the AI
legitimately has nothing to say about.

---

## Verification Record

Full unscoped suite: `./mvnw clean test` — **918 tests, 0 failures, 0 errors, 4 skipped**.
Independently reproduced by Developer, Code Reviewer, and QA.

Live-fire on `uat` profile, sender `+27812815707` only:

| Scenario | Expected | Result |
|---|---|---|
| 503 stub | `[AI-ATTEMPT] attempt=1/2/3` at T+0/+2s/+6s, then fallback with `wamid` | PASS |
| Invalid API key (real OpenAI 401) | Exactly 1 attempt, no retry, fallback with `wamid` | PASS |
| `AI_FALLBACK_MESSAGE` on exhaustion | Delivered with `wamid` on both paths | PASS |
| Clean boot | `Started IjudiApplication`, no `NoUniqueBeanDefinitionException`, `connect=3s response=15s` logged | PASS |
| FIX-01: IMAGE inbound | `[AI-SKIP]` logged, no fallback sent | PASS |
| FIX-01: INTERACTIVE inbound | `[AI-SKIP]` logged, no fallback sent | PASS |
| FIX-01: LOCATION inbound | AI invoked, `wamid` delivered | PASS |

Logging convention confirmed: attempt=1 at INFO (non-retried 4xx leaves exactly one greppable
`attempt=1` line as positive evidence the call was made), attempt≥2 at WARN.

Full test plan and live-fire notes: `docs/test-plans/platform/openai-retry-resilience.md`

---

## Consequences

### New in codebase
- `spring-retry` 2.0.10 is the **first retry library in ijudi-api**. Zero prior matches for
  `spring-retry|spring-aspects|@Retryable|RetryTemplate` anywhere in the tree.
- `OpenAiRetryConfig.java` (new class, `izinga-messaging`) — sole source of retry policy and
  backoff configuration.
- `@Bean("openAiRestTemplate")` in `YocoHeaderConfig` (`izinga-yoco-pay`) — sole RestTemplate with
  explicit timeouts.

### No breaking changes
- No API contracts changed.
- No shared models or enums changed.
- No existing endpoints, tool names, or response shapes changed.
- Repos `izinga-messaging`, `izinga-yoco-pay`, and `izinga-ordermanager` are the only modules
  touched. All other iZinga repos are unaffected.

### Boot safety
- `@Primary` on the shared `restTemplate()` bean prevents `NoUniqueBeanDefinitionException` on
  startup. No production bean body was modified.

---

## Alternatives Considered

**`@Retryable` annotation:** Rejected — both agents' `catch (Exception e)` blocks return `null`
rather than re-throw, so exceptions never reach the CGLIB proxy that makes `@Retryable` work.

**Hand-rolled retry loop:** Rejected — equivalent semantics, more code, lower readability.

**Add timeouts to the shared `restTemplate()` bean:** Rejected — also serves payment and email paths;
cross-cutting regression risk exceeds the benefit.

**Fallback inside each agent:** Rejected — line-routing context (`audience`, `storeId`) is not
available inside the agent; would require structural changes wider than the fix.

**Hardcode message-type list to guard fallback dispatch:** Rejected — would drift from agents'
own classification logic. `extractUserText()` is the authoritative classifier.

---

## Known Gaps and Follow-ups

| ID | Description | Status |
|---|---|---|
| GAP-001 | HTTP 429 retry path is unit-tested against the production bean but not live-fired — a real rate-limit condition cannot be induced on demand | Open — QA limitation |
| GAP-002 | Context-window-overflow HTTP 400 root cause is not fixed — out of scope; ADR-031 guarantees only that it is never retried and receives the fallback instead of silence | Deferred |
| GAP-003 | `AiAgentConfig` missing `@Builder.Default` on `active` and `version` fields — untouched | Separate task |
| GAP-004 | `AiCustomerServiceAgentExample` and dead 2-arg `AiCustomerServiceAgent.handleWhatsappQuery` overload have no production callers; their retry wrapper is never exercised at runtime | Cleanup deferred |
| GAP-005 | Retry attempt count and backoff intervals not yet externalised to `application.yml` | Deferred |
| GAP-006 | `AiCustomerServiceAgentTest` remains class-level `@Disabled` — deliberately not re-enabled in this tranche | Deferred |

---

## Files Changed

- `izinga-messaging/pom.xml` — `spring-retry` dependency added
- `izinga-messaging/.../ai/OpenAiRetryConfig.java` — new: retry policy, backoff policy, bean
- `izinga-messaging/.../ai/AiCustomerServiceAgent.java` — inject qualified beans, wrap call in `RetryTemplate`, FIX-01 guard on `extractUserText`
- `izinga-messaging/.../ai/StoreAiAgent.java` — inject qualified beans, wrap call in `RetryTemplate`, FIX-01 guard
- `izinga-yoco-pay/.../config/YocoConfiguration.kt` — `@Primary` on shared bean, new `openAiRestTemplate` bean with configurable Apache HC5 timeouts
- `izinga-ordermanager/src/main/resources/application.yml` — `izinga.ai.openai.connect-timeout-seconds` and `izinga.ai.openai.response-timeout-seconds` properties
- `izinga-messaging/.../whatsapp/webhooks/WhatsappInboundEventHandler.java` — fallback dispatch, FIX-01 guard
- `izinga-messaging/.../ai/OpenAiRetryConfigTest.java` — new: retry policy unit tests including `TooManyRequests` subclass resolution
- `izinga-yoco-pay/.../config/YocoConfigurationTest.java` — timeout binding tests (defaults and overrides)
- `izinga-messaging/.../whatsapp/webhooks/WhatsappInboundEventHandlerTest.java` — fallback and FIX-01 test cases
