package io.curiousoft.izinga.ordermanagement.cancellation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ADR-019: Insert-only repository for {@link CancellationAuditLog}.
 * <p>
 * Callers must ONLY use {@link #save} (insert).  There are no update or delete
 * methods here by design — enforced at the service layer via {@link CancellationAuditService}.
 */
@Repository
public interface CancellationAuditRepository extends MongoRepository<CancellationAuditLog, String> {
    List<CancellationAuditLog> findByOrderId(String orderId);
}
