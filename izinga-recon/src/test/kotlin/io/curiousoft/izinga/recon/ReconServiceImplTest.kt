package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
import io.curiousoft.izinga.commons.repo.OrderRepository
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.PayoutBundleRepo
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.tips.TipsService
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.springframework.data.repository.findByIdOrNull
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class ReconServiceTest {

    lateinit var sut: ReconService
    private val orderRepo = mockk<OrderRepository>()
    private val payoutBundleRepo = mockk<PayoutBundleRepo>()
    private val storeRepo = mockk<StoreRepository>()
    private val messengerRepo = mockk<UserProfileRepo>()
    private val payoutRepo = mockk<PayoutRepository>()
    private val tipService = mockk<TipsService>()

    @Before
    fun setUp() {
        sut = ReconServiceImpl(orderRepo = orderRepo, payoutBundleRepo = payoutBundleRepo, storeRepo = storeRepo,
            messengerRepo = messengerRepo, payoutRepo = payoutRepo, tipsService = tipService)
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
            toAccountNumber = "shop1",
            toType = BankAccType.CHEQUE,
            orders = shop1Orders,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
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
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
        )
        val payout3 = ShopPayout(
            toId = shop3,
            toName = shop3Name,
            toType = BankAccType.CHEQUE,
            toBankName = "ABSA",
            toAccountNumber = "shop3",
            orders = shop3Orders,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
        )

        every { payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP) } returns PayoutBundle(payouts = listOf(payout1,payout2,payout3),
            createdBy = "Lindani", type = PayoutType.SHOP)

        every { orderRepo.findByShopPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID) } returns shop1Orders.plus(shop2Orders).plus(shop3Orders).toList()

        every { storeRepo.findById(shop1) } returns Optional.of(createStoreProfile(StoreType.FOOD, 8, 18))
        every { storeRepo.findById(shop2) } returns Optional.of(createStoreProfile(StoreType.FOOD, 8, 18))
        every { storeRepo.findById(shop3) } returns Optional.of(createStoreProfile(StoreType.FOOD, 8, 18))

        every { payoutBundleRepo.save(any()) } returnsArgument 0

        //when
        val paybundle = sut.generateNextPayoutsToShop()

        //then
        assertFalse(paybundle?.executed!!)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(700.0.toBigDecimal(), paybundle.payoutTotalAmount)
        verify { payoutBundleRepo.save(any()) }
    }

    @Test
    fun `generate next payout for shops if not exist`() {
        //given 3 orders not paid to shop
        val shop1 = "shop1-id"
        val shop2 = "shop2-id"
        val shop3 = "shop3-id"

        val shop1Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop1
            },
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop2
            },
            mockk<Order>().also {
                every { it.basketAmount } returns 100.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
                every { it.shopId } returns shop3
            }
        )

        every { payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP) } returns null
        every { orderRepo.findByShopPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID) } returns shop1Orders

        every { storeRepo.findByIdOrNull(shop1) } returns mockk<StoreProfile>().also {
            every { it.bank } returns Bank().apply { name = "FNB"; accountId = "1234543"; type = BankAccType.EWALLET; branchCode = "code" }
            every { it.name } returns shop1
            every { it.emailAddress } returns "shop1"
        }
        every { storeRepo.findByIdOrNull(shop2) } returns mockk<StoreProfile>().also {
            every { it.bank } returns Bank().apply { name = "Ewallet"; accountId = "0812815707"; type = BankAccType.EWALLET; branchCode = "code" }
            every { it.name } returns shop2
            every { it.emailAddress } returns "shop2"
        }
        every { storeRepo.findByIdOrNull(shop3) } returns mockk<StoreProfile>().also {
            every { it.bank } returns Bank().apply { name = "ABSA"; accountId = "1221122112"; type = BankAccType.EWALLET; branchCode = "code" }
            every { it.name } returns shop3
            every { it.emailAddress } returns "shop3"
        }

        every { payoutBundleRepo.save(any()) } returnsArgument 0

        //when
        val paybundle = sut.generateNextPayoutsToShop()

        //then
        assertFalse(paybundle?.executed!!)
        assertNotNull(paybundle.createdDate)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(700.0.toBigDecimal(), paybundle.payoutTotalAmount)

        paybundle.payouts.forEach {
            assertNotNull(it.toBankName)
            assertNotNull(it.toAccountNumber)
            assertNotNull(it.toId)
        }

        verify { storeRepo.findByIdOrNull(shop1) }
        verify { storeRepo.findByIdOrNull(shop2) }
        verify { storeRepo.findByIdOrNull(shop3) }
        verify { payoutBundleRepo.save(any()) }
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

        every { payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.MESSENGER) } returns PayoutBundle(payouts = listOf(shopPayout1,payout2,payout3),
            createdBy = "Lindani", type = PayoutType.MESSENGER)

        every { orderRepo.findByMessengerPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID) } returns shop1Orders.plus(shop2Orders).plus(shop3Orders).toList()

        every { messengerRepo.findById(messenger1) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "FNB"; accountId = "1234543"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger1
        }.let { Optional.of(it) }

        every { messengerRepo.findById(messenger2) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "Ewallet"; accountId = "0812815707"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger2
        }.let { Optional.of(it) }

        every { messengerRepo.findById(messenger3) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "ABSA"; accountId = "1221122112"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger3
        }.let { Optional.of(it) }

        every { payoutBundleRepo.save(any()) } returnsArgument 0

        //when
        val paybundle = sut.generateNextPayoutsToMessenger()

        //then
        assertFalse(paybundle?.executed!!)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(PayoutType.MESSENGER, paybundle.type)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(105.75.toBigDecimal(), paybundle.payoutTotalAmount)
        verify { payoutBundleRepo.save(any()) }
    }

    @Test
    fun `generate next payout for messengers if not exist`() {
        //given 3 orders not paid to messengers
        val messenger1 = "messenger1-id"
        val messenger2 = "messenger2-id"
        val messenger3 = "messenger3-id"

        val shop1Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = messenger1 }
                every { it.messengerPaid } returns false
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0; messengerId = messenger2 }
                every { it.messengerPaid } returns false
            },
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.56; messengerId = messenger3 }
                every { it.messengerPaid } returns false
            }
        )

        every { payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.MESSENGER) } returns null
        every { orderRepo.findByMessengerPaidAndStage(false, OrderStage.STAGE_7_ALL_PAID) } returns shop1Orders

        every { messengerRepo.findByIdOrNull(messenger1) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "FNB"; accountId = "1234543"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger1
        }
        every { messengerRepo.findByIdOrNull(messenger2) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "Ewallet"; accountId = "0812815707"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger2
        }
        every { messengerRepo.findByIdOrNull(messenger3) } returns mockk<UserProfile>().also {
            every { it.emailAddress } returns "l@email.com"
            every { it.bank } returns Bank().apply { name = "ABSA"; accountId = "1221122112"; type = BankAccType.EWALLET; branchCode = "code"  }
            every { it.name } returns messenger3
        }

        every { payoutBundleRepo.save(any()) } returnsArgument 0

        //when
        val paybundle = sut.generateNextPayoutsToMessenger()

        //then
        assertFalse(paybundle?.executed!!)
        assertNotNull(paybundle.createdDate)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(120.56.toBigDecimal(), paybundle.payoutTotalAmount)

        paybundle.payouts.forEach {
            assertNotNull(it.toBankName)
            assertNotNull(it.toAccountNumber)
            assertNotNull(it.toId)
        }

        verify { messengerRepo.findByIdOrNull(messenger1) }
        verify { messengerRepo.findByIdOrNull(messenger2) }
        verify { messengerRepo.findByIdOrNull(messenger3) }
        verify { payoutBundleRepo.save(any()) }
    }

    @Test
    fun `update payout status for all shops and orders`() {
        //given
        val a = 0..1
        val payoutBundleResults = PayoutBundleResults(bundleId = "12344554",
            payoutItemResults = (0..100).mapIndexed { index, it ->
                PayoutItemResults(toId = "$it", paid = index%3==0)
            } )

        every { payoutBundleRepo.findByIdOrNull("12344554") } returns PayoutBundle(
            executed = false,
            createdBy = "test",
            type = PayoutType.SHOP,
            payouts = (0..100).map { ShopPayout(
                toId = "shop1",
                toName = "shop1Name",
                toBankName = "Ewallet",
                toType = BankAccType.CHEQUE,
                toAccountNumber = "shop1",
                orders = mutableSetOf(),
                toBranchCode = "codeBranch",
                fromReference = "fromRef",
                toReference = "toRef",
                emailSubject = "email",
                emailAddress = "email",
                emailNotify = "email"
            ).apply { id = "$it" } }
        ).apply { id = "12344554" }

        every { payoutBundleRepo.save(match { it.id == "12344554" && it.executed }) } returns payoutBundleRepo.findByIdOrNull("12344554")!!

        //when
        val payoutBundle = sut.updatePayoutStatus(bundleResponse = payoutBundleResults)

        //then
        assertEquals(true, payoutBundle?.executed)
        payoutBundle?.payouts?.forEachIndexed { index, py ->
            assertEquals(index%3==0, py.paid)
        }

        verify { payoutBundleRepo.findByIdOrNull("12344554") }
        verify { payoutBundleRepo.save(match { it.id == "12344554" && it.executed }) }
    }

    @Test
    fun `update payout status for all shops and orders in payoutBundle`() {
        //given
        val a = 0..1
        val payoutBundleResults = PayoutBundleResults(bundleId = "12344554")
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

        val bundle = PayoutBundle(
            executed = false,
            createdBy = "test",
            type = PayoutType.SHOP,
            payouts = (0..100).map {
                ShopPayout(
                    toId = "shop1",
                    toName = "shop1Name",
                    toBankName = "Ewallet",
                    toType = BankAccType.CHEQUE,
                    toAccountNumber = "shop1",
                    orders = shop1Orders,
                    toBranchCode = "codeBranch",
                    fromReference = "fromRef",
                    toReference = "toRef",
                    emailSubject = "email",
                    emailAddress = "email",
                    emailNotify = "email"
                ).apply { id = "$it" }
            }
        ).apply { id = "12344554" }
        every { payoutBundleRepo.findByIdOrNull("12344554") } returns bundle

        every { orderRepo.findByIdIn(listOf("10.00", "30.00", "20.00")) } returns shop1Orders.toList()
        every { orderRepo.save(match { it.id in listOf("10.00", "30.00", "20.00")}) } returnsArgument 0
        every { payoutBundleRepo.save(match { it.id == "12344554" && it.executed }) } returns bundle

        //when
        val payoutBundle = sut.updatePayoutStatus(bundleResponse = payoutBundleResults)

        //then
        assertEquals(true, payoutBundle?.executed)
        payoutBundle?.payouts?.forEachIndexed { index, py ->
            assertEquals(true, py.paid)
        }

        verify { payoutBundleRepo.findByIdOrNull("12344554") }
        verify { orderRepo.findByIdIn(listOf("10.00", "30.00", "20.00")) }
        verify { orderRepo.save(any()) }
        verify { payoutBundleRepo.save(match { it.id == "12344554" && it.executed }) }
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