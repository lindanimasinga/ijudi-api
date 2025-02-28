package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class ReconServiceTest {

    lateinit var sut: ReconServiceImpl
    private val storeRepo = mockk<StoreRepository>()
    private val messengerRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

    @Before
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = messengerRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            applicationEventPublisher = applicationEventPublisher)
    }

    @Test
    fun `get next payout for shops`() {
        //given 3 orders not paid to shop
        val shop1 = "shop1-id"
        val shop2 = "shop2-id"
        val shop3 = "shop3-id"
        val shop1Name = "shop1name"
        val shop2Name = "shop2name"
        val shop3Name = "shop3name"

        val shop1Orders: MutableSet<Order> = mutableSetOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop1
            }
        )
        val shop2Orders: MutableSet<Order> = mutableSetOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop2
            }
        )
        val shop3Orders: MutableSet<Order> = mutableSetOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 100.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop3
            }
        )

        val payout1 = ShopPayout(
            toId = shop1,
            toName = shop1Name,
            toBankName = "Ewallet",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "shop1",
            orders = shop1Orders,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            emailAddress = "email",
            emailSubject = "email",
        )
        val payout2 = ShopPayout(
            toId = shop2,
            toName = shop2Name,
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "shop2",
            orders = shop2Orders,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            emailAddress = "email",
            emailSubject = "email",
        )
        val payout3 = ShopPayout(
            toId = shop3,
            toName = shop3Name,
            toBankName = "ABSA",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "shop3",
            orders = shop3Orders,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            emailAddress = "email",
            emailSubject = "email",
        )

        every { shopPayoutRepository.findByPayoutStage() } returns listOf(payout1,payout2,payout3)

        //when
        val paybundle = sut.getCurrentPayoutBundleForShops()

        //then
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(700.0.toBigDecimal(), paybundle.payoutTotalAmount)
    }

    @Test
    fun `get next payout for messengers`() {
        //given 3 orders not paid to messengers
        val messenger1 = "messenger1-id"
        val messenger2 = "messenger2-id"
        val messenger3 = "messenger3-id"

        val messenger1Name = "messenger1name"
        val messenger2Name = "messenger2name"
        val messenger3Name = "messenger3name"

        val shop1Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order1"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger1; fee = 30.0 }
            }
        )
        val shop2Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order2"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger2; fee = 40.0 }
            }
        )
        val shop3Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order3"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger3; fee = 35.75 }
            }
        )

        val shopPayout1 = MessengerPayout(
            toId = messenger1, toName = messenger1Name, toBankName = "Ewallet", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger1",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop1Orders,
            emailAddress = "email",
            emailSubject = "email"
        )
        val payout2 = MessengerPayout(
            toId = messenger2, toName = messenger2Name, toBankName = "FNB", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger2",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop2Orders,
            emailAddress = "email",
            emailSubject = "email"
        )
        val payout3 = MessengerPayout(
            toId = messenger3, toName = messenger3Name, toBankName = "ABSA", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger3",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop3Orders,
            emailAddress = "email",
            emailSubject = "email"
        )

        every { messengerPayoutRepository.findByPayoutStage() } returns listOf(shopPayout1,payout2,payout3)

        every { messengerPayoutRepository.save(any()) } returnsArgument 0
        every { shopPayoutRepository.save(any()) } returnsArgument 0

        //when
        val paybundle = sut.getCurrentPayoutBundleForMessenger()

        //then
        assertEquals(3, paybundle.payouts.size)
        assertEquals(PayoutType.MESSENGER, paybundle.type)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(105.75.toBigDecimal(), paybundle.payoutTotalAmount)
    }

    @Test
    fun `get next payout for messengers mark as processing`() {
        //given 3 orders not paid to messengers
        val messenger1 = "messenger1-id"
        val messenger2 = "messenger2-id"
        val messenger3 = "messenger3-id"

        val messenger1Name = "messenger1name"
        val messenger2Name = "messenger2name"
        val messenger3Name = "messenger3name"

        val shop1Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order1"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger1; fee = 30.0 }
            }
        )
        val shop2Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order2"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger2; fee = 40.0 }
            }
        )
        val shop3Orders: MutableSet<Order> = mutableSetOf(
            Order().also {
                it.description = "order3"
                it.messengerPaid = false
                it.shippingData = ShippingData().apply { messengerId = messenger3; fee = 35.75 }
            }
        )

        val shopPayout1 = MessengerPayout(
            toId = messenger1, toName = messenger1Name, toBankName = "Ewallet", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger1",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop1Orders,
            emailAddress = "email",
            emailSubject = "email"
        )
        val payout2 = MessengerPayout(
            toId = messenger2, toName = messenger2Name, toBankName = "FNB", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger2",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop2Orders,
            emailAddress = "email",
            emailSubject = "email"
        )
        val payout3 = MessengerPayout(
            toId = messenger3, toName = messenger3Name, toBankName = "ABSA", toType = BankAccType.CHEQUE,
            toAccountNumber = "messenger3",
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailNotify = "email",
            orders = shop3Orders,
            emailAddress = "email",
            emailSubject = "email"
        )

        every { messengerPayoutRepository.findByPayoutStage() } returns listOf(shopPayout1,payout2,payout3)

        every { messengerPayoutRepository.save(any()) } returnsArgument 0
        every { shopPayoutRepository.save(any()) } returnsArgument 0

        //mark payout as processing
        var paybundle = sut.getCurrentPayoutBundleForMessenger()
        paybundle.payouts.forEach { it.payoutStage = PayoutStage.PROCESSING }
        sut.updateBundle(paybundle)

        //when
        paybundle = sut.getCurrentPayoutBundleForMessenger()

        //then
        assertEquals(0, paybundle.payouts.size)
        assertEquals(PayoutType.MESSENGER, paybundle.type)
        assertEquals(0, paybundle.numberOfPayouts)
        assertEquals(0.00.toBigDecimal(), paybundle.payoutTotalAmount)
    }

    @Test
    fun `update payout status for all messengers and orders in payoutBundle`() {
        //given
        val a = 0..1
        val messengerOrders: MutableSet<Order> = mutableSetOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = "messenger1" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 10.00
                every { it.id } returns "10.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = "messenger2" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 30.00
                every { it.id } returns "30.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.56; messengerId = "messenger3" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 20.00
                every { it.id } returns "20.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            }
        )

        val payoutBundleResults = PayoutBundleResults(
            bundleId = "12344554" ,
            payoutItemResults = (0..100).map {
                PayoutItemResults(
                    type = PayoutType.MESSENGER,
                    paid = Random().nextBoolean(),
                    toId = "Messenger $it",
                    message = "Failed or passed due to system settings"
                )
            })
        val storedPayoutList = (0..100).map {
            MessengerPayout(
                toId = "Messenger $it",
                toName = "Messenger $it name",
                toBankName = "Bank $it",
                toType = BankAccType.CHEQUE,
                toAccountNumber = "12345$it",
                orders = messengerOrders,
                toBranchCode = "codeBranch",
                fromReference = "fromRef",
                toReference = "toRef",
                emailSubject = "email",
                emailAddress = "email",
                emailNotify = "email",
                payoutStage = if (it % 3 == 0) PayoutStage.PENDING else if (it % 2 == 0) PayoutStage.PROCESSING else PayoutStage.COMPLETED
            ).apply { id = "$it" }
        }

        storedPayoutList.forEach {
            every { messengerPayoutRepository.findByToIdAndPayoutStage(it.toId, PayoutStage.PROCESSING) } returns it
        }
        payoutBundleResults.payoutItemResults.forEach { results ->
            every { messengerPayoutRepository.save(match { it.paid == results.paid  && it.toId == results.toId}) } returnsArgument 0
        }

        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        //when
        sut.updatePayoutStatus(bundleResponse = payoutBundleResults)

        //then
        payoutBundleResults.payoutItemResults.forEach { results ->
            verify { messengerPayoutRepository.save(match { it.paid == results.paid  && it.toId == results.toId}) }
        }

        verify (timeout = 3) {
            applicationEventPublisher.publishEvent(
                match {
                    (it as OrderPayoutEvent)
                    it.isMessengerPaid
                }
            )
        }
    }

    @Test
    fun `update payout status for all shops and orders in payoutBundle`() {
        //given
        val a = 0..1
        val shop1Orders: MutableSet<Order> = mutableSetOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = "messenger1" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 10.00
                every { it.id } returns "10.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = "messenger2" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 30.00
                every { it.id } returns "30.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.56; messengerId = "messenger3" }
                every { it.messengerPaid } returns false
                every { it.basketAmount } returns 20.00
                every { it.id } returns "20.00"
                every { it.shopPaid = true } just runs
                every { it.messengerPaid = false } just runs
            }
        )

        val payoutBundleResults = PayoutBundleResults(
            bundleId = "12344554" ,
            payoutItemResults = (0..100).map {
                PayoutItemResults(
                    type = PayoutType.SHOP,
                    paid = Random().nextBoolean(),
                    toId = "shop $it",
                    message = "Failed or passed due to system settings"
                )
            })
        val storedPayoutList = (0..100).map {
                ShopPayout(
                    toId = "shop $it",
                    toName = "shop $it name",
                    toBankName = "Bank $it",
                    toType = BankAccType.CHEQUE,
                    toAccountNumber = "12345$it",
                    orders = shop1Orders,
                    toBranchCode = "codeBranch",
                    fromReference = "fromRef",
                    toReference = "toRef",
                    emailNotify = "email",
                    emailAddress = "email",
                    emailSubject = "email",
                ).apply { id = "$it" }
            }

        storedPayoutList.forEach {
            every { shopPayoutRepository.findByToIdAndPayoutStage(it.toId, PayoutStage.PROCESSING) } returns it
        }
        payoutBundleResults.payoutItemResults.forEach { results ->
            every { shopPayoutRepository.save(match { it.paid == results.paid  && it.toId == results.toId}) } returnsArgument 0
        }

        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        //when
        sut.updatePayoutStatus(bundleResponse = payoutBundleResults)

        //then
        payoutBundleResults.payoutItemResults.forEach { results ->
            verify { shopPayoutRepository.save(match { it.paid == results.paid  && it.toId == results.toId}) }
        }

        verify (timeout = 3) {
            applicationEventPublisher.publishEvent(
                match {
                    (it as OrderPayoutEvent)
                    it.isStorePaid
                }
            )
        }
    }

    private fun createStoreProfile(food: StoreType, openHour: Int, closeHour: Int): StoreProfile {
        val businessHours = ArrayList<BusinessHours>()
        val dateTimeOpen = LocalDateTime.now().withHour(openHour).withMinute(0)
        val dateTimeClose = LocalDateTime.now().withHour(closeHour).withMinute(59)
        val open = Date.from(dateTimeOpen.toInstant(ZoneOffset.of("+02:00")))
        val close = Date.from(dateTimeClose.toInstant(ZoneOffset.of("+02:00")))
        businessHours.add(BusinessHours(DayOfWeek.MONDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.TUESDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.WEDNESDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.THURSDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.FRIDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.SATURDAY, open, close))
        businessHours.add(BusinessHours(DayOfWeek.SUNDAY, open, close))
        val tags = mutableListOf("Pizza")
        val storeProfile = StoreProfile(
            food,
            "name",
            "shortname",
            "address",
            "https://image.url",
            "081mobilenumb",
            tags,
            businessHours,
            "ownerId",
            Bank().apply { name = "FNB"; accountId = "123432"; type = BankAccType.CHEQUE; phone = "081281112"; branchCode = "1243321" }
        )
        storeProfile.apply {
            featured = true
            hasVat = false
            latitude = -29.7828761
            longitude = 31.0012573
            id = "shopid"
            emailAddress = "shop@email.com"
        }
        return storeProfile
    }
}