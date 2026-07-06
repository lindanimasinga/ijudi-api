package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AmbassadorPayoutTest {

    private fun buildPayout(commission: BigDecimal = BigDecimal("150.00")) = AmbassadorPayout(
        toId = "ambassador-001",
        toName = "John Doe",
        toBankName = "FNB",
        toType = BankAccType.CHEQUE,
        toAccountNumber = "62012345678",
        toBranchCode = "250655",
        fromReference = "iZinga Ambassador Commission",
        toReference = "iZinga pay",
        emailNotify = "",
        emailAddress = "john@example.com",
        emailSubject = "Ambassador payout",
        commissionAmount = commission,
        driverFirstDeliveryOrderId = "order-abc-123"
    )

    @Test
    fun `total returns commissionAmount`() {
        val payout = buildPayout(BigDecimal("250.00"))
        assertEquals(BigDecimal("250.00"), payout.total)
    }

    @Test
    fun `paid defaults to false`() {
        val payout = buildPayout()
        assertFalse(payout.paid)
    }

    @Test
    fun `payoutStage defaults to PENDING`() {
        val payout = buildPayout()
        assertEquals(PayoutStage.PENDING, payout.payoutStage)
    }

    @Test
    fun `driverFirstDeliveryOrderId is stored correctly`() {
        val payout = buildPayout()
        assertEquals("order-abc-123", payout.driverFirstDeliveryOrderId)
    }

    @Test
    fun `VOIDED stage is a valid PayoutStage`() {
        val payout = buildPayout()
        payout.payoutStage = PayoutStage.VOIDED
        assertEquals(PayoutStage.VOIDED, payout.payoutStage)
    }

    @Test
    fun `total reflects updated commissionAmount`() {
        val payout = buildPayout(BigDecimal("100.00"))
        payout.commissionAmount = BigDecimal("200.00")
        assertEquals(BigDecimal("200.00"), payout.total)
    }
}
