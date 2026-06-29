---
name: izinga-backend-developer
description: "Use when working on the iZinga backend in ijudi-api: Java 17 and Kotlin on Spring Boot 3, Spring Web REST APIs, JWT/OAuth2 resource server, MongoDB with Spring Data Mongo, Spring AI MCP server tools, and domain flows for users, stores, orders, drivers, WhatsApp/push messaging, store menus, Yoco payments, and recon payouts. Includes architecture-safe workflows for scanning modules, fixing bugs, and adding features without breaking existing contracts."
argument-hint: "Backend task goal, affected domain, and target module"
---

# iZinga Backend Developer Skill

## Outcome
Use this skill to make safe, architecture-aligned backend changes in ijudi-api.

This skill helps you:
- Map the correct module before editing.
- Preserve API contracts and shared models.
- Implement features and bug fixes with minimal cross-module risk.
- Validate with focused checks before finishing.

## When To Use
Use this skill for:
- Kotlin and Java 17 Spring Boot 3 changes.
- Spring Web controllers, services, validation, JSON payload changes.
- Spring Data Mongo repositories and query logic.
- JWT and OAuth2 resource server behavior.
- MCP tool additions and MCP server debugging.
- iZinga domain work: order management, users, stores, user roles, store type, WhatsApp, push notifications, order stages, drivers, store menu sync, Yoco payment, recon payouts.

Do not use this skill for:
- Frontend Angular work.
- Large schema redesign without migration planning.

## Project Architecture Snapshot
Repository root is multi-module Maven with Spring Boot parent.

Core modules and responsibilities:
- izinga-ordermanager:
  - Runtime API host and main integration hub.
  - Entry point: src/main/java/io/curiousoft/izinga/ordermanagement/IjudiApplication.java
  - Domain APIs: order, store, device, promotion, restricted regions, shopping-list.
  - Security and JWT/OAuth2 resource server config.
  - MCP server wiring via McpConfig using `MethodToolCallbackProvider.builder().toolObjects(...)`.
  - MCP transport endpoints exposed by Spring AI: `GET /sse` and `POST /mcp/message`.
- izinga-commons:
  - Shared models, enums, repositories, and validators.
  - MongoDB shared entities and reusable data contracts.
- izinga-usermanagement:
  - User profile CRUD, pending approvals, user config, wallet pass.
  - User config templates are stored in `userTypeConfig` and now support role mapping via `UserConfig.userRole`.
  - MCP tools for user lookup/create/location-based search.
- izinga-recon:
  - Payout and payout bundles (shop and messenger), CSV exports, bank config.
  - MCP tool for payouts lookup.
- izinga-messaging:
  - WhatsApp webhook and forwarding, Firebase push and Firestore integration, SMS support, AI agent config classes.
- izinga-yoco-pay:
  - Yoco payment initiation, webhook finalization, refunds.
- izinga-documentmanagement:
  - Document upload/delete and S3 integration.
- izinga-qrcode-generator:
  - Tip QR and promo code management.
- store-menu-to-izinga-menu:
  - Store menu ingestion/update utility (AWS/serverless style integration module).
  - In sandbox clones this submodule is often not initialized; local reactor builds may require a temporary `store-menu-to-izinga-menu/pom.xml` stub.

## Key Domain Contracts
Shared enums and models are in izinga-commons and must be treated as stable contracts:
- Profile roles: CUSTOMER, STORE_ADMIN, STORE, MESSENGER, MESSENGER_ADMIN, ADMIN.
- Store type: FOOD, CLOTHING, SALON, CAR_WASH, MOVERS, TIPS, LICENSING, PARTS.
- Order stages: STAGE_0_CUSTOMER_NOT_PAID through STAGE_7_ALL_PAID, CANCELLED.

Messenger admin ownership contract:
- Messenger assignment is resolved via `UserProfile.tag["messengerAdminId"]`.
- Team-scoped reads must only return messengers/orders/payouts that map to the authenticated messenger admin.

Primary API surfaces:
- /order, /store, /user, /user-config, /recon, /document, /yoco/payment, /whatsapp/webhook.

