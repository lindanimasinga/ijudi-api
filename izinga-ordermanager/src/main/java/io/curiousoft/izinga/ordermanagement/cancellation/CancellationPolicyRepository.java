package io.curiousoft.izinga.ordermanagement.cancellation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * ADR-019: Spring Data MongoDB repository for the {@code cancellation_policy} collection.
 * Only a single policy document is expected; access is always via
 * {@link CancellationPolicyCacheService} to avoid repeated DB reads.
 */
@Repository
public interface CancellationPolicyRepository extends MongoRepository<CancellationPolicy, String> {
}
