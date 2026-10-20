---
userType: admin (platform integration)
Feature: MCP tools — find_stores and start_order — AI agent tools for discovering stores and creating orders; SA-022-01 guards (customerConfirmed, identity, idempotency); BSON field name fix (e5508de)
App: izinga-ordermanager (MCP SSE endpoint, McpOrderService, OrderRepository)
Last verified: 2026-10-20 | Gate 1 automated (feature/mcp-find-stores-start-order) + Gate 2 full regression (release/1.16.0) | PASS
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

Out of scope: `McpSseEndpointTest` (requires live MongoDB — `@Disabled`, manual only); `find_stores` tool is covered by `McpToolRegistrationTest`; store browse logic is delegated to `StoreService`.

## Repos affected

| Repo | Work |
|---|---|
| ijudi-api / izinga-ordermanager | All new code; McpOrderService, McpToolRegistrationTest, McpSseEndpointTest, McpOrderServiceTest, OrderRepository query |

## Known-good test fixtures

| Field | Value |
|---|---|
| Test customer ID | "cust-001" |
| Test shop ID | "shop-001" |
| Test idempotency key | "order-key-abc123" |
| Idempotency window | 24 hours |
| Basket JSON | `{"items":[{"name":"chips","quantity":2,"price":10,"totalPrice":20}]}` |
| Shipping JSON | `{"fromAddress":"1 Sender St","toAddress":"2 Dest Rd","type":"SCHEDULED_DELIVERY"}` |
| Domain error with raw ObjectId | message: "shop with id abcdef1234567890abcdef12 does not exist" |
| Expected sanitized output | does not contain "abcdef1234567890abcdef12"; does contain "does not exist" |

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

## Known gaps / Known defects

| Item | Severity | Location | Date found |
|---|---|---|---|
| McpSseEndpointTest (2 tests) requires live MongoDB — @Disabled in CI | Medium | McpSseEndpointTest.java:57 | 2026-10-20 |
| find_stores tool not covered by McpOrderServiceTest (separate tool, no unit test beyond registration) | Low | McpToolRegistrationTest | 2026-10-20 |

No open defects as of 2026-10-20.

## Validation commands

```bash
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api

# Scoped (fast iteration during development):
./mvnw -pl izinga-ordermanager -am test

# Final Gate check (mandatory — full unscoped reactor):
./mvnw test
```
