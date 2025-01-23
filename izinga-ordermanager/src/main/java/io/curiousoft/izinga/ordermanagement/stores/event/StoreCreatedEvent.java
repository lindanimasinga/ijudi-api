package io.curiousoft.izinga.ordermanagement.stores.event;

import io.curiousoft.izinga.commons.model.StoreProfile;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class StoreCreatedEvent extends ApplicationEvent {

    private final StoreProfile storeProfile;

    public StoreCreatedEvent(Object source, StoreProfile storeProfile) {
        super(source);
        this.storeProfile = storeProfile;
    }

}