Delivery pricing contract:
- `GET /order/delivery-price` remains stable and returns `DeliveryPriceEstimateDto`.
- `Rates` now supports additive per-vehicle distance pricing fields: `ratePerKmBike`, `ratePerKmCar`, `ratePerKmBakkie`, `ratePerKmTruck`.
- Per-vehicle fallback order for distance rate is: vehicle-specific `ratePerKm*` -> store-level `ratePerKm` -> global default `ratePerKm`.
- Vehicle resolution is based on existing category labels (`Bike Delivery Driver`, `Small/Medium Vehicle Driver`, `Bakkie Delivery Driver`, `Truck Delivery Driver`). Preserve these labels for compatibility unless a coordinated contract change is requested.

User config contract:
- `UserConfig` documents are keyed by `name` and patched via `/user-config/{userType}`.
- `UserConfig.userRole` is an additive optional `ProfileRoles` mapping and must be preserved on update paths.
- `UserConfigService.update` keeps the persisted key (`name`) and updates label/mandatoryFields/optionalFields/userRole.

Recent additive query surfaces:
- `/user?role=MESSENGER&messengerAdminId=<id>` for messenger admin team lookup.
- `/order?messengerAdminId=<id>&allStages=<bool>` for team orders.
- `/recon/payout?payoutType=MESSENGER&messengerAdminId=<id>&fromDate=<iso>&toDate=<iso>&messengerId=<optional>` for team payouts and drill-down.
- `/order/{orderId}/nextstage?latitude=<double>&longitude=<double>` — optional location params appended to every stage advance. Params are `required = false`; omit for backward-compatible no-location calls.

## Messenger Order API

Controller: `izinga-ordermanager/.../orders/MessengerOrderController.java`
Base path: `/messenger/order`

All responses are wrapped in `MessengerOrderDto` so fees reflect the actual messenger payout after iZinga commission deduction.

Endpoints:
- `GET /messenger/order?messengerId=<id>&allStages=<bool>` — list orders for an assigned messenger.
- `GET /messenger/order?messengerAdminId=<id>&allStages=<bool>` — list orders for all messengers in a team. Uses `findOrdersByMessengerAdminId` (returns raw `Order`) and wraps each in `new MessengerOrderDto(order, izingaCommissionPerc)`.
- `GET /messenger/order/{id}` — single order with commission deducted. Calls `findOrderForMessenger`.
- `GET /messenger/order/{orderId}/nextstage?latitude=<double>&longitude=<double>` — advance stage, optionally with GPS coordinates.
- `PATCH /messenger/order/{orderId}/quote` — accept or reject a delivery quote.

Key design notes:
- `findOrderByMessengerId` already returns `MessengerOrderDto` instances internally; cast directly.
- `findOrdersByMessengerAdminId` returns raw `Order`; wrap explicitly with `new MessengerOrderDto(o, izingaCommissionPerc)`.
- Do NOT return raw `Order` from this controller — always wrap so commission is applied.

## Scheduled Jobs

Class: `izinga-ordermanager/.../service/SchedulerService.java`

### `notifyUnregisteredWhatsappUsers`
- **Status**: implemented but currently **disabled** — the `@Scheduled` annotation is commented out.
- **Intended schedule**: daily at 08:00 UTC (`cron = "* 0 8 * * *"`).
- **Logic**:
  1. Queries `WhatsappSessionRepo.findByLastMessageDateBetween(yesterday, today)` for sessions active the previous day.
  2. For each session, prepends `+` to `session.getFrom()` and calls `userProfileRepo.findByMobileNumber(from)`.
  3. If no registered user is found, sends re-engagement landing options via `smsNotificationService.sendLandingOptions(from, null, null)`.
  4. Tracks `[sentCount, errorCount]` and logs a summary in the finally block.
- **Enabling**: remove the comment prefix from `@Scheduled(cron = "* 0 8 * * *")` on the method.
- **Dependency**: requires `WhatsappSessionRepo` injected into `SchedulerService` (already wired).

