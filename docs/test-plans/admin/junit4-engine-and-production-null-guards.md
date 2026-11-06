# Test Plan: Admin / Platform — JUnit 4 Engine Fix + Production Null Guards

**userType:** admin (platform / engineering)
**Feature:** Restore JUnit 4 test execution (Surefire 3.2.5 / JUnit Platform silent-skip fix) and production null-guard fixes exposed once tests ran
**App:** `ijudi-api` (all modules)
**Last verified:** 2026-11-02, Gate 1 automated + live E2E run on uat profile — PASS (LV-4 and LV-5 NOT VERIFIED LIVE; see gaps)

## Scope

Covers:

1. **Test infrastructure fix** — `pom.xml`: adding `org.junit.vintage:junit-vintage-engine` (test scope, BOM-managed) so JUnit 4 tests are picked up by Surefire 3.2.5 under the JUnit Platform provider.
2. **Device.kt annotation target fix** — `@field:NotBlank` on the `token` field; previously `@NotBlank` was on the constructor parameter (bean validation-invisible), silently allowing blank device tokens.
3. **OrderServiceImpl null guards** — six null guards added around shippingData accesses that would NPE on INSTORE orders; `DeliveryCalculationService` extracted from static `IjudiUtils`; stage-progression terminal-stage guard.
4. **StoreService partial-patch OR guard** — new `update()` override preserves `profileApproved`, `hasPaymentAgreement`, and other admin-set boolean flags when a partial PATCH payload omits them.

Out of scope: Maps outage (503) path — cannot be induced against live Maps API; covered by unit tests `OrderControllerTest.startOrder_mapsOutage_returns503` and `DeliveryCalculationServiceTest`.

## Repos affected

- `ijudi-api` — all modules (`pom.xml`); `izinga-ordermanager`, `izinga-commons`, `izinga-usermanagement`, `izinga-messaging`, `izinga-recon`

## Known-good test fixtures

| Field | Value | Notes |
|---|---|---|
| Local uat boot command | `cd izinga-ordermanager && ../mvnw spring-boot:run -Dspring-boot.run.profiles=uat` | Do NOT use `-pl izinga-ordermanager -am spring-boot:run` — maven runs the goal on parent too, causing "Unable to find main class"; must `cd` into the module first |
| JAVA_HOME | `/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home` | Must be set; Homebrew JDK 23 breaks ordermanager tests |
| Required env var for boot | `STORE_SCOPE_JWT_SECRET` | No fallback; app fails loudly if missing. Any ≥32-char string works locally. |
| app-version header (uat) | `2.1.1` | min supported = 2.1.1 in application-uat.yml; omitting returns 500 "download new version" |
| Sandbox test store ID | `330d68de-03dc-4f34-875c-8acc455cd022` | "Unique Store ABC123XYZ"; not actively accepting orders in sandbox (verified 2026-11-02) |
| Sandbox customer ID | `5jIWdGqg5IZ3wAyTtFNDPnRqLrn1` | phone +27812815707 |
| Known-good from address | `1 Sandton Drive Johannesburg` | Successfully geocoded by Maps; returns distance |
| Known-good to address | `10 Bryanston Place Sandton` | Successfully geocoded by Maps; 7.74 km, R60.53 estimated delivery fee |
| Nonsense addresses | `XYZNONEXISTENT999QATEST`, `XYZNONEXISTENT888QATEST` | Used to induce Maps geocoding failure → 400 |
| Baseline test count (before fix) | 868 | JUnit 4 tests silently skipped by Surefire 3.2.5 without vintage-engine |
| Test count after fix | 1154 | All JUnit 4 + JUnit 5 tests running |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| TC-01 | junit-vintage-engine wires JUnit 4 tests into Surefire 3.2.5 | Build | `./mvnw clean test` on fixed branch | 1154 tests run, 0 failures, 0 errors | vintage-engine present |
| TC-02 | Without vintage-engine, JUnit 4 tests silently skip | Build | `./mvnw clean test` on unfixed branch | ~868 tests run, formerly-JUnit4 classes show 0 tests | vintage-engine absent |
| TC-03 | Failing JUnit 4 test turns build red | Build | JUnit 4 test with `fail()` in `@RunWith(MockitoJUnitRunner.class)` class | `[ERROR] BUILD FAILURE`, FAILURE in test output | vintage-engine wired end-to-end |
| TC-04 | Security test classes have non-zero counts | Build | `./mvnw clean test` | `StoreScopeValidatorTest`, `StoreScopedMcpToolTest`, `AgentIdentityAuditServiceTest`, `StoreScopeContextTest`, `ReconServiceImplCallerIdentityTest`, `OrderServiceImplCallerIdentityTest`, `McpOrderServiceTest` each show ≥1 test | formerly-silent security classes |
| TC-05 | No security assertions weakened | Code review | Diff of all 7 security test classes | 0 assertion removals; only fixture additions and ObjectMapper config changes | no regression |
| TC-06 | Device blank token rejected (LV-6a) | Live E2E | `POST /device {"token": ""}` with `app-version: 2.1.1` | HTTP 400 `{"message":["device token required"]}` | `@field:NotBlank` on field |
| TC-07 | Device whitespace-only token rejected (LV-6b) | Live E2E | `POST /device {"token": "   "}` | HTTP 400 `{"message":["device token required"]}` | `@NotBlank` whitespace check |
| TC-08 | delivery-price: unresolvable from-address → 400 (LV-3a) | Live E2E | `GET /order/delivery-price?category=FOOD&fromAddress=XYZNONEXISTENT999QATEST&toAddress=XYZNONEXISTENT888QATEST&shopId=330d68de-03dc-4f34-875c-8acc455cd022` | HTTP 400 `{"error":"Unable to calculate delivery distance: could not find the pickup (from) address..."}` | Maps IAE → 400 |
| TC-09 | delivery-price: real addresses → 200 with price (LV-3b) | Live E2E | `GET /order/delivery-price?category=FOOD&fromAddress=1+Sandton+Drive+Johannesburg&toAddress=10+Bryanston+Place+Sandton&shopId=330d68de-03dc-4f34-875c-8acc455cd022` | HTTP 200 `{"distanceKm":7.737,"estimatedDeliveryFee":60.5275,...}` | Maps happy path |
| TC-10 | POST /order with IAE-triggering input → 400 (LV-2) | Live E2E | `POST /order {"orderType":"ONLINE","shopId":"330d68de-...","stage":"STAGE_0_CUSTOMER_NOT_PAID","basket":{"items":[...]},"shippingData":{"type":"SCHEDULED_DELIVERY","pickUpTime":1794441600000,...}}` | HTTP 400 `{"error":"Collection or scheduled orders not allowed for Unique Store ABC123XYZ"}` | IAE → 400 path in POST /order |
| TC-11 | Kotlin JUnit 4 classes now have non-zero test counts | Build | `./mvnw clean test` after fix | `UserServiceTest` 12, `YocoPaymentControllerTest` 2, `TipsServiceTest` 1, `ReconServiceImplCallerIdentityTest` 4 | formerly-silent Kotlin modules |
| TC-12 | OrderServiceImpl — shippingData null guard prevents NPE on INSTORE orders | Unit | `OrderServiceTest` with INSTORE orderType, null shippingData | No NullPointerException; serviceFee = serviceFeePerc * basketAmount | null guard branch |
| TC-13 | StoreService partial-PATCH OR guard preserves profileApproved | Unit | `StoreServiceTest` with incoming patch that omits profileApproved | persisted profileApproved=true unchanged in result | OR semantics |

