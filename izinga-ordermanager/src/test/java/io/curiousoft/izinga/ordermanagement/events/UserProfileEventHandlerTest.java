package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class UserProfileEventHandlerTest {

    private final WhatsappNotificationService whatsappNotificationService = mock(WhatsappNotificationService.class);
    private final UserProfileRepo userProfileRepo = mock(UserProfileRepo.class);
    private final UserProfileEventHandler handler = new UserProfileEventHandler(whatsappNotificationService, userProfileRepo);

    @Test
    public void handleProfileUpdated_sendsDriverTemplateWhenApprovedForFirstTime() {
        UserProfile profile = messengerProfile(true);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, profile));

        verify(whatsappNotificationService).sendDriverApprovedMessage(profile.getMobileNumber(), profile.getName());
        verify(userProfileRepo).save(profile);
        assertEquals("true", profile.getTag().get("driverApprovalWhatsappSent"));
    }

    @Test
    public void handleProfileUpdated_doesNotResendDriverTemplateWhenAlreadySent() {
        UserProfile profile = messengerProfile(true);
        profile.getTag().put("driverApprovalWhatsappSent", "true");

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, profile));

        verify(whatsappNotificationService, never()).sendDriverApprovedMessage(anyString(), anyString());
        verify(userProfileRepo, never()).save(profile);
    }

    @Test
    public void handleProfileUpdated_clearsApprovalNotificationTagWhenProfileBecomesUnapproved() {
        UserProfile profile = messengerProfile(false);
        profile.getTag().put("driverApprovalWhatsappSent", "true");

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, profile));

        verify(whatsappNotificationService, never()).sendDriverApprovedMessage(anyString(), anyString());
        verify(userProfileRepo).save(profile);
        assertFalse(profile.getTag().containsKey("driverApprovalWhatsappSent"));
    }

    private UserProfile messengerProfile(boolean approved) {
        UserProfile profile = new UserProfile(
                "Driver Name",
                UserProfile.SignUpReason.DELIVERY_DRIVER,
                "Address",
                "https://image.url",
                "0821234567",
                ProfileRoles.MESSENGER
        );
        profile.setProfileApproved(approved);
        return profile;
    }
}
