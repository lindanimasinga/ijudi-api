package io.curiousoft.izinga.qrcodegenerator.promocodes.repo

import io.curiousoft.izinga.qrcodegenerator.promocodes.model.RedeemedCode
import org.springframework.data.mongodb.repository.MongoRepository

interface RedeemedCodeRepository : MongoRepository<RedeemedCode, String> {
    fun findByCodeAndUserId(promoCode: String, userId: String): RedeemedCode?
    fun countByCode(code: String): Int
}