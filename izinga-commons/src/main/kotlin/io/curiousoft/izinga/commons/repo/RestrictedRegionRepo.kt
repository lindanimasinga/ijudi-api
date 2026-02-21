package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.RestrictedRegion
import org.springframework.data.mongodb.repository.MongoRepository

interface RestrictedRegionRepo : MongoRepository<RestrictedRegion, String> {
    fun findByActive(active: Boolean): List<RestrictedRegion>
}
