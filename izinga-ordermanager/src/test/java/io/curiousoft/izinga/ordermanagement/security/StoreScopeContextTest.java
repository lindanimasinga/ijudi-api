package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * SEC-WA02-01-C/E: proves that no residual ThreadLocal state persists across
 * two requests handled on the same reused thread.
 *
 * Both permittedStoreId and audience are verified to be null after clear().
 */
@RunWith(MockitoJUnitRunner.class)
public class StoreScopeContextTest {

    @Test
    public void clear_removesPermittedStoreId() {
        StoreScopeContext.setPermittedStoreId("store-abc");
        StoreScopeContext.clear();
        assertNull("permittedStoreId must be null after clear()",
                StoreScopeContext.getPermittedStoreId());
    }

    @Test
    public void clear_removesAudience() {
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.clear();
        assertNull("audience must be null after clear()",
                StoreScopeContext.getAudience());
    }

    @Test
    public void clear_removesBothFields_simultaneously() {
        StoreScopeContext.setPermittedStoreId("store-xyz");
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.clear();
        assertNull("permittedStoreId must be null after clear()", StoreScopeContext.getPermittedStoreId());
        assertNull("audience must be null after clear()", StoreScopeContext.getAudience());
    }

    /**
     * SEC-WA02-01-E: proves no residual state leaks across two sequential simulated requests
     * on the same reused thread — mirrors the Tomcat thread-pool reuse pattern.
     */
    @Test
    public void noResidualState_acrossTwoRequestsOnSameThread() throws Exception {
        // Simulate two sequential requests on the same thread
        var executor = Executors.newSingleThreadExecutor();
        AtomicReference<String>   storeIdAfterFirstRequest    = new AtomicReference<>();
        AtomicReference<Audience> audienceAfterFirstRequest = new AtomicReference<>();

        CountDownLatch firstRequestDone = new CountDownLatch(1);

        executor.execute(() -> {
            // --- Request 1: set context, do work, call finally-clear ---
            StoreScopeContext.setPermittedStoreId("store-r1");
            StoreScopeContext.setAudience(Audience.STORE);
            try {
                // simulate request processing
                assertEquals("store-r1", StoreScopeContext.getPermittedStoreId());
                assertEquals(Audience.STORE, StoreScopeContext.getAudience());
            } finally {
                // SEC-WA02-01-C: unconditional finally block
                StoreScopeContext.clear();
            }
            // Read AFTER clear to prove residual state is gone
            storeIdAfterFirstRequest.set(StoreScopeContext.getPermittedStoreId());
            audienceAfterFirstRequest.set(StoreScopeContext.getAudience());
            firstRequestDone.countDown();
        });

        firstRequestDone.await();
        executor.shutdown();

        // Both fields must be null after the first request completed
        assertNull("permittedStoreId must be null after first request clears it",
                storeIdAfterFirstRequest.get());
        assertNull("audience must be null after first request clears it",
                audienceAfterFirstRequest.get());
    }

    @Test
    public void secondRequest_seesOwnContextOnly_notFirstRequest() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> storeIdInSecondRequest = new AtomicReference<>();

        CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            // Request 1
            StoreScopeContext.setPermittedStoreId("store-req1");
            StoreScopeContext.setAudience(Audience.STORE);
            try {
                // work...
            } finally {
                StoreScopeContext.clear();
            }

            // Request 2 on the same thread — no context set
            try {
                // This request does NOT set a storeId
                storeIdInSecondRequest.set(StoreScopeContext.getPermittedStoreId());
            } finally {
                StoreScopeContext.clear();
            }
            done.countDown();
        });

        done.await();
        executor.shutdown();

        assertNull("Second request on same thread must NOT see first request's storeId",
                storeIdInSecondRequest.get());
    }

    @Test
    public void clear_whenNothingSet_doesNotThrow() {
        // Should never throw even when nothing was set
        StoreScopeContext.clear();
        assertNull(StoreScopeContext.getPermittedStoreId());
        assertNull(StoreScopeContext.getAudience());
    }
}