### `WhatsappSessionRepo` — additive query method
File: `izinga-messaging/.../repo/WhatsappSessionRepo.java`
- `List<WhatsappSession> findByLastMessageDateBetween(LocalDate start, LocalDate end)` — finds sessions whose `lastMessageDate` falls within the given date range. Used by the scheduler job above.
- Note: `WhatsappSession.lastMessageDate` is stored as `Instant` in the model but this derived query uses Spring Data's between semantics — verify index if session volume grows.

## AI Correction Feedback Loop

Controller: `izinga-messaging/.../whatsapp/FirestoreToWhatsappController.java`
Endpoint: `GET /forward/chatSession/{cSId}/message/{msgId}`

**Purpose**: When a human agent writes a correction message in a Firestore chat session and calls this endpoint, it:
1. Loads `ChatSession` and `FireStoreMessage` from Firestore (404 if either missing). Guards non-TEXT message type with 400.
2. Normalizes `customerMobileNumber` (`0` prefix → `+27`).
3. Sends the human-written correction text (`msg.getMessage()`) directly to the customer via WhatsApp. `AiCustomerServiceAgent` is NOT involved — the human text is forwarded as-is.
4. **On success — Correction Step A**: calls `ConversationHistoryService.recordHumanCorrection(normalizedPhone, customerName, messageText)` — atomic `@Transactional` method that gets-or-creates the conversation and appends `[HUMAN CORRECTION] <text>` as an assistant message.
5. **On success — Correction Step B**: calls `AiAgentConfigService.appendHumanCorrection("driver_support", normalizedPhone, messageText)` — null-safe method that loads the active config, creates the `## Human Correction Log` section if absent, appends the entry, and saves (version incremented).
6. Returns 200 with `{status: sent, response: <WhatsApp body>}`.

**Injected services**: `FirestoreService`, `WhatsAppService`, `WhatsappConfig`, `ConversationHistoryService`, `AiAgentConfigService`. `AiCustomerServiceAgent` is intentionally NOT injected.

**Refactored service methods (current)**:
- `ConversationHistoryService.recordHumanCorrection(String phone, String name, String messageText)` — atomic `@Transactional`: calls `getOrCreateConversation` then `addAssistantMessage`.
- `AiAgentConfigService.appendHumanCorrection(String agentName, String phone, String messageText)` — null-safe load → prompt guard → section create/append → `saveAgentConfig`. Returns early if no active config found.

**Safety**: Both correction steps run AFTER the successful WhatsApp send and are wrapped in independent try/catch blocks. A failure in either does NOT affect the WhatsApp delivery response.

**Agent name constant**: `AGENT_NAME = "driver_support"`.

**Correction log format** in system prompt:
```
## Human Correction Log
The following corrections were made by human agents. Apply these as guidance when similar questions arise:
- [<Instant>] Customer <normalizedTo>: <message text>
- [<Instant>] Customer <normalizedTo>: <message text>
```

## Order Location History Feature

### Model Changes (izinga-commons)
`Order.kt` now carries:
```kotlin
var statusHistory: MutableList<OrderStatusHistory>? = mutableListOf()

fun addStatusHistory(stage: OrderStage?, lati: Double? = null, longi: Double? = null) {
    // appends OrderStatusHistory(stage, OrderStatusLocation(lati, longi)) if lati/longi non-null else location=null
}
fun addStatusHistory(stage: OrderStage?) { addStatusHistory(stage, null, null) }  // Java-callable bridge overload
```
Data classes at end of Order.kt:
```kotlin
data class OrderStatusHistory(var stage: OrderStage? = null, var location: OrderStatusLocation? = null)
data class OrderStatusLocation(var lati: Double? = null, var longi: Double? = null)
```
Stored as an embedded MongoDB subdocument array. Last item in the list is the most recent stage.