## Integration test scenarios

- **Full unscoped reactor** (`./mvnw clean test`) — 1154 tests, 0 failures confirms all modules including formerly-silent JUnit 4 classes are wired and passing.
- **Security class count verification** — run the build and grep Surefire XML under `*/target/surefire-reports/` for the 7 security test class names to confirm non-zero counts.
- **Device token validation across all user types** — POST /device with blank token should fail with 400 regardless of other fields.

## Regression checks

- [ ] All 1154 tests still pass after any further changes to `pom.xml`
- [ ] Security test classes still show non-zero counts after any Surefire or JUnit dependency changes
- [ ] `DeliveryCalculationService` injection still wired in `OrderServiceImpl` after any Spring context refactor
- [ ] `@field:NotBlank` on `Device.token` not removed during any Kotlin migration or model cleanup
- [ ] `StoreService.update()` OR guard for `profileApproved` and `hasPaymentAgreement` preserved after any store service refactor
- [ ] Probe pattern: to verify future JUnit 4 test wiring, create a `@RunWith(MockitoJUnitRunner.class)` test with `fail()`, confirm `BUILD FAILURE`, then delete

## Known gaps / Known defects

| Severity | Description | Location | Date found |
|---|---|---|---|
| MEDIUM (gap) | LV-4 NOT VERIFIED LIVE — POST /order with valid INSTORE/DELIVERY order and serviceFee confirmation: sandbox store `330d68de-03dc-4f34-875c-8acc455cd022` is not active ("Shop not available"). Covered by `OrderServiceTest`. | `OrderServiceImpl.startOrder` serviceFee null guard | 2026-11-02 |
| MEDIUM (gap) | LV-5 NOT VERIFIED LIVE — Store PATCH partial-patch OR guard: requires a valid Firebase JWT to PATCH /store/{id}; not feasible without auth credentials in automated QA context. Covered by `StoreServiceTest`. | `StoreService.update()` boolean OR guards | 2026-11-02 |
| LOW (gap) | Maps 503 path not tested live — cannot induce a real Maps outage against the live Maps API. Covered by `OrderControllerTest.startOrder_mapsOutage_returns503` and `DeliveryCalculationServiceTest`. | `DeliveryCalculationService`, `OrderController` | 2026-11-02 |

## Live verification (2026-11-02 run)

- **LV-1** — App boot on uat profile: PASS. App starts on port 80, connects to `cluster0-7odiz.mongodb.net/izinga-sandbox`, no startup errors. `STORE_SCOPE_JWT_SECRET` missing → fails loudly (confirmed no-fallback).
- **LV-2** — POST /order IAE → 400: PASS. Sandbox store returns `{"error":"Collection or scheduled orders not allowed for Unique Store ABC123XYZ"}` HTTP 400. IAE → HTTP 400 `{"error": msg}` path confirmed in OrderController.
- **LV-3** — delivery-price both paths: PASS. Nonsense addresses → HTTP 400 `{"error":"Unable to calculate delivery distance: could not find the pickup (from) address..."}`. Real addresses → HTTP 200 with `distanceKm: 7.737, estimatedDeliveryFee: 60.5275`.
- **LV-4** — POST /order succeeds with serviceFee: NOT VERIFIED LIVE. Sandbox store not active; covered by unit tests.
- **LV-5** — Store PATCH boolean OR guard: NOT VERIFIED LIVE. Requires Firebase auth JWT; covered by unit tests.
- **LV-6** — Device blank/whitespace token rejected: PASS. Both `{"token":""}` and `{"token":"   "}` → HTTP 400 `{"message":["device token required"]}`. Pre-fix behavior would have accepted blank tokens silently.

## Validation commands

- `export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home`
- `cd /Users/lindanimasinga/Documents/GitHub/ijudi-api-junit4-fix && ./mvnw clean test` (full reactor — mandatory final check)
- Scoped iteration only: `./mvnw -pl izinga-ordermanager -am test` (not sufficient for a PASS verdict)
