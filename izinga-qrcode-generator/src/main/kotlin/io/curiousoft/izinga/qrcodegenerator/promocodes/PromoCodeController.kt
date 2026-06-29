package io.curiousoft.izinga.qrcodegenerator.promocodes

import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.valueOrNull
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoCode
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoType
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.RedeemedCode
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.UserPromoDetails
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/promocodes")
class PromoCodeController(val promoCodeService: PromoCodeService) {

    @PostMapping
    fun createPromoCodes(@RequestBody promoCodeDTOList: List<PromoCode>): ResponseEntity<List<PromoCode>> {
        val promoCodes = promoCodeService.createPromoCodes(promoCodeDTOList)
        return ResponseEntity.ok(promoCodes)
    }

    @GetMapping
    fun getPromoCodes(@RequestParam(required = false) type: PromoType?): ResponseEntity<List<PromoCode>> {
        val promoCodes = promoCodeService.getAllPromoCodes(type)
        return ResponseEntity.ok(promoCodes)
    }

    @GetMapping("/forUser")
    fun verifyUser(@RequestParam userId: String, @RequestParam promoCode: String, @RequestParam orderId: String): ResponseEntity<UserPromoDetails> {
        val userPromoCodeResults = promoCodeService.getPromoDetailsForUser(userId, promoCode.uppercase(), orderId)
        userPromoCodeResults.onFailure {
            throw it.reason
        }
        return userPromoCodeResults.valueOrNull().let { ResponseEntity.ok(it) }

    }

    @PostMapping("/redeem")
    fun redeem(@RequestBody userPromoDetails: UserPromoDetails): ResponseEntity<RedeemedCode> {
        val promoDetails = verifyUser(userPromoDetails.userId, userPromoDetails.promo.uppercase(), userPromoDetails.orderId)
        val userPromoCodeResults = promoCodeService.redeem(promoDetails.body!!)
        userPromoCodeResults.onFailure {
            throw it.reason
        }
        return userPromoCodeResults.valueOrNull().let { ResponseEntity.ok(it) }
    }

    fun handleError(exception: Exception): ResponseEntity<Exception> {
        return when (exception.message) {
            "error.userAlreadyRedeemed" -> ResponseEntity.badRequest().body(exception)
            "error.promoCodeNotFound" -> ResponseEntity.status(404).body(exception)
            "error.maxRedemptionReached" -> ResponseEntity.status(410).body(exception)
            "error.orderNotFound", "error.userNotFound" -> ResponseEntity.status(404).body(exception)
            "error.orderFromShopNotEligible", "error.orderNotEligible" , "error.userNotEligible" -> ResponseEntity.badRequest().body(exception)
            else -> ResponseEntity.internalServerError().body(exception)
        }
    }

}