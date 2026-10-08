package io.curiousoft.izinga.payfast.service

/**
 * Thrown by [PayFastValidateClient] when the server-to-server validate call fails with
 * a transient error (network timeout, connection refused, or PayFast 5xx response).
 *
 * SEC-TB01-01-C: Transient validate failures must result in HTTP 500 from the ITN endpoint
 * so that PayFast retries the delivery. This exception propagates out of [PayFastItnHandler]
 * and is caught specifically in [io.curiousoft.izinga.ordermanagement.subscription.MerchantSubscriptionController],
 * which returns ResponseEntity.status(500) when it is present.
 *
 * This is distinct from a definitive INVALID response (PayFast confirmed the payment does not
 * exist on their platform), which is a permanent rejection and must return HTTP 200 to
 * prevent PayFast from retrying an unfixable failure.
 */
class PayFastValidateTransientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
