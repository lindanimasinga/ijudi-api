---
userType: admin (platform integration)
Feature: MCP tools — tokenless auth bypass fix (SEC-MCP-01/02, fix/mcp-tokenless-auth-bypass); find_stores and start_order AI agent tools (SA-022-01 guards); BSON field name fix (e5508de); basketJson @ToolParam example corrected (fix/mcp-basket-item-toolparam-example)
App: izinga-ordermanager + izinga-usermanagement + izinga-commons (MCP surface, 18 tools, CallerIdentityValidator, StoreScopeValidationFilter, CallerIdentityContext)
Last verified: 2026-11-03 | Gate 1 live uat profile re-verification (fix/mcp-tokenless-auth-bypass) | PASS
---

## Scope

Covers:

- **SEC-MCP-01/02 (fix/mcp-tokenless-auth-bypass)**: FIX-03/04/05 — tokenless auth bypass fixes on 18 MCP tools; guards moved before any repo/data access; `rejectTokenlessMcp` infrastructure in CallerIdentityValidator; ThreadLocal isolation; 20 live security test cases (SEC-01 through SEC-20)
- `McpOrderService#startOrder` — all three SA-022-01 guards and happy-path creation
- Guard 1: customer identity existence check (`UserProfileRepo.existsById`)
- Guard 2: `customerConfirmed` must be `true` (consent gate)
- Guard 3a: idempotency key must be non-blank / non-null
- Guard 3b: duplicate within 24-hour window returns existing order (idempotent response)
- Domain exception translation — raw MongoDB ObjectIds must not leak in MCP response messages
- Unexpected exception fallback — generic sanitized message
- `McpToolRegistrationTest` — all MCP tool names registered correctly on the classpath
- BSON field name fix: `findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow` query correctly maps `tag.idempotencyKey` field (e5508de)
- basketJson `@ToolParam` example corrected: `discountPerc` replaces `totalPrice` (fix/mcp-basket-item-toolparam-example)
- JUnit 4 → JUnit 5 conversion of `McpOrderServiceTest` (previously silently skipped by Surefire; 8 tests now actually run)
- AC-02 drift guard: reflection-based test reads live `@ToolParam` description and deserializes the inline example via the production mapper
- AC-03 old-shape behavior: empirically asserts that the old example (`totalPrice`) silently succeeded in production (FAIL_ON_UNKNOWN_PROPERTIES=false, discountPerc defaulted to 0.0)

Out of scope: `McpSseEndpointTest` (requires live MongoDB — `@Disabled`, manual only); `find_stores` tool is covered by `McpToolRegistrationTest`; store browse logic is delegated to `StoreService`.

## Repos affected

| Repo | Work |
|---|---|
| ijudi-api / izinga-ordermanager | SEC-MCP-01/02: OrderServiceImpl (FIX-03/04), StoreScopeValidationFilter, StoreScopeValidator; McpOrderService, McpToolRegistrationTest, McpSseEndpointTest, McpOrderServiceTest, OrderRepository query |
| ijudi-api / izinga-usermanagement | SEC-MCP-01/02: UserProfileService (FIX-05 for find_user_by_phone + create_user) |
| ijudi-api / izinga-commons | SEC-MCP-01/02: CallerIdentityValidator (rejectTokenlessMcp, validateMcpAudience), CallerIdentityContext (ThreadLocal holder), CallerIdentityViolationException |

## Known-good test fixtures

### Unit test fixtures (mocked — no real MongoDB)
| Field | Value |
|---|---|
| Test customer ID | "cust-001" |
| Test shop ID | "shop-001" |
| Test idempotency key | "order-key-abc123" |
| Idempotency window | 24 hours |
| Basket JSON (corrected — use this) | `{"items":[{"name":"chips","quantity":2,"price":10,"discountPerc":0}]}` |
| Basket JSON (old shape — do NOT use in new code) | `{"items":[{"name":"chips","quantity":2,"price":10,"totalPrice":20}]}` — silently succeeds in production; totalPrice is unknown-field, discountPerc defaults 0.0 |
| Shipping JSON | `{"fromAddress":"1 Sender St","toAddress":"2 Dest Rd","type":"SCHEDULED_DELIVERY"}` |
| Domain error with raw ObjectId | message: "shop with id abcdef1234567890abcdef12 does not exist" |
| Expected sanitized output | does not contain "abcdef1234567890abcdef12"; does contain "does not exist" |

