package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutType
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface PayoutBundleRepo: MongoRepository<PayoutBundle, String> {

    fun findOneByTypeAndExecuted(type: PayoutType, executed: Boolean = false): PayoutBundle?

    fun  findByCreatedDateBetweenAndType(fromDate: Date, toDate: Date, payoutType: PayoutType): List<PayoutBundle>
}

