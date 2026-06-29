package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.usermanagement.userconfig.FieldDataType;
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfig;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappImageDocumentServiceTest {

    @Mock
    private UserProfileRepo userProfileRepo;

    @Mock
    private UserConfigService userConfigService;

    @Mock
    private WhatsappMediaDownloader mediaDownloader;

    @Mock
    private MediaStorageService mediaStorageService;

    private WhatsappImageDocumentService service;

    @BeforeEach
    void setUp() {
        service = new WhatsappImageDocumentService(userProfileRepo, userConfigService, mediaDownloader, mediaStorageService);
    }

    @Test
    void processImageAndTagUser_tagsConfiguredFieldAndUploads() throws Exception {
        var user = testUser("+27812345678");
        when(userProfileRepo.findByMobileNumber("0812345678")).thenReturn(user);
                when(userConfigService.findAll()).thenReturn(java.util.List.of(testConfig()));

        var image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("mid-1");
        image.setCaption("field:identityDocument");
        image.setMimeType("image/png");

        when(mediaDownloader.downloadImage(image))
                .thenReturn(new WhatsappMediaDownloader.DownloadedWhatsappMedia("mid-1", "image/png", "img".getBytes()));
        when(mediaStorageService.upload(any(), any())).thenReturn(new URL("https://s3.example.com/doc.png"));

        WhatsappImageProcessResult result = service.processImageAndTagUser("27812345678", image);

        assertEquals("identityDocument", result.tagField());
        assertEquals("https://s3.example.com/doc.png", result.uploadedUrl());
        assertEquals("https://s3.example.com/doc.png", user.getTag().get("identityDocument"));
        verify(userProfileRepo).save(user);
    }

        @Test
        void processImageAndTagUser_bakkieDriverConfig_updatesUserTagMapAsExpected() throws Exception {
                var user = testUser("+27812345678");
                user.getTag().put("driverLicenseNumber", "DL-123456");
                user.getTag().put("idNumber", "9101015800087");
                user.getTag().put("driverLicenseDocument", "https://old.example.com/license.jpg");

                when(userProfileRepo.findByMobileNumber("0812345678")).thenReturn(user);
                when(userConfigService.findAll()).thenReturn(java.util.List.of(testConfig()));

                var image = new WhatsappWebhookPayload.Value.Message.Image();
                image.setId("mid-bakkie-1");
                image.setCaption("field:vehiclePhotoFront");
                image.setMimeType("image/jpeg");

                when(mediaDownloader.downloadImage(image))
                                .thenReturn(new WhatsappMediaDownloader.DownloadedWhatsappMedia("mid-bakkie-1", "image/jpeg", "img".getBytes()));
                when(mediaStorageService.upload(any(), any())).thenReturn(new URL("https://s3.example.com/vehicle-front.jpg"));

                WhatsappImageProcessResult result = service.processImageAndTagUser("27812345678", image);

                assertEquals("vehiclePhotoFront", result.tagField());
                assertEquals("https://s3.example.com/vehicle-front.jpg", result.uploadedUrl());

                Map<String, String> expectedTags = Map.of(
                                "driverLicenseNumber", "DL-123456",
                                "idNumber", "9101015800087",
                                "driverLicenseDocument", "https://old.example.com/license.jpg",
                                "vehiclePhotoFront", "https://s3.example.com/vehicle-front.jpg"
                );
                assertEquals(expectedTags, user.getTag());
        }

    @Test
    void processImageAndTagUser_fallsBackToMissingConfiguredDocumentFieldWhenCaptionMissing() throws Exception {
        var user = testUser("+27812345678");
        when(userProfileRepo.findByMobileNumber("0812345678")).thenReturn(user);
        when(userConfigService.findAll()).thenReturn(java.util.List.of(testConfig()));

        var image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("mid-2");
        image.setMimeType("image/jpeg");

        when(mediaDownloader.downloadImage(image))
                .thenReturn(new WhatsappMediaDownloader.DownloadedWhatsappMedia("mid-2", "image/jpeg", "img".getBytes()));
        when(mediaStorageService.upload(any(), any())).thenReturn(new URL("https://s3.example.com/doc2.jpg"));

        WhatsappImageProcessResult result = service.processImageAndTagUser("27812345678", image);

        assertEquals("driverLicenseDocument", result.tagField());
        assertEquals("https://s3.example.com/doc2.jpg", result.uploadedUrl());
        assertEquals("https://s3.example.com/doc2.jpg", user.getTag().get("driverLicenseDocument"));
    }

    @Test
    void processImageAndTagUser_throwsWhenUserNotFound() {
        var image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("mid-3");
        image.setMimeType("image/jpeg");

        when(userProfileRepo.findByMobileNumber(eq("0812345678"))).thenReturn(null);
        when(userProfileRepo.findByMobileNumber(eq("+27812345678"))).thenReturn(null);
        when(userProfileRepo.findByMobileNumber(eq("27812345678"))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.processImageAndTagUser("27812345678", image));
    }

    @Test
    void processImageAndTagUser_throwsWhenNoDocumentFieldsAreConfigured() {
        var user = testUser("+27812345678");
        when(userProfileRepo.findByMobileNumber("0812345678")).thenReturn(user);
        when(userConfigService.findAll()).thenReturn(java.util.List.of(
                new UserConfig("driver", "Driver", ProfileRoles.MESSENGER, java.util.List.of(), java.util.List.of())
        ));

        var image = new WhatsappWebhookPayload.Value.Message.Image();
        image.setId("mid-4");
        image.setMimeType("image/jpeg");

        assertThrows(IllegalArgumentException.class,
                () -> service.processImageAndTagUser("27812345678", image));
    }

    private UserProfile testUser(String phone) {
        UserProfile user = new UserProfile(
                "Driver",
                UserProfile.SignUpReason.DELIVERY_DRIVER,
                "Address",
                "https://image.url/profile.png",
                phone,
                ProfileRoles.MESSENGER
        );
        user.setId("user-1");
        user.setDescription("Bakkie Delivery Driver");
        return user;
    }

    private UserConfig testConfig() {
        return new UserConfig(
                "BAKKIE_DELIVERY_DRIVER",
                "Bakkie Delivery Driver",
                ProfileRoles.MESSENGER,
                java.util.List.of(
                        new FieldSpec("driverLicenseNumber", "Driver's License Number", FieldDataType.STRING),
                        new FieldSpec("idNumber", "id number", FieldDataType.STRING),
                        new FieldSpec("driverLicenseDocument", "Driver's License Document", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("identityDocument", "Identity Document", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("driverLicenseExpiry", "Driver's License Expiry Date", FieldDataType.DATE),
                        new FieldSpec("vehicleRegistration", "Bakkie Registration Number", FieldDataType.STRING),
                        new FieldSpec("vehicleLicenseDisk", "Bakkie License Disk", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("licenseDiskExpiry", "License Disk Expiry Date", FieldDataType.DATE),
                        new FieldSpec("vehicleMake", "Bakkie Make", FieldDataType.STRING),
                        new FieldSpec("vehicleModel", "Bakkie Model", FieldDataType.STRING),
                        new FieldSpec("vehicleYear", "Bakkie Year", FieldDataType.NUMBER),
                        new FieldSpec("vehicleMileage", "Bakkie Mileage", FieldDataType.NUMBER),
                        new FieldSpec("vehiclePhotoFront", "Bakkie Photo - Front View", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("vehiclePhotoSide", "Bakkie Photo - Side View", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("vehiclePhotoBack", "Bakkie Photo - Back View", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("vehicleSpeedometerPhoto", "Speedometer Photo", FieldDataType.DOCUMENT_URL),
                        new FieldSpec("loadCapacity", "Load Capacity (kg)", FieldDataType.NUMBER)
                ),
                java.util.List.of()
        );
    }
}
