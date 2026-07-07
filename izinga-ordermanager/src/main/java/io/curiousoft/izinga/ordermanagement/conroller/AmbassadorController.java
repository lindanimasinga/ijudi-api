package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.Bank;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PostMapping("/ambassador")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AmbassadorResponse> createAmbassador(@RequestBody AmbassadorRequest request) {
        logger.info("Create ambassador request for phone={}", request.getPhone());

        UserProfile existing = userProfileRepo.findByMobileNumber(request.getPhone());
        if (existing != null) {
            logger.warn("Ambassador creation conflict: phone={} already exists", request.getPhone());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        UserProfile profile = new UserProfile(
                request.getName(),
                UserProfile.SignUpReason.DELIVERY_DRIVER,
                null,
                null,
                request.getPhone(),
                ProfileRoles.AMBASSADOR
        );
        profile.setId(UUID.randomUUID().toString());
        profile.setProfileApproved(true);

        Bank bank = new Bank();
        bank.setAccountId(request.getBankAccount());
        bank.setName(request.getBankName());
        bank.setBranchCode(request.getBranchCode());
        profile.setBank(bank);

        userProfileRepo.save(profile);

        String referralUrl = ONBOARDING_BASE_URL + profile.getId();
        logger.info("Ambassador created: userId={}", profile.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AmbassadorResponse(profile.getId(), referralUrl));
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean isOwner = auth != null && userId.equals(auth.getName());

        if (!isAdmin && !isOwner) {
            logger.warn("Ambassador QR access denied: requestor={} userId={}", auth != null ? auth.getName() : "anonymous", userId);
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
