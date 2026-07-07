package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AmbassadorControllerTest {

    @Mock
    private UserProfileRepo userProfileRepo;

    @Mock
    private QRCodeService qrCodeService;

    @InjectMocks
    private AmbassadorController ambassadorController;

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /ambassador ──────────────────────────────────────────────────────

    @Test
    public void createAmbassador_happyPath_returns201WithReferralUrl() {
        AmbassadorRequest request = new AmbassadorRequest("Alice", "+27821234567", "1234567890", "FNB", "250655");
        when(userProfileRepo.findByMobileNumber("+27821234567")).thenReturn(null);
        when(userProfileRepo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<AmbassadorResponse> response = ambassadorController.createAmbassador(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getUserId());
        assertTrue(response.getBody().getReferralUrl()
                .startsWith("https://onboarding.izinga.co.za/indivisuals?ref="));
        assertTrue(response.getBody().getReferralUrl()
                .contains(response.getBody().getUserId()));
        verify(userProfileRepo).save(any(UserProfile.class));
    }

    @Test
    public void createAmbassador_duplicatePhone_returns409() {
        AmbassadorRequest request = new AmbassadorRequest("Bob", "+27821234567", "111", "ABSA", "632005");
        UserProfile existing = mock(UserProfile.class);
        when(userProfileRepo.findByMobileNumber("+27821234567")).thenReturn(existing);

        ResponseEntity<AmbassadorResponse> response = ambassadorController.createAmbassador(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userProfileRepo, never()).save(any());
    }

    @Test
    public void createAmbassador_savedProfileHasCorrectFields() {
        AmbassadorRequest request = new AmbassadorRequest("Carol", "+27831112222", "9876", "Standard Bank", "051001");
        when(userProfileRepo.findByMobileNumber("+27831112222")).thenReturn(null);
        when(userProfileRepo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ambassadorController.createAmbassador(request);

        verify(userProfileRepo).save(argThat(p ->
                ProfileRoles.AMBASSADOR.equals(p.getRole())
                && Boolean.TRUE.equals(p.getProfileApproved())
                && "+27831112222".equals(p.getMobileNumber())
                && p.getBank() != null
                && "9876".equals(p.getBank().getAccountId())
                && "Standard Bank".equals(p.getBank().getName())
                && "051001".equals(p.getBank().getBranchCode())
        ));
    }

    // ── GET /{userId}/ambassador-qr ───────────────────────────────────────────

    private void setSecurityContext(String uid, boolean isAdmin) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(uid);
        List<SimpleGrantedAuthority> authorities = isAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        doReturn(authorities).when(auth).getAuthorities();
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    public void getAmbassadorQrCode_userNotFound_returns404() {
        when(userProfileRepo.findById("unknown-id")).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("unknown-id");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(qrCodeService);
    }

    @Test
    public void getAmbassadorQrCode_userNotAmbassador_returns403() {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.MESSENGER);
        when(userProfileRepo.findById("user-1")).thenReturn(Optional.of(profile));

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-1");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(qrCodeService);
    }

    @Test
    public void getAmbassadorQrCode_ambassadorNotApproved_returns403() {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(false);
        when(userProfileRepo.findById("user-2")).thenReturn(Optional.of(profile));

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-2");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(qrCodeService);
    }

    @Test
    public void getAmbassadorQrCode_nonOwnerNonAdmin_returns403() {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(userProfileRepo.findById("user-3")).thenReturn(Optional.of(profile));
        setSecurityContext("different-uid", false);

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-3");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(qrCodeService);
    }

    @Test
    public void getAmbassadorQrCode_ownerAccess_returns200() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn("John");
        when(userProfileRepo.findById("user-3")).thenReturn(Optional.of(profile));
        setSecurityContext("user-3", false);

        byte[] fakeImage = new byte[]{1, 2, 3};
        when(qrCodeService.generateQRCodeImage(
                eq("REFER A FRIEND"),
                eq("https://onboarding.izinga.co.za/indivisuals?ref=user-3"),
                eq("John"),
                eq(450),
                eq(450)
        )).thenReturn(fakeImage);

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-3");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertArrayEquals(fakeImage, response.getBody());
    }

    @Test
    public void getAmbassadorQrCode_adminAccess_returns200() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn("Jane");
        when(userProfileRepo.findById("user-4")).thenReturn(Optional.of(profile));
        setSecurityContext("admin-uid", true);

        byte[] fakeImage = new byte[]{4, 5, 6};
        when(qrCodeService.generateQRCodeImage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(fakeImage);

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-4");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(fakeImage, response.getBody());
    }

    @Test
    public void getAmbassadorQrCode_approvedAmbassador_nullName_usesUserIdAsLabel() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn(null);
        when(userProfileRepo.findById("user-4")).thenReturn(Optional.of(profile));
        setSecurityContext("user-4", false);

        byte[] fakeImage = new byte[]{4, 5, 6};
        when(qrCodeService.generateQRCodeImage(
                eq("REFER A FRIEND"),
                eq("https://onboarding.izinga.co.za/indivisuals?ref=user-4"),
                eq("user-4"),
                eq(450),
                eq(450)
        )).thenReturn(fakeImage);

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-4");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(fakeImage, response.getBody());
    }

    @Test
    public void getAmbassadorQrCode_qrServiceThrows_returns500() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn("Jane");
        when(userProfileRepo.findById("user-5")).thenReturn(Optional.of(profile));
        setSecurityContext("user-5", false);

        when(qrCodeService.generateQRCodeImage(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("QR generation failed"));

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-5");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
