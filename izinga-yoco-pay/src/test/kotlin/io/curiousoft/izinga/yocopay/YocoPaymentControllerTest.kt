package io.curiousoft.izinga.yocopay

import com.fasterxml.jackson.databind.ObjectMapper
import io.curiousoft.izinga.commons.model.Basket
import io.curiousoft.izinga.commons.model.BasketItem
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.yocopay.api.YocoPaymentClient
import io.curiousoft.izinga.yocopay.api.YocoPaymentInitiate
import io.curiousoft.izinga.yocopay.config.YocoConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions


class YocoPaymentControllerTest {

    private val orderManager = mockk<IzingaOrderManagerClient>()
    private val yocoConfig = YocoConfiguration(url = "url", key = "apikey", webhooksec = "sec")
    private val yocoPaymentClient = mockk<YocoPaymentClient>()
    private lateinit var sut: YocoPaymentController

    @Before
    fun setUp() {
        sut = YocoPaymentController(yocoConfiguration = yocoConfig,
            izingaOrderMananger = orderManager, yocoPaymentClient = yocoPaymentClient, mapper = ObjectMapper()
        )
    }

    @Test
    fun verifyPaymentSuccess() {
        //given

        val yocoEvent = YocoEvent(id = "evt_rLQQMyMj2j1iynQUQJGCPmAL", type = "payment.succeeded",
            payload = PaymentData(amount = 100.toBigDecimal(), createdDate = "Date()", status = "", id = "", type = "", currency = "ZAR",
                metadata =  PaymentMetadata(externalId = "new-order-id", checkoutId = "")), createdDate = "202311221")

        val newOrder = Order().apply {
            id = "new-order-id"
            description = "new order description"
            shopId = "myshopid"
            basket = Basket().apply {
                items = mutableListOf(BasketItem(name = "item 1", price = 100.00, quantity = 1, discountPerc = 0.00))
            }
        }

        every { orderManager.findOrder("new-order-id") } returns newOrder
        every { orderManager.finishOrder("new-order-id", newOrder) } returns newOrder

        //when
        val httpResponse = sut.verifyPaymentSuccess(successEvent = yocoEvent, yocoHash = "8AvKYTPMYVtjgiXM7KmaPMQLM+pVfmUOEVl6SalovSs=")

        //verify
        Assertions.assertEquals(200, httpResponse.statusCodeValue)
        Assertions.assertEquals("new order description:yoco-8AvKYTPMYVtjgiXM7KmaPMQLM+pVfmUOEVl6SalovSs=:", newOrder.description)

        verify {
            orderManager.findOrder("new-order-id")
            orderManager.finishOrder("new-order-id", newOrder)
        }
    }

    @Test
    fun initiatePayment() {

        //given
        val paymentRequest = YocoPaymentInitiate(amount = 100.toBigDecimal(), currency = "ZAR", successUrl = "https://successUrl",
            metadata = PaymentMetadata(externalId = "new-order-id", checkoutId = ""))
        every { yocoPaymentClient.checkout(paymentRequest) } returns YocoPaymentInitiateResponse(id =  "id",
            redirectUrl = "https:/yoco.redirect", status = "created")

        //when
        val httpResponse = sut.initiatePayment(paymentRequest)

        //verify
        Assertions.assertEquals(200, httpResponse.statusCodeValue)
        Assertions.assertEquals("https:/yoco.redirect", (httpResponse.body as YocoPaymentInitiateResponse).redirectUrl)
        verify {
            yocoPaymentClient.checkout(paymentRequest)
        }
    }
}