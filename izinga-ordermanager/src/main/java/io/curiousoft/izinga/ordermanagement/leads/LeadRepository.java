package io.curiousoft.izinga.ordermanagement.leads;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends MongoRepository<Lead, String> {
    Optional<Lead> findByPhone(String phone);
    List<Lead> findByStoreTypeOrderByCreatedDateDesc(io.curiousoft.izinga.commons.model.StoreType storeType);
}
