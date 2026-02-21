package io.curiousoft.izinga.ordermanagement.orders;

import io.curiousoft.izinga.commons.model.GeoDistance;
import io.curiousoft.izinga.commons.model.GeoPoint;
import io.curiousoft.izinga.commons.model.RestrictedRegion;
import io.curiousoft.izinga.commons.repo.RestrictedRegionRepo;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.ShippingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RestrictedRegionService {

    private static final Logger LOG = LoggerFactory.getLogger(RestrictedRegionService.class);

    private final RestrictedRegionRepo restrictedRegionRepo;

    public RestrictedRegionService(RestrictedRegionRepo restrictedRegionRepo) {
        this.restrictedRegionRepo = restrictedRegionRepo;
    }

    // --- CRUD/service helper methods ---
    public List<RestrictedRegion> findAll(Boolean active) {
        return (active == null) ? restrictedRegionRepo.findAll() : restrictedRegionRepo.findByActive(active);
    }

    public Optional<RestrictedRegion> findById(String id) {
        return restrictedRegionRepo.findById(id);
    }

    public boolean existsById(String id) {
        return id != null && restrictedRegionRepo.existsById(id);
    }

    public RestrictedRegion create(RestrictedRegion region) {
        if (region.getId() == null || region.getId().isEmpty()) {
            region.setId(UUID.randomUUID().toString());
        } else if (restrictedRegionRepo.existsById(region.getId())) {
            throw new IllegalArgumentException("Restricted region with id already exists");
        }
        return restrictedRegionRepo.save(region);
    }

    public RestrictedRegion patch(String id, RestrictedRegion patch) {
        RestrictedRegion persisted = restrictedRegionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Restricted region not found"));
        // Copy non-id properties from patch into persisted
        BeanUtils.copyProperties(patch, persisted, "id");
        return restrictedRegionRepo.save(persisted);
    }

    // --- existing validation helpers ---
    /**
     * Validate order against configured restricted regions. Throws IllegalArgumentException when either the from or to
     * geo point falls within an active restricted region.
     */
    public void validationRestrictedRegions(Order order) {
        LOG.info("Validating order shipping points for orderId={}", order != null ? order.getId() : "null");
        if (order == null || order.getShippingData() == null) throw new IllegalArgumentException("Order or delivery data cannot be missing");
        ShippingData shippingData = order.getShippingData();
        if (shippingData.getShippingDataGeoData() == null) throw new IllegalArgumentException("Delivery location data cannot be missing");

        GeoPoint from = shippingData.getShippingDataGeoData().getFromGeoPoint();
        GeoPoint to = shippingData.getShippingDataGeoData().getToGeoPoint();

        List<RestrictedRegion> regions = restrictedRegionRepo.findByActive(true);
        LOG.info("Found {} active restricted regions to check for orderId={}", regions.size(), order.getId());
        for (RestrictedRegion region : regions) {
            if (from != null) {
                double distanceFromInM = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(from, region.getCenter()) * 1000;
                LOG.debug("Order {} origin distance to region {} = {} km (radius={})", order.getId(), region.getName(), distanceFromInM, region.getRadius());
                if (distanceFromInM <= region.getRadius()) {
                    LOG.warn("Order {} origin falls within restricted region {} (distance={} km, radius={} km)", order.getId(), region.getName(), distanceFromInM, region.getRadius());
                    throw new IllegalArgumentException("Order falls within restricted region: " + region.getName());
                }
            }
            if (to != null) {
                double distanceToInM = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(to, region.getCenter()) * 1000;
                LOG.debug("Order {} destination distance to region {} = {} km (radius={})", order.getId(), region.getName(), distanceToInM, region.getRadius());
                if (distanceToInM <= region.getRadius()) {
                    LOG.warn("Order {} destination falls within restricted region {} (distance={} km, radius={} km)", order.getId(), region.getName(), distanceToInM, region.getRadius());
                    throw new IllegalArgumentException("Destination falls within restricted region: " + region.getName());
                }
            }
        }
        LOG.info("Order {} passed restricted region validation", order.getId());
    }

    public boolean isInRestrictedRegion(GeoPoint point) {
        if (point == null) return false;
        List<RestrictedRegion> regions = restrictedRegionRepo.findByActive(true);
        for (RestrictedRegion region : regions) {
            double distance = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(point, region.getCenter());
            LOG.debug("Point distance to region {} = {} km (radius={})", region.getName(), distance, region.getRadius());
            if (distance <= region.getRadius()) {
                LOG.info("Point is inside restricted region {}", region.getName());
                return true;
            }
        }
        return false;
    }
}
