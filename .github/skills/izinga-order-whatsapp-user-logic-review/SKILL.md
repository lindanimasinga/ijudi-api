---
name: izinga-order-whatsapp-user-logic-review
description: "Use when reviewing or changing OrderServiceImpl, WhatsappInboundEventHandler, and UserProfileService in ijudi-api. Covers deep branch analysis, side effects, event publishing, phone normalization, order stage transitions, WhatsApp session handling, and architecture-safe bug fixes/features without breaking APIs or domain contracts."
argument-hint: "Target class and task: review, bug fix, or feature"
---

# iZinga Order, WhatsApp, and User Logic Review

## Outcome
Use this skill to perform a detailed, repeatable logic review and safe implementation workflow for:
- izinga-ordermanager orders service logic.
- izinga-messaging WhatsApp inbound event handling.
- izinga-usermanagement user profile service logic.

## Target Files
Primary classes:
- izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/orders/OrderServiceImpl.java
- izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/service/SchedulerService.java
- izinga-messaging/src/main/java/io/curiousoft/izinga/messaging/whatsapp/webhooks/WhatsappInboundEventHandler.java
- izinga-messaging/src/main/java/io/curiousoft/izinga/messaging/whatsapp/FirestoreToWhatsappController.java
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/UserProfileService.kt

Supporting repositories and contracts:
- izinga-messaging/src/main/java/io/curiousoft/izinga/messaging/repo/WhatsappSessionRepo.java

Supporting inheritance and contracts:
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/ProfileServiceImpl.kt
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/userconfig/UserConfig.kt
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/userconfig/UserConfigService.kt
- izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/OrderStage.kt
- izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/ProfileRoles.kt
- izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/model/StoreType.kt

## When To Use
Use this skill for:
- logic audits and code review of these classes.
- fixing production bugs in order progression, quote handling, user phone lookup, or WhatsApp ingestion.
- adding features that touch order lifecycle, inbound message routing, or user create/find behavior.
- MCP tool reliability for order/user lookup methods.

Do not use this skill for frontend flows.

## Core Review Workflow

### 1. Build Execution Map First
For each class, map method entry points and side effects.
- Input checks.
- Decision branches.
- Repository calls.
- Event publication.
- External integrations.
- Return and failure behavior.

### 2. Branch-Level Invariant Review
Validate branch invariants before changing logic:
- Null safety assumptions.
- Enum state transition validity.
- Idempotency and duplicate-processing guards.
- Data consistency across writes and events.

### 3. Side-Effect Ordering Review
Ensure each side effect occurs in correct order:
- Persistence before event if downstream relies on saved state.
- Payment checks before stock update.
- Session update before AI/landing decision.
- Duplicate or conflicting notifications are avoided.

### 4. Cross-Module Contract Safety
If changing shared behavior:
- Preserve API path and payload compatibility.
- Preserve enum meanings in order stage flow.
- Keep SA phone normalization behavior consistent across user and order lookups.

### 5. Focused Validation
- Compile impacted modules with dependencies.
- Verify high-risk branches with targeted tests.
- Confirm events fire exactly once for each intended path.

## Class Logic Atlas

### OrderServiceImpl: Detailed Behavior

startOrder:
- Validates customer exists.
- Validates store exists and store availability restrictions.
- Enforces scheduled delivery and deliver-now constraints.
- Validates basket items for non-INSTORE orders.
- Initializes order id, stage, VAT, deposit settings.
- Adds discount basket item when basket total discount exists.
- Computes delivery and geo data for ONLINE delivery.
- Resolves distance rate-per-km using vehicle-first fallback (`ratePerKmBike|Car|Bakkie|Truck` -> `ratePerKm` -> global default).
- Applies weight, volume, and labor fees from store rates.
- Validates restricted regions.
- Determines free delivery and service fee.
- Emits quote-created event when quote required.
- Computes allowed payment types.
- Persists order.

finishOder:
- Loads persisted order.
- Short-circuits if request stage is not stage 0.
- Copies description and payment type into persisted order.
- Verifies payment before progression.
- Sets stage by order type and paid status.
- Marks promo code as redeemed when present.
- Decrements stock quantities and updates item external URLs.
- Increments store service count.
- Persists store and order.
- Publishes scheduled or new-order event.

progressNextStage:
- Loads order and forbids cancelled progression.
- Computes next stage from onlineDeliveryStages list.
- **Appends `OrderStatusHistory` entry with stage and optional lat/lng coordinates.**
- Publishes order-updated event.
- Persists updated stage.
- Overloads: `progressNextStage(orderId)` delegates to `progressNextStage(orderId, null, null)`. The coordinate-aware overload is the canonical implementation.
- REST endpoint: `GET /order/{orderId}/nextstage?latitude=<double>&longitude=<double>` — params are `required = false`; backward compatible.

