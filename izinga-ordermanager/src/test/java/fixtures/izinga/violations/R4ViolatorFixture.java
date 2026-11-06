package fixtures.izinga.violations;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;

/**
 * ADR-026 D5 Gap-E: Non-vacuity fixture for ArchUnit R4.
 *
 * <p>This class is in package {@code fixtures.izinga.violations} — intentionally OUTSIDE
 * the R4-allowed packages ({@code ..ordermanagement.mcp..}, {@code ..ordermanagement.security..},
 * {@code ..commons.security..}) and NOT in the LEGACY_EXCLUSIONS set.</p>
 *
 * <p>It depends on {@link StoreScopeContext}, which is an R4-restricted class.
 * This means that if R4 were run against this fixture, it would flag a violation.
 * {@code McpArchitectureTest#nonVacuityGuard_r4_detectsR4RestrictedDependency()} uses this
 * fixture to prove that R4's gate is live and not vacuous.</p>
 *
 * <p>This class must NEVER appear in production source or be component-scanned.</p>
 */
@SuppressWarnings("unused") // Used via ClassFileImporter in McpArchitectureTest
public class R4ViolatorFixture {

    /**
     * Uses StoreScopeContext getter — creates a bytecode dependency on the R4-restricted class.
     * R4 must flag this class because it depends on StoreScopeContext from outside the allowed packages.
     */
    public Audience getCallerAudience() {
        return StoreScopeContext.getAudience();
    }
}
