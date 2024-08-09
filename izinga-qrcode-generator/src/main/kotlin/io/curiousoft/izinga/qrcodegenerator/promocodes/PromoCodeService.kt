package io.curiousoft.izinga.qrcodegenerator.promocodes

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.resultFrom
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.order.OrderRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoCode
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoType
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.RedeemedCode
import io.curiousoft.izinga.qrcodegenerator.promocodes.model.UserPromoDetails
import io.curiousoft.izinga.qrcodegenerator.promocodes.repo.PromoCodeRepository
import io.curiousoft.izinga.qrcodegenerator.promocodes.repo.RedeemedCodeRepository
import org.joda.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class PromoCodeService(val promoCodeRepository: PromoCodeRepository,
                       val redeemedCodeRepository: RedeemedCodeRepository,
                       val userProfileRepo: UserProfileRepo,
                       val orderRepository: OrderRepository) {

    fun createPromoCodes(promoCodes: List<PromoCode>): List<PromoCode> {
        return promoCodeRepository.saveAll(promoCodes)
    }

    fun getPromoDetailsForUser(userId: String, promoCode: String, orderId: String): Result<UserPromoDetails, Exception> {
        return redeemedCodeRepository.findByCodeAndUserId(promoCode, userId)
            ?.let { Failure(Exception("error.userAlreadyRedeemed")) }
            ?: promoCodeRepository.findByCode(promoCode)
                ?.let {
                    userPromoDetails(userId, it, orderId)
                }
            ?: Failure(Exception("error.promoCodeNotFound"))

    }

    private fun userPromoDetails(userId: String, promoCode: PromoCode, orderId: String): Result<UserPromoDetails, Exception> {
        val maxRedemptionReached = redeemedCodeRepository.countByCode(promoCode.code) >= promoCode.maxRedemption
        if (maxRedemptionReached) {
            return Failure(Exception("error.maxRedemptionReached"))
        }

        val order = orderRepository.findByIdOrNull(orderId) ?: return Failure(Exception("error.orderNotFound"))

        return userProfileRepo.findByIdOrNull(userId)?.let {
            val codeType = promoCode.type
            when (codeType) {
                PromoType.DISCOUNT -> userPromoDetailsForAll(it, promoCode, order)
                PromoType.CASH -> userPromoForTips(it, promoCode, order)
                PromoType.SIGNUP -> userPromoDetailsForNewUser(it, promoCode, order)
            }
        } ?: Failure(Exception("error.userNotFound"))
    }

    private fun userPromoDetailsForAll(user: UserProfile, promoCode: PromoCode, order: Order): Result<UserPromoDetails, Exception> {
        promoCode.storeId?.let {
            if (order.shopId != it) {
                return Failure(Exception("error.orderFromShopNotEligible"))
            }
        }

        return resultFrom {
            UserPromoDetails(userId = user.id!!,
                promo = promoCode.code,
                verified = true,
                expiry = promoCode.expiryDate,
                amount = (promoCode.percentage * order.totalAmount * -1).toBigDecimal(),
                orderId = order.id!!
            )
        }
    }

    private fun userPromoForTips(user: UserProfile, promoCode: PromoCode, order: Order): Result<UserPromoDetails, Exception> {
        order.basket.items.firstOrNull { it.name.lowercase() == "tip" } ?: return Failure(Exception("error.orderNotEligible"))
        return resultFrom {
            UserPromoDetails(userId = user.id!!,
                promo = promoCode.code,
                verified = true,
                expiry = promoCode.expiryDate,
                amount = (promoCode.percentage * order.totalAmount).toBigDecimal(),
                orderId = order.id!!
            )
        }
    }

    private fun userPromoDetailsForNewUser(user: UserProfile, promoCode: PromoCode, order: Order): Result<UserPromoDetails, Exception> {
        val previousOrders = orderRepository.findByCustomerIdAndStage(user.id!!, OrderStage.STAGE_7_ALL_PAID)
        if (previousOrders?.isNotEmpty() == true) {
            return Failure(Exception("error.userNotEligible"))
        }

        return resultFrom {
            UserPromoDetails(userId = user.id!!,
                promo = promoCode.code,
                verified = true,
                expiry = promoCode.expiryDate,
                amount = (promoCode.percentage * order.totalAmount * -1).toBigDecimal(),
                orderId = order.id!!
            )
        }
    }

    fun getAllPromoCodes(): List<PromoCode> {
        return promoCodeRepository.findByExpiryDateAfter(LocalDateTime.now())
    }

    fun redeem(userPromoDetails: UserPromoDetails): Result<RedeemedCode, Exception> {
        return resultFrom {
            redeemedCodeRepository.save(RedeemedCode(
                code = userPromoDetails.promo,
                userId = userPromoDetails.userId,
                date = java.time.LocalDateTime.now()))
        }
    }
}