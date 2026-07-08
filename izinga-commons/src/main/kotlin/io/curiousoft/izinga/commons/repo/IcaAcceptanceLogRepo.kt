package io.curiousoft.izinga.commons.repo

import io.curiousoft.izinga.commons.model.IcaAcceptanceLog
import org.springframework.data.mongodb.repository.MongoRepository

interface IcaAcceptanceLogRepo : MongoRepository<IcaAcceptanceLog, String>
