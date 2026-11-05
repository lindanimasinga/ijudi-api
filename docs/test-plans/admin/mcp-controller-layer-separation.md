---
userType: admin (platform integration)
Feature: MCP tools controller-layer separation — ADR-026 / MCP-SEC-03 (Phases 0–6); 18 @Tool methods moved from domain services to 6 @McpToolController classes; services de-gated for REST; bank masking at controller layer; ArchUnit R1–R6; single-provider McpConfig; @RequiresAudience retired; contract snapshot + access matrix safety nets
App: izinga-ordermanager (McpConfig, GuardedToolCallback, 6 McpToolControllers, McpToolContractSnapshotTest, McpAccessMatrixTest, McpArchitectureTest) + izinga-usermanagement + izinga-recon + izinga-messaging (StoreScopeJwtService)
Last verified: 2026-11-05 | Gate 1 ADR-026 Phases 0–6 (full unscoped suite + live UAT profile verification) | PASS
---

## Scope

Covers:

- **ADR-026 Phase 0**: @McpToolController annotation, GuardedToolCallback, McpConfig single-provider wiring, ArchUnit rules R1–R6 with non-vacuity guards, McpToolContractSnapshotTest (golden file), McpAccessMatrixTest (188-row matrix)
- **ADR-026 Phase 1**: OrderCreationMcpController (start_order), DocumentUploadMcpController (4 doc tools) created; services de-gated
- **ADR-026 Phase 2**: UserMcpController (find_user_by_phone, find_users, create_user, get_missing_fields_by_phone)
- **ADR-026 Phases 3+4**: OrderMcpController (5 order-read tools), StoreMcpController (3 store tools; bank masking moved to controller)
- **ADR-026 Phases 5+6**: PayoutMcpController (get_payouts_for_user); @RequiresAudience deleted; LEGACY_EXCLUSIONS removed; McpConfig takes single ApplicationContext parameter
- **AC-04**: services (OrderServiceImpl, McpOrderService, StoreService, UserProfileService, DocumentUploadMcpService, ReconServiceImpl) are context-free for REST callers — no MCP gate in service layer
- **AC-07 / REQ-08**: bank accountId masking at controller layer for MCP responses; REST responses return real accountId
- **AC-09 / AC-14**: clean startup, one provider (userProfileToolCallbackProvider), no duplicate-tool IllegalStateException, no circular-dependency errors
- **AC-11 / AC-12**: N/A (deferred — not in Phases 0–6)
- **AC-13**: deferred (Phase 2b, OQ-7 — create_user role assignment over MCP intentionally NOT restricted; create_user still accepts any role)
- **Known gap Phase 2b (create_user role)**: not implemented; 4 open questions (OQ-7 decision awaited from Lindani); create_user accepts any role including ADMIN over MCP in this release

Out of scope: Phase 2b (create_user role restriction), REST tier testing beyond AC-04 smoke checks, full create_user role-persistence matrix over live REST (covered in existing tests).

## Repos affected

- `izinga-ordermanager` — primary (all controller and config changes)
- `izinga-usermanagement` — services de-gated (UserProfileService, DocumentUploadMcpService)
- `izinga-recon` — service de-gated (ReconServiceImpl)
- `izinga-messaging` — StoreScopeJwtService (unchanged; used for scope JWT minting in live verification)

## Known-good test fixtures

**JWT minting (local dev):**
- Secret: `local-dev-secret-for-testing-only-min-32-chars` (documented in `application-uat.yml` comment; env var `STORE_SCOPE_JWT_SECRET` must be set to this value when running locally)
- Library: PyJWT 2.15.1, `jwt.encode(payload, SECRET, algorithm="HS256")`
- Issuer claim: `"izinga-agent-scope"`
- Audience enum values: `DRIVER`, `CUSTOMER`, `STORE`, `OTP`, `ADMIN` (NOT "MESSENGER")
- Scope query param: `?scope=<token>` appended to MCP URL

