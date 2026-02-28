package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import io.curiousoft.izinga.commons.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/store")
public class StoreControler {

    private final StoreService storeService;
    private final UserProfileService userProfileService;

    public StoreControler(StoreService storeService, UserProfileService userProfileService) {
        this.storeService = storeService;
        this.userProfileService = userProfileService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> create(@Valid @RequestBody StoreProfile profile) throws Exception {
        return ResponseEntity.ok(storeService.create(profile));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> update(@PathVariable String id, @Valid @RequestBody StoreProfile profile) throws Exception {
        return !id.equals(profile.getId())? ResponseEntity.badRequest().build() : ResponseEntity.ok(storeService.update(id, profile));
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

    @PatchMapping(value = "/{id}/stock", produces = "application/json")
    public ResponseEntity findStockForStore(@Valid @RequestBody Stock stock, @PathVariable String id) throws Exception {
        storeService.addStockForShop(id, stock);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deleteStore(@PathVariable String id) {
        storeService.delete(id);
        return ResponseEntity.ok().build();
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
        if(ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                isAdmin ?  storeService.findStoresAdmin(0, 0, 100, 1000)
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
        if(ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                        isAdmin ?  storeService.findStoresAdmin(0, 0, 100, 1000)
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
        if(ownerId != null) profile = userProfileService.find(ownerId);
        boolean isAdmin = profile != null && profile.getRole() == ProfileRoles.ADMIN;
        List<StoreProfile> stores = featured ?
                storeService.findFeatured(latitude, longitude, storeType, range, size) :
                !StringUtils.isEmpty(ownerId) && !isAdmin ? storeService.findByOwner(ownerId) :
                        isAdmin ?  storeService.findStoresAdmin(0, 0, 100, 1000)
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
}
