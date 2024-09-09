package io.curiousoft.izinga.messaging.firebase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "google.firebase-messaging.secrets")
public record FirebaseAuthConfig(
    @JsonProperty("type")
    String type,
    @JsonProperty("project_id")
    String projectId,
    @JsonProperty("private_key_id")
    String privateKeyId,
    @JsonProperty("private_key")
    String privateKey,
    @JsonProperty("client_email")
    String clientEmail,
    @JsonProperty("client_id")
    String clientId,
    @JsonProperty("auth_uri")
    String authUri,
    @JsonProperty("token_uri")
    String tokenUri,
    @JsonProperty("auth_provider_x509_cert_url")
    String authProviderX509CertUrl,
    @JsonProperty("client_x509_cert_url")
    String clientX509CertUrl,
    @JsonProperty("universe_domain")
    String universeDomain) {

    public String configAsJson() throws JsonProcessingException {
        var mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper.writeValueAsString(this).replace("\\\\n", "\\n");
    }
}
