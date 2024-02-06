package io.curiousoft.izinga.yocopay.api

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(value = "yocoDashboard-api", url = "\${yoco.dashboard-api.url}")
interface YocoTransactionsClient {

    @GetMapping(value = ["transactions/"], consumes = ["application/json"])
    fun transactions(@RequestParam filters: String, @RequestParam pageSize: Int = 500): TransactionsResponse?
}