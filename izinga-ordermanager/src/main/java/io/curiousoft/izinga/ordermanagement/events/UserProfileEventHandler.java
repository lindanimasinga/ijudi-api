package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.ordermanagement.service.whatsapp.WhatsappNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class UserProfileEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileEventHandler.class);
    private final WhatsappNotificationService whatsappNotificationService;

    public UserProfileEventHandler(WhatsappNotificationService whatsappNotificationService) {
        this.whatsappNotificationService = whatsappNotificationService;
    }

    @Async
    @EventListener
    public void handleProfileCreated(ProfileCreatedEvent event) {
        if (!(event.getProfile() instanceof UserProfile)) return;

        UserProfile p = (UserProfile) event.getProfile();
        LOG.info("[user-profile-event] created: id={} name={} mobile={}", p.getId(), p.getName(), p.getMobileNumber());
        if (p.getRole() == ProfileRoles.MESSENGER) {
            whatsappNotificationService.sendWelcomeMessageDriver(p.getMobileNumber(), p.getName());
        }
    }

    @Async
    @EventListener
    public void handleProfileUpdated(ProfileUpdatedEvent event) {
        if (!(event.getProfile() instanceof UserProfile)) return;

        UserProfile p = (UserProfile) event.getProfile();
        LOG.info("[user-profile-event] updated: id={} name={} mobile={}", p.getId(), p.getName(), p.getMobileNumber());
        if (p.getRole() == ProfileRoles.MESSENGER) {
            p.setProfileApproved(false);
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
