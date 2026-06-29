package io.curiousoft.izinga.usermanagement.users;

import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventHandlers {
    private static final Logger LOG = LoggerFactory.getLogger(ProfileEventHandlers.class);

    @Async
    @EventListener
    public void handleProfileCreated(ProfileCreatedEvent event) {
        Profile p = event.getProfile();
        LOG.info("Profile created: id={} name={} role={}", p.getId(), p.getName(), p.getRole());
    }

    @Async
    @EventListener
    public void handleProfileUpdated(ProfileUpdatedEvent event) {
        Profile p = event.getProfile();
        LOG.info("Profile updated: id={} name={} role={}", p.getId(), p.getName(), p.getRole());
    }

    @Async
    @EventListener
    public void handleProfileDeleted(ProfileDeletedEvent event) {
        Profile p = event.getProfile();
        LOG.info("Profile deleted: id={} name={} role={}", p.getId(), p.getName(), p.getRole());
    }
}

