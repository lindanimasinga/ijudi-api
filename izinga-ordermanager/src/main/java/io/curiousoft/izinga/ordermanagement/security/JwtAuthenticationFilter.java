package io.curiousoft.izinga.ordermanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.messaging.firebase.FirebaseAuthConfig;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String GOOGLE_KEYS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();
    private long publicKeysExpiry = 0;

    private final FirebaseAuthConfig firebaseAuthConfig;

    @Autowired
    public JwtAuthenticationFilter(FirebaseAuthConfig firebaseAuthConfig) {
        this.firebaseAuthConfig = firebaseAuthConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        log.info("url path is {}", request.getServletPath());
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.replace("Bearer ", "").trim();
        try {
            // 1. Parse header
            String[] parts = token.split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> headerMap = objectMapper.readValue(headerJson, Map.class);

            // 2. Check alg and kid
            if (!"RS256".equals(headerMap.get("alg"))) throw new Exception("Invalid alg");
            String kid = (String) headerMap.get("kid");
            if (kid == null) throw new Exception("Missing kid");

            // 3. Load public keys if needed
            refreshPublicKeysIfNeeded();

            PublicKey publicKey = publicKeys.get(kid);
            if (publicKey == null) throw new Exception("Unknown kid");

            // 4. Parse and verify JWT
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();

            // 5. Validate payload claims
            long now = Instant.now().getEpochSecond();
            String projectId = firebaseAuthConfig.projectId();
            String issuer = "https://securetoken.google.com/" + projectId;

            if (claims.getExpiration().getTime() / 1000 <= now) {
                throw new Exception("Token expired");
            }
            if (!projectId.equals(claims.getAudience())) {
                throw new Exception("Invalid aud");
            }
            if (!issuer.equals(claims.getIssuer())) {
                throw new Exception("Invalid iss");
            }
            if (claims.getSubject() == null || claims.getSubject().isEmpty()) {
                throw new Exception("Invalid sub");
            }
            if (claims.get("auth_time", Long.class) > now) {
                throw new Exception("auth_time in future");
            }

            // 6. Set authentication
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private void refreshPublicKeysIfNeeded() throws IOException {
        long now = System.currentTimeMillis();
        if (now < publicKeysExpiry && !publicKeys.isEmpty()) return;

        URL url = new URL(GOOGLE_KEYS_URL);
        var conn = url.openConnection();
        conn.connect();
        int maxAge = 3600; // default 1 hour
        String cacheControl = conn.getHeaderField("Cache-Control");
        if (cacheControl != null && cacheControl.contains("max-age=")) {
            String[] parts = cacheControl.split("max-age=");
            maxAge = Integer.parseInt(parts[1].split(",")[0].trim());
        }
        publicKeysExpiry = now + maxAge * 1000L;

        Map<String, String> keys = objectMapper.readValue(conn.getInputStream(), Map.class);
        publicKeys.clear();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            String kid = entry.getKey();
            String cert = entry.getValue();
            PublicKey publicKey = getPublicKeyFromCert(cert);
            publicKeys.put(kid, publicKey);
        }
    }

    private PublicKey getPublicKeyFromCert(String cert) throws IOException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(
                    new java.io.ByteArrayInputStream(cert.getBytes()));
            return certificate.getPublicKey();
        } catch (Exception e) {
            throw new IOException("Failed to parse public key", e);
        }
    }
}