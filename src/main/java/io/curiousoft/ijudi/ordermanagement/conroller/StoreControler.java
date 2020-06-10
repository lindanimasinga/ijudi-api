package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Stock;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.service.StoreService;
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

    public StoreControler(StoreService storeService) {
        this.storeService = storeService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> create(@Valid @RequestBody StoreProfile profile) throws Exception {
        return ResponseEntity.ok(storeService.create(profile));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<StoreProfile> update(@PathVariable String id, @Valid @RequestBody StoreProfile profile) throws Exception {
        return ResponseEntity.ok(storeService.update(id, profile));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<StoreProfile> findStore(@PathVariable String id) {
        StoreProfile user = storeService.find(id);
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
                                                            @RequestParam(required = false) String ownerId) {
        List<StoreProfile> stores = featured ?
                storeService.findFeatured() : !StringUtils.isEmpty(ownerId) ?
                storeService.findByOwner(ownerId) : storeService.findAll();
        return stores != null ? ResponseEntity.ok(stores) : ResponseEntity.notFound().build();
    }
}
