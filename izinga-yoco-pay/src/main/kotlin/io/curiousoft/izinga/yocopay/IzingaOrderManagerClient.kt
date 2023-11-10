package io.curiousoft.izinga.yocopay

import io.curiousoft.izinga.commons.model.Order
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(value = "izinga-order-manager", url = "0.0.0.0/")
interface IzingaOrderManagerClient {

    @GetMapping(value = ["order/{orderId}"], consumes = ["application/json"])
    fun findOrder(@PathVariable orderId: String): Order

    @PostMapping(value = ["order/{orderId}"], consumes = ["application/json"], produces = ["application/json"])
    fun finishOrder(@PathVariable orderId: String, @RequestBody order: Order): Order

}
