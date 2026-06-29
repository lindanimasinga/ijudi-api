package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.commons.model.UserProfile
import java.math.BigDecimal

interface PassGenerator<T> {
    fun generatePass(user: UserProfile): T
    fun updateBalance(user: UserProfile, balance: BigDecimal): Boolean
}