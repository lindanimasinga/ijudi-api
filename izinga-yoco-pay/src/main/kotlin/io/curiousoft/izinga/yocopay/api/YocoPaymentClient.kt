package io.curiousoft.izinga.yocopay.api

import io.curiousoft.izinga.yocopay.YocoPaymentInitiateResponse
import io.curiousoft.izinga.yocopay.config.YocoHeaderConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(value = "yoco-api", url = "\${yoco.api.url}", configuration = [YocoHeaderConfig::class])
interface YocoPaymentClient {

    @PostMapping(value = ["checkouts"], consumes = ["application/json"])
    fun checkout(@RequestBody yocoPayRequest: YocoPaymentInitiate): YocoPaymentInitiateResponse?

    @PostMapping(value = ["checkouts/{checkoutId}/refund"], consumes = ["application/json"],
        headers = ["x-auth-token:\${yoco.dashboard-api.token}"])
    fun refund(@PathVariable checkoutId: String): YocoRefundResponse?
}