getDeliveryPriceEstimate:
- Validates required inputs: `category`, `fromAddress`, `toAddress`.
- Uses `DeliveryPriceEstimateDto` as stable response contract.
- Resolves standard fee by category-specific standard price first, then generic default.
- Resolves distance rate-per-km by category-specific `ratePerKm*` first, then generic store `ratePerKm`, then global default.
- Preserves existing category-label matching contract for vehicle resolution.

find methods and MCP tools:
- find_order_by_id, find_orders_by_user_id, find_orders_by_phone_number, find_orders_by_messenger_id, findOrdersByMessengerAdminId.
- Phone lookup uses SA prefixes 0, +27, 27.
- Messenger lookup merges assigned orders plus unaccepted quote-requested orders.
- Messenger admin lookup resolves team members from user profile ownership mapping and merges team assigned plus quote-requested orders.

cancelOrder:
- Blocks cancellation at late stages.
- Reverses payment first.
- Marks cancelled and saves.
- Sends customer push notifications.

acceptQuote:
- Validates order exists and not cancelled.
- Validates approval order id consistency.
- Blocks duplicate processing using quoteAcceptedBy tag.
- On approve: assigns messenger and updates quote repository acceptedBy.
- On reject: writes rejection tags and reason.
- Saves order and publishes QuoteAcceptedEvent when store available.

OrderServiceImpl review checkpoints:
- Stage progression bounds and terminal stage handling.
- Request-vs-persisted state checks in finish flow.
- Negative stock and concurrency safety around stock decrement.
- Consistency of quote state updates for approve and reject paths.
- Phone substring safety for invalid short numbers.
- Vehicle/category label drift can silently bypass category-specific pricing; keep label matching synchronized with frontend category values.
- **`addStatusHistory()` must be called at every `setStage()` site — there are 7 total (startOrder, finishOder×2, progressNextStage, cancelOrder, CustomerOrderEventHandler, MessengerOrderEventHandler).**
- **When calling `addStatusHistory` from Java, use the single-arg bridge overload `addStatusHistory(stage)` — Kotlin default params do NOT generate Java-compatible overloads without `@JvmOverloads`.**

### WhatsappInboundEventHandler: Detailed Behavior

handleInbound:
- Async event listener for WhatsappInboundEvent.
- Iterates entry and change payload nodes.
- For each message:
  - Upserts whatsapp session and marks new session if last message older than 90 minutes.
  - Runs verification-consent flow.
  - For new session, sends landing options.
  - If AI service enabled and session AI active, gets AI response and sends back.
  - Routes by message type:
    - text -> persist to Firestore and notify admins via push.
    - interactive -> parse button/list replies and detect accept actions.
    - location/image -> logs and optional persistence stubs.
  - Processes status updates.

Interactive behavior:
- Accept detection uses substring match on id/title containing accept.
- Publishes WhatsappTemplateReplyEvent when detected.

Firestore text persistence:
- Builds FireStoreTextMessage with metadata from contact profile/waId.
- Writes under customer phone key.

WhatsApp handler review checkpoints:
- Constructor injection correctness and parameter duplication.
- Null-safety of contacts list for landing options path (never call `value.getContacts().get(0)` without null/empty checks).
- Notification fan-out behavior for every text message.
- Verification flow interaction with subsequent text processing in same cycle.
- Accept detection robustness against localization and non-English replies.

### UserProfileService: Detailed Behavior

findUserByPhone:
- Extracts last 9 digits and tries prefixes 0, +27, 27.
- Returns first matched user.

findByLocation:
- Builds bounding box from lat/long/range and delegates to repository.
- Uses role and storeType in query.

create:
- Normalizes mobile number to +27 + last 9 digits.
- Rejects duplicates by mobile number.
- Delegates to base create in ProfileServiceImpl.

pendingAproval:
- Returns users with profileApproved false.

Inherited create behavior from ProfileServiceImpl:
- Validates bean constraints.
- Assigns UUID id.
- Saves profile.
- Publishes ProfileCreatedEvent.

User config linkage:
- `UserConfig` in `/user-config` now carries optional `userRole: ProfileRoles`.
- `UserConfigService.update` preserves persisted key (`name`) and updates `label`, `mandatoryFields`, `optionalFields`, and `userRole`.

