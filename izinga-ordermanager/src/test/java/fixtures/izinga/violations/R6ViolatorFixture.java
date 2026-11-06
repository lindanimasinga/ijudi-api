package fixtures.izinga.violations;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;

/**
 * ADR-026 D5 Gap-E: Non-vacuity fixture for ArchUnit R6.
 *
 * <p>This class calls {@link StoreScopeContext#setAudience}, which is a scope setter
 * that only {@code StoreScopeValidationFilter} is permitted to call (per R6).
 * Any other class calling a scope setter is an R6 violation.</p>
 *
 * <p>{@code McpArchitectureTest#nonVacuityGuard_r6_detectsScopeSetterCall()} imports this
 * fixture class directly and asserts that R6 flags it, proving R6's gate is live.
 * Without this proof, R6 would appear vacuously passing in Phase 0.</p>
 *
 * <p>This class must NEVER appear in production source or be component-scanned.</p>
 */
@SuppressWarnings("unused") // Used via ClassFileImporter in McpArchitectureTest
public class R6ViolatorFixture {

    /**
     * Calls a StoreScopeContext setter — this is the action R6 forbids for all classes
     * except StoreScopeValidationFilter. ArchUnit R6 must flag this method call.
     */
    public void setCallerAudience(Audience audience) {
        StoreScopeContext.setAudience(audience);
    }
}
