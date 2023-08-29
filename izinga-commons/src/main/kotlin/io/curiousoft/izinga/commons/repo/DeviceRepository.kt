package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.Device
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface DeviceRepository : MongoRepository<Device?, String?> {
    fun findOneByToken(token: String?): Optional<Device?>?
    fun findByUserId(userId: String?): List<Device?>?
    fun findOneByIdOrToken(id: String?, token: String?): Optional<Device?>?
}