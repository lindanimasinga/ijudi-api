# ADR-028: Sealed PayoutGenerationOutcome, Deferred Retry, and Notification Throttle

**Status:** Accepted  
**Date:** 2026-11-06  
**Ticket:** DEF-1, DEF-2  
**Author:** iZinga Backend Developer  
**Approver:** Lindani Masinga (CEO / co-founder)

---

## Context

Two production defects were found during QA on the payout disbursement feature:

**DEF-1:** `emailAddress = messng.emailAddress!!` in `generatePayoutForMessengerAndOrder` throws
`NullPointerException` when a messenger has no email address on file, blocking payout creation.

**DEF-2:** When a messenger or store has a payment agreement but no bank details, payout generation
previously returned `null` and the scheduler marked the order as `payoutCreated=true`, silently
dropping the payout. The order never retried.

Additionally, the missing-bank WhatsApp notification (BUG-3 / GAP-7) was sent on every scheduler
run (hourly), spamming drivers and store owners with the same message.

## Decision

### A. Sealed `PayoutGenerationOutcome<T>` return type

Replace nullable return types on both payout generator methods with a sealed class:

```kotlin
sealed class PayoutGenerationOutcome<out T : Payout> {
    data class Created<T : Payout>(val payout: T) : PayoutGenerationOutcome<T>()
    data class NotApplicable(val reason: String) : PayoutGenerationOutcome<Nothing>()
    data class Deferred(val reason: String) : PayoutGenerationOutcome<Nothing>()
}
```

- `Created` — payout was persisted; callers that need the entity extract it via `.payout`.
- `NotApplicable` — by-design skip (wrong stage, no payment agreement, perm-employed zero-tip,
  cross-stage duplicate). The order is considered fully processed and `payoutCreated` may be set.
- `Deferred` — transient gap (missing bank details). The order must NOT be marked `payoutCreated`.
  The scheduler retries on its next run.

### B. DEF-1: Remove unsafe `!!` on nullable email address

`emailAddress = messng.emailAddress` (no `!!`). `Payout.emailAddress` is already `String?`, the
email sender uses `?.let`, and the CSV writer uses `?: ""`. No downstream changes needed.

### C. DEF-2: Deferred retry in `SchedulerService.updateMissingPayouts`

The scheduler evaluates both outcomes independently:

- If either `shopOutcome` or `messengerOutcome` is `Deferred`, the order is left with
  `payoutCreated=false` and logged at INFO. The scheduler picks it up again on the next run.
- Only when BOTH sides are `Created` or `NotApplicable` (i.e. neither is `Deferred`) is
  `order.payoutCreated=true` saved.

### D. 24-hour notification throttle

A new `payout_notification_cooldowns` MongoDB collection backed by a TTL index (86 400 s) prevents
the same recipient from receiving the same notification type more than once per 24 hours.

Cooldown key: `"<recipientId>:<NOTIFICATION_TYPE>"` (e.g. `"abc123:DRIVER_MISSING_BANK"`).

Algorithm in `WhatsappNotificationService.acquireCooldown(key)`:
1. `cooldownRepository == null` → always send (test mode; no throttle).
2. `repository.insert(record)` succeeds → send.
3. `DuplicateKeyException` → read existing record; if `createdAt` age ≥ 24 h → overwrite and send;
   otherwise suppress. Race condition (TTL deleted record between insert and read) → retry insert.

Deferred reason strings and log messages must NOT contain phone numbers — use entity IDs only.

### E. Cross-stage idempotency guard

Both `generatePayoutForShopAndOrder` and `generatePayoutForMessengerAndOrder` check
`existsByToIdAndOrderId(toId, orderId)` BEFORE the bank check. This prevents double-pay across
all PayoutStages (PENDING → PROCESSING → COMPLETED → VOIDED). Returns `NotApplicable` if the
order is already in any existing payout.

Field path in the `@Query`: `orders._id` (not `orders.id`) because Spring Data MongoDB maps the
`@Id`-annotated `id` field in embedded subdocuments to `_id` in BSON.

### F. `PayoutNotificationPort` interface: 3-arg signatures

Both `notifyDriverMissingBankDetails` and `notifyStoreMissingBankDetails` gain a `recipientId`
parameter (entity ID, not phone number) for the throttle key and log correlation.

### G. Event handler adaptations

- `StoreOrderEventHandler.handleOrderUpdatedEvent`: captures outcome, logs Deferred.
- `MessengerOrderEventHandler.handleNewOrderEventToWhatsapp` (TIPS path): uses `instanceof Created`
  pattern match to unwrap `MessengerPayout`.
- `MessengerOrderEventHandler.handleOrderUpdatedEvent` (delivery path): same `instanceof Created`
  pattern match; logs Deferred.

### H. ADR-026 boundary preserved

`generatePayoutForShopAndOrder` / `generatePayoutForMessengerAndOrder` remain INTERNAL methods
and are NEVER annotated `@Tool`.

## Consequences

- Payout retry is now automatic: orders with missing bank details are retried on every hourly
  scheduler run until bank details are added.
- Drivers and store owners receive at most one missing-bank notification per 24 hours.
- Double-pay across all PayoutStages is prevented by the cross-stage idempotency guard.
- DEF-1 (NPE on null email) is eliminated.
- Java callers use `instanceof PayoutGenerationOutcome.Created<T>` and `.getPayout()`.
- All existing API contracts, enum values, and query semantics are preserved.

## Risks and follow-up

- **Field path `orders._id` vs `orders.id`**: QA must verify against real sandbox documents that
  `existsByToIdAndOrderId` returns `true` for an order already embedded in a payout. If the field
  is mapped differently in production documents, the query will silently always return `false`.
- **`store_missing_bank_details` template**: still pending Meta Business approval. The catch block
  absorbs `HTTP 400` failures so payout generation is never blocked.
- **TTL monitor lag**: the 24-hour staleness check in `acquireCooldown` compensates for the MongoDB
  TTL monitor's 60-second polling interval, but very high lag (>24 h) is not expected in production.
