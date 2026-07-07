package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.recon.ReconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class UserProfileEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileEventHandler.class);
    private static final String DRIVER_APPROVAL_WHATSAPP_SENT_TAG = "driverApprovalWhatsappSent";
    private static final String AMBASSADOR_COMMISSION_CREATED_TAG = "ambassadorCommissionCreated";
    private final WhatsappNotificationService whatsappNotificationService;
    private final UserProfileRepo userProfileRepo;
    private final ReconService reconService;

    public UserProfileEventHandler(WhatsappNotificationService whatsappNotificationService,
                                   UserProfileRepo userProfileRepo,
                                   ReconService reconService) {
        this.whatsappNotificationService = whatsappNotificationService;
        this.userProfileRepo = userProfileRepo;
        this.reconService = reconService;
    }

    @Async
    @EventListener
    public void handleProfileCreated(ProfileCreatedEvent event) {
        if (!(event.getProfile() instanceof UserProfile)) return;

        UserProfile p = (UserProfile) event.getProfile();
        LOG.info("[user-profile-event] created: id={} name={} mobile={}", p.getId(), p.getName(), p.getMobileNumber());
        if (p.getRole() == ProfileRoles.MESSENGER || p.getRole() == ProfileRoles.MESSENGER_ADMIN) {
            whatsappNotificationService.sendWelcomeMessageDriver(p.getMobileNumber(), p.getName());
            p.setWelcomeMessageSent(true);
        }
    }

    @Async
    @EventListener
    public void handleProfileUpdated(ProfileUpdatedEvent event) {
        if (!(event.getProfile() instanceof UserProfile)) return;

        UserProfile driver = (UserProfile) event.getProfile();
        LOG.info("[user-profile-event] updated: id={} name={} mobile={}", driver.getId(), driver.getName(), driver.getMobileNumber());
        if (driver.getRole() == ProfileRoles.MESSENGER) {
            var alreadySent = "true".equalsIgnoreCase(driver.getTag().get(DRIVER_APPROVAL_WHATSAPP_SENT_TAG));
            if (driver.getProfileApproved() && !alreadySent) {
                whatsappNotificationService.sendDriverApprovedMessage(driver.getMobileNumber(), driver.getName());
                driver.getTag().put(DRIVER_APPROVAL_WHATSAPP_SENT_TAG, "true");
                userProfileRepo.save(driver);

                // Ambassador commission: trigger once when driver is first approved
                triggerAmbassadorCommissionIfEligible(driver);
            } else if (!driver.getProfileApproved() && alreadySent) {
                driver.getTag().remove(DRIVER_APPROVAL_WHATSAPP_SENT_TAG);
                userProfileRepo.save(driver);
            }
        }

    }

    private void triggerAmbassadorCommissionIfEligible(UserProfile driver) {
        if (driver.getAmbassadorId() == null) {
            return;
        }
        var alreadyCreated = "true".equalsIgnoreCase(driver.getTag().get(AMBASSADOR_COMMISSION_CREATED_TAG));
        if (alreadyCreated) {
            LOG.info("[ambassador-commission] already created for driver {}, skipping", driver.getId());
            return;
        }
        try {
            var ambassadorOpt = userProfileRepo.findById(driver.getAmbassadorId());
            if (ambassadorOpt.isEmpty()) {
                LOG.warn("[ambassador-commission] ambassador {} not found for driver {}, skipping", driver.getAmbassadorId(), driver.getId());
                return;
            }
            reconService.generatePayoutForAmbassadorAndApproval(driver, ambassadorOpt.get());
            driver.getTag().put(AMBASSADOR_COMMISSION_CREATED_TAG, "true");
            userProfileRepo.save(driver);
            LOG.info("[ambassador-commission] commission created for ambassador {} on driver approval {}", driver.getAmbassadorId(), driver.getId());
        } catch (Exception e) {
            LOG.warn("[ambassador-commission] failed to create commission for driver {}: {}", driver.getId(), e.getMessage());
        }
    }

    @Async
    @EventListener
    public void handleProfileDeleted(ProfileDeletedEvent event) {
        if (event.getProfile() instanceof UserProfile) {
            UserProfile p = (UserProfile) event.getProfile();
            LOG.info("[user-profile-event] deleted: id={} name={} mobile={}", p.getId(), p.getName(), p.getMobileNumber());
            // add user-specific delete handling here
        }
    }
}
