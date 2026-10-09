package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AmbassadorPayoutTest {

    private fun buildPayout(
        commission: BigDecimal = BigDecimal("150.00"),
        vehicleType: String? = null
    ) = AmbassadorPayout(
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
        triggerDriverId = "driver-abc-123",
        triggerDriverVehicleType = vehicleType
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
    fun `triggerDriverId is stored correctly`() {
        val payout = buildPayout()
        assertEquals("driver-abc-123", payout.triggerDriverId)
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

    @Test
    fun `triggerDriverVehicleType defaults to null when not supplied`() {
        val payout = buildPayout()
        assertNull(payout.triggerDriverVehicleType)
    }

    @Test
    fun `triggerDriverVehicleType is stored correctly when supplied`() {
        val payout = buildPayout(vehicleType = "BIKE")
        assertEquals("BIKE", payout.triggerDriverVehicleType)
    }

    @Test
    fun `triggerDriverVehicleType can be updated after construction`() {
        val payout = buildPayout()
        payout.triggerDriverVehicleType = "TRUCK"
        assertEquals("TRUCK", payout.triggerDriverVehicleType)
    }
}
