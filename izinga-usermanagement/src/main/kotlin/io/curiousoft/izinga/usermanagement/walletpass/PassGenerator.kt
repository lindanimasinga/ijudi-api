package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.commons.model.UserProfile

interface PassGenerator<T> {
    fun generatePass(user: UserProfile): T
}