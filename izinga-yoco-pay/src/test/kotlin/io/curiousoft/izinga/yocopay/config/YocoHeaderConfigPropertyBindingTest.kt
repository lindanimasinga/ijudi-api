package io.curiousoft.izinga.yocopay.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * ADR-031 / NOTE-01: Spring property resolver binding verification for YocoHeaderConfig.
 *
 * The two existing YocoConfigurationTest tests set Kotlin fields directly and bypass
 * Spring's property resolver — they prove the bean respects the fields, but they cannot
 * distinguish "YAML property bound correctly" from "YAML typo, silently fell back to the
 * Kotlin default (3L/15L)".
 *
 * This test loads YocoHeaderConfig inside a minimal Spring context and uses
 * @TestPropertySource to override both timeout properties to intentionally non-default values
 * (connect=7, response=22).  Spring's @Value processor resolves the property keys during
 * context creation.  If either key is mis-spelled, or the binder fails silently, the field
 * stays at its Kotlin default (3L or 15L) and the assertion fails — exposing a broken knob
 * before an operator discovers it under incident pressure.
 *
 * The context is kept as small as possible: only YocoHeaderConfig (the class under test)
 * and TestDependencies (a stub bean satisfying YocoHeaderConfig.basicAuthRequestInterceptor's
 * YocoDashBoardConfiguration parameter).  No @SpringBootTest, no component scanning.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [YocoHeaderConfigPropertyBindingTest.TestDependencies::class, YocoHeaderConfig::class])
@TestPropertySource(properties = [
    "izinga.ai.openai.response-timeout-seconds=22",
    "izinga.ai.openai.connect-timeout-seconds=7",
    "yoco.api.key=test-key-not-real"
])
class YocoHeaderConfigPropertyBindingTest {

    /**
     * Provides the YocoDashBoardConfiguration stub so that
     * YocoHeaderConfig.basicAuthRequestInterceptor can be created without
     * @ConfigurationPropertiesScan or yoco.dashboard-api.* properties.
     */
    @Configuration
    class TestDependencies {
        @Bean
        fun yocoDashBoardConfiguration() = YocoDashBoardConfiguration(
            url = "http://test-host",
            user = "test-user",
            token = "test-token",
            businessuuid = "test-business-uuid"
        )
    }

    @Autowired
    private lateinit var yocoHeaderConfig: YocoHeaderConfig

    /**
     * Empirically verifies that Spring's @Value processor resolves
     * izinga.ai.openai.connect-timeout-seconds and izinga.ai.openai.response-timeout-seconds
     * from the property source rather than falling back to the Kotlin field defaults.
     *
     * Values 7 and 22 are intentionally non-default (defaults are 3 / 15) so a silent
     * fallback to the Kotlin field defaults would be caught as a mismatch immediately.
     *
     * If this test fails: check the @Value expressions in YocoHeaderConfig — the property
     * key in the annotation must match the key advertised in application.yml exactly, including
     * the hyphen vs camel-case form Spring's relaxed-binding translates.
     */
    @Test
    fun `Spring property resolver binds timeout properties to YocoHeaderConfig fields`() {
        assertEquals(
            7L,
            yocoHeaderConfig.openAiConnectTimeoutSeconds,
            "connect-timeout-seconds=7 must override Kotlin default 3L — " +
            "if this is 3L, the @Value expression '\${izinga.ai.openai.connect-timeout-seconds:3}' " +
            "is mis-bound or the property key does not match what Spring resolves"
        )
        assertEquals(
            22L,
            yocoHeaderConfig.openAiResponseTimeoutSeconds,
            "response-timeout-seconds=22 must override Kotlin default 15L — " +
            "if this is 15L, the @Value expression '\${izinga.ai.openai.response-timeout-seconds:15}' " +
            "is mis-bound or the property key does not match what Spring resolves"
        )
    }
}
