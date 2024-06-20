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
import java.math.BigDecimal
import java.security.interfaces.RSAPrivateKey
import java.util.*
import java.util.List
import kotlin.collections.HashMap
import kotlin.collections.listOf
import kotlin.collections.set


@Service
class GooglePassGenerator : PassGenerator<String> {

    private var service: Walletobjects
    private var credentials: GoogleCredentials

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
        service =
            Walletobjects.Builder(httpTransport, GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(credentials))
                .setApplicationName("APPLICATION_NAME")
                .build()
    }

    override fun generatePass(user: UserProfile): String {
        return createJWTNewObjects(user);
    }

    fun createJWTNewObjects(user: UserProfile): String {

        val loyaltyObject = createObject(issuerId, classSuffix, user.loyaltyId(), user)

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
        val algorithm: Algorithm =
            Algorithm.RSA256(null, (credentials as ServiceAccountCredentials).privateKey as RSAPrivateKey)

        val token = JWT.create().withPayload(claims).sign(algorithm)
        println("Add to Google Wallet link")
        System.out.printf("https://pay.google.com/gp/v/save/%s%n", token)
        return "https://pay.google.com/gp/v/save/$token"
    }

    fun createObject(issuerId: String, classSuffix: String, objectSuffix: String, user: UserProfile): LoyaltyObject? {
        // Check if the object exists
        return runCatching { service.loyaltyobject()["$issuerId.$objectSuffix"].execute() }
            .onSuccess {
                it.updateIzingaLoyalty(user)
                return service.loyaltyobject().update(it.id, it).execute()
            }
            .exceptionOrNull()
            ?.takeIf { (it as GoogleJsonResponseException).statusCode == 404 }
            ?.let { LoyaltyObject() }
            ?.let {
                it.updateIzingaLoyalty(user)
                it.id
                service.loyaltyobject().insert(it).execute()
            }
    }

    fun getObject(issuerId: String, classSuffix: String, objectSuffix: String, user: UserProfile): LoyaltyObject? {
        return run { service.loyaltyobject()["$issuerId.$objectSuffix"].execute() }
    }

    fun updateObject(issuerId: String, classSuffix: String, objectSuffix: String, loyaltyObject: LoyaltyObject) {
        return run { service.loyaltyobject().update("$issuerId.$objectSuffix", loyaltyObject).execute() }
    }

    fun deleteObject(issuerId: String, classSuffix: String, objectSuffix: String, loyaltyObject: LoyaltyObject) {
        return run { service.loyaltyobject().update("$issuerId.$objectSuffix", loyaltyObject).execute() }
    }

    override fun updateBalance(user: UserProfile, balance: BigDecimal): Boolean {
        val loyaltyObject = getObject(issuerId, classSuffix, user.loyaltyId(), user)
        loyaltyObject?.loyaltyPoints?.balance?.money?.micros = (balance * 1000000.toBigDecimal()).toLong()
        return loyaltyObject?.let { updateObject(issuerId, classSuffix, user.loyaltyId(), it); true } ?: false
    }

}