### Live sandbox fixtures (real MongoDB — izinga-sandbox)
| Field | Value | Notes |
|---|---|---|
| Sandbox customer ID | `5jIWdGqg5IZ3wAyTtFNDPnRqLrn1` | phone +27812815707; verified 2026-10-21 |
| Sandbox test store ID | `330d68de-03dc-4f34-875c-8acc455cd022` | "Unique Store ABC123XYZ"; verified 2026-10-21 |
| Live idempotency key prefix | `qa-test-<timestamp>-<label>` | ensure unique per call |
| Live shipping data | `{"fromAddress":"1 Sandton Drive Johannesburg","toAddress":"10 Bryanston Place","type":"SCHEDULED_DELIVERY"}` | store rejects SCHEDULED_DELIVERY — use for deserialization proof only |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| MCP-01 | customerConfirmed=false — rejected before any repo call | Unit | customerConfirmed=false | success=false, message contains "confirmation", no repo interactions | Guard 2 |
| MCP-02 | blank idempotency key — rejected before any repo call | Unit | idempotencyKey="  " | success=false, message contains "idempotency", no repo interactions | Guard 3a blank |
| MCP-03 | null idempotency key — rejected before any repo call | Unit | idempotencyKey=null | success=false, no repo interactions | Guard 3a null |
| MCP-04 | customer not found — generic rejection, domain never reached | Unit | existsById=false | success=false, message contains "no customer account", orderService.startOrder never called | Guard 1 |
| MCP-05 | duplicate within 24h window — returns existing order, no new order | Unit | existsById=true, repo returns existing order | success=true, isIdempotent=true, orderId="existing-id", orderService.startOrder never called | Guard 3b |
| MCP-06 | happy path — creates new order, stamps idempotency key in tag | Unit | all guards pass, no duplicate found | success=true, isIdempotent=false, orderId="new-order-id", tag["idempotencyKey"]=key | Happy path |
| MCP-07 | domain throws IllegalArgumentException with raw ObjectId — message sanitized | Unit | orderService.startOrder throws IllegalArgumentException with 24-hex-char ID | success=false, raw 24-char hex absent from message, readable text retained | Exception translation |
| MCP-08 | domain throws unexpected RuntimeException — generic message returned | Unit | orderService.startOrder throws RuntimeException | success=false, message contains "could not be created" | Generic exception |
| MCP-09 | Tool registration — find_stores and start_order registered on classpath | Unit | Spring context scan of ToolCallbackProvider beans | both tool names present in registered tool list | Tool wiring |
| MCP-10 | AC-01/AC-02 drift guard — basketJson @ToolParam example contains discountPerc, no totalPrice, deserializes via production mapper | Unit/Reflection | read live annotation via reflection + objectMapper.readValue via Config().mapper() | contains discountPerc=true, contains totalPrice=false; Basket with 1 item: name=chips, qty=2, price=10.0, discountPerc=0.0 | Annotation drift |
| MCP-11 | AC-03 old-shape behavior — old example (totalPrice) silently succeeds in production | Unit | `{"items":[{"name":"chips","quantity":2,"price":10,"totalPrice":20}]}` via Config().mapper() | no exception; basket deserialized; item discountPerc=0.0 (default); totalPrice=20.0 (computed) | FAIL_ON_UNKNOWN_PROPERTIES=false behavior |
| SEC-01 | find_order_by_id tokenless — non-existent ID must return IDENTITY_REFUSAL before DB lookup | Live/MCP | tokenless POST /mcp, orderId="nonexistent-order-id-qa" | isError=true, text contains "IDENTITY_REFUSAL: This tool requires an authenticated scope token" | FIX-03: guard before orderRepo.findById |
| SEC-02 | find_order_by_id tokenless — REAL UAT order ID must still return IDENTITY_REFUSAL (no data leak) | Live/MCP | tokenless POST /mcp, orderId="1623320122" (real sandbox order) | isError=true, text contains "IDENTITY_REFUSAL" — no order data visible | FIX-03: guard first, before DB never returns data |
| SEC-03 | find_order_for_messenger tokenless — non-existent ID | Live/MCP | tokenless, orderId="nonexistent-for-messenger-qa" | isError=true, IDENTITY_REFUSAL | FIX-04: guard before orderRepo.findById.orElseThrow |
| SEC-04 | find_order_for_messenger tokenless — real UAT order ID (no data leak) | Live/MCP | tokenless, orderId="1623320122" | isError=true, IDENTITY_REFUSAL — no order data | FIX-04: guard first |
| SEC-05 | find_user_by_phone tokenless — short phone (length < 9) must return IDENTITY_REFUSAL, not validation error | Live/MCP | tokenless, phone="12345" | isError=true, IDENTITY_REFUSAL — not "phone too short" or 500 | FIX-05: rejectTokenlessMcp before phone.length < 9 |
| SEC-06 | find_user_by_phone tokenless — real-length phone | Live/MCP | tokenless, phone="+27811285707" | isError=true, IDENTITY_REFUSAL | FIX-05: rejectTokenlessMcp first |
| SEC-07 | create_user tokenless — no mobileNumber | Live/MCP | tokenless, profile without mobileNumber | isError=true, IDENTITY_REFUSAL — not null-pointer or validation error | FIX-05: rejectTokenlessMcp before mobileNumber null check |
| SEC-08 | find_orders_by_messenger_id tokenless | Live/MCP | tokenless, id="nonexistent-driver-id" | isError=true, IDENTITY_REFUSAL | inline null-audience guard in OrderServiceImpl |
| SEC-09 | find_users tokenless | Live/MCP | tokenless, page=0 size=10 | isError=true, IDENTITY_REFUSAL (STORE audience required) | CallerIdentityValidator.validateMcpAudience |
| SEC-10 | find_store_or_shops_by_id tokenless (param=id) | Live/MCP | tokenless, id=STORE_ID | isError=true, "Audience required but no scope token is present" | StoreScopeValidator.validateAudience (pre-existing path, different message from IDENTITY_REFUSAL — isError=true is correct) |
| SEC-11 | find_stores tokenless | Live/MCP | tokenless, no params | isError=true, "Audience required but no scope token is present" | StoreScopeValidator.validateAudience (same pre-existing path as SEC-10) |
| SEC-12 | start_order tokenless | Live/MCP | tokenless, basket+customer params | isError=true, IDENTITY_REFUSAL | CallerIdentityValidator.rejectTokenlessMcp |
| SEC-13 | get_missing_fields_by_phone tokenless (param=mobileNumber) | Live/MCP | tokenless, mobileNumber="+27811285707" | isError=true, IDENTITY_REFUSAL | rejectTokenlessMcp via find_user_by_phone |
| SEC-14 | Document tools tokenless (4 tools: get_required_documents_for_user, recheck_required_documents, create_document_upload_session, attach_uploaded_document_to_user_tag) | Live/MCP | tokenless | isError=true, IDENTITY_REFUSAL | CallerIdentityValidator guards |
| SEC-15 | get_payouts_for_user tokenless — BUG-02 (pre-existing) | Live/MCP | tokenless, phone="+27811234567" | isError=true, "object is not an instance of declaring class" (Spring AI CGLIB reflection error) | Pre-existing BUG-02; security enforcement fails before IDENTITY_REFUSAL guard; no data leaked |
| SEC-16 | ThreadLocal bleed — tokened call then tokenless call on same path | Live/MCP | Step1: CUSTOMER token + find_user_by_phone; Step2: tokenless + find_user_by_phone | Step1: isError=false; Step2: isError=true IDENTITY_REFUSAL — no bleed of Step1 audience | CallerIdentityContext.clear() in finally block |
| SEC-17 | Tokened regression — find_order_by_id with valid STORE token + real order | Live/MCP | STORE scope token + orderId="1623320122" | isError=false, order JSON returned (2246+ chars) | Full path unblocked for valid token |
| SEC-18 | Tokened regression — find_store_or_shops_by_id STORE token (param=id) | Live/MCP | STORE scope token + id=STORE_ID | isError=false, store JSON returned (2707+ chars), mainPayAccount absent | STORE audience accepted, masking in effect |
| SEC-19 | REST regression — GET /store/{id} unchanged behavior | Live/REST | GET /store/STORE_ID (no MCP) | HTTP 200, store JSON, isMcpRequest=false (guard never fires) | CallerIdentityContext NOT set on non-MCP threads |
| SEC-20 | Scheduler thread isolation — 0 CallerIdentity violations from scheduled tasks | Live/Log | grep app log for MCP-IDENTITY-VIOLATION from non-HTTP threads | 0 entries | IS_MCP_REQUEST ThreadLocal never set on scheduler threads |