UserProfileService review checkpoints:
- Short phone number handling before substring.
- Normalization consistency with order phone search.
- Duplicate policy and race conditions for exists-plus-save flow.
- Location range units and expected caller semantics.
- Team lookup correctness for `findMessengersByAdminId` and ownership mapping (`tag.messengerAdminId`).
- If roles are added/changed, validate that `userTypeConfig.userRole` mappings remain consistent with `ProfileRoles`.

### SchedulerService.notifyUnregisteredWhatsappUsers: Detailed Behavior

**Status**: implemented, currently **disabled** — `@Scheduled` annotation is commented out.

Logic flow:
1. Computes `yesterday = LocalDate.now(UTC).minusDays(1)` and `today = LocalDate.now(UTC)`.
2. Calls `whatsappSessionRepo.findByLastMessageDateBetween(yesterday, today)` to get sessions active the previous day.
3. For each session: prepends `+` to `session.getFrom()` and calls `userProfileRepo.findByMobileNumber(from)`.
4. If no user found (unregistered): calls `smsNotificationService.sendLandingOptions(from, null, null)`.
5. Tracks `counters[0]` (sent) and `counters[1]` (errors). Logs summary in `finally`.

Review checkpoints:
- `session.getFrom()` may be `null` — guard before prepending `+` to avoid NullPointerException.
- `findByLastMessageDateBetween` uses `LocalDate` params; `WhatsappSession.lastMessageDate` is `Instant` — verify Spring Data conversion correctness at scale.
- `sendLandingOptions` is an external call — failure is caught per session; total job failure is also caught.
- Enabling the job: remove the comment prefix from `@Scheduled(cron = "* 0 8 * * *")`.

### WhatsappSessionRepo: Additive Query Methods

File: `izinga-messaging/.../repo/WhatsappSessionRepo.java`

Current methods:
- `Optional<WhatsappSession> findByFrom(String from)` — session lookup by sender phone.
- `List<WhatsappSession> findByLastMessageDateBetween(LocalDate start, LocalDate end)` — sessions active within date range. Added to support the scheduler re-engagement job. Uses Spring Data derived query naming; verify MongoDB index on `lastMessageDate` if session volume grows.

### FirestoreToWhatsappController.forwardMessageToWhatsapp: Detailed Behavior

Endpoint: `GET /forward/chatSession/{cSId}/message/{msgId}`

**Injected services (current)**: `FirestoreService`, `WhatsAppService`, `WhatsappConfig`, `ConversationHistoryService`, `AiAgentConfigService`. `AiCustomerServiceAgent` is intentionally NOT injected.

Purpose: A human agent writes a correction into the Firestore chat session and calls this endpoint. The endpoint:
1. Loads `ChatSession` and `FireStoreMessage` from Firestore. Returns 404 if either is missing.
2. Guards non-TEXT message type — returns 400 if `messageType != null && messageType != TEXT`.
3. Normalizes `customerMobileNumber` (`0` prefix → `+27`) via private static `normalizePhone()`.
4. Sends `msg.getMessage()` (the human-written correction text, not an AI response) to the customer via `WhatsAppService.sendTextMessage`. Returns 500 if the Retrofit call returns a non-successful response.
5. **On success — Correction Step A (non-blocking)**: calls `ConversationHistoryService.recordHumanCorrection(normalizedPhone, customerName, messageText)`. This is an atomic `@Transactional` method — internally calls `getOrCreateConversation` then `addAssistantMessage` with `[HUMAN CORRECTION] <text>`. Failure is caught and logged.
6. **On success — Correction Step B (non-blocking)**: calls `AiAgentConfigService.appendHumanCorrection("driver_support", normalizedPhone, messageText)`. This encapsulates: load active config → null-safe prompt guard → create `## Human Correction Log` section if absent or append if present → `saveAgentConfig` (version incremented). Returns early if no active config. Failure is caught and logged.
7. Returns `ResponseEntity.ok(Map.of("status", "sent", "response", resp.body()))`.

**Key refactoring**: correction steps A and B were previously implemented as inline logic in the controller. They were moved to `ConversationHistoryService.recordHumanCorrection()` and `AiAgentConfigService.appendHumanCorrection()` respectively. The controller is now ~40 lines with linear flow.

Review checkpoints:
- Both correction steps must remain non-blocking — each inside an independent try/catch AFTER the WhatsApp send succeeds.
- `ConversationHistoryService.recordHumanCorrection` uses `@Transactional` — do not call it inside a broader catch block that would suppress rollback.
- `AiAgentConfigService.appendHumanCorrection` null-guards `getSystemPrompt()` internally using `config.getSystemPrompt() != null ? ... : ""` — no NPE risk in the controller.
- Correction log grows unbounded — consider pruning or summarizing entries if the system prompt size becomes a concern with OpenAI token limits.
- Phone normalization in this controller only handles `0` prefix; numbers already in `+27` format pass through unchanged.

