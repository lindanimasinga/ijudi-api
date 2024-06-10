package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.usermanagement.users.UserProfileService
import io.curiousoft.izinga.usermanagement.walletpass.apple.ApplePassGenerator
import io.curiousoft.izinga.usermanagement.walletpass.google.GooglePassGenerator
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class WalletPassService(private val applePassGenerator: ApplePassGenerator, private val googlePassGenerator: GooglePassGenerator,
                        private val userProfileService: UserProfileService) {

    fun generatePass(user: UserProfile, deviceType: DeviceType): Any? {
        return when (deviceType) {
            DeviceType.APPLE -> applePassGenerator.generatePass(user)
            DeviceType.ANDROID -> googlePassGenerator.generatePass(user)
        }
    }

    @EventListener
    fun updateBalance(payoutBalanceUpdatedEvent: PayoutBalanceUpdatedEvent): Any? {
        val user = userProfileService.find(payoutBalanceUpdatedEvent.userId)
        return when (payoutBalanceUpdatedEvent.deviceType) {
            DeviceType.APPLE -> applePassGenerator.updateBalance(user, payoutBalanceUpdatedEvent.balance)
            DeviceType.ANDROID -> googlePassGenerator.updateBalance(user, payoutBalanceUpdatedEvent.balance)
        }
    }

    data class PayoutBalanceUpdatedEvent(val userId: String, val balance: BigDecimal, val deviceType: DeviceType, val origin: Any) : ApplicationEvent(origin)
}