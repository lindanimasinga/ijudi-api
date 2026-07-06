package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
import io.curiousoft.izinga.commons.payout.events.AmbassadorPayoutEvent
import org.springframework.context.ApplicationEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal

class GenerateAmbassadorPayoutTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

    private lateinit var sut: ReconServiceImpl

    @BeforeEach
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = userProfileRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            applicationEventPublisher = applicationEventPublisher,
            ambassadorProperties = AmbassadorProperties(commissionAmount = BigDecimal("70.00"))
        )
    }

    private fun makeAmbassador(id: String): UserProfile {
        val bank = Bank().apply {
            name = "FNB"; accountId = "0821234567"; type = BankAccType.EWALLET; branchCode = "250655"
        }
        return UserProfile(
            "Ambassador One",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "10 Amb Street",
            "img.jpg",
            "0821234567",
            ProfileRoles.AMBASSADOR
        ).also {
            it.id = id
            it.emailAddress = "ambassador@mail.com"
            it.bank = bank
        }
    }

    private fun makeOrder(orderId: String, messengerId: String): Order {
        val shippingData = mockk<ShippingData>()
        every { shippingData.messengerId } returns messengerId
        val order = mockk<Order>()
        every { order.id } returns orderId
        every { order.shippingData } returns shippingData
        return order
    }

    @Test
    fun `happy path - creates AmbassadorPayout and publishes AmbassadorPayoutEvent`() {
        val ambassadorId = "amb-001"
        val driverId = "driver-001"
        val ambassador = makeAmbassador(ambassadorId)
        val order = makeOrder("order-001", driverId)

        every { ambassadorPayoutRepository.findByToIdAndPayoutStage(ambassadorId, PayoutStage.PENDING) } returns null
        val capturedPayout = slot<AmbassadorPayout>()
        every { ambassadorPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "PAY01" }
        }
        val capturedEvent = slot<ApplicationEvent>()
        every { applicationEventPublisher.publishEvent(capture(capturedEvent)) } just runs

        val result = sut.generatePayoutForAmbassadorAndOrder(order, ambassador)

        assertNotNull(result)
        assertEquals(ambassadorId, result!!.toId)
        assertEquals(BigDecimal("70.00"), result.commissionAmount)
        assertEquals("order-001", result.driverFirstDeliveryOrderId)
        assertEquals(PayoutStage.PENDING, result.payoutStage)

        verify(exactly = 1) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }

        val event = capturedEvent.captured as AmbassadorPayoutEvent
        assertEquals(ambassadorId, event.ambassadorId)
        assertEquals(driverId, event.driverId)
        assertEquals(BigDecimal("70.00"), event.commissionAmount)
        assertEquals("PAY01", event.payoutId)
    }

    @Test
    fun `returns null and skips payout when PENDING AmbassadorPayout already exists`() {
        val ambassadorId = "amb-002"
        val ambassador = makeAmbassador(ambassadorId)
        val order = makeOrder("order-002", "driver-002")

        val existingPayout = mockk<AmbassadorPayout>()
        every { existingPayout.id } returns "EXIST"
        every { ambassadorPayoutRepository.findByToIdAndPayoutStage(ambassadorId, PayoutStage.PENDING) } returns existingPayout

        val result = sut.generatePayoutForAmbassadorAndOrder(order, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null when ambassador id is null`() {
        val ambassador = makeAmbassador("amb-003").also { it.id = null }
        val order = makeOrder("order-003", "driver-003")

        val result = sut.generatePayoutForAmbassadorAndOrder(order, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null when order has no shippingData`() {
        val ambassadorId = "amb-004"
        val ambassador = makeAmbassador(ambassadorId)
        val order = mockk<Order>()
        every { order.id } returns "order-004"
        every { order.shippingData } returns null

        every { ambassadorPayoutRepository.findByToIdAndPayoutStage(ambassadorId, PayoutStage.PENDING) } returns null

        val result = sut.generatePayoutForAmbassadorAndOrder(order, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }
}
