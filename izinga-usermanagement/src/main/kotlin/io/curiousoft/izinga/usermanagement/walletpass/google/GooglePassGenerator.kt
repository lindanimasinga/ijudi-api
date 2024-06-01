package io.curiousoft.izinga.usermanagement.walletpass.google

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.walletobjects.Walletobjects
import com.google.api.services.walletobjects.WalletobjectsScopes
import com.google.api.services.walletobjects.model.*
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.usermanagement.walletpass.PassGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.interfaces.RSAPrivateKey
import java.util.*
import java.util.List
import kotlin.collections.HashMap
import kotlin.collections.listOf
import kotlin.collections.set


@Service
class GooglePassGenerator : PassGenerator<String> {

    private lateinit var service: Walletobjects
    private lateinit var credentials: GoogleCredentials

    private var googleConfig: GoogleConfig

    @Autowired
    constructor(googleConfig: GoogleConfig) {
        this.googleConfig = googleConfig
        val walletKeyStream = ByteArrayInputStream(googleConfig.configAsJson().toByteArray())
        credentials = GoogleCredentials.fromStream(walletKeyStream)
            .createScoped(List.of(WalletobjectsScopes.WALLET_OBJECT_ISSUER))
        credentials.refresh()
        val httpTransport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()

        // Initialize Google Wallet API service
        service = Walletobjects.Builder(httpTransport, GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(credentials))
            .setApplicationName("APPLICATION_NAME")
            .build()
    }

    override fun generatePass(user: UserProfile): String {
        return createJWTNewObjects(user);
    }

    fun createJWTNewObjects(user: UserProfile): String {
        val objectSuffix = "loyalty-${user.id}"
        val issuerId = "3388000000021566341"
        val classSuffix = "io.curiousoft.izinga"

        val loyaltyObject = createObject(issuerId, classSuffix, objectSuffix, user)


        // Create the JWT as a HashMap object
        val claims = HashMap<String, Any>()
        claims["iss"] = (credentials as ServiceAccountCredentials).clientEmail
        claims["aud"] = "google"
        claims["origins"] = listOf("izinga.co.za")
        claims["typ"] = "savetowallet"

        // Create the Google Wallet payload and add to the JWT
        val payload = HashMap<String, Any>()
        //payload["loyaltyClasses"] = listOf(newClass)
        payload["loyaltyObjects"] = listOf(loyaltyObject)
        claims["payload"] = payload

        // Loyalty cards
        // The service account credentials are used to sign the JWT
        val algorithm: Algorithm = Algorithm.RSA256(null, (credentials as ServiceAccountCredentials).privateKey as RSAPrivateKey)

        val token = JWT.create().withPayload(claims).sign(algorithm)
        println("Add to Google Wallet link")
        System.out.printf("https://pay.google.com/gp/v/save/%s%n", token)
        return "https://pay.google.com/gp/v/save/$token"
    }

    fun createObject(issuerId: String, classSuffix: String, objectSuffix: String, user: UserProfile): LoyaltyObject? {
        // Check if the object exists
       return runCatching { service.loyaltyobject()["$issuerId.$objectSuffix"].execute() }
           .onSuccess { return it }
           .exceptionOrNull()
           ?.takeIf { (it as GoogleJsonResponseException).statusCode == 404 }
           ?.let {
               // See link below for more information on required properties
               // https://developers.google.com/wallet/retail/loyalty-cards/rest/v1/loyaltyobject
               val newObject = LoyaltyObject().apply {
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
                    accountId = user.mobileNumber
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
                }
                return service.loyaltyobject().insert(newObject).execute()
            }
    }

}