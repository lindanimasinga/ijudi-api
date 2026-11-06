package io.curiousoft.izinga.recon.payout

/**
 * Sealed result type for the two shop/messenger payout generator methods.
 *
 * Three mutually exclusive outcomes replace the previous nullable return:
 *
 * - [Created]      — a payout was saved (happy path). The saved entity is carried inside.
 * - [NotApplicable] — the order does not qualify for a payout at this time and will never
 *                     qualify without a data change (wrong stage, no payment agreement,
 *                     perm-employed with zero tip, order already included in an existing payout).
 *                     The scheduler must still mark the order `payoutCreated = true` so that the
 *                     order is not retried on subsequent runs where it would remain permanently
 *                     NotApplicable. When BOTH sides (shop and messenger) are NotApplicable,
 *                     the scheduler should still mark the order done.
 * - [Deferred]     — a transient precondition was not met (missing bank details). The scheduler
 *                     must leave `payoutCreated = false` so that the order is re-evaluated on
 *                     the next run. A WhatsApp notification is sent once per 24-hour window
 *                     (throttled in the notification port implementation).
 *
 * MUST NEVER be used as an `@Tool` return type or exposed through any MCP/HTTP path.
 * This type is an internal batch/event-coordination contract.
 */
sealed class PayoutGenerationOutcome<out T : Payout> {

    /**
     * A payout record was successfully saved. [payout] is the persisted entity.
     */
    data class Created<T : Payout>(val payout: T) : PayoutGenerationOutcome<T>()

    /**
     * The order does not qualify for a payout and never will without a data change.
     * [reason] is a loggable diagnostic string (must not contain phone numbers).
     */
    data class NotApplicable(val reason: String) : PayoutGenerationOutcome<Nothing>()

    /**
     * A transient precondition (e.g. missing bank details) prevented payout creation.
     * The scheduler must retry on the next run.
     * [reason] is a loggable diagnostic string (must not contain phone numbers).
     */
    data class Deferred(val reason: String) : PayoutGenerationOutcome<Nothing>()
}
