package io.curiousoft.izinga.payfast.config

import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import io.curiousoft.izinga.payfast.service.PayFastCheckoutService
import io.curiousoft.izinga.payfast.service.PayFastItnHandler
import io.curiousoft.izinga.payfast.service.PayFastSignatureUtil
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * TIER-BILLING-01: Spring auto-configuration for the izinga-payfast module.
 * Registers all beans so they are discoverable when the module is on the classpath
 * of izinga-ordermanager.
 */
@Configuration
@EnableConfigurationProperties(PayFastProperties::class)
@EnableMongoRepositories(basePackages = ["io.curiousoft.izinga.payfast.repo"])
class PayFastAutoConfiguration {

    @Bean
    fun payFastSignatureUtil(properties: PayFastProperties): PayFastSignatureUtil =
        PayFastSignatureUtil(properties.passphrase)

    @Bean
    fun payFastCheckoutService(
        properties: PayFastProperties,
        signatureUtil: PayFastSignatureUtil,
        subscriptionRepository: MerchantSubscriptionRepository,
        storeRepository: StoreRepository,
        userProfileRepo: UserProfileRepo
    ): PayFastCheckoutService = PayFastCheckoutService(
        properties, signatureUtil, subscriptionRepository, storeRepository, userProfileRepo
    )

    @Bean
    fun payFastItnHandler(
        properties: PayFastProperties,
        signatureUtil: PayFastSignatureUtil,
        subscriptionRepository: MerchantSubscriptionRepository,
        eventPublisher: ApplicationEventPublisher
    ): PayFastItnHandler = PayFastItnHandler(
        properties, signatureUtil, subscriptionRepository, eventPublisher
    )
}
