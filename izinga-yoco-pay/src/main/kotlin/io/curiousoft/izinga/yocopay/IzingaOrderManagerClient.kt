package io.curiousoft.izinga.yocopay

import io.curiousoft.izinga.commons.model.Order
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class IzingaOrderManagerClient(private val restTemplate: RestTemplate,
                               @Value("\${yoco.verifier.order-manager-url}") private val baseUrl: String) {

    fun findOrder(orderId: String): Order {
        val url = "$baseUrl/order/$orderId"
        return restTemplate.getForObject(url, Order::class.java)!!
    }

    fun finishOrder(orderId: String, order: Order): Order {
        val url = "$baseUrl/order/$orderId"
        val headers = HttpHeaders().also {
            it.contentType = MediaType.APPLICATION_JSON
            it.set("Origin" , "app://izinga")
        }
        val entity = HttpEntity(order, headers)
        val response = restTemplate.patchForObject(url, entity, Order::class.java)
        return response!!
    }

    fun cancelOrder(orderId: String) {
        val url = "$baseUrl/order/$orderId"
        restTemplate.delete(url)
    }
}
