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

        createObject(issuerId, classSuffix, objectSuffix, user)

        // See link below for more information on required properties
        // https://developers.google.com/wallet/retail/loyalty-cards/rest/v1/loyaltyclass
        val newClass = LoyaltyClass()
            .setId(String.format("%s.%s", issuerId, classSuffix))
            .setIssuerName("izinga")
            .setReviewStatus("UNDER_REVIEW")
            .setProgramName("Program name")
            .setProgramLogo(
                Image()
                    .setSourceUri(
                        ImageUri()
                            .setUri(
                                "https://farm4.staticflickr.com/3723/11177041115_6e6a3b6f49_o.jpg"
                            )
                    )
                    .setContentDescription(
                        LocalizedString()
                            .setDefaultValue(
                                TranslatedString()
                                    .setLanguage("en-US")
                                    .setValue("Logo description")
                            )
                    )
            )


        // See link below for more information on required properties
        // https://developers.google.com/wallet/retail/loyalty-cards/rest/v1/loyaltyobject
        val newObject = LoyaltyObject()
            .setId(String.format("%s.%s", issuerId, objectSuffix))
            .setClassId(String.format("%s.%s", issuerId, classSuffix))
            .setState("ACTIVE")
            .setHeroImage(
                Image()
                    .setSourceUri(ImageUri().setUri("https://farm4.staticflickr.com/3723/11177041115_6e6a3b6f49_o.jpg"))
                    .setContentDescription(LocalizedString()
                        .setDefaultValue(TranslatedString()
                            .setLanguage("en-US")
                            .setValue("Hero image description"))))
            .setTextModulesData(listOf(TextModuleData()
                .setHeader("Text module header")
                .setBody("Text module body")
                .setId("TEXT_MODULE_ID")))
            .setLinksModuleData(LinksModuleData()
                .setUris(
                Arrays.asList(Uri()
                    .setUri("http://maps.google.com/")
                    .setDescription("Link module URI description")
                    .setId("LINK_MODULE_URI_ID"),
                    Uri()
                        .setUri("tel:6505555555")
                        .setDescription("Link module tel description")
                        .setId("LINK_MODULE_TEL_ID"))))
            .setImageModulesData(listOf(ImageModuleData()
                .setMainImage(Image()
                    .setSourceUri(ImageUri().setUri("http://farm4.staticflickr.com/3738/12440799783_3dc3c20606_b.jpg"))
                    .setContentDescription(LocalizedString()
                        .setDefaultValue(TranslatedString()
                            .setLanguage("en-US")
                            .setValue("Image module description"))))
                        .setId("IMAGE_MODULE_ID")))
            .setBarcode(Barcode().setType("QR_CODE").setValue("QR code value"))
            .setLocations(listOf(LatLongPoint()
                .setLatitude(37.424015499999996)
                .setLongitude(-122.09259560000001)))
            .setAccountId("Account ID")
            .setAccountName("Account name")
            .setLoyaltyPoints(LoyaltyPoints()
                .setLabel("Points")
                .setBalance(LoyaltyPointsBalance().setInt(800)))

        // Create the JWT as a HashMap object
        val claims = HashMap<String, Any>()
        claims["iss"] = (credentials as ServiceAccountCredentials).clientEmail
        claims["aud"] = "google"
        claims["origins"] = listOf("www.example.com")
        claims["typ"] = "savetowallet"

        // Create the Google Wallet payload and add to the JWT
        val payload = HashMap<String, Any>()
        payload["loyaltyClasses"] = listOf(newClass)
        payload["loyaltyObjects"] = listOf(newObject)
        claims["payload"] = payload

        // Loyalty cards
        // The service account credentials are used to sign the JWT
        val algorithm: Algorithm = Algorithm.RSA256(null, (credentials as ServiceAccountCredentials).privateKey as RSAPrivateKey)

        val token = JWT.create().withPayload(claims).sign(algorithm)
        println("Add to Google Wallet link")
        System.out.printf("https://pay.google.com/gp/v/save/%s%n", token)
        return "https://pay.google.com/gp/v/save/$token"
    }

    @Throws(IOException::class)
    fun createObject(issuerId: String, classSuffix: String, objectSuffix: String, user: UserProfile): String {
        // Check if the object exists
        try {
            service.loyaltyobject()["$issuerId.$objectSuffix"].execute()
            System.out.printf("Object %s.%s already exists!%n", issuerId, objectSuffix)
            return "$issuerId.$objectSuffix"
        } catch (ex: GoogleJsonResponseException) {
            if (ex.statusCode != 404) {
                // Something else went wrong...
                ex.printStackTrace()
                return "$issuerId.$objectSuffix"
            }
        }

        // See link below for more information on required properties
        // https://developers.google.com/wallet/retail/loyalty-cards/rest/v1/loyaltyobject
        val newObject = LoyaltyObject().apply {
            id = "$issuerId.$objectSuffix"
            classId = "$issuerId.$classSuffix"
            state = ("ACTIVE")
            heroImage = Image()
                .setSourceUri(ImageUri()
                    .setUri("https://shop.izinga.co.za/assets/images/izinga-logo.png"))
            textModulesData = (List.of(TextModuleData()
                .setHeader("Text module header")
                .setBody("Text module body")
                .setId("TEXT_MODULE_ID")))
            linksModuleData = (LinksModuleData()
                .setUris(Arrays
                    .asList(Uri()
                        .setUri("https://onboard.izinga.co.za/user-lookup/${user.mobileNumber}")
                        .setDescription("Click To Update Banking Details")
                        .setId("LINK_MODULE_URI_ID"),
                        Uri()
                            .setUri("tel:+27812815707")
                            .setDescription("Contact Support")
                            .setId("LINK_MODULE_TEL_ID"))))
            imageModulesData = listOf(ImageModuleData()
                .setMainImage(Image()
                .setSourceUri(ImageUri()
                    .setUri("http://farm4.staticflickr.com/3738/12440799783_3dc3c20606_b.jpg"))
                .setContentDescription(LocalizedString()
                    .setDefaultValue(TranslatedString()
                        .setLanguage("en-US")
                        .setValue("Image module description"))))
                .setId("IMAGE_MODULE_ID"))
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
                            micros = 100L
                        }
                    }
                }
        }
        val response = service.loyaltyobject().insert(newObject).execute()
        println("Object insert response")
        println(response.toPrettyString())
        return response.id
    }

}