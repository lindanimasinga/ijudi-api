package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1Commission;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.recon.ReconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserProfileEventHandlerTest {

    @Mock private WhatsappNotificationService whatsappNotificationService;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private ReconService reconService;
    @Mock private StorePartnerStage1CommissionRepo storeStage1CommissionRepo;

    private UserProfileEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserProfileEventHandler(whatsappNotificationService, userProfileRepo, reconService, storeStage1CommissionRepo);
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
    // RP-007: Store Partner Stage 1 commission on store approval
    // -------------------------------------------------------------------------

    @Test
    public void handleProfileUpdated_createsStage1Commission_whenFoodStoreApprovedWithReferral() {
        StoreProfile store = foodStore("store-001", "partner-rp-1", true);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, store));

        ArgumentCaptor<StorePartnerStage1Commission> captor = ArgumentCaptor.forClass(StorePartnerStage1Commission.class);
        verify(storeStage1CommissionRepo).insert(captor.capture());
        assertEquals("store-001", captor.getValue().getStoreId());
        assertEquals("partner-rp-1", captor.getValue().getReferralPartnerId());
        assertEquals(new java.math.BigDecimal("100.00"), captor.getValue().getAmount());
    }

    @Test
    public void handleProfileUpdated_skipsStage1Commission_whenStoreNotApproved() {
        StoreProfile store = foodStore("store-002", "partner-rp-2", false);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, store));

        verify(storeStage1CommissionRepo, never()).insert(any(StorePartnerStage1Commission.class));
    }

    @Test
    public void handleProfileUpdated_skipsStage1Commission_whenStoreHasNoReferral() {
        StoreProfile store = foodStore("store-003", null, true);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, store));

        verify(storeStage1CommissionRepo, never()).insert(any(StorePartnerStage1Commission.class));
    }

    @Test
    public void handleProfileUpdated_skipsStage1Commission_whenStoreTypeIsNotFood() {
        StoreProfile store = foodStore("store-004", "partner-rp-4", true);
        store.setStoreType(StoreType.CLOTHING);

        handler.handleProfileUpdated(new ProfileUpdatedEvent(this, store));

        verify(storeStage1CommissionRepo, never()).insert(any(StorePartnerStage1Commission.class));
    }

    @Test
    public void handleProfileUpdated_handlesStage1DuplicateKeyGracefully() {
        StoreProfile store = foodStore("store-005", "partner-rp-5", true);
        when(storeStage1CommissionRepo.insert(any(StorePartnerStage1Commission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        // should not throw
        assertDoesNotThrow(() -> handler.handleProfileUpdated(new ProfileUpdatedEvent(this, store)));
        verify(storeStage1CommissionRepo).insert(any(StorePartnerStage1Commission.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StoreProfile foodStore(String storeId, String referredByPartnerId, boolean approved) {
        Bank bank = new Bank();
        bank.setAccountId("acc-1");
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(java.time.DayOfWeek.MONDAY, new java.util.Date(), new java.util.Date()));
        ArrayList<String> tags = new ArrayList<>();
        tags.add("food");
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store",
                "1 Store St", "https://img.test/s.png", "0811111111",
                tags, hours, "owner-001", bank
        );
        store.setId(storeId);
        store.setReferredByPartnerId(referredByPartnerId);
        store.setProfileApproved(approved);
        return store;
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
