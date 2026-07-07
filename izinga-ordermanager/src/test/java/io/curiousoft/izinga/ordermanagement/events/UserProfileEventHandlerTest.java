package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.recon.ReconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserProfileEventHandlerTest {

    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ReconService reconService;

    private UserProfileEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserProfileEventHandler(whatsappNotificationService, userProfileRepo, reconService);
    }

    // -------------------------------------------------------------------------
    // Existing approval-notification tests
    // -------------------------------------------------------------------------

    @Test
    public void handleProfileUpdated_sendsDriverTemplateWhenApprovedForFirstTime() {
        UserProfile profile = messengerProfile(true);
        when(userProfileRepo.save(any())).thenReturn(profile);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, profile));

        verify(whatsappNotificationService).sendDriverApprovedMessage(profile.getMobileNumber(), profile.getName());
        verify(userProfileRepo, atLeastOnce()).save(profile);
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
        when(userProfileRepo.save(any())).thenReturn(profile);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, profile));

        verify(whatsappNotificationService, never()).sendDriverApprovedMessage(anyString(), anyString());
        verify(userProfileRepo).save(profile);
        assertFalse(profile.getTag().containsKey("driverApprovalWhatsappSent"));
    }

    // -------------------------------------------------------------------------
    // Ambassador commission tests
    // -------------------------------------------------------------------------

    @Test
    public void handleProfileUpdated_triggersAmbassadorCommission_whenDriverApprovedWithAmbassadorId() {
        UserProfile driver = messengerProfile(true);
        driver.setId("driver-001");
        driver.setAmbassadorId("amb-001");

        UserProfile ambassador = new UserProfile(
                "Ambassador", UserProfile.SignUpReason.DELIVERY_DRIVER,
                "Amb Address", "img.jpg", "0829999999", ProfileRoles.AMBASSADOR
        );
        ambassador.setId("amb-001");

        when(userProfileRepo.findById("amb-001")).thenReturn(Optional.of(ambassador));
        when(userProfileRepo.save(any())).thenReturn(driver);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, driver));

        verify(reconService).generatePayoutForAmbassadorAndApproval(driver, ambassador);
        assertEquals("true", driver.getTag().get("ambassadorCommissionCreated"));
    }

    @Test
    public void handleProfileUpdated_skipsAmbassadorCommission_whenTagAlreadySet() {
        UserProfile driver = messengerProfile(true);
        driver.setId("driver-002");
        driver.setAmbassadorId("amb-002");
        driver.getTag().put("driverApprovalWhatsappSent", "true");
        driver.getTag().put("ambassadorCommissionCreated", "true");

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, driver));

        verify(reconService, never()).generatePayoutForAmbassadorAndApproval(any(), any());
    }

    @Test
    public void handleProfileUpdated_skipsAmbassadorCommission_whenNoAmbassadorId() {
        UserProfile driver = messengerProfile(true);
        driver.setId("driver-003");
        // ambassadorId is null by default
        when(userProfileRepo.save(any())).thenReturn(driver);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, driver));

        verify(reconService, never()).generatePayoutForAmbassadorAndApproval(any(), any());
        verify(userProfileRepo, never()).findById(anyString());
    }

    @Test
    public void handleProfileUpdated_skipsAmbassadorCommission_whenAmbassadorNotFound() {
        UserProfile driver = messengerProfile(true);
        driver.setId("driver-004");
        driver.setAmbassadorId("amb-404");

        when(userProfileRepo.findById("amb-404")).thenReturn(Optional.empty());
        when(userProfileRepo.save(any())).thenReturn(driver);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, driver));

        verify(reconService, never()).generatePayoutForAmbassadorAndApproval(any(), any());
        assertFalse(driver.getTag().containsKey("ambassadorCommissionCreated"));
    }

    @Test
    public void handleProfileUpdated_doesNotTriggerAmbassadorCommission_whenDriverNotApproved() {
        UserProfile driver = messengerProfile(false);
        driver.setId("driver-005");
        driver.setAmbassadorId("amb-001");
        driver.getTag().put("driverApprovalWhatsappSent", "true");
        when(userProfileRepo.save(any())).thenReturn(driver);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, driver));

        verify(reconService, never()).generatePayoutForAmbassadorAndApproval(any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
