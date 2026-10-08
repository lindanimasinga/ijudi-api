package io.curiousoft.izinga.payfast.config

import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository
import io.curiousoft.izinga.payfast.service.PayFastCheckoutService
import io.curiousoft.izinga.payfast.service.PayFastItnHandler
import io.curiousoft.izinga.payfast.service.PayFastSignatureUtil
import io.curiousoft.izinga.payfast.service.PayFastValidateClient
import io.curiousoft.izinga.payfast.service.PayFastValidateClientImpl
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

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

    /**
     * Production PayFast server-to-server validate client.
     *
     * SEC-TB01-01-D: This bean is ALWAYS wired in production. No feature flag or environment
     * property can disable it. To substitute a test double, use a @Profile("test") bean
     * in a test configuration class — never by disabling or bypassing this bean.
     *
     * Timeout is driven by `payfast.subscription.validateTimeoutSeconds` (default 5s).
     */
    @Bean
    fun payFastValidateClient(properties: PayFastProperties): PayFastValidateClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.validateTimeoutSeconds * 1000)
            setReadTimeout(properties.validateTimeoutSeconds * 1000)
        }
        return PayFastValidateClientImpl(properties.validateUrl, RestTemplate(factory))
    }

    @Bean
    fun payFastItnHandler(
        properties: PayFastProperties,
        signatureUtil: PayFastSignatureUtil,
        subscriptionRepository: MerchantSubscriptionRepository,
        eventPublisher: ApplicationEventPublisher,
        validateClient: PayFastValidateClient,
        mongoTemplate: MongoTemplate
    ): PayFastItnHandler = PayFastItnHandler(
        properties, signatureUtil, subscriptionRepository, eventPublisher, validateClient, mongoTemplate
    )
}
