package io.curiousoft.ijudi.ordermanagent.conroller;

import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

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

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<StoreProfile>> findAllStores() {
        List<StoreProfile> user = storeService.findAll();
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }
}