## Integration test scenarios

`McpSseEndpointTest` covers the full SSE handshake and tool invocation against a live MongoDB instance. It is `@Disabled` in CI (requires a running MongoDB) — run manually before release against the UAT profile:

```bash
# Un-disable the test class, then:
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
./mvnw -pl izinga-ordermanager -am -Dspring-boot.run.profiles=uat test -Dtest=McpSseEndpointTest
```

This is a known gap at Gate 1 level; must be run manually before production release.

## Regression checks

- [ ] `OrderRepository#findFirstByCustomerIdAndTagIdempotencyKeyWithinWindow` query maps `tag.idempotencyKey` BSON field correctly (e5508de fix)
- [ ] Standard `startOrder` (non-MCP path) still creates orders without idempotency key check
- [ ] `StoreService` browse logic unaffected by MCP wiring
- [ ] MCP SSE endpoint (`/mcp/sse`) still responds to SSE `initialize` handshake

## Live verification note (2026-11-03) — SEC-MCP-01/02 re-verification

Re-verification of fix/mcp-tokenless-auth-bypass (FIX-03/04/05) against app on uat profile, port 18080.

**Build**: `./mvnw clean test` — 946 tests / 0 F / 0 E / 18 pre-existing @Disabled skips. BUILD SUCCESS.

