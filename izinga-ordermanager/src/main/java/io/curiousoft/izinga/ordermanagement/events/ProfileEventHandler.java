package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProfileEventHandler.class);

    @Async
    @EventListener
    public void handleProfileCreated(ProfileCreatedEvent event) {
        LOG.info("[profile-event] created: {}", event.getProfile());
    }

    @Async
    @EventListener
    public void handleProfileUpdated(ProfileUpdatedEvent event) {
        LOG.info("[profile-event] updated: {}", event.getProfile());
    }

    @Async
    @EventListener
    public void handleProfileDeleted(ProfileDeletedEvent event) {
        LOG.info("[profile-event] deleted: {}", event.getProfile());
    }
}

