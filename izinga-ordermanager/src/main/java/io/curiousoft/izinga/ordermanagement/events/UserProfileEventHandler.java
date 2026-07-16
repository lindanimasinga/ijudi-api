package io.curiousoft.izinga.ordermanagement.events;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus;
import io.curiousoft.izinga.commons.referral.ReferralCommissionType;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1Commission;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappNotificationService;
import io.curiousoft.izinga.recon.ReconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserProfileEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileEventHandler.class);
    private static final String DRIVER_APPROVAL_WHATSAPP_SENT_TAG = "driverApprovalWhatsappSent";
    private static final String AMBASSADOR_COMMISSION_CREATED_TAG = "ambassadorCommissionCreated";
    private final WhatsappNotificationService whatsappNotificationService;
    private final UserProfileRepo userProfileRepo;
    private final ReconService reconService;
    private final StorePartnerStage1CommissionRepo storeStage1CommissionRepo;

    public UserProfileEventHandler(WhatsappNotificationService whatsappNotificationService,
                                   UserProfileRepo userProfileRepo,
                                   ReconService reconService,
                                   StorePartnerStage1CommissionRepo storeStage1CommissionRepo) {
        this.whatsappNotificationService = whatsappNotificationService;
        this.userProfileRepo = userProfileRepo;
        this.reconService = reconService;
        this.storeStage1CommissionRepo = storeStage1CommissionRepo;
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
        // RP-007: store partner stage 1 commission on store approval
        if (event.getProfile() instanceof StoreProfile) {
            triggerStoreStage1CommissionIfEligible((StoreProfile) event.getProfile());
            return;
        }

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

    /**
     * RP-007: Creates a R100 StorePartnerStage1Commission when a referred FOOD store is approved
     * for the first time. Idempotency is enforced by the unique index on storeId.
     */
    private void triggerStoreStage1CommissionIfEligible(StoreProfile store) {
        if (store.getStoreType() != StoreType.FOOD) return;
        if (!store.getProfileApproved()) return;
        var partnerId = store.getReferredByPartnerId();
        if (!StringUtils.hasText(partnerId)) return;

        try {
            var commission = new StorePartnerStage1Commission(
                    java.util.UUID.randomUUID().toString(),
                    store.getId(),
                    partnerId,
                    new java.math.BigDecimal("100.00"),
                    ReferralCommissionStatus.PENDING,
                    new java.util.Date()
            );
            storeStage1CommissionRepo.insert(commission);
            LOG.info("[rp-007] store stage1 commission created: storeId={} partnerId={}", store.getId(), partnerId);
            // RP-009: wire the commission into a payout immediately
            reconService.generatePayoutForReferralPartner(
                    partnerId,
                    commission.getAmount(),
                    ReferralCommissionType.STORE_PARTNER_STAGE_1,
                    store.getId()
            );
        } catch (DuplicateKeyException e) {
            LOG.info("[rp-007] stage1 commission already exists for storeId={}, skipping (idempotent)", store.getId());
        } catch (Exception e) {
            LOG.error("[rp-007] failed to create stage1 commission for storeId={}: {}", store.getId(), e.getMessage());
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
