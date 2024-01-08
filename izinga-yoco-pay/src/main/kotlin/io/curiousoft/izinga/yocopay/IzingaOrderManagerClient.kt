package io.curiousoft.izinga.yocopay

import io.curiousoft.izinga.commons.model.Order
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@FeignClient(value = "izinga-order-manager", url = "\${yoco.verifier.order-manager-url}")
interface IzingaOrderManagerClient {

    @GetMapping(value = ["order/{orderId}"], consumes = ["application/json"])
    fun findOrder(@PathVariable orderId: String): Order

    @PatchMapping(value = ["order/{orderId}"], consumes = ["application/json"], produces = ["application/json"])
    fun finishOrder(@PathVariable orderId: String, @RequestBody order: Order): Order

    @DeleteMapping(value = ["order/{orderId}"], consumes = ["application/json"], produces = ["application/json"])
    fun cancelOrder(orderId: String)

}
