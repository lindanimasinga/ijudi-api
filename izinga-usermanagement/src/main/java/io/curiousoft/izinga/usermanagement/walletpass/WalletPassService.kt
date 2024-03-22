package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.usermanagement.walletpass.apple.ApplePassGenerator
import io.curiousoft.izinga.commons.model.UserProfile
import org.springframework.stereotype.Service

@Service
class WalletPassService(private val applePassGenerator: ApplePassGenerator) {

    fun generatePass(user: UserProfile, deviceType: DeviceType): ByteArray? {
        return when (deviceType) {
            DeviceType.APPLE -> applePassGenerator.generatePass(user)
            else -> null
        }
    }
}