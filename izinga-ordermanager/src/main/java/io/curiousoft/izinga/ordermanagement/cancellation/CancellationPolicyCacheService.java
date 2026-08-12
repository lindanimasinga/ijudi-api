package io.curiousoft.izinga.ordermanagement.cancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ADR-019: Lightweight in-memory cache for {@link CancellationPolicy}.
 * <p>
 * Refreshes from MongoDB at most once per {@code cancellation.fee.policy-cache-ttl-minutes}
 * (default 5) so parameter changes don't require a redeploy.
 * <p>
 * Fails with a clear {@link IllegalStateException} — never NPE — when no policy document
 * exists in the database, so the absence of a seed is visible immediately at call time.
 */
@Service
public class CancellationPolicyCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CancellationPolicyCacheService.class);

    private final CancellationPolicyRepository repository;
    private final long cacheTtlMillis;

    private final AtomicReference<CancellationPolicy> cached = new AtomicReference<>();
    private final AtomicLong lastLoadedMs = new AtomicLong(0L);

    public CancellationPolicyCacheService(
            CancellationPolicyRepository repository,
            @Value("${cancellation.fee.policy-cache-ttl-minutes:5}") long cacheTtlMinutes) {
        this.repository = repository;
        this.cacheTtlMillis = cacheTtlMinutes * 60_000L;
    }

    /**
     * Returns the active cancellation policy.
     * <p>
     * The cached copy is used if it is younger than the configured TTL.
     * On a cache miss the repository is queried and the first document is used.
     *
     * @throws IllegalStateException when the {@code cancellation_policy} collection is empty.
     */
    public CancellationPolicy getPolicy() {
        long now = Instant.now().toEpochMilli();
        if (cached.get() == null || (now - lastLoadedMs.get()) > cacheTtlMillis) {
            LOG.debug("Refreshing cancellation policy from database");
            CancellationPolicy fresh = repository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No cancellation_policy document found in the database. " +
                            "Please seed the collection before processing cancellations."));
            cached.set(fresh);
            lastLoadedMs.set(now);
            LOG.info("Cancellation policy loaded (id={})", fresh.getId());
        }
        return cached.get();
    }

    /** Evicts the cache entry, forcing a reload on the next call. Useful for testing. */
    public void evict() {
        cached.set(null);
        lastLoadedMs.set(0L);
    }
}
