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
- izinga-messaging/src/main/java/io/curiousoft/izinga/messaging/whatsapp/webhooks/WhatsappInboundEventHandler.java
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/UserProfileService.kt

Supporting inheritance and contracts:
- izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement/users/ProfileServiceImpl.kt
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
- Publishes order-updated event.
- Persists updated stage.

find methods and MCP tools:
- find_order_by_id, find_orders_by_user_id, find_orders_by_phone_number, find_orders_by_messenger_id.
- Phone lookup uses SA prefixes 0, +27, 27.
- Messenger lookup merges assigned orders plus unaccepted quote-requested orders.

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
- Null-safety of contacts list for landing options path.
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

UserProfileService review checkpoints:
- Short phone number handling before substring.
- Normalization consistency with order phone search.
- Duplicate policy and race conditions for exists-plus-save flow.
- Location range units and expected caller semantics.

## High-Risk Patterns To Check First
- Substring operations without input-length guards.
- List index operations where list can be empty.
- Stage transition index arithmetic without terminal guards.
- Side effects that depend on request payload instead of persisted state.
- Event publication paths that may run on partially updated data.

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

## Validation Matrix
Use targeted module checks:
- mvn -pl izinga-ordermanager -am test
- mvn -pl izinga-messaging -am test
- mvn -pl izinga-usermanagement -am test

Minimum scenario checks:
- Order start, finish, next stage, cancel, quote accept/reject.
- WhatsApp text, interactive accept, missing contact payload.
- User create with formatted/unformatted phone and duplicate numbers.

## Completion Criteria
- Reviewed all relevant branches in all three target classes.
- Any change preserves contracts and side effects intentionally.
- High-risk edge cases are guarded or explicitly documented.
- Targeted tests or compile checks pass for affected modules.
