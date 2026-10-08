package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/store")
public class StoreControler {

    private final StoreService storeService;
    private final UserProfileService userProfileService;

    public StoreControler(StoreService storeService, UserProfileService userProfileService) {
        this.storeService = storeService;
        this.userProfileService = userProfileService;
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> create(
            @Valid @RequestBody StoreProfile profile,
            @RequestParam(required = false) String referralCode,
            Authentication authentication) throws Exception {
        // Derive ownerId from the authenticated principal — never trust the request body.
        profile.setOwnerId(authentication.getName());
        return ResponseEntity.ok(storeService.create(profile, referralCode));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> update(
            @PathVariable String id,
            @Valid @RequestBody StoreProfile profile,
            Authentication authentication) throws Exception {
        if (!id.equals(profile.getId())) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(storeService.update(id, profile, authentication));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<StoreProfile> findStore(@PathVariable String id) throws Exception {
        StoreProfile user = storeService.findOneByIdOrShortName(id, id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/{id}/stock", produces = "application/json")
    public ResponseEntity<Set<Stock>> findStockForStore(@PathVariable String id) throws Exception {
        Set<Stock> stock = storeService.findStockForShop(id);
        return stock != null ? ResponseEntity.ok(stock) : ResponseEntity.notFound().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping(value = "/{id}/stock", produces = "application/json")
    public ResponseEntity findStockForStore(
            @Valid @RequestBody Stock stock,
            @PathVariable String id,
            Authentication authentication) throws Exception {
        storeService.addStockForShop(id, stock, authentication);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deleteStore(@PathVariable String id) {
        storeService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * ONB-02 T-06: Records the STORE_ADMIN's acceptance of the Store/Merchant Partner Agreement.
     *
     * <p>SEC-ONB02-03-A: storeId is validated against the JWT {@code storeId} claim to prevent IDOR.
     * A STORE_ADMIN whose JWT does not carry a matching storeId claim receives HTTP 403.
     * ADMIN callers bypass the IDOR check.
     */
    @PreAuthorize("hasRole('STORE_ADMIN') or hasRole('ADMIN')")
    @PatchMapping(value = "/{id}/ica-acceptance", consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> acceptIca(
            @PathVariable String id,
            @RequestBody IcaAcceptanceRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) throws Exception {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        String jwtStoreId = extractJwtStoreId(authentication);
        String acceptedByUserId = authentication.getName();
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        StoreProfile result = storeService.acceptIca(
                id,
                acceptedByUserId,
                request.isIcaAccepted(),
                request.getIcaVersion(),
                ipAddress,
                userAgent,
                jwtStoreId,
                isAdmin);
        return ResponseEntity.ok(result);
    }

    /**
     * ONB-02 T-09: Changes the subscription tier for a store.
     *
     * <p>SEC-ONB02-03-A: storeId is validated against the JWT {@code storeId} claim.
     * SEC-ONB02-03-B: Request body is the dedicated {@link SubscriptionTierRequest} DTO (tier only).
     * SEC-ONB02-03-C: SecurityConfig matcher added for this path separately (see SecurityConfig.java).
     */
    @PreAuthorize("hasRole('STORE_ADMIN') or hasRole('ADMIN')")
    @PatchMapping(value = "/{id}/subscription-tier", consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> updateSubscriptionTier(
            @PathVariable String id,
            @RequestBody SubscriptionTierRequest request,
            Authentication authentication) throws Exception {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        String jwtStoreId = extractJwtStoreId(authentication);
        String changedByUserId = authentication.getName();

        StoreProfile result = storeService.updateSubscriptionTier(
                id,
                changedByUserId,
                request.getSubscriptionTier(),
                jwtStoreId,
                isAdmin);
        return ResponseEntity.ok(result);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<StoreProfile>> findAllStores(@RequestParam(required = false) boolean featured,
                                                            @RequestParam(required = false) String ownerId,
                                                            @RequestParam(required = false) StoreType storeType,
                                                            @RequestParam(required = false, defaultValue = "0") double latitude,
                                                            @RequestParam(required = false, defaultValue = "0") double longitude,
                                                            @RequestParam(required = false, defaultValue = "0") double range,
                                                            @RequestParam(required = false, defaultValue = "0") int size) {
        UserProfile profile = null;
        if (ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                isAdmin ? storeService.findStoresAdmin(0, 0, 100, 1000)
                        : storeService.findNearbyStores(latitude, longitude, storeType, range, size);
        return stores != null ? ResponseEntity.ok(stores) : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/stock-flattened", produces = "application/json")
    public ResponseEntity<List<String>> findAllStoresStock(@RequestParam(required = false) boolean featured,
                                                            @RequestParam(required = false) String ownerId,
                                                            @RequestParam(required = false) StoreType storeType,
                                                            @RequestParam(required = false, defaultValue = "0") double latitude,
                                                            @RequestParam(required = false, defaultValue = "0") double longitude,
                                                            @RequestParam(required = false, defaultValue = "0") double range,
                                                            @RequestParam(required = false, defaultValue = "0") int size) {
        UserProfile profile = null;
        if (ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                        isAdmin ? storeService.findStoresAdmin(0, 0, 100, 1000)
                                : storeService.findNearbyStores(latitude, longitude, storeType, range, size);

        return ResponseEntity.ok(stores.stream()
                .flatMap(store -> store.getStockList().stream())
                .map(stock -> "%s#!#%s#!#%s".formatted(
                        stock.getName(),
                        stock.getId(),
                        stock.getImages() != null && !stock.getImages().isEmpty() ? stock.getImages().get(0) : ""))
                .toList());
    }

    @GetMapping(path = "/names", produces = "application/json")
    public ResponseEntity<List<StoreNamesMap>> findAllStoresNames(@RequestParam(required = false) boolean featured,
                                                                  @RequestParam(required = false) String ownerId,
                                                                  @RequestParam(required = false) StoreType storeType,
                                                                  @RequestParam(required = false, defaultValue = "0") double latitude,
                                                                  @RequestParam(required = false, defaultValue = "0") double longitude,
                                                                  @RequestParam(required = false, defaultValue = "0") double range,
                                                                  @RequestParam(required = false, defaultValue = "0") int size) {
        UserProfile profile = null;
        if (ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                        isAdmin ? storeService.findStoresAdmin(0, 0, 100, 1000)
                                : storeService.findNearbyStores(latitude, longitude, storeType, range, size);
        var storeNames = stores
                .stream()
                .map(store -> new StoreNamesMap(store.getName(),
                        store.getId(),
                        store.getFranchiseName(),
                        store.getLatitude(),
                        store.getLongitude(),
                        store.getImageUrl(),
                        store.getDescription(),
                        store.isStoreOffline()))
                .toList();
        return ResponseEntity.ok(storeNames);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * SEC-ONB02-03-A: Extracts the {@code storeId} custom claim from the Firebase JWT.
     * Returns {@code null} if the authentication is not a JwtAuthenticationToken or the claim
     * is absent — callers treat null as an IDOR failure.
     */
    private String extractJwtStoreId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = (Jwt) jwtAuth.getCredentials();
            return jwt.getClaimAsString("storeId");
        }
        return null;
    }

    /**
     * Extracts the client IP address, preferring X-Forwarded-For for reverse-proxy deployments.
     */
    private String extractIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // X-Forwarded-For can be a comma-separated list; first entry is the original client IP.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
