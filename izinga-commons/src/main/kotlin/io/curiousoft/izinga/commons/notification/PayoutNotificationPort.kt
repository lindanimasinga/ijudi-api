package io.curiousoft.izinga.commons.notification

/**
 * Port for notifying users when payout generation cannot proceed due to missing profile data.
 *
 * Defined in izinga-commons so that izinga-recon can accept it via constructor injection
 * without a compile-time coupling to izinga-messaging.
 *
 * Live implementation: [io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService]
 * in izinga-messaging, registered as a Spring bean and wired at runtime by izinga-ordermanager.
 */
interface PayoutNotificationPort {

    /**
     * Notifies a driver/messenger that their bank details are missing and payout generation
     * was skipped for their account.
     *
     * Template: `driver_missing_bank_details`
     * IMPORTANT: This template has NOT yet been submitted to Meta for approval.
     * The application code is built and wired; a product/PO action is required to submit
     * the template to Meta Business Manager before notifications will reach drivers.
     * Until the template is approved, the WhatsApp API call will fail with a "template not found"
     * error — implementations MUST absorb all exceptions internally (log-and-swallow) so that a
     * template-not-yet-approved failure never propagates to the payout generation path.
     *
     * @param mobileNumber normalised or raw mobile number of the driver (normalisation applied internally).
     * @param driverName   display name of the driver (or a fallback like "Driver").
     */
    fun notifyDriverMissingBankDetails(mobileNumber: String, driverName: String)
}
