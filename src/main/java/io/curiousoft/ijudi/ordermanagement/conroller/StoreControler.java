package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.service.StoreService;
import io.curiousoft.ijudi.ordermanagement.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/store")
public class StoreControler {

    private StoreService storeService;
    private UserProfileService userProfileService;

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
                !StringUtils.isEmpty(ownerId) && isAdmin ?  storeService.findNearbyStores(0, 0, storeType, 100, 1000)
                        : storeService.findNearbyStores(latitude, longitude, storeType, range, size);
        return stores != null ? ResponseEntity.ok(stores) : ResponseEntity.notFound().build();
    }
}
