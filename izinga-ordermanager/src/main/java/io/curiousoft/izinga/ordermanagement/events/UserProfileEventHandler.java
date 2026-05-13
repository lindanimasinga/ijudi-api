package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class UserProfileEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileEventHandler.class);
    private static final String DRIVER_APPROVAL_WHATSAPP_SENT_TAG = "driverApprovalWhatsappSent";
    private final WhatsappNotificationService whatsappNotificationService;
    private final UserProfileRepo userProfileService;

    public UserProfileEventHandler(WhatsappNotificationService whatsappNotificationService, UserProfileRepo userProfileService) {
        this.whatsappNotificationService = whatsappNotificationService;
        this.userProfileService = userProfileService;
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

        UserProfile p = (UserProfile) event.getProfile();
        LOG.info("[user-profile-event] updated: id={} name={} mobile={}", p.getId(), p.getName(), p.getMobileNumber());
        if (p.getRole() == ProfileRoles.MESSENGER) {
            var alreadySent = "true".equalsIgnoreCase(p.getTag().get(DRIVER_APPROVAL_WHATSAPP_SENT_TAG));
            if (p.getProfileApproved() && !alreadySent) {
                whatsappNotificationService.sendDriverApprovedMessage(p.getMobileNumber(), p.getName());
                p.getTag().put(DRIVER_APPROVAL_WHATSAPP_SENT_TAG, "true");
                userProfileService.save(p);
            } else if (!p.getProfileApproved() && alreadySent) {
                p.getTag().remove(DRIVER_APPROVAL_WHATSAPP_SENT_TAG);
                userProfileService.save(p);
            }
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
