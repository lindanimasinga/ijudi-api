package io.curiousoft.izinga.commons.order

import java.util.Date

/**
 * ADR-019: Response returned by GET /order/{id}/cancellation-preview.
 * Contains the calculated fee, refund amount, a human-readable factor summary,
 * and the short-lived signed token that must be supplied to confirm the cancellation.
 */
data class CancellationPreviewResponse(
    val orderId: String,
    val calculatedFeeZAR: Double,
    val refundAmountZAR: Double,
    val factorSummary: String,
    val feeToken: String,
    val tokenExpiresAt: Date
)
