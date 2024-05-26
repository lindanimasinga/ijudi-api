package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.usermanagement.walletpass.apple.ApplePassGenerator
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.usermanagement.walletpass.google.GooglePassGenerator
import org.springframework.stereotype.Service

@Service
class WalletPassService(private val applePassGenerator: ApplePassGenerator, private val googlePassGenerator: GooglePassGenerator) {

    fun generatePass(user: UserProfile, deviceType: DeviceType): Any? {
        return when (deviceType) {
            DeviceType.APPLE -> applePassGenerator.generatePass(user)
            DeviceType.ANDROID -> googlePassGenerator.generatePass(user)
        }
    }
}