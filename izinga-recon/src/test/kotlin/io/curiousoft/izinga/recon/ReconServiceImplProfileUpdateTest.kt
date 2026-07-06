package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.BusinessHours
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.ShippingData
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ShopPayout
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.DayOfWeek
import java.util.Date

class ReconServiceImplProfileUpdateTest {

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
            applicationEventPublisher = applicationEventPublisher
        )
    }

    @Test
    fun `profile update refreshes pending messenger payout banking details for ewallet`() {
        val messenger = UserProfile(
            "messenger-name",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "address",
            "https://image.url",
            "0810000001",
            ProfileRoles.MESSENGER
        ).apply {
            id = "messenger-1"
            bank = Bank().apply {
                name = "New Bank"
                accountId = "+2782000111"
                type = BankAccType.EWALLET
                phone = "0810000001"
                branchCode = "250655"
            }
        }

        val pendingPayout1 = MessengerPayout(
            toId = "messenger-1",
            toName = "Messenger 1",
            toBankName = "Old Bank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "0111111111",
            toBranchCode = "000001",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            orders = mutableSetOf(),
            emailAddress = "m1@x.com",
            emailSubject = "subject"
        )
        val pendingPayout2 = MessengerPayout(
            toId = "messenger-1",
            toName = "Messenger 1",
            toBankName = "Older Bank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "0222222222",
            toBranchCode = "000002",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            orders = mutableSetOf(),
            emailAddress = "m1@x.com",
            emailSubject = "subject"
        )

        val updatedPayouts = slot<Iterable<MessengerPayout>>()
        every {
            messengerPayoutRepository.findAllByToIdAndPayoutStage("messenger-1", PayoutStage.PENDING)
        } returns listOf(pendingPayout1, pendingPayout2)
        every { messengerPayoutRepository.saveAll(capture(updatedPayouts)) } returnsArgument 0

        sut.handleProfileUpdated(ProfileUpdatedEvent(this, messenger))

        val savedPayouts = updatedPayouts.captured.toList()
        assertEquals(2, savedPayouts.size)
        savedPayouts.forEach {
            assertEquals("New Bank", it.toBankName)
            assertEquals(BankAccType.EWALLET, it.toType)
            assertEquals("082000111", it.toAccountNumber)
            assertEquals("250655", it.toBranchCode)
        }
    }

    @Test
    fun `profile update refreshes pending shop payout banking details without changing non ewallet account numbers`() {
        val store = StoreProfile(
            StoreType.FOOD,
            "store-name",
            "store-short-name",
            "address",
            "https://image.url",
            "0810000001",
            mutableListOf("food"),
            mutableListOf(BusinessHours(DayOfWeek.MONDAY, Date(), Date())),
            "owner-1",
            Bank().apply {
                name = "Shop Bank"
                accountId = "+2782333444"
                type = BankAccType.CHEQUE
                phone = "0810000001"
                branchCode = "632005"
            }
        ).apply {
            id = "shop-1"
            emailAddress = "shop@x.com"
        }

        val pendingPayout1 = ShopPayout(
            toId = "shop-1",
            toName = "Shop 1",
            toBankName = "Old Shop Bank",
            toType = BankAccType.SAVINGS,
            toAccountNumber = "0111111111",
            orders = mutableSetOf(),
            toBranchCode = "000001",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            emailAddress = "shop@x.com",
            emailSubject = "subject"
        )
        val pendingPayout2 = ShopPayout(
            toId = "shop-1",
            toName = "Shop 1",
            toBankName = "Older Shop Bank",
            toType = BankAccType.SAVINGS,
            toAccountNumber = "0222222222",
            orders = mutableSetOf(),
            toBranchCode = "000002",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            emailAddress = "shop@x.com",
            emailSubject = "subject"
        )

        val updatedPayouts = slot<Iterable<ShopPayout>>()
        every {
            shopPayoutRepository.findAllByToIdAndPayoutStage("shop-1", PayoutStage.PENDING)
        } returns listOf(pendingPayout1, pendingPayout2)
        every { shopPayoutRepository.saveAll(capture(updatedPayouts)) } returnsArgument 0

        sut.handleProfileUpdated(ProfileUpdatedEvent(this, store))

        val savedPayouts = updatedPayouts.captured.toList()
        assertEquals(2, savedPayouts.size)
        assertEquals("Shop Bank", savedPayouts[0].toBankName)
        assertEquals(BankAccType.CHEQUE, savedPayouts[0].toType)
        assertEquals("0111111111", savedPayouts[0].toAccountNumber)
        assertEquals("632005", savedPayouts[0].toBranchCode)
        assertEquals("Shop Bank", savedPayouts[1].toBankName)
        assertEquals(BankAccType.CHEQUE, savedPayouts[1].toType)
        assertEquals("0222222222", savedPayouts[1].toAccountNumber)
        assertEquals("632005", savedPayouts[1].toBranchCode)
    }

    @Test
    fun `shop payout creation keeps non ewallet account number unchanged`() {
        val order = Order().apply {
            id = "order-1"
            stage = OrderStage.STAGE_7_ALL_PAID
            shopId = "shop-1"
        }
        val store = StoreProfile(
            StoreType.FOOD,
            "store-name",
            "store-short-name",
            "address",
            "https://image.url",
            "0810000001",
            mutableListOf("food"),
            mutableListOf(BusinessHours(DayOfWeek.MONDAY, Date(), Date())),
            "owner-1",
            Bank().apply {
                name = "Shop Bank"
                accountId = "+2782333444"
                type = BankAccType.CHEQUE
                phone = "0810000001"
                branchCode = "632005"
            }
        ).apply {
            id = "shop-1"
            emailAddress = "shop@x.com"
            hasPaymentAgreement = true
        }

        every { storeRepo.findById("shop-1") } returns java.util.Optional.of(store)
        every { shopPayoutRepository.findByToIdAndPayoutStage("shop-1", PayoutStage.PENDING) } returns null
        every { shopPayoutRepository.save(any()) } answers { firstArg() }

        val payout = sut.generatePayoutForShopAndOrder(order)

        assertEquals("+2782333444", payout?.toAccountNumber)
        assertEquals(BankAccType.CHEQUE, payout?.toType)
    }

    @Test
    fun `messenger payout creation keeps non ewallet account number unchanged`() {
        val order = Order().apply {
            id = "order-2"
            shippingData = ShippingData().apply { messengerId = "messenger-1" }
            tip = 10.0
        }
        val messenger = UserProfile(
            "messenger-name",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "address",
            "https://image.url",
            "0810000001",
            ProfileRoles.MESSENGER
        ).apply {
            id = "messenger-1"
            emailAddress = "m1@x.com"
            bank = Bank().apply {
                name = "New Bank"
                accountId = "+2782000111"
                type = BankAccType.SAVINGS
                phone = "0810000001"
                branchCode = "250655"
            }
        }

        every { userProfileRepo.findById("messenger-1") } returns java.util.Optional.of(messenger)
        every { messengerPayoutRepository.findByToIdAndPayoutStage("messenger-1", PayoutStage.PENDING) } returns null
        every { messengerPayoutRepository.save(any()) } answers { firstArg() }

        val payout = sut.generatePayoutForMessengerAndOrder(order)

        assertEquals("+2782000111", payout?.toAccountNumber)
        assertEquals(BankAccType.SAVINGS, payout?.toType)
    }
}
