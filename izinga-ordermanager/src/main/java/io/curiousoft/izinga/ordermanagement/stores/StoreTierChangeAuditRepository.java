package io.curiousoft.izinga.ordermanagement.stores;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * ONB-02 T-09 / SEC-ONB02-03-E: Narrow, insert-only repository for {@link StoreTierChangeAudit}.
 *
 * <p>Follows the same ADR-017 pattern as {@link StoreAgreementAuditRepository}: extends the base
 * Spring Data {@link Repository} marker only, exposing no delete or update methods at the
 * interface boundary.
 */
public interface StoreTierChangeAuditRepository extends Repository<StoreTierChangeAudit, String> {

    /** Persists a new {@link StoreTierChangeAudit} record. Never call with a pre-existing id. */
    StoreTierChangeAudit save(StoreTierChangeAudit audit);

    /** Returns all tier-change audit records for the given store. */
    List<StoreTierChangeAudit> findByStoreId(String storeId);
}
