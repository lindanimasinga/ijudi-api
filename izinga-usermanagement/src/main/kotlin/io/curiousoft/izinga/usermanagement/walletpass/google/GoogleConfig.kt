package io.curiousoft.izinga.usermanagement.walletpass.google

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "google.secrets")
data class GoogleConfig(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("project_id")
    val projectId: String,
    @JsonProperty("private_key_id")
    val privateKeyId: String,
    @JsonProperty("private_key")
    val privateKey: String,
    @JsonProperty("client_email")
    val clientEmail: String,
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("auth_uri")
    val authUri: String,
    @JsonProperty("token_uri")
    val tokenUri: String,
    @JsonProperty("auth_provider_x509_cert_url")
    val authProviderX509CertUrl: String,
    @JsonProperty("client_x509_cert_url")
    val clientX509CertUrl: String,
    @JsonProperty("universe_domain")
    val universeDomain: String) {
    fun configAsJson() : String = ObjectMapper()
        .apply { propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE }
        .writeValueAsString(this)
        .replace("\\\\n", "\\n");
}
