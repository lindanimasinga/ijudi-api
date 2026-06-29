package io.curiousoft.izinga.recon.bank

import io.curiousoft.izinga.commons.model.BaseModel
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "bank_configs")
data class BankConfig(val switchCode: String, val branchCode: String, val bankName: String, val bankId: String) : BaseModel()