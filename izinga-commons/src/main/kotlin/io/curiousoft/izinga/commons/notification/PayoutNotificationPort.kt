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

    /**
     * Notifies a store owner that their store has no bank details on file and payout generation
     * was skipped for the store.
     *
     * Template: `store_missing_bank_details`
     * IMPORTANT: This template is pending Meta Business approval. The PO is submitting it in
     * parallel with this wiring commit. Until the template is approved, the WhatsApp API call
     * will fail with a "template not found" error — implementations MUST absorb all exceptions
     * internally (log-and-swallow) so that a missing template never propagates to the payout
     * generation path.
     *
     * Suggested template body (UTILITY category, en, single BODY TEXT parameter + URL button):
     *   "Hi {{1}}, your iZinga store payout is ready but we could not send it because your bank
     *    details are missing. Please log in to izinga.co.za to add your bank details."
     *   Button: "Update Profile" -> https://izinga.co.za
     *
     * @param mobileNumber normalised or raw mobile number of the store owner.
     * @param storeName    display name of the store (or a fallback like "Store").
     */
    fun notifyStoreMissingBankDetails(mobileNumber: String, storeName: String)
}