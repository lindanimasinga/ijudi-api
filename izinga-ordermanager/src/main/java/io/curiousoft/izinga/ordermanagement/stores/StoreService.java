package io.curiousoft.izinga.ordermanagement.stores;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.service.ProfileServiceImpl;
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionActivatedEvent;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private static final Logger LOG = LoggerFactory.getLogger(StoreService.class);

    /** Hardcoded verification channel for SEC-ONB02-01-B — update when additional channels are added. */
    private static final String ICA_VERIFICATION_CHANNEL = "WHATSAPP_OTP";

    private final UserProfileRepo userProfileRepo;
    private final String mainPayAccount;
    private final double markupPercentage;
    private final ReferralCodeService referralCodeService;
    private final StoreAgreementAuditRepository storeAgreementAuditRepository;
    private final StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    private final FirebaseAuth firebaseAuth;

    public StoreService(StoreRepository storeRepository,
                        UserProfileRepo userProfileRepo,
                        @Value("${ukheshe.main.account}") String mainPayAccount,
                        @Value("${service.markup.perc}") double markupPercentage,
                        ApplicationEventPublisher applicationEventPublisher,
                        ReferralCodeService referralCodeService,
                        StoreAgreementAuditRepository storeAgreementAuditRepository,
                        StoreTierChangeAuditRepository storeTierChangeAuditRepository,
                        FirebaseAuth firebaseAuth) {
        super(storeRepository, applicationEventPublisher);
        this.userProfileRepo = userProfileRepo;
        this.mainPayAccount = mainPayAccount;
        this.markupPercentage = markupPercentage;
        this.referralCodeService = referralCodeService;
        this.storeAgreementAuditRepository = storeAgreementAuditRepository;
        this.storeTierChangeAuditRepository = storeTierChangeAuditRepository;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * RP-005a: Store creation with optional referral attribution.
     * If [referralCode] is non-null and resolves to a REFERRAL_PARTNER, sets
     * [StoreProfile.referredByPartnerId] before persisting.
     *
     * Called from StoreControler when a `referralCode` query param is present.
     */
    public StoreProfile create(StoreProfile profile, String referralCode) throws Exception {
        if (StringUtils.hasText(referralCode)) {
            var partner = referralCodeService.resolveCode(referralCode);
            if (partner != null) {
                LOG.info("Referral code {} resolved to partnerId={} for new store ownerId={}",
                        referralCode, partner.getId(), profile.getOwnerId());
                profile.setReferredByPartnerId(partner.getId());
            } else {
                LOG.warn("Referral code {} could not be resolved — no attribution set for store ownerId={}",
                        referralCode, profile.getOwnerId());
            }
        }
        return create(profile);
    }

    @Override
    public StoreProfile create(StoreProfile profile) throws Exception {
        UserProfile user = userProfileRepo.findById(profile.getOwnerId())
                .orElseThrow(() -> new Exception("Store owner with user id does not exist."));
        Optional<StoreProfile> exists = profileRepo.findOneByIdOrShortName(profile.getId(), profile.getShortName());
        if (exists.isPresent()) {
            throw new Exception("Shop shortname or id already exists. Please try a different shortname");
        }

        // C-04 (DEFECT-ONB02-01 fix): ICA gate applies to ALL store-creation calls —
        // both first-time creators (role=CUSTOMER, upgraded to STORE_ADMIN after this method
        // returns) and existing STORE_ADMINs creating additional stores. The prior condition
        // only gated STORE_ADMINs; a first-time CUSTOMER caller always bypassed it because
        // the role upgrade happens at line 102, after this gate. Since POST /store is
        // exclusively a merchant-onboarding path, the gate must be unconditional.
        if (!Boolean.TRUE.equals(profile.getIcaAccepted())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MERCHANT_ICA_NOT_ACCEPTED");
        }

        profile.setBank(user.getBank());

        // T-08: Bank validation — validate the effective bank (user's bank) after overwrite.
        validateBankForCreate(profile.getBank());

        StoreProfile newStore = super.create(profile);
        user.setRole(ProfileRoles.STORE_ADMIN);
        newStore.setMarkUp(markupPercentage);
        userProfileRepo.save(user);

        // TIER-BILLING-01: Set the storeId Firebase custom claim so that
        // MerchantSubscriptionController.initiateSubscription() can read it from the JWT (IDOR-safe).
        // user.getId() is the Firebase UID — WhatsAppOtpService.createUserProfile() sets profile.id = uid
        // at account creation, so the Mongo document id and Firebase UID are always identical.
        //
        // Error handling: log and continue if Firebase rejects the claim write. The store record is
        // the source of truth; the JWT claim is a secondary auth decoration that can be healed
        // separately (e.g. via a future admin "sync-claims" endpoint). Throwing here would mean the
        // merchant sees a failed store-creation response while the store was actually persisted —
        // worse UX than a successful creation with a deferred claim-sync.
        //
        // Multi-store note: the Firebase JWT holds a single storeId string. If a merchant creates
        // a second store this claim is overwritten to the newest store, which means only the latest
        // store can reach the subscription checkout via JWT. This matches the single-store assumption
        // baked into MerchantSubscriptionController's IDOR design (TIER-BILLING-01).
        // TODO: TIER-BILLING-02 — multi-store support will require a different JWT claim shape
        //  (e.g. storeIds array or a dedicated subscription-initiate token).
        try {
            firebaseAuth.setCustomUserClaims(user.getId(), Map.of("storeId", newStore.getId()));
            LOG.info("Firebase storeId claim set for uid={} storeId={}", user.getId(), newStore.getId());
        } catch (FirebaseAuthException e) {
            LOG.error("Failed to set storeId Firebase custom claim for uid={} storeId={}: {} " +
                    "— store created successfully; claim must be healed via support before subscription checkout can proceed",
                    user.getId(), newStore.getId(), e.getMessage(), e);
        }

        return newStore;
    }

    /**
     * T-08: Validates that the effective bank (user's registered bank) has all required fields
     * for store creation. Legacy BankAccType values {@code wallet} and {@code string} are rejected.
     *
     * @throws ResponseStatusException HTTP 400 if any required field is missing or invalid.
     */
    private void validateBankForCreate(Bank bank) {
        if (bank == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank details are required");
        }
        if (!StringUtils.hasText(bank.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account ID is required");
        }
        if (!StringUtils.hasText(bank.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank name is required");
        }
        if (!StringUtils.hasText(bank.getBranchCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank branch code is required");
        }
        if (!StringUtils.hasText(bank.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank phone is required");
        }
        if (bank.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account type is required");
        }
        // Reject legacy values that represent no valid account type
        if (bank.getType() == BankAccType.wallet || bank.getType() == BankAccType.string) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bank account type '" + bank.getType().name() + "' is not valid for store registration");
        }
    }

    /**
     * T-06 / ONB-02: Records the STORE_ADMIN's acceptance of the Store/Merchant Partner Agreement
     * (ICA, L-07). Writes to {@code StoreProfile} first; if the audit write fails, the StoreProfile
     * change is rolled back at application level.
     *
     * <p>Idempotent: if {@code icaAccepted} is already {@code true} for the same {@code icaVersion},
     * returns the current profile without writing a duplicate audit record.
     *
     * <p>SEC-ONB02-01-B: {@code userAgent} and {@code verificationChannel} are required on every
     * audit record.
     *
     * @param storeId          The MongoDB ID of the store.
     * @param acceptedByUserId The authenticated caller's profile ID.
     * @param icaAccepted      Must be {@code true}; rejected if {@code false}.
     * @param icaVersion       Non-blank version string of the ICA document accepted.
     * @param ipAddress        IP address from the HTTP request (nullable).
     * @param userAgent        User-Agent header value (nullable, but stored for audit).
     * @throws ResponseStatusException HTTP 400 if icaAccepted is false or icaVersion is blank.
     * @throws ResponseStatusException HTTP 403 if the caller is not the store owner (IDOR check).
     * @throws ResponseStatusException HTTP 404 if the store does not exist.
     * @throws RuntimeException        If the audit write fails and the StoreProfile rollback succeeds.
     */
    public StoreProfile acceptIca(String storeId,
                                   String acceptedByUserId,
                                   boolean icaAccepted,
                                   String icaVersion,
                                   String ipAddress,
                                   String userAgent,
                                   String jwtStoreId,
                                   boolean isAdmin) throws Exception {
        // Validate inputs
        if (!icaAccepted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "icaAccepted must be true to record ICA acceptance");
        }
        if (!StringUtils.hasText(icaVersion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "icaVersion must not be blank");
        }

        // IDOR check: non-admin callers must present a JWT storeId matching the path id
        if (!isAdmin) {
            if (jwtStoreId == null || !jwtStoreId.equals(storeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You do not have permission to accept ICA for this store");
            }
        }

        StoreProfile store = profileRepo.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Store not found: " + storeId));

        // Idempotency check: already accepted for the same version
        if (Boolean.TRUE.equals(store.getIcaAccepted()) && icaVersion.equals(store.getIcaVersion())) {
            LOG.info("ICA already accepted for storeId={} icaVersion={} — returning existing record", storeId, icaVersion);
            return store;
        }

        // Snapshot current state for rollback on audit failure
        Boolean prevIcaAccepted = store.getIcaAccepted();
        Date prevIcaAcceptedDate = store.getIcaAcceptedDate();
        String prevIcaVersion = store.getIcaVersion();

        // Step 1: Update StoreProfile
        Date acceptedAt = new Date();
        store.setIcaAccepted(true);
        store.setIcaAcceptedDate(acceptedAt);
        store.setIcaVersion(icaVersion);
        profileRepo.save(store);

        // Step 2: Write audit record — roll back StoreProfile if audit fails
        try {
            StoreAgreementAudit audit = new StoreAgreementAudit(
                    storeId, acceptedByUserId, icaVersion, acceptedAt,
                    ipAddress, userAgent, ICA_VERIFICATION_CHANNEL);
            storeAgreementAuditRepository.save(audit);
        } catch (Exception auditEx) {
            LOG.error("Audit write failed for storeId={} — rolling back StoreProfile ICA fields", storeId, auditEx);
            store.setIcaAccepted(prevIcaAccepted);
            store.setIcaAcceptedDate(prevIcaAcceptedDate);
            store.setIcaVersion(prevIcaVersion);
            profileRepo.save(store);
            throw new RuntimeException("ICA acceptance could not be recorded: audit write failed", auditEx);
        }

        return store;
    }

    /**
     * T-09 / ONB-02: Updates the subscription tier for a store. Writes a {@link StoreTierChangeAudit}
     * record for every successful change.
     *
     * <p>SEC-ONB02-03-A: storeId is validated against the JWT {@code storeId} claim, not the path
     * parameter alone.
     *
     * <p>SEC-ONB02-03-D: On downgrade, Firestore {@code whatsapp_provisioning_requests/{storeId}}
     * should be updated from PENDING to CANCELLED_DOWNGRADE. This is deferred pending WA-LINES-02
     * REQ-10 implementation — there is no Firestore client in izinga-ordermanager.
     * TODO: WA-LINES-02 REQ-10 — add Firestore downgrade notification once the provisioning path is established.
     *
     * <p>SEC-ONB02-03-F: The {@code isAdmin} bypass must be replaced by a dedicated
     * {@code BILLING_ADMIN} role at TIER-BILLING-01.
     * TODO: TIER-BILLING-01 — replace ADMIN bypass with BILLING_ADMIN role check.
     *
     * @throws ResponseStatusException HTTP 400 if {@code newTier} is null.
     * @throws ResponseStatusException HTTP 403 if IDOR check fails.
     * @throws ResponseStatusException HTTP 404 if store does not exist.
     * @throws ResponseStatusException HTTP 422 if the store has not accepted the ICA.
     */
    public StoreProfile updateSubscriptionTier(String storeId,
                                                String changedByUserId,
                                                SubscriptionTier newTier,
                                                String jwtStoreId,
                                                boolean isAdmin) throws Exception {
        if (newTier == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subscriptionTier must not be null");
        }

        // IDOR check: non-admin callers must present a JWT storeId matching the path id
        if (!isAdmin) {
            if (jwtStoreId == null || !jwtStoreId.equals(storeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You do not have permission to change the subscription tier for this store");
            }
        }

        StoreProfile store = profileRepo.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Store not found: " + storeId));

        // ICA gate: store must have accepted the ICA before upgrading beyond FREE
        if (!Boolean.TRUE.equals(store.getIcaAccepted())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Store must accept the Merchant Partner Agreement before changing the subscription tier");
        }

        SubscriptionTier fromTier = store.getSubscriptionTier();
        Date changedAt = new Date();

        store.setSubscriptionTier(newTier);
        store.setSubscriptionTierSince(changedAt);
        profileRepo.save(store);

        // Write tier-change audit record
        StoreTierChangeAudit audit = new StoreTierChangeAudit(
                storeId, changedByUserId, fromTier, newTier, changedAt);
        storeTierChangeAuditRepository.save(audit);

        return store;
    }

    /**
     * TIER-BILLING-01 / REQ-05: Listens for [MerchantSubscriptionActivatedEvent] published by
     * PayFastItnHandler when a valid PayFast ITN with payment_status=COMPLETE is received.
     * Calls the existing [updateSubscriptionTier] method to elevate StoreProfile.subscriptionTier
     * to the tier activated by payment.
     *
     * <p>isAdmin=true bypasses the JWT IDOR check (this is a server-side system event, not a
     * user HTTP request). jwtStoreId is null for the same reason.
     *
     * <p>TODO TIER-BILLING-01 follow-up (SEC-ONB02-03-F): Replace the isAdmin=true bypass with
     * a dedicated BILLING_ADMIN role check once the role is introduced.
     */
    @EventListener
    public void onMerchantSubscriptionActivated(MerchantSubscriptionActivatedEvent event) {
        try {
            LOG.info("MerchantSubscriptionActivatedEvent received: storeId={} tier={} ownerId={}",
                    event.getStoreId(), event.getTier(), event.getOwnerId());
            updateSubscriptionTier(
                    event.getStoreId(),
                    event.getOwnerId(),
                    event.getTier(),
                    null,    // jwtStoreId — null because this is a system event
                    true     // isAdmin — bypasses IDOR check for server-side event
            );
        } catch (Exception e) {
            LOG.error("Failed to update subscription tier for storeId={} tier={}: {}",
                    event.getStoreId(), event.getTier(), e.getMessage(), e);
        }
    }

    @Override
    public List<StoreProfile> findAll() {
        return super.findAll()
                .stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());
    }

    @Tool(name = "find_store_or_shops_by_id", description = "Find a store profile by its ID. If the store has no business hours set, default hours will be added.")
    @Override
    public StoreProfile find(String id) {
        // SA-021-8: enforce store scope — throws StoreScopeViolationException if this
        // request's scope token is locked to a different store.
        io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator.validate(id);
        StoreProfile store = profileRepo.findById(id).orElse(null);
        if (store != null && store.getBusinessHours() == null) {
            ArrayList<BusinessHours> hours = new ArrayList<>();
            Calendar instance1 = Calendar.getInstance();
            instance1.set(2020, 1, 1, 8, 0);
            Calendar instance2 = Calendar.getInstance();
            instance1.set(2020, 1, 1, 17, 0);
            hours.add(new BusinessHours(DayOfWeek.MONDAY, instance1.getTime(), instance2.getTime()));
            store.setBusinessHours(hours);
            profileRepo.save(store);
        }
        if (store != null) {
            // #60 — Lazy-seed default categories when the store has none.
            // This is idempotent: if categories are already populated the branch is skipped entirely.
            // Images are left as empty strings; the onboarding team uploads real images via the S3 endpoint.
            // TODO #62: Confirm S3 bucket ACL allows public-read on category images before uploading (DevOps/Lindani item).
            if (store.getCategories() == null || store.getCategories().isEmpty()) {
                List<Category> defaults = buildDefaultCategories();
                store.setCategories(defaults);
                profileRepo.save(store);
            }
            store.setMarkUp(markupPercentage);
        }
        return store;
    }

    /** Builds the 5 default delivery categories seeded for every new store on first GET. */
    static List<Category> buildDefaultCategories() {
        return Arrays.asList(
                new Category(UUID.randomUUID().toString(), "Large Furniture", "", true),
                new Category(UUID.randomUUID().toString(), "Small Furniture & Large Parcels", "", true),
                new Category(UUID.randomUUID().toString(), "Small Parcels", "", true),
                new Category(UUID.randomUUID().toString(), "Medicine", "", true),
                new Category(UUID.randomUUID().toString(), "Parcels (General)", "", true)
        );
    }


    /**
     * SEC-01: authenticated update — enforces that the caller either owns the store or holds ROLE_ADMIN.
     * The ownership check is performed against the PERSISTED record's ownerId, not the request body.
     */
    public StoreProfile update(String storeId, StoreProfile incoming, Authentication authentication) throws Exception {
        StoreProfile persisted = profileRepo.findById(storeId)
                .orElseThrow(() -> new Exception("Profile not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !authentication.getName().equals(persisted.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to update this store");
        }
        // NOTE-01: ownership transfer via PATCH /store/{id} is not permitted — strip any ownerId
        // the caller supplied in the body and restore the persisted value before delegating to the
        // base update so that BeanUtils.copyProperties cannot overwrite it.
        incoming.setOwnerId(persisted.getOwnerId());
        return update(storeId, incoming);
    }

    /**
     * SEC-01: authenticated stock update — enforces that the caller either owns the store or holds ROLE_ADMIN.
     * The ownership check is performed against the PERSISTED record's ownerId.
     */
    public void addStockForShop(String profileId, Stock stock, Authentication authentication) throws Exception {
        StoreProfile persisted = profileRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !authentication.getName().equals(persisted.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to update stock for this store");
        }
        addStockForShop(profileId, stock);
    }

    @Tool(name = "find_stores_by_owner", description = "Find all store profiles owned by a specific user ID.")
    public List<StoreProfile> findByOwner(String ownerId) {
        return profileRepo.findByOwnerId(ownerId);
    }

    public List<StoreProfile> findFeatured(double latitude,
                                           double longitude,
                                           StoreType storeType,
                                           double range,
                                           int maxStores) {
        return findNearbyStores(latitude, longitude, storeType, range, maxStores)
                .stream()
                .filter(storeProfile -> storeProfile.getFeaturedExpiry() != null)
                .filter(profile -> profile.getFeaturedExpiry().after(new Date()))
                .filter(StoreProfile::getFeatured)
                .collect(Collectors.toList());
    }

    public Set<Stock> findStockForShop(String profileId) throws Exception {
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        store.setMarkUp(markupPercentage);
        return store.getStockList();
    }

    public void addStockForShop(String profileId, Stock stock) throws Exception {
        validate(stock);
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        Optional<Stock> stockOptional = store.getStockList().stream()
                .filter(item -> stock.getName().equals(item.getName()))
                .findFirst();
        if (stockOptional.isPresent()) {
            Stock oldStock = stockOptional.get();
            String id = oldStock.getId();
            BeanUtils.copyProperties(stock, oldStock);
            oldStock.setId(id);
        } else {
            store.getStockList().add(stock);
        }
        store.setMarkUp(markupPercentage);
        profileRepo.save(store);
    }

    public List<StoreProfile> findNearbyStores(double latitude,
                                               double longitude,
                                               StoreType storeType,
                                               double range,
                                               int maxLocations) {
        double maxLong = longitude + range,
                minLong = longitude - range;
        double maxLat = latitude + range,
                minLat = latitude - range;

        List<StoreProfile> stores = profileRepo.findByLatitudeBetweenAndLongitudeBetweenAndStoreType(
                minLat, maxLat, minLong, maxLong, storeType).stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());

        GeoPoint origin = new GeoPointImpl(latitude, longitude);
        stores.sort((a, b) -> {
            double distanceToA = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, a);
            double distanceToB = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, b);
            var difference = distanceToA - distanceToB;
            return difference < 0 ? -1 : difference > 0 ? 1 : 0;
        });

        maxLocations = maxLocations <= 0 ? 30 : maxLocations;
        return maxLocations > stores.size() ? stores : stores.subList(0, maxLocations);
    }

    public List<StoreProfile> findStoresAdmin(double latitude,
                                               double longitude,
                                               double range,
                                               int maxLocations) {
        double maxLong = longitude + range,
                minLong = longitude - range;
        double maxLat = latitude + range,
                minLat = latitude - range;

        List<StoreProfile> stores = profileRepo.findByLatitudeBetweenAndLongitudeBetween(
                        minLat, maxLat, minLong, maxLong).stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());

        GeoPoint origin = new GeoPointImpl(latitude, longitude);
        stores.sort((a, b) -> {
            double distanceToA = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, a);
            double distanceToB = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, b);
            var difference = distanceToA - distanceToB;
            return difference < 0 ? -1 : difference > 0 ? 1 : 0;
        });

        maxLocations = maxLocations <= 0 ? 30 : maxLocations;
        return maxLocations > stores.size() ? stores : stores.subList(0, maxLocations);
    }

    public StoreProfile findOneByIdOrShortName(String id, String shortname) throws Exception {
        var store = profileRepo.findOneByIdOrShortName(id, shortname).orElseThrow(() -> new Exception("Shop Profile not found"));
        store.setMarkUp(markupPercentage);
        return store;
    }
}
