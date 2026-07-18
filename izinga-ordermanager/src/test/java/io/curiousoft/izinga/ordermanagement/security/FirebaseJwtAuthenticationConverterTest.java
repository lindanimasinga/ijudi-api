package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for FirebaseJwtAuthenticationConverter.convert().
 *
 * These tests call convert() directly with no Spring context. They exist because
 * every @WebMvcTest security test in ReferralPartnerControllerSecurityTest and
 * ReconControllerSecurityTest uses @WithMockUser, which injects a pre-built
 * Authentication object and completely bypasses this converter. This class is the
 * ONLY test that verifies the real JWT-to-Authentication conversion path.
 *
 * See also:
 *   - izinga-usermanagement: ReferralPartnerControllerSecurityTest — verifies @PreAuthorize SpEL via @WithMockUser
 *   - izinga-recon: ReconControllerSecurityTest — verifies @PreAuthorize SpEL via @WithMockUser
 */
@RunWith(MockitoJUnitRunner.class)
public class FirebaseJwtAuthenticationConverterTest {

    private static final String FIREBASE_UID = "firebase-uid-abc123";
    private static final String PHONE        = "+27821234567";
    private static final String MONGO_ID     = "mongo-uuid-xyz789";

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private FirebaseJwtAuthenticationConverter converter;

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private Jwt buildJwt(String sub, String phoneNumber) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims  = phoneNumber != null
                ? Map.of("sub", sub, "phone_number", phoneNumber)
                : Map.of("sub", sub);
        return Jwt.withTokenValue("token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private UserProfile profileWith(String id, ProfileRoles role) {
        UserProfile p = mock(UserProfile.class);
        when(p.getId()).thenReturn(id);
        when(p.getRole()).thenReturn(role);
        return p;
    }

    private java.util.Set<String> authorityNames(JwtAuthenticationToken token) {
        return token.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }

    // -----------------------------------------------------------------------
    // Test 1: profile found with REFERRAL_PARTNER role
    // -----------------------------------------------------------------------

    @Test
    public void convert_profileWithReferralPartnerRole_setsMongoIdAsPrincipalAndCorrectAuthority() {
        UserProfile profile = profileWith(MONGO_ID, ProfileRoles.REFERRAL_PARTNER);
        when(userProfileService.findUserByPhone(PHONE)).thenReturn(profile);

        Jwt jwt = buildJwt(FIREBASE_UID, PHONE);
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals("principal name must be MongoDB profile id, not Firebase uid", MONGO_ID, token.getName());
        java.util.Set<String> authorities = authorityNames(token);
        assertEquals("exactly one authority", 1, authorities.size());
        assertTrue("must contain ROLE_REFERRAL_PARTNER", authorities.contains("ROLE_REFERRAL_PARTNER"));
    }

    // -----------------------------------------------------------------------
    // Test 2: profile found with ADMIN role
    // -----------------------------------------------------------------------

    @Test
    public void convert_profileWithAdminRole_setsRoleAdmin() {
        UserProfile profile = profileWith(MONGO_ID, ProfileRoles.ADMIN);
        when(userProfileService.findUserByPhone(PHONE)).thenReturn(profile);

        Jwt jwt = buildJwt(FIREBASE_UID, PHONE);
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals(MONGO_ID, token.getName());
        assertTrue(authorityNames(token).contains("ROLE_ADMIN"));
        assertEquals(1, token.getAuthorities().size());
    }

    // -----------------------------------------------------------------------
    // Test 3: profile found with AMBASSADOR role
    // -----------------------------------------------------------------------

    @Test
    public void convert_profileWithAmbassadorRole_setsRoleAmbassador() {
        UserProfile profile = profileWith(MONGO_ID, ProfileRoles.AMBASSADOR);
        when(userProfileService.findUserByPhone(PHONE)).thenReturn(profile);

        Jwt jwt = buildJwt(FIREBASE_UID, PHONE);
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals(MONGO_ID, token.getName());
        assertTrue(authorityNames(token).contains("ROLE_AMBASSADOR"));
        assertEquals(1, token.getAuthorities().size());
    }

    // -----------------------------------------------------------------------
    // Test 4: profile not found — fallback to Firebase uid, empty authorities
    // -----------------------------------------------------------------------

    @Test
    public void convert_profileNotFound_fallsBackToFirebaseUidAndEmptyAuthorities() {
        when(userProfileService.findUserByPhone(PHONE)).thenReturn(null);

        Jwt jwt = buildJwt(FIREBASE_UID, PHONE);
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals("must fall back to Firebase uid when no profile found", FIREBASE_UID, token.getName());
        assertTrue("authorities must be empty when no profile found", token.getAuthorities().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Test 5: no phone_number claim in JWT — empty authorities, uid as name
    // -----------------------------------------------------------------------

    @Test
    public void convert_noPhoneNumberClaim_grantsEmptyAuthoritiesAndFirebaseUidAsName() {
        Jwt jwt = buildJwt(FIREBASE_UID, null);

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals("must use Firebase uid when no phone claim", FIREBASE_UID, token.getName());
        assertTrue("authorities must be empty when no phone claim", token.getAuthorities().isEmpty());
        verify(userProfileService, never()).findUserByPhone(any());
    }

    // -----------------------------------------------------------------------
    // Test 6: profile found but role is null — empty authorities (no ROLE_null)
    // -----------------------------------------------------------------------

    @Test
    public void convert_profileFoundButRoleIsNull_grantsEmptyAuthoritiesNotRoleNull() {
        UserProfile profile = profileWith(MONGO_ID, null);
        when(userProfileService.findUserByPhone(PHONE)).thenReturn(profile);

        Jwt jwt = buildJwt(FIREBASE_UID, PHONE);
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        // Principal should still be the profile id
        assertEquals(MONGO_ID, token.getName());
        assertTrue("authorities must be empty when role is null", token.getAuthorities().isEmpty());
        // Explicit guard: no "ROLE_null" must appear
        assertFalse(authorityNames(token).contains("ROLE_null"));
    }
}
