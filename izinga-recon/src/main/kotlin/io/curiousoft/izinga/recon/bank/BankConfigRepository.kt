package io.curiousoft.izinga.recon.bank

import org.springframework.data.mongodb.repository.MongoRepository

interface BankConfigRepository : MongoRepository<BankConfig, String> {
    fun findByBankId(bankId: String): BankConfig?
    fun findByBankName(bankName: String): BankConfig?
}

