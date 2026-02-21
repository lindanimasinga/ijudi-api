package io.curiousoft.izinga.ordermanagement.orders;

import io.curiousoft.izinga.commons.model.RestrictedRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/restricted-regions")
public class RestrictedRegionController {

    private static final Logger LOG = LoggerFactory.getLogger(RestrictedRegionController.class);

    private final RestrictedRegionService restrictedRegionService;

    public RestrictedRegionController(RestrictedRegionService restrictedRegionService) {
        this.restrictedRegionService = restrictedRegionService;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<RestrictedRegion>> getAll(@RequestParam(required = false) Boolean active) {
        LOG.info("Get all restricted regions request, active filter={}", active);
        List<RestrictedRegion> regions = restrictedRegionService.findAll(active);
        return ResponseEntity.ok(regions);
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<RestrictedRegion> getById(@PathVariable String id) {
        LOG.info("Get restricted region by id={}", id);
        Optional<RestrictedRegion> region = restrictedRegionService.findById(id);
        return region.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<RestrictedRegion> create(@RequestBody @Valid RestrictedRegion region) {
        LOG.info("Create restricted region request name={}", region.getName());
        try {
            RestrictedRegion saved = restrictedRegionService.create(region);
            LOG.info("Restricted region created id={}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<RestrictedRegion> patch(@PathVariable String id, @RequestBody RestrictedRegion patch) {
        LOG.info("Patch restricted region id={}", id);
        try {
            RestrictedRegion saved = restrictedRegionService.patch(id, patch);
            LOG.info("Restricted region updated id={}", saved.getId());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
