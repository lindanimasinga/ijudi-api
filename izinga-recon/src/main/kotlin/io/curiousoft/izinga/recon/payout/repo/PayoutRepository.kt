package io.curiousoft.izinga.recon.payout.repo

import io.curiousoft.izinga.recon.payout.PayoutBundle
import org.springframework.data.mongodb.repository.MongoRepository

interface PayoutRepository: MongoRepository<PayoutBundle, String>