### Stage-Change Coverage
`addStatusHistory()` is called at every stage-change site:
| Location | Stage Recorded |
|---|---|
| `OrderServiceImpl.startOrder()` | `STAGE_0_CUSTOMER_NOT_PAID` |
| `OrderServiceImpl.finishOder()` — online-paid branch | `STAGE_1_WAITING_STORE_CONFIRM` |
| `OrderServiceImpl.finishOder()` — already-paid branch | `STAGE_7_ALL_PAID` |
| `OrderServiceImpl.progressNextStage()` | computed next stage (with optional lat/lng) |
| `OrderServiceImpl.cancelOrder()` | `CANCELLED` |
| `CustomerOrderEventHandler` | `STAGE_7_ALL_PAID` on STAGE_6 auto-complete |
| `MessengerOrderEventHandler` | `STAGE_7_ALL_PAID` on incentive order creation |

### Interface Contract
`OrderService.kt` carries both overloads:
```kotlin
fun progressNextStage(orderId: String?): Order?
fun progressNextStage(orderId: String?, latitude: Double?, longitude: Double?): Order?
```

### Kotlin ↔ Java Interop Rule
**Kotlin default parameters do NOT generate Java-callable overloads automatically.** When a Kotlin method with default parameters is called from Java, always add an explicit bridge overload:
```kotlin
// Primary (Kotlin and Java callers passing all args)
fun addStatusHistory(stage: OrderStage?, lati: Double? = null, longi: Double? = null) { ... }
// Bridge (Java callers omitting lat/lng)
fun addStatusHistory(stage: OrderStage?) { addStatusHistory(stage, null, null) }
```
Without `@JvmOverloads` or explicit bridge, Java callers get compile errors.

## Standard Workflow

### 1. Understand The Request And Domain
- Identify business domain first: users, stores, orders, payments, messaging, recon, docs, QR.
- Identify whether change is API behavior, model shape, persistence, security, or integration.

### 2. Map To The Correct Module
- Start from controller location and trace to service and repository.
- If a model is in izinga-commons, treat it as cross-module impact.

### 3. Contract Impact Check (Required)
Before editing, answer:
- Will request/response JSON change?
- Will enum values or stage transitions change?
- Will repository query semantics change?
- Will security requirements change for /v2 APIs or MCP endpoints?

If yes, apply backward-compatible change where possible:
- Prefer additive fields over renames/removals.
- Keep endpoint paths and HTTP methods stable unless explicitly requested.
- Preserve existing query parameter behavior.

### 4. Implement Minimal Safe Change
- Follow current style in that module (Java or Kotlin).
- Keep logic in service layer, not controller-heavy.
- Reuse shared validators/utilities where available.
- For Mongo changes, preserve repository method naming conventions and index assumptions.

### 5. Domain Decision Points

User management:
- If lookup is by phone, normalize SA numbers consistently (0, +27, 27 patterns).
- If create/update user, preserve role-based behavior and profile approval flow.
- For messenger admin features, use additive filters and resolve team members by `tag.messengerAdminId`.
- For `/user-config`, keep `name` as the stable id and treat `userRole` as additive role metadata (do not rename/remove existing user types).

Store management:
- Keep owner role/store linkage and markup behavior intact.
- Respect location/range filtering semantics.

Order management:
- Preserve stage transition rules and cancellation constraints.
- Keep payment reversal and notification side effects intact.
- Handle quote acceptance without bypassing sanity checks.
- Preserve existing `messengerId` query behavior while adding `messengerAdminId` team scope.
- For delivery pricing, keep per-vehicle rate-per-km behavior additive: resolve vehicle-specific rates first, then generic fallback, without changing endpoint contracts.

Payments and recon:
- Yoco webhook signature validation must stay strict.
- Recon payouts should remain separated by payout type (SHOP or MESSENGER).
- For `MESSENGER` payouts with `messengerAdminId`, aggregate only managed messenger payout rows.

Messaging:
- WhatsApp webhook verification and payload handling must preserve event publishing behavior.
- Push and Firestore integrations should fail safely and log clearly.

MCP server:
- New MCP capabilities should be exposed via @Tool methods in service classes.
- Ensure service is registered through McpConfig toolObjects.
- Keep tool names descriptive and stable once published.

### 6. Validation Checklist
Run focused validation before completion:
- Compile affected module and dependencies:
  - ./mvnw -pl <module> -am test
