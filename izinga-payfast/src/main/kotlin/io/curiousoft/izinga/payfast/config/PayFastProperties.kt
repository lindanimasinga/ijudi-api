package io.curiousoft.izinga.payfast.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TIER-BILLING-01 / REQ-02: PayFast subscription configuration.
 *
 * AC-20 / RISK-05: merchant-key and passphrase MUST be resolved from environment secrets
 * (AWS Secrets Manager / environment variables) — they must NOT be committed to any
 * application.yml or other version-controlled file.
 *
 * Sandbox vs production credentials are separated by Spring profile:
 *   - spring.profiles.active=sandbox → uses sandbox merchant credentials + sandbox base-url
 *   - spring.profiles.active=prod    → uses production merchant credentials + production base-url
 *
 * The merchantId (16791971) is not itself secret and is exposed in the PayFast form parameters,
 * but we still load it via config to allow sandbox testing with a different ID.
 */
@ConfigurationProperties(prefix = "payfast.subscription")
data class PayFastProperties(
    /** PayFast merchant ID. Production: 16791971. */
    val merchantId: String,

    /**
     * PayFast merchant key — loaded from environment/secrets manager only.
     * Never hardcode. Never log.
     */
    val merchantKey: String,

    /**
     * PayFast passphrase — loaded from environment/secrets manager only.
     * Never hardcode. Never log.
     */
    val passphrase: String,

    /** PayFast hosted payment page base URL.
     *  Sandbox: https://sandbox.payfast.co.za/eng/process
     *  Production: https://www.payfast.co.za/eng/process
     */
    val baseUrl: String,

    /**
     * Return URL after merchant completes payment on PayFast.
     * e.g. https://biz.izinga.co.za/business/subscription-success
     */
    val returnUrl: String,

    /**
     * Cancel URL after merchant cancels on PayFast.
     * e.g. https://biz.izinga.co.za/business/subscription-cancel
     */
    val cancelUrl: String,

    /**
     * ITN webhook URL (iZinga's own endpoint that PayFast posts to).
     * e.g. https://api.izinga.co.za/merchant/subscription/itn
     */
    val notifyUrl: String,

    /**
     * PayFast server-to-server ITN validate endpoint.
     * Sandbox:    https://sandbox.payfast.co.za/eng/query/validate
     * Production: https://www.payfast.co.za/eng/query/validate
     *
     * SEC-TB01-01-C/D: This property must be set in all profiles. The production implementation
     * has no bypass path — [PayFastValidateClientImpl] always calls this URL.
     */
    val validateUrl: String,

    /**
     * HTTP connect+read timeout (seconds) for the PayFast server-to-server validate call.
     * SEC-TB01-01-C: On timeout, [PayFastValidateTransientException] is thrown → HTTP 500
     * is returned → PayFast retries the ITN.
     *
     * Tunable without a code change. Default: 5 seconds.
     */
    val validateTimeoutSeconds: Int = 5
)
