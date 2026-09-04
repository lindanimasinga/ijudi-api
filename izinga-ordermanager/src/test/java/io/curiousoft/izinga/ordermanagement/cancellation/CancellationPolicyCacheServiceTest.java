package io.curiousoft.izinga.ordermanagement.cancellation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationPolicyCacheServiceTest {

    @Mock
    private CancellationPolicyRepository repository;

    private CancellationPolicyCacheService sut;

    @BeforeEach
    void setUp() {
        // Use 5-minute TTL (300 000 ms) — long enough that both calls within a test hit the cache
        sut = new CancellationPolicyCacheService(repository, 5L);
    }

    // -------------------------------------------------------------------------
    // QA gap 6: evict() clears cached policy, forces reload on next getPolicy()
    // -------------------------------------------------------------------------

    @Test
    void evict_clearsCachedPolicy_forcesReloadOnNextCall() {
        CancellationPolicy policy = new CancellationPolicy();
        policy.setId("pol-1");
        when(repository.findAll()).thenReturn(List.of(policy));

        // First call: loads from repo and populates cache
        CancellationPolicy first = sut.getPolicy();
        assertSame(policy, first);

        // Evict the cache
        sut.evict();

        // Second call after evict: cache is cold → must hit repo again
        CancellationPolicy second = sut.getPolicy();
        assertSame(policy, second);

        // Repository must have been called exactly twice — before and after evict
        verify(repository, times(2)).findAll();
    }

    // -------------------------------------------------------------------------
    // getPolicy() within TTL uses cached copy — repo is called only once
    // -------------------------------------------------------------------------

    @Test
    void getPolicy_withinTtl_returnsCachedCopyWithoutHittingRepo() {
        CancellationPolicy policy = new CancellationPolicy();
        policy.setId("pol-2");
        when(repository.findAll()).thenReturn(List.of(policy));

        sut.getPolicy(); // cold cache — hits repo
        sut.getPolicy(); // warm cache — must NOT hit repo

        verify(repository, times(1)).findAll();
    }

    // -------------------------------------------------------------------------
    // getPolicy() with no document in collection → ISE, never NPE
    // -------------------------------------------------------------------------

    @Test
    void getPolicy_noDocumentInCollection_throwsIllegalStateException() {
        when(repository.findAll()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> sut.getPolicy());
    }
}