### ConversationHistoryService.recordHumanCorrection: New Method

File: `izinga-messaging/.../aiAgent/conversation/ConversationHistoryService.java`

```java
@Transactional
public void recordHumanCorrection(String phone, String name, String messageText) {
    ConversationHistory history = getOrCreateConversation(phone, name);
    addAssistantMessage(history, "[HUMAN CORRECTION] " + messageText);
}
```

Purpose: Atomic correction entry — callers need one call instead of two.
Storage: `ai_conversation_histories` MongoDB collection.
Note: `@Transactional` scope covers both the get-or-create and the save inside `addAssistantMessage`.

### AiAgentConfigService.appendHumanCorrection: New Method

File: `izinga-messaging/.../aiAgent/config/AiAgentConfigService.java`

```java
public void appendHumanCorrection(String agentName, String phone, String messageText) {
    AiAgentConfig config = getActiveAgentConfig(agentName);
    if (config == null) { LOG.warn(...); return; }
    String currentPrompt = config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
    String entry = "\n- [" + Instant.now() + "] Customer " + phone + ": " + messageText;
    String updatedPrompt = currentPrompt.contains("## Human Correction Log")
            ? currentPrompt + entry
            : currentPrompt + "\n\n## Human Correction Log\n..." + entry;
    saveAgentConfig(agentName, updatedPrompt, config.getDescription());
}
```

Purpose: Encapsulates the full load → null-safe guard → create/append section → save flow. No caller needs to handle `getSystemPrompt()` nullability.
Storage: `ai_agent_configs` MongoDB collection; `version` is incremented by `saveAgentConfig` on every call.

## High-Risk Patterns To Check First
- Substring operations without input-length guards.
- List index operations where list can be empty.
- Stage transition index arithmetic without terminal guards.
- Side effects that depend on request payload instead of persisted state.
- Event publication paths that may run on partially updated data.
- Team-scope data leakage where non-admin or wrong admin can query another messenger team's orders/payouts.
- Query branches that accidentally change legacy single-user behavior (`messengerId` or `toId` paths).
- **Missing `addStatusHistory()` call after any new `setStage()` site — keeps location history in sync with all stage transitions.**
- **Calling Kotlin default-parameter methods from Java without explicit bridge overload — always add a Java-callable single-arg overload alongside the Kotlin multi-arg function.**
- **`session.getFrom()` null-check in `notifyUnregisteredWhatsappUsers` — prepending `+` to a null value throws NPE; guard before string concatenation.**
- **`AiAgentConfigService.appendHumanCorrection` null-guards `getSystemPrompt()` internally — do NOT call `agentConfig.getSystemPrompt().contains(...)` from controller code; always delegate to `appendHumanCorrection` instead.**
- **AI agent system prompt unbounded growth** — each human correction appends to the system prompt. Monitor size against OpenAI token limits; plan a pruning strategy if volume grows.

## Safe Change Playbook

### Bug Fix
1. Reproduce exact failing branch and input payload.
2. Confirm persisted vs request object assumptions.
3. Patch minimal logic branch.
4. Add branch-focused test.
5. Verify event and repository side effects remain consistent.

### Feature
1. Add feature behind existing invariants.
2. Keep API and enum contracts backward compatible.
3. Preserve order and user normalization conventions.
4. Add tests for success and rejection paths.
5. For messenger admin features, add ownership authorization tests (valid admin, wrong admin, non-admin).

## Validation Matrix
If `store-menu-to-izinga-menu/pom.xml` is missing in a sandbox clone, create a temporary stub `pom.xml` so Maven reactor commands can run.

Use targeted module checks:
- ./mvnw -pl izinga-ordermanager -am test
- ./mvnw -pl izinga-messaging -am test
- ./mvnw -pl izinga-usermanagement -am test

Minimum scenario checks:
- Order start, finish, next stage (with and without coordinates), cancel, quote accept/reject.
- Verify `statusHistory` grows with each stage change and last entry matches current stage.
- WhatsApp text, interactive accept, missing contact payload.
- User create with formatted/unformatted phone and duplicate numbers.
- Messenger admin team order query returns only managed messenger orders.
- Messenger admin payout query returns only managed messenger payouts and blocks unrelated messengerId filters.

## Completion Criteria
- Reviewed all relevant branches in all three target classes.
- Any change preserves contracts and side effects intentionally.
- High-risk edge cases are guarded or explicitly documented.
- Targeted tests or compile checks pass for affected modules.
