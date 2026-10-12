package io.curiousoft.izinga.commons.notification

/**
 * Abstraction for sending driver-invite WhatsApp notifications.
 *
 * Defined in izinga-commons so that izinga-usermanagement (which cannot depend on
 * izinga-messaging due to the reverse dependency) can accept the notification sender
 * via constructor injection without a compile-time coupling to the messaging module.
 *
 * The live implementation is [io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService]
 * (in izinga-messaging), registered as a Spring bean and wired at runtime by izinga-ordermanager.
 */
interface DriverInviteNotificationPort {

    /**
     * Sends a WhatsApp invite notification to the driver.
     *
     * Template: `driver_store_invite` — requires Meta Business approval before going live.
     * Implementations MUST absorb all exceptions internally (log-and-swallow) so that a
     * template-not-yet-approved or network failure never propagates to the invite endpoint.
     *
     * @param driverMobileNumber normalised E.164 mobile number of the driver to notify.
     * @param driverName         display name of the driver (or a fallback like "Driver").
     * @param storeName          display name of the inviting store (or admin group).
     * @param invitationCode     bare UUID code appended to the button's static URL prefix.
     */
    fun sendDriverStoreInvitation(
        driverMobileNumber: String,
        driverName: String,
        storeName: String,
        invitationCode: String
    )
}