**Live sandbox data (uat profile, MongoDB izinga-sandbox):**
- Store ID: `8f3323bf-ca94-4b4a-bad7-c861ca68a823` (Chicken Licken Ntuzuma) — real bank accountId: `0734396642`; MCP-masked accountId: `6900576239` (uat mainPayAccount from secrets manager)
- Admin caller phone: `+27812815707` — has a CUSTOMER profile in sandbox (id: `5jIWdGqg5IZ3wAyTtFNDPnRqLrn1`)
- Order endpoint version header: `app-version: 2.1.1`

**Startup:**
- Run: `java -Dspring.profiles.active=uat -Dserver.port=8081 -DSTORE_SCOPE_JWT_SECRET=local-dev-secret-for-testing-only-min-32-chars -jar izinga-ordermanager/target/izinga-ordermanager-1.2.6-SNAPSHOT.jar`
- Expected startup: 21–25 seconds; 1 WARN "No tool methods found" (benign — default Spring AI empty provider); `INFO Registered tools: 18` from McpServerAutoConfiguration
- MCP endpoint: `POST http://localhost:8081/mcp` with `Content-Type: application/json`, `Accept: application/json, text/event-stream`; SSE `data:` lines carry JSON-RPC responses

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | Full unscoped reactor build | Automated | `./mvnw clean test` | BUILD SUCCESS, 0 failures, 0 errors | All |
| TC-02 | McpAccessMatrixTest — 188 rows | Automated | Full matrix | 0 failures | Access matrix |
| TC-03 | McpArchitectureTest — R1–R6 + non-vacuity | Automated | ArchUnit | 6 @ArchTest rules + 6 non-vacuity guards | Architecture |
| TC-04 | McpToolContractSnapshotTest — 5 assertions | Automated | Golden file | PASS (golden file unchanged since c71d029) | Contract |
| TC-05 | McpConfigTest — 4 tests | Automated | Spring context | PASS | Config |
| TC-06 | GuardedToolCallbackTest — 12 tests | Automated | GuardedToolCallback | PASS | Guard |
| TC-07 | 11 controller unit tests (OrderCreationMcp, DocumentUploadMcp, UserMcp, OrderMcp, StoreMcp, PayoutMcp) | Automated | Mock services | 128 total PASS | Controller logic |
| TC-08 | McpSseEndpointTest — 2 tests | Automated | SSE endpoint | PASS | Endpoint |
| TC-09 | Live: startup on uat profile, port 8081 | Live | Local run | App started in <30s, Registered tools: 18, no IllegalStateException | AC-09/AC-14 |
| TC-10 | Live: tools/list — exact 18 tools | Live | POST /mcp tools/list | 18 tool names match golden file names; schemas match after stripping $schema header | AC-01 |
| TC-11 | Live: tokenless find_user_by_phone | Live | No scope token | `IDENTITY_REFUSAL: This tool requires...` (isError: true) | Shape 1 |
| TC-12 | Live: tokenless find_stores | Live | No scope token | `Audience required but no scope token is present on this request` (isError: true) | Shape 2 |
| TC-13 | Live: tokenless start_order | Live | No scope token | `{"success":false,...,"message":"IDENTITY_REFUSAL..."}` (isError: false) | Shape 3 |
| TC-14 | Live: tokenless find_store_or_shops_by_id | Live | No scope token | `Audience required but no scope token is present on this request` (isError: true) | Shape 2 |
| TC-15 | Live: tokenless get_payouts_for_user | Live | No scope token | `IDENTITY_REFUSAL: This tool requires...` (isError: true) | Shape 1 |
| TC-16 | Live: CUSTOMER own phone find_user_by_phone | Live | CUSTOMER token, callerPhone=+27812815707, phone=+27812815707 | Returns user profile data (not error) | ALLOW own |
| TC-17 | Live: CUSTOMER other phone find_user_by_phone | Live | CUSTOMER token, callerPhone=+27812815707, phone=+27899999999 | `IDENTITY_REFUSAL: I can only assist with queries for your own account...` | DENY other |
| TC-18 | Live: ADMIN find_users | Live | ADMIN token | Returns array (empty or populated) without error | ALLOW ADMIN |
| TC-19 | Live: STORE own store find_store_or_shops_by_id | Live | STORE token, storeId=8f3323bf..., id=8f3323bf... | Returns store; bank.accountId=6900576239 (masked) | ALLOW STORE + masking |
| TC-20 | Live: STORE other store find_store_or_shops_by_id | Live | STORE token scoped to 8f3323bf..., id=other-store-id | `Store scope violation: access to store 'other-store-id' is not permitted by this token` | DENY other store |
| TC-21 | Live AC-04: REST /store/{id} ungated | Live | GET /store/8f3323bf... | Returns store with real bank.accountId=0734396642 (NOT masked) | AC-04, REST |
| TC-22 | Live AC-04: REST /user/{phone} ungated | Live | GET /user/+27812815707 | Returns user profile | AC-04, REST |
| TC-23 | Live AC-04: REST /order?shopId= ungated | Live | GET /order?shopId=test, app-version: 2.1.1 | Returns 3108 orders (not gated) | AC-04, REST |
| TC-24 | Golden file unchanged since Phase 0 | Git diff | `git diff c71d029 HEAD -- .../tool-contract-snapshot.json` | Empty (no output) | Contract stability |
| TC-25 | McpAccessMatrixTest diff is wiring-only | Git diff | `git diff c71d029 HEAD -- .../McpAccessMatrixTest.java` | Only imports + constructor wiring + comment updates; zero row changes | Safety-net stability |

