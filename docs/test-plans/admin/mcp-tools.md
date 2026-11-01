---
userType: admin (platform integration)
Feature: MCP tools — find_stores and start_order — AI agent tools for discovering stores and creating orders; SA-022-01 guards (customerConfirmed, identity, idempotency); BSON field name fix (e5508de); basketJson @ToolParam example corrected (fix/mcp-basket-item-toolparam-example)
App: izinga-ordermanager (MCP SSE endpoint, McpOrderService, OrderRepository)
Last verified: 2026-10-21 | Gate 1 automated (fix/mcp-basket-item-toolparam-example) + live uat profile verification | PASS
---

## Scope

Covers:

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
| ijudi-api / izinga-ordermanager | All new code; McpOrderService, McpToolRegistrationTest, McpSseEndpointTest, McpOrderServiceTest, OrderRepository query |

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

| Item | Severity | Location | Date found |
|---|---|---|---|
| McpSseEndpointTest (2 tests) requires live MongoDB — @Disabled in CI | Medium | McpSseEndpointTest.java:57 | 2026-10-20 |
| find_stores tool not covered by McpOrderServiceTest (separate tool, no unit test beyond registration) | Low | McpToolRegistrationTest | 2026-10-20 |
| PRE-EXISTING: `OrderServiceImpl.startOrder(line 147)` NPE when `getShippingData()` is null on INSTORE orders — `getShippingData().getType()` called without null check | Medium | OrderServiceImpl.java:147 | 2026-10-21 |

No defects introduced by fix/mcp-basket-item-toolparam-example as of 2026-10-21.

## Validation commands

```bash
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api

# Scoped (fast iteration during development):
./mvnw -pl izinga-ordermanager -am test

# Final Gate check (mandatory — full unscoped reactor):
./mvnw test
```
