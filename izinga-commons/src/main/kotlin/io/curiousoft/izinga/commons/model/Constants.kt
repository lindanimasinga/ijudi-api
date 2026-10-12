package io.curiousoft.izinga.commons.model

/**
 * Cross-module constants shared between izinga-commons consumers.
 *
 * Add only values that are referenced in two or more modules or that must stay
 * stable as a shared contract.  Module-local constants belong in the module itself.
 */
object IzingaConstants {

    /**
     * Placeholder image URL used when a user profile has been created but the person has not yet
     * uploaded a profile photo.  Used by:
     *  - AmbassadorAdminController: admin-initiated AMBASSADOR records
     *  - StoreDriverInviteService: driver profiles created via the store invite flow (B-04)
     *
     * Replace with an iZinga-hosted asset if this URL is ever rotated.
     */
    const val PENDING_PHOTO_URL = "https://pbs.twimg.com/media/C1OKE9QXgAAArDp.jpg"
}
