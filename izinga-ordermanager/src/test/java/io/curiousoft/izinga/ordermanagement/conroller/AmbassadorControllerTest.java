package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
    public void getAmbassadorQrCode_approvedAmbassador_returnsPngImage() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn("John");
        when(userProfileRepo.findById("user-3")).thenReturn(Optional.of(profile));

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
    public void getAmbassadorQrCode_approvedAmbassador_nullName_usesUserIdAsLabel() throws Exception {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getRole()).thenReturn(ProfileRoles.AMBASSADOR);
        when(profile.getProfileApproved()).thenReturn(true);
        when(profile.getName()).thenReturn(null);
        when(userProfileRepo.findById("user-4")).thenReturn(Optional.of(profile));

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

        when(qrCodeService.generateQRCodeImage(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("QR generation failed"));

        ResponseEntity<byte[]> response = ambassadorController.getAmbassadorQrCode("user-5");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
