package io.curiousoft.izinga.usermanagement.walletpass.google

import com.google.api.services.walletobjects.model.*
import io.curiousoft.izinga.commons.model.UserProfile
import java.util.*

val issuerId = "3388000000021566341"
val classSuffix = "io.curiousoft.izinga"

inline fun UserProfile.loyaltyId(): String  = "loyalty-${id}"

inline fun LoyaltyObject.updateIzingaLoyalty(user: UserProfile): LoyaltyObject {
    // See link below for more information on required properties
    // https://developers.google.com/wallet/retail/loyalty-cards/rest/v1/loyaltyobject
    val objectSuffix = user.loyaltyId()
    id = "$issuerId.$objectSuffix"
    classId = "$issuerId.$classSuffix"
    state = ("ACTIVE")
    heroImage = Image()
        .setSourceUri(
            ImageUri()
                .setUri("https://shop.izinga.co.za/assets/images/izinga-logo-magging.png")
        )
    textModulesData = (listOf())
    linksModuleData = (LinksModuleData()
        .setUris(
            Arrays
                .asList(
                    Uri()
                        .setUri("https://onboard.izinga.co.za/user-lookup/${user.mobileNumber}")
                        .setDescription("Update Banking Details")
                        .setId("LINK_MODULE_URI_ID"),
                    Uri()
                        .setUri("tel:+27812815707")
                        .setDescription("Contact Support")
                        .setId("LINK_MODULE_TEL_ID")
                )
        ))
    imageModulesData = listOf(
        ImageModuleData()
            .setMainImage(
                Image()
                    .setSourceUri(
                        ImageUri()
                            .setUri("http://farm4.staticflickr.com/3738/12440799783_3dc3c20606_b.jpg")
                    )
                    .setContentDescription(
                        LocalizedString()
                            .setDefaultValue(
                                TranslatedString()
                                    .setLanguage("en-US")
                                    .setValue("Image module description")
                            )
                    )
            )
            .setId("IMAGE_MODULE_ID")
    )
    barcode = Barcode().setType("QR_CODE").setValue("https://tips.izinga.co.za/tip?messengerId=" + user.id)
    locations = listOf(LatLongPoint().apply {
        latitude = 37.424015499999996
        longitude = -122.09259560000001
    })
    accountId = user.name
    accountName = user.name
    loyaltyPoints = LoyaltyPoints().apply {
        label = "Points"
        balance = LoyaltyPointsBalance().apply {
            money = Money().apply {
                currencyCode = "ZAR"
                micros = 10 * 1000000L
            }
        }
    }
    return this
}