package io.curiousoft.izinga.yocopay.config

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class YocoConfigurationTest {

    @Test
    internal fun testYocoSignature() {
        //given
        val data = """{"createdDate":"2023-11-03T14:09:29.391675Z","id":"evt_rLQQMyMj2j1iynQUQJGCPmAL","payload":{"amount":7764,"createdDate":"2023-11-03T14:09:06.040866Z","currency":"ZAR","id":"p_3LRQ3y3jjn4cN1PinevSqMAx","metadata":{"checkoutId":"ch_Kx6mdodJNWQCkAFbpvh3wOg8","orderId":"1699017774","productType":"checkout"},"mode":"test","paymentMethodDetails":{"card":{"expiryMonth":11,"expiryYear":24,"maskedCard":"************1111","scheme":"visa"},"type":"card"},"status":"succeeded","type":"payment"},"type":"payment.succeeded"}"""

        //when
        val yocoConf = YocoConfiguration(key = "key", url = "url", webhooksec = "M0JGQUI3MTcwODQxMDg2MUMyNjk2OUQ4MzA1NTI0QUE")
        val hash = yocoConf.yocoHash(webhookId = "msg_2XfWMtEcCBTQrgL9iM00gDMAQso",
            webhookTimestamp = "1699020569", body = data)

        //verify
        Assertions.assertEquals("8AvKYTPMYVtjgiXM7KmaPMQLM+pVfmUOEVl6SalovSs=", hash)
    }
}