## Integration test scenarios

- **MCP tokenless → REST**: confirm that a tokenless request to MCP is rejected, but the same resource is accessible via REST (AC-04): verified for /store/{id}, /user/{phone}, /order
- **MCP scope token → bank masking**: STORE-scoped JWT call to find_store_or_shops_by_id returns masked accountId; REST call to /store/{id} returns real accountId
- **MCP scope token → identity cross-check**: CUSTOMER token with callerPhone=X can only look up phone=X; any other phone is refused with VIOLATION_MSG

## Regression checks

- [x] Full reactor `./mvnw clean test` passes with 0 failures across all modules
- [x] ArchUnit rules R1–R6 execute (confirmed in surefire XML for McpArchitectureTest)
- [x] McpToolContractSnapshotTest golden file unchanged since Phase 0 (git diff empty)
- [x] McpAccessMatrixTest: 188 rows pass; diff since Phase 0 is wiring/comments only
- [x] All pre-existing service tests (OrderServiceImpl, StoreService, UserProfileService, ReconServiceImpl) still pass
- [x] No duplicate-tool IllegalStateException at startup
- [x] REST endpoints remain ungated (store, user, order)
- [x] Bank masking: MCP returns mainPayAccount; REST returns real accountId

## Known gaps / Known defects

| Defect | Severity | Location | Date found |
|---|---|---|---|
| create_user accepts any role over MCP (OQ-7 / Phase 2b not implemented) | Medium | OrderCreationMcpController (or UserMcpController) | 2026-11-05 (known, deliberate) |
| DRIVER own-id test requires sandbox profile to exist for the caller's phone | Low | McpCaller.resolveUserId | 2026-11-05 |
| `No tool methods found in provided tool objects: []` WARN at startup | Info | Spring AI SyncMcpToolProvider (benign — empty default provider) | 2026-11-05 |

## Validation commands

```bash
# Full unscoped suite (mandatory):
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api-adr026-phase0
./mvnw clean test

# Build and start locally (uat profile, port 8081):
./mvnw -pl izinga-ordermanager -am package -DskipTests
export STORE_SCOPE_JWT_SECRET=local-dev-secret-for-testing-only-min-32-chars
java -Dspring.profiles.active=uat -Dserver.port=8081 -DSTORE_SCOPE_JWT_SECRET=$STORE_SCOPE_JWT_SECRET \
  -jar izinga-ordermanager/target/izinga-ordermanager-1.2.6-SNAPSHOT.jar

# Verify MCP endpoint:
curl -s http://localhost:8081/mcp  # Expected: "Invalid Accept header. Expected TEXT_EVENT_STREAM"
```
