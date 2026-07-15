package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
import io.curiousoft.izinga.commons.payout.events.AmbassadorPayoutEvent
import org.springframework.context.ApplicationEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
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
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()
    private val foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>()
    private val storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>()
    private val storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>()
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
            referralPartnerPayoutRepository = referralPartnerPayoutRepository,
            foodCustomerCommissionRepo = foodCustomerCommissionRepo,
            storeStage1CommissionRepo = storeStage1CommissionRepo,
            storeStage2CommissionRepo = storeStage2CommissionRepo,
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

    private fun makeDriver(driverId: String): UserProfile {
        return UserProfile(
            "Driver One",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "1 Driver Street",
            "img.jpg",
            "0831111111",
            ProfileRoles.MESSENGER
        ).also {
            it.id = driverId
        }
    }

    @Test
    fun `happy path - creates AmbassadorPayout and publishes AmbassadorPayoutEvent on driver approval`() {
        val ambassadorId = "amb-001"
        val driverId = "driver-001"
        val ambassador = makeAmbassador(ambassadorId)
        val driver = makeDriver(driverId)

        every { ambassadorPayoutRepository.findByTriggerDriverId(driverId) } returns null
        val capturedPayout = slot<AmbassadorPayout>()
        every { ambassadorPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "PAY01" }
        }
        val capturedEvent = slot<ApplicationEvent>()
        every { applicationEventPublisher.publishEvent(capture(capturedEvent)) } just runs

        val result = sut.generatePayoutForAmbassadorAndApproval(driver, ambassador)

        assertNotNull(result)
        assertEquals(ambassadorId, result!!.toId)
        assertEquals(BigDecimal("70.00"), result.commissionAmount)
        assertEquals(driverId, result.triggerDriverId)
        assertEquals(PayoutStage.PENDING, result.payoutStage)
        assertTrue(result.orders.isEmpty(), "orders set must be empty for approval-based payout")

        verify(exactly = 1) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }

        val event = capturedEvent.captured as AmbassadorPayoutEvent
        assertEquals(ambassadorId, event.ambassadorId)
        assertEquals(driverId, event.driverId)
        assertEquals(BigDecimal("70.00"), event.commissionAmount)
        assertEquals("PAY01", event.payoutId)
    }

    @Test
    fun `returns null and skips payout when a commission for the same driver already exists`() {
        val ambassadorId = "amb-002"
        val driverId = "driver-002"
        val ambassador = makeAmbassador(ambassadorId)
        val driver = makeDriver(driverId)

        val existingPayout = mockk<AmbassadorPayout>()
        every { existingPayout.id } returns "EXIST"
        every { ambassadorPayoutRepository.findByTriggerDriverId(driverId) } returns existingPayout

        val result = sut.generatePayoutForAmbassadorAndApproval(driver, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `creates a second payout for same ambassador when a different driver is approved`() {
        val ambassadorId = "amb-002"
        val driver2Id = "driver-003"
        val ambassador = makeAmbassador(ambassadorId)
        val driver2 = makeDriver(driver2Id)

        // driver-003 has no existing commission
        every { ambassadorPayoutRepository.findByTriggerDriverId(driver2Id) } returns null
        val capturedPayout = slot<AmbassadorPayout>()
        every { ambassadorPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "PAY02" }
        }
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        val result = sut.generatePayoutForAmbassadorAndApproval(driver2, ambassador)

        assertNotNull(result)
        assertEquals(ambassadorId, result!!.toId)
        assertEquals(driver2Id, result.triggerDriverId)
        assertEquals(BigDecimal("70.00"), result.commissionAmount)
        verify(exactly = 1) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null when ambassador id is null`() {
        val ambassador = makeAmbassador("amb-003").also { it.id = null }
        val driver = makeDriver("driver-003")

        val result = sut.generatePayoutForAmbassadorAndApproval(driver, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null when driver id is null`() {
        val ambassadorId = "amb-004"
        val ambassador = makeAmbassador(ambassadorId)
        val driver = makeDriver("driver-004").also { it.id = null }

        val result = sut.generatePayoutForAmbassadorAndApproval(driver, ambassador)

        assertNull(result)
        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }
}
