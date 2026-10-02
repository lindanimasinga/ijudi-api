package io.curiousoft.izinga.ordermanagement.stores;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * ONB-02 / SEC-ONB02-02-A: Narrow, insert-only repository for {@link StoreAgreementAudit}.
 *
 * <p>Extends the base Spring Data {@link Repository} marker interface — NOT {@code MongoRepository}
 * or {@code CrudRepository} — so that only the two explicitly declared methods are accessible
 * at the interface boundary. This enforces ADR-017's insert-only constraint at compile time:
 * no {@code delete}, {@code deleteAll}, {@code deleteById}, or implicit update path exists on
 * this interface.
 *
 * <p>Spring Data MongoDB generates the implementation automatically:
 * <ul>
 *   <li>{@code save} is delegated to {@code SimpleMongoRepository.save()} (insert or upsert).</li>
 *   <li>{@code findByStoreId} is a derived query on the {@code storeId} field.</li>
 * </ul>
 *
 * <p>Layer-2 enforcement (at the database level via MongoDB roles) is a DevOps action tracked
 * separately from this interface.
 */
public interface StoreAgreementAuditRepository extends Repository<StoreAgreementAudit, String> {

    /**
     * Persists a new {@link StoreAgreementAudit} record. Must only be called for new documents;
     * callers must never pass a record with an existing {@code id} — doing so would silently
     * overwrite the immutable audit record (MongoDB upsert semantics).
     */
    StoreAgreementAudit save(StoreAgreementAudit audit);

    /**
     * Returns all audit records for the given store, ordered as stored (insertion order in MongoDB).
     */
    List<StoreAgreementAudit> findByStoreId(String storeId);
}
