package io.curiousoft.izinga.yocopay

import com.fasterxml.jackson.databind.ObjectMapper
import io.curiousoft.izinga.commons.model.PaymentType
import io.curiousoft.izinga.yocopay.api.YocoPaymentClient
import io.curiousoft.izinga.yocopay.api.YocoPaymentInitiate
import io.curiousoft.izinga.yocopay.config.YocoConfiguration
import io.curiousoft.izinga.yocopay.config.checksum
import io.curiousoft.izinga.yocopay.config.yocoHash
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("/yoco/payment")
class YocoPaymentController(private val yocoConfiguration: YocoConfiguration,
                            private val izingaOrderMananger: IzingaOrderManagerClient,
                            private val yocoPaymentClient: YocoPaymentClient,
                            private val mapper: ObjectMapper
) {

    val log = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/finalise/raw")
    fun verifyPaymentSuccess(request: HttpServletRequest): ResponseEntity<Any>  {
        val reader = request.reader
        val body = reader.lines().collect(Collectors.joining("\n"))
        val webhookId = request.getHeader("webhook-id")
        val webhookTimestamp = request.getHeader("webhook-timestamp")
        val webhookSignature = request.getHeader("webhook-signature").split(" ").map { it.split(",")[1] }
        val successEvent = mapper.readValue(body, YocoPaymentSuccess::class.java)

        val yocoHash = yocoConfiguration.yocoHash(webhookId = webhookId, webhookTimestamp = webhookTimestamp,
            body = body)
        val isValidOrigin = webhookSignature.any { it == yocoHash }

        if(!isValidOrigin) {
            log.info("the signature does not match header= $webhookSignature, computed = $yocoHash")
            return ResponseEntity.badRequest().build()
        }
        return verifyPaymentSuccess(successEvent = successEvent, yocoHash = yocoHash)
    }

    @PostMapping("/finalise/json")
    fun verifyPaymentSuccess(@RequestBody successEvent: YocoPaymentSuccess, yocoHash: String): ResponseEntity<Any> {
        log.info("yoco payment is ${successEvent.type}")
        val paymentSuccessful = successEvent.type == "payment.succeeded"
        val orderId = successEvent.payload?.metadata?.orderId!!
        return if (paymentSuccessful)
            Result.runCatching {
                log.info("fetching order $orderId")
                izingaOrderMananger.findOrder(orderId)
            }
            .map {
                var checksum = yocoConfiguration.checksum("$orderId${it.totalAmount}${it.customerId}")
                it.description = "${it.description}:yoco-$checksum:"
                it.paymentType = PaymentType.YOCO
                log.info("finishing order $orderId")
                izingaOrderMananger.finishOrder(it.id!!, it)
            }
            .fold(
                onSuccess = { ResponseEntity.ok().build() },
                onFailure = {
                    log.error(it.message, it)
                    ResponseEntity.internalServerError().body(it.message)
                })
        else ResponseEntity.internalServerError().build()
        
    }

    @PostMapping("/initiate")
    fun initiatePayment(@RequestBody paymentInitiate: YocoPaymentInitiate): ResponseEntity<Any> =
        yocoPaymentClient.checkout(paymentInitiate.also { it.amount = it.amount * 100.toBigDecimal() })?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()


    //@PostMapping("/reverse")
    //fun reversePayment(@RequestBody payoutResults: YocoPaymentReverse) = reconService.generateNextPayoutsToShop()
}