**App start**: `./mvnw -pl izinga-ordermanager -am spring-boot:run -Dspring-boot.run.profiles=uat` on port 18080 with `STORE_SCOPE_JWT_SECRET=qa-re-verification-test-secret-for-mcp-auth-bypass-fix-round3` (local override).

**Protocol**: MCP STREAMABLE (`POST /mcp`, `Accept: text/event-stream, application/json`).

**Known-good sandbox fixtures used**:
- Sandbox store ID: `330d68de-03dc-4f34-875c-8acc455cd022`
- Real UAT order ID: `1623320122` (found via find_orders_by_user_id with STORE token, store owner `650f5078-10aa-4f98-b6e3-eac2fbd276ad`)
- Sandbox customer phone: `+27811285707`

**FIX-03 (find_order_by_id)**: Guard is first in method (before `orderRepo.findById`). Both non-existent and real UAT order ID tokenless calls return IDENTITY_REFUSAL. PASS.

**FIX-04 (find_order_for_messenger)**: Guard is first in method (before `orderRepo.findById().orElseThrow`). Both non-existent and real UAT order ID tokenless calls return IDENTITY_REFUSAL. PASS.

**FIX-05 (find_user_by_phone)**: `rejectTokenlessMcp` is first statement (before `phone.length < 9` check). Short phone (length < 9) AND real-length phone tokenless both return IDENTITY_REFUSAL, not validation error. PASS.

**FIX-05 (create_user)**: `rejectTokenlessMcp` is first statement (before `mobileNumber` null check). Tokenless call without mobileNumber returns IDENTITY_REFUSAL, not NPE or validation error. PASS.

**TC-3e sweep (all 18 MCP tools tokenless)**:
- find_order_by_id, find_order_for_messenger: IDENTITY_REFUSAL (FIX-03/04) PASS
- find_user_by_phone, create_user: IDENTITY_REFUSAL (FIX-05) PASS
- find_orders_by_messenger_id: IDENTITY_REFUSAL (inline guard) PASS
- find_users, find_stores_by_owner, find_orders_by_phone_number, find_orders_by_user_id, start_order: IDENTITY_REFUSAL PASS
- get_missing_fields_by_phone (param=mobileNumber): IDENTITY_REFUSAL PASS
- 4 document tools: IDENTITY_REFUSAL PASS
- find_store_or_shops_by_id, find_stores: "Audience required but no scope token" (isError=true — pre-existing path, different message, see defects)
- get_payouts_for_user: BUG-02 "object is not an instance of declaring class" (isError=true — pre-existing CGLIB issue, not introduced by this fix)

**TC-3f tokened regression**:
- find_user_by_phone CUSTOMER token + own phone: isError=false PASS
- find_order_by_id STORE token + real order ID: isError=false, 2246 chars PASS
- find_store_or_shops_by_id STORE token + id param: isError=false, 2707 chars, mainPayAccount absent PASS

**TC-3g REST regression**: GET /store/{STORE_ID} → 200 OK, store data, CallerIdentityContext NOT set. PASS.

**TC-3h ThreadLocal bleed**: Tokened call (CUSTOMER, audience set) → tokenless call on same tool → tokenless still rejected with IDENTITY_REFUSAL. No bleed. PASS.

