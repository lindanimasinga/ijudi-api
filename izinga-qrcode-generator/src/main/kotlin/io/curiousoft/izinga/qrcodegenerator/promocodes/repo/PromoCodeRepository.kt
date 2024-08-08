package io.curiousoft.izinga.qrcodegenerator.promocodes.repo

import io.curiousoft.izinga.qrcodegenerator.promocodes.model.PromoCode
import org.joda.time.LocalDateTime
import org.springframework.data.mongodb.repository.MongoRepository

interface PromoCodeRepository : MongoRepository<PromoCode, String?> {
    fun findByCode(promoCode: String): PromoCode?
    fun findByExpiryDateAfter(now: LocalDateTime): List<PromoCode>
}