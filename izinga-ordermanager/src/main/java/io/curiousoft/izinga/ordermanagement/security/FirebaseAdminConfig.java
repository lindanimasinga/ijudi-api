package io.curiousoft.izinga.ordermanagement.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import io.curiousoft.izinga.messaging.firebase.FirebaseAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Initialises the Firebase Admin SDK for WhatsApp OTP login.
 *
 * IMPORTANT: Uses an independent GoogleCredentials stream created directly from
 * FirebaseAuthConfig.configAsJson() with NO .createScoped() call. This is intentional:
 * - MessagingConfig's GoogleCredentials is scoped to firebase.messaging only and would
 *   fail on createCustomToken / getUserByPhoneNumber (Admin Auth calls).
 * - Same service-account JSON, separate credential object with full Admin SDK scope.
 *
 * SEC-07 prerequisite: the service account must hold the Firebase Authentication Admin
 * role (or firebaseauth.users.create + firebaseauth.tokens.sign permissions) in GCP
 * before custom token minting will succeed at runtime. Verify in GCP console before
 * deploying to production — this cannot be confirmed from code at build time.
 *
 * SEC-04 accepted tradeoff (Lindani, 2026-09): reusing the existing FCM service account
 * for Auth Admin operations. If the FIREBASE_SERVICE_ACCOUNT_KEY secret is ever
 * exfiltrated, an attacker can mint custom tokens for any UID. Accepted in exchange
 * for zero new secrets to provision.
 */
@Configuration
public class FirebaseAdminConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FirebaseAdminConfig.class);
    private static final String FIREBASE_ADMIN_APP_NAME = "izinga-admin";

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseAuthConfig firebaseAuthConfig) throws IOException {
        // Check if the named app already exists (e.g. during tests with context refresh)
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(FIREBASE_ADMIN_APP_NAME);
            LOG.info("FirebaseApp '{}' already initialised — reusing.", FIREBASE_ADMIN_APP_NAME);
        } catch (IllegalStateException e) {
            // App not yet initialised — create it.
            // CRITICAL: new ByteArrayInputStream each time — do NOT reuse MessagingConfig's
            // already-consumed/FCM-scoped GoogleCredentials object.
            var credentialStream = new ByteArrayInputStream(
                    firebaseAuthConfig.configAsJson().getBytes());
            var credentials = GoogleCredentials.fromStream(credentialStream);
            // No .createScoped() — Admin SDK manages its own scopes internally.

            var options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(firebaseAuthConfig.projectId())
                    .build();

            app = FirebaseApp.initializeApp(options, FIREBASE_ADMIN_APP_NAME);
            LOG.info("FirebaseApp '{}' initialised for project '{}'.",
                    FIREBASE_ADMIN_APP_NAME, firebaseAuthConfig.projectId());
        }
        return FirebaseAuth.getInstance(app);
    }
}