- For API changes, check controller mappings and serialization.
- For security changes, confirm protected vs public endpoint behavior.
- For MCP changes:
  - confirm /sse and /mcp/message reachability
  - verify new tool appears and executes.
- For messenger admin scope:
  - confirm cross-admin access is denied.
  - confirm non-admin role cannot query team endpoints.
  - confirm existing single-user query paths remain unchanged.

### 7. Write Tests With 100% Coverage (Mandatory)
Every implementation task MUST include tests. Task is not complete until tests are written and pass.

**Coverage requirements:**
- Every new public method in a service or controller must have a corresponding test.
- Cover the happy path AND all error/null/boundary branches in the same method.
- For service methods: mock all collaborators; test the service logic in isolation.
- For controller methods: use `@WebMvcTest` or direct method call; assert HTTP status and body shape.
- For scheduled jobs: test each branch (no sessions, registered user skip, null-from skip, error handling).

**Test conventions in ijudi-api:**
- `izinga-messaging` uses **JUnit 5** with Mockito: `@ExtendWith(MockitoExtension.class)`, `@Mock`, constructor injection in `@BeforeEach`.
- Other modules may use **JUnit 4**: `@RunWith(MockitoJUnitRunner.class)`, `@Mock`, `@InjectMocks` or constructor injection in `@Before`. Match the convention already used in that module's test directory.
- Use `mock(ClassName.class)` when the class has no no-arg constructor.
- Place tests under `src/test/java/` mirroring the main package path.
- Name the test class `<ClassName>Test.java` in the same package as the class under test.
- Verify behaviour with `verify(mock).method(...)`, not just that no exception is thrown.
- Use `when(...).thenReturn(...)` for all collaborator stubs, never rely on default mock behaviour.

**Coverage verification:**
- After writing tests, run: `./mvnw -pl <module> -am test`
- All tests must pass before marking the task done.
- If a test cannot be written for a branch (e.g., private method reached only via integration), note it explicitly in the output.

### 8. Completion Criteria
Task is complete only when:
- Correct module and layers were updated.
- No accidental contract breaks on shared JSON or enums.
- **Tests written for every new or changed method with 100% branch coverage.**
- All module tests pass: `./mvnw -pl <module> -am test`
- Any new endpoint/tool is documented in code comments or module docs.

## Bug Fix Playbook
1. Reproduce from controller input to service output.
2. Locate failing branch and domain invariant violated.
3. Patch smallest change in service/repo/model layer.
4. Write or update tests covering the fixed branch AND any adjacent untested branches.
5. Re-run module checks: `./mvnw -pl <module> -am test`
6. Verify no cross-module regression in shared contracts.

## Feature Playbook
1. Confirm existing endpoint can be extended additively.
2. Add DTO/field/logic with backward compatibility.
3. Keep persistence and validation aligned.
4. Add role/security checks if needed.
5. Add MCP tool exposure only if feature is intended for AI tools.
6. **Write tests: happy path + all error branches + null/boundary cases.**
7. Validate: `./mvnw -pl <module> -am test` — all tests must pass.

## Known Architectural Guardrails

## Recommended Prompt Patterns

## Quick Start Commands
  - ./mvnw clean install -DskipTests
  - ./mvnw -pl izinga-ordermanager -am test
  - ./mvnw -pl izinga-usermanagement -am test
  - ./mvnw -pl izinga-recon -am test

## Related Skills

- [meta-templates-developer](meta-templates-developer/SKILL.md): Use for WhatsApp Business template creation, update, listing, and sending via Meta Graph API and curl. Reference this skill for all template management workflows and troubleshooting.

## File Structure Landmarks
- Parent modules list: pom.xml
- Security: izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/security
- MCP config: izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/McpConfig.java
- Shared domain and repos: izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons
- User APIs: izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement
- Recon and payouts: izinga-recon/src/main/kotlin/io/curiousoft/izinga/recon
- Messaging and WhatsApp: izinga-messaging/src/main/java/io/curiousoft/izinga/messaging
- Payments: izinga-yoco-pay/src/main/kotlin/io/curiousoft/izinga/yocopay
