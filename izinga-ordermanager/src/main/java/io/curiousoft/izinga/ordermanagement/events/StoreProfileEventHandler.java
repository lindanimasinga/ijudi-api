package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StoreProfileEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(StoreProfileEventHandler.class);

    @Async
    @EventListener
    public void handleProfileCreated(ProfileCreatedEvent event) {
        if (event.getProfile() instanceof StoreProfile) {
            StoreProfile p = (StoreProfile) event.getProfile();
            LOG.info("[store-profile-event] created: id={} name={} shortName={}", p.getId(), p.getName(), p.getShortName());
            // store-specific initialization (e.g., create default stock, schedule tasks)
        }
    }

    @Async
    @EventListener
    public void handleProfileUpdated(ProfileUpdatedEvent event) {
        if (event.getProfile() instanceof StoreProfile) {
            StoreProfile p = (StoreProfile) event.getProfile();
            LOG.info("[store-profile-event] updated: id={} name={} shortName={}", p.getId(), p.getName(), p.getShortName());
            // store-specific update handling
        }
    }

    @Async
    @EventListener
    public void handleProfileDeleted(ProfileDeletedEvent event) {
        if (event.getProfile() instanceof StoreProfile) {
            StoreProfile p = (StoreProfile) event.getProfile();
            LOG.info("[store-profile-event] deleted: id={} name={} shortName={}", p.getId(), p.getName(), p.getShortName());
            // store-specific cleanup
        }
    }
}