**TC-3i Scheduler isolation**: 0 MCP-IDENTITY-VIOLATION entries from scheduler threads. All 22 violation entries are from test calls on HTTP threads only. PASS.

**Previous session empty-text anomaly**: The empty-text `isError:true` response observed for `find_orders_by_messenger_id` in the previous session was caused by a stale/expired MCP session ID. Fresh session correctly returns IDENTITY_REFUSAL. Not a code defect.

**Gate 1 verdict**: PASS — all acceptance criteria met, no security regression, no data leak from tokenless calls.

---

## Live verification note (2026-10-21)

Run by QA against real app on uat profile (local port 80, Spring profile `uat`, sandbox MongoDB `izinga-sandbox`).

### Tool schema (AC-01 live)
MCP STREAMABLE transport at `POST /mcp`. `tools/list` response confirmed:
- `start_order.basketJson.description` contains `"discountPerc"`: YES
- `start_order.basketJson.description` contains `"totalPrice"`: NO

Raw live schema fragment:
```
JSON of the Basket with items array, e.g. {"items":[{"name":"chips","quantity":2,"price":10,"discountPerc":0}]}
```

### Basket shape deserialization (three shapes, real app)
All three shapes tested using real sandbox customer `5jIWdGqg5IZ3wAyTtFNDPnRqLrn1` and store `330d68de-03dc-4f34-875c-8acc455cd022`.

| Shape | Payload | Result | Rejection source |
|---|---|---|---|
| A (old, totalPrice) | `{"items":[{"name":"chips","quantity":2,"price":10,"totalPrice":20}]}` | success=false | Domain layer ("Collection or scheduled orders not allowed for Unique Store ABC123XYZ") |
| B (no discountPerc) | `{"items":[{"name":"chips","quantity":2,"price":10}]}` | success=false | Domain layer (same message) |
| C (new, discountPerc=0) | `{"items":[{"name":"chips","quantity":2,"price":10,"discountPerc":0}]}` | success=false | Domain layer (same message) |

Server log evidence (all three show `WARN start_order: domain layer rejected order`, zero `order construction failed` entries):
- No deserialization error for any shape
- All three reached `OrderServiceImpl.startOrder()` and were rejected there

### Conclusion
The old documented example was NOT causing hard failures in production. All three shapes successfully deserialize. The fix is a documentation accuracy improvement: `discountPerc` is the actual constructor parameter and must appear in the documented example.

## Known gaps / Known defects

| Item | Severity | Location | Date found | Status |
|---|---|---|---|---|
| McpSseEndpointTest (2 tests) requires live MongoDB — @Disabled in CI | Medium | McpSseEndpointTest.java:57 | 2026-10-20 | Open |
| find_stores tool not covered by McpOrderServiceTest (separate tool, no unit test beyond registration) | Low | McpToolRegistrationTest | 2026-10-20 | Open |
| PRE-EXISTING: `OrderServiceImpl.startOrder(line 147)` NPE when `getShippingData()` is null on INSTORE orders | Medium | OrderServiceImpl.java:147 | 2026-10-21 | Open |
| BUG-02 (pre-existing): `get_payouts_for_user` throws Spring AI CGLIB reflection error "object is not an instance of declaring class" before any security guard fires; isError=true but no IDENTITY_REFUSAL message; no data leaked | High | izinga-recon or usermanagement get_payouts_for_user @Tool method | 2026-11-03 | Open — separate issue, not introduced by SEC-MCP-01/02 fix |
| find_stores / find_store_or_shops_by_id return "Audience required but no scope token is present on this request" (from StoreScopeValidator.validateAudience) instead of IDENTITY_REFUSAL message — inconsistent error message format, but isError=true is correct | Low | StoreService.java (StoreScopeValidator.validateAudience call) | 2026-11-03 | Open — pre-existing; not introduced by this fix |
| NOTE: empty-text response seen for find_orders_by_messenger_id in previous QA session was a STALE MCP SESSION artifact — fresh session correctly returns IDENTITY_REFUSAL; NOT a code defect | Info | N/A | 2026-11-03 | Resolved/Non-issue |

No defects introduced by fix/mcp-tokenless-auth-bypass (FIX-03/04/05) as of 2026-11-03.

## Validation commands

```bash
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api

# Scoped (fast iteration during development):
./mvnw -pl izinga-ordermanager -am test

# Final Gate check (mandatory — full unscoped reactor):
./mvnw test
```
