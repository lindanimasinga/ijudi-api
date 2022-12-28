package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.*
import io.curiousoft.izinga.commons.repo.OrderRepository
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.PayoutBundleRepo
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.springframework.data.repository.findByIdOrNull

class ReconServiceTest {

    lateinit var sut: ReconService
    private val orderRepo = mockk<OrderRepository>()
    private val payoutBundleRepo = mockk<PayoutBundleRepo>()
    private val storeRepo = mockk<StoreRepository>()
    private val messengerRepo = mockk<UserProfileRepo>()

    @Before
    fun setUp() {
        sut = ReconServiceImpl(orderRepo = orderRepo, payoutBundleRepo = payoutBundleRepo, storeRepo = storeRepo, messengerRepo = messengerRepo)
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

        val shop1Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
            }
        )
        val shop2Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 300.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
            }
        )
        val shop3Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.basketAmount } returns 100.0
                every { it.shopPaid } returns false
                every { it.shopPaid = true } just runs
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

        //when
        val paybundle = sut.generateNextPayoutsToShop()

        //then
        assertFalse(paybundle?.executed!!)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(700.0.toBigDecimal(), paybundle.payoutTotalAmount)
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
            every { it.bank } returns Bank().apply { name = "FNB"; accountId = "1234543" }
            every { it.name } returns shop1
        }
        every { storeRepo.findByIdOrNull(shop2) } returns mockk<StoreProfile>().also {
            every { it.bank } returns Bank().apply { name = "Ewallet"; accountId = "0812815707" }
            every { it.name } returns shop2
        }
        every { storeRepo.findByIdOrNull(shop3) } returns mockk<StoreProfile>().also {
            every { it.bank } returns Bank().apply { name = "ABSA"; accountId = "1221122112" }
            every { it.name } returns shop3
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

        val shop1Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 30.0 }
                every { it.messengerPaid } returns false
            }
        )
        val shop2Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 40.0 }
                every { it.messengerPaid } returns false
            }
        )
        val shop3Orders: List<Order> = mutableListOf(
            mockk<Order>().also {
                every { it.shippingData } returns ShippingData().apply { fee = 35.75 }
                every { it.messengerPaid } returns false
            }
        )

        val shopPayout1 = MessengerPayout(
            toId = messenger1, toName = messenger1Name, toBankName = "Ewallet", toAccountNumber = "messenger1",
            orders = shop1Orders,
            toType = BankAccType.CHEQUE,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
        )
        val payout2 = MessengerPayout(
            toId = messenger2, toName = messenger2Name, toBankName = "FNB", toAccountNumber = "messenger2",
            orders = shop2Orders,
            toType = BankAccType.CHEQUE,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
        )
        val payout3 = MessengerPayout(
            toId = messenger3, toName = messenger3Name, toBankName = "ABSA", toAccountNumber = "messenger3",
            orders = shop3Orders,
            toType = BankAccType.CHEQUE,
            toBranchCode = "codeBranch",
            fromReference = "fromRef",
            toReference = "toRef",
            emailSubject = "email",
            emailAddress = "email",
            emailNotify = "email"
        )

        every { payoutBundleRepo.findOneByTypeAndExecuted(PayoutType.SHOP) } returns PayoutBundle(payouts = listOf(shopPayout1,payout2,payout3),
            createdBy = "Lindani", type = PayoutType.MESSENGER)

        //when
        val paybundle = sut.generateNextPayoutsToShop()

        //then
        assertFalse(paybundle?.executed!!)
        assertEquals(3, paybundle.payouts.size)
        assertEquals(3, paybundle.numberOfPayouts)
        assertEquals(105.75.toBigDecimal(), paybundle.payoutTotalAmount)
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
            every { it.bank } returns Bank().apply { name = "FNB"; accountId = "1234543" }
            every { it.name } returns messenger1
        }
        every { messengerRepo.findByIdOrNull(messenger2) } returns mockk<UserProfile>().also {
            every { it.bank } returns Bank().apply { name = "Ewallet"; accountId = "0812815707" }
            every { it.name } returns messenger2
        }
        every { messengerRepo.findByIdOrNull(messenger3) } returns mockk<UserProfile>().also {
            every { it.bank } returns Bank().apply { name = "ABSA"; accountId = "1221122112" }
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
                PayoutItemResults(payoutId = "$it", paid = index%3==0)
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
                orders = mutableListOf(),
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
}