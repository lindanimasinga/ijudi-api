package io.curiousoft.izinga.recon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled

/**
 * GAP-5 scheduler-wiring regression: verifies that [ReconServiceImpl.reconcilePendingReferralCommissions]
 * is annotated with the exact expected @Scheduled cron expression.
 *
 * This test uses reflection so it does NOT require a Spring context. It catches:
 *   - Accidental removal of @Scheduled
 *   - A changed or misconfigured cron expression (e.g. wrong time, wrong field count)
 *
 * without needing to boot the whole application or wait for the clock.
 */
class ReconServiceImplSchedulerWiringTest {

    @Test
    fun `reconcilePendingReferralCommissions carries @Scheduled with cron 0 30 2 * * *`() {
        val method = ReconServiceImpl::class.java.getDeclaredMethod("reconcilePendingReferralCommissions")
        val scheduled = method.getAnnotation(Scheduled::class.java)

        assertNotNull(
            scheduled,
            "reconcilePendingReferralCommissions must carry @Scheduled — annotation was removed or the method was renamed"
        )
        assertEquals(
            "0 30 2 * * *",
            scheduled!!.cron,
            "cron expression must be exactly '0 30 2 * * *' (02:30 daily after nightly payout bundle); actual: '${scheduled.cron}'"
        )
    }
}
