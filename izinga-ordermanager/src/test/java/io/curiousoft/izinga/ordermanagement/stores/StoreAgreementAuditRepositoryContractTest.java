package io.curiousoft.izinga.ordermanagement.stores;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ONB-02 / SEC-ONB02-02-A: Verifies that {@link StoreAgreementAuditRepository} exposes ONLY
 * {@code save()} and {@code findByStoreId()} — no delete, deleteById, deleteAll, update,
 * or any other mutating method that would break the ADR-017 insert-only contract.
 *
 * <p>This is a compile-time / reflection contract test: it does not require a running MongoDB
 * instance and does not use @SpringBootTest.
 */
public class StoreAgreementAuditRepositoryContractTest {

    @Test
    public void repository_exposes_only_save_and_findByStoreId() {
        // Collect only the methods declared DIRECTLY on the interface (not inherited from Object).
        // Repository<T, ID> itself has zero declared methods, so all methods here belong to our interface.
        Set<String> declaredMethodNames = Arrays.stream(StoreAgreementAuditRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(2, declaredMethodNames.size(), "Interface must declare exactly 2 methods");
        assertTrue(declaredMethodNames.contains("save"), "save must be declared");
        assertTrue(declaredMethodNames.contains("findByStoreId"), "findByStoreId must be declared");
    }

    @Test
    public void repository_does_not_declare_delete_methods() {
        Set<String> allMethodNames = Arrays.stream(StoreAgreementAuditRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(allMethodNames.contains("delete"), "delete must NOT be declared");
        assertFalse(allMethodNames.contains("deleteById"), "deleteById must NOT be declared");
        assertFalse(allMethodNames.contains("deleteAll"), "deleteAll must NOT be declared");
    }

    @Test
    public void repository_does_not_declare_update_methods() {
        Set<String> allMethodNames = Arrays.stream(StoreAgreementAuditRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(allMethodNames.contains("saveAll"),
                "saveAll must NOT be declared (bulk operations not part of the narrow contract)");
        assertFalse(allMethodNames.contains("findAll"),
                "findAll must NOT be declared (unbounded reads not part of the narrow contract)");
    }

    @Test
    public void repository_extends_spring_data_base_repository_marker() {
        // Extends org.springframework.data.repository.Repository (not MongoRepository or CrudRepository)
        // so that Spring Data does not auto-expose the full CRUD surface.
        boolean extendsBaseRepository = Arrays.stream(StoreAgreementAuditRepository.class.getInterfaces())
                .anyMatch(iface -> iface.equals(org.springframework.data.repository.Repository.class));
        assertTrue(extendsBaseRepository,
                "Must extend org.springframework.data.repository.Repository (not MongoRepository)");
    }

    @Test
    public void storeAgreementAudit_hasRequiredFields() throws Exception {
        // Verify all SEC-ONB02-01-B required fields exist as properties
        StoreAgreementAudit audit = new StoreAgreementAudit(
                "store-1", "user-1", "merchant-v2", new java.util.Date(),
                "127.0.0.1", "Mozilla/5.0", "WHATSAPP_OTP");

        assertEquals("store-1", audit.getStoreId());
        assertEquals("user-1", audit.getAcceptedByUserId());
        assertEquals("merchant-v2", audit.getIcaVersion());
        assertNotNull(audit.getAcceptedAt());
        assertEquals("127.0.0.1", audit.getIpAddress());
        assertEquals("Mozilla/5.0", audit.getUserAgent());
        assertEquals("WHATSAPP_OTP", audit.getVerificationChannel());
        assertNull(audit.getId(), "id must be null until MongoDB assigns it");
    }
}
