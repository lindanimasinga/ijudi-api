package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.StoreType
import java.util.*

interface StoreRepository : ProfileRepo<StoreProfile> {
    fun findByFeatured(b: Boolean): List<StoreProfile>?
    fun findByOwnerId(ownerId: String?): List<StoreProfile>?
    fun findByLatitudeBetweenAndLongitudeBetween(v: Double, v1: Double, v2: Double, v3: Double): List<StoreProfile>?
    fun findByLatitudeBetweenAndLongitudeBetweenAndStoreType(
        minLat: Double, maxLat: Double,
        minLong: Double, maxLong: Double,
        storeType: StoreType?
    ): List<StoreProfile?>?

    fun findOneByIdOrShortName(id: String?, shortname: String?): Optional<StoreProfile>?
}