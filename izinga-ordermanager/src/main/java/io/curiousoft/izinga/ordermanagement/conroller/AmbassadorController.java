package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class AmbassadorController {

    private static final Logger logger = LoggerFactory.getLogger(AmbassadorController.class);
    private static final String ONBOARDING_BASE_URL = "https://onboarding.izinga.co.za/indivisuals?ref=";

    private final UserProfileRepo userProfileRepo;
    private final QRCodeService qrCodeService;

    public AmbassadorController(UserProfileRepo userProfileRepo, QRCodeService qrCodeService) {
        this.userProfileRepo = userProfileRepo;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping(value = "/{userId}/ambassador-qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getAmbassadorQrCode(@PathVariable String userId) {
        logger.info("Ambassador QR code request for userId={}", userId);

        UserProfile profile = userProfileRepo.findById(userId).orElse(null);
        if (profile == null) {
            logger.warn("Ambassador QR request for unknown userId={}", userId);
            return ResponseEntity.notFound().build();
        }
        if (profile.getRole() != ProfileRoles.AMBASSADOR || !Boolean.TRUE.equals(profile.getProfileApproved())) {
            logger.warn("Ambassador QR denied: userId={} role={} approved={}", userId, profile.getRole(), profile.getProfileApproved());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String qrContent = ONBOARDING_BASE_URL + userId;
        String label = profile.getName() != null ? profile.getName() : userId;
        try {
            byte[] qrImage = qrCodeService.generateQRCodeImage("REFER A FRIEND", qrContent, label, 450, 450);
            logger.info("Ambassador QR generated for userId={}", userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(qrImage);
        } catch (Exception e) {
            logger.error("Failed to generate ambassador QR for userId={}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
