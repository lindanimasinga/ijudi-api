package io.curiousoft.izinga.qrcodegenerator

import io.curiousoft.izinga.commons.model.BaseModel
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class LinkCodeUser(val userId: String, val linkCode: String) : BaseModel()