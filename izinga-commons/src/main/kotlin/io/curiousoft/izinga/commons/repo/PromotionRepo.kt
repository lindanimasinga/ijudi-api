package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.Promotion
import io.curiousoft.izinga.commons.model.StoreType
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface PromotionRepo : MongoRepository<Promotion?, String?> {
    fun findByExpiryDateBefore(date: Date?): List<Promotion?>?
    fun findByExpiryDateAfterAndShopType(date: Date?, storeType: StoreType?): List<Promotion?>?
    fun findByShopIdAndExpiryDateAfterAndShopType(
        storeId: String?,
        date: Date?,
        storeType: StoreType?
    ): List<Promotion?>?
}