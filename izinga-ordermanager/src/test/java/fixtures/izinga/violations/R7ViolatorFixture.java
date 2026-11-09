package fixtures.izinga.violations;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.mcp.McpAudience;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * ADR-029: Non-vacuity fixture for ArchUnit R7.
 *
 * <p>This class is in package {@code fixtures.izinga.violations} — intentionally OUTSIDE
 * the production component-scan base ({@code io.curiousoft.izinga.*}) and excluded from the
 * ArchUnit scan by {@code ImportOption.DoNotIncludeTests.class} on the main {@code @AnalyzeClasses}.
 * It is imported directly in
 * {@code McpArchitectureTest#nonVacuityGuard_r7_detectsToolMethodWithoutGateOrPublicAnnotation()}
 * to prove that R7's gate is live.</p>
 *
 * <p>The {@code @Tool} method here deliberately omits both an identity gate call AND
 * the {@link io.curiousoft.izinga.ordermanagement.mcp.McpPublicTool @McpPublicTool} annotation,
 * which is exactly the configuration that R7 must reject. Without this fixture,
 * R7 would be vacuously satisfied if {@code CALL_AN_IDENTITY_GATE} had a bug.</p>
 *
 * <p>This class must NEVER appear in production source or be component-scanned.</p>
 */
@McpToolController
@SuppressWarnings("unused") // Used via ClassFileImporter in McpArchitectureTest
public class R7ViolatorFixture {

    /**
     * Deliberately misconfigured tool: has {@code @Tool} and non-empty {@code @McpAudience}
     * but calls NO identity gate method and lacks {@code @McpPublicTool}.
     * R7 must flag this method as a violation.
     */
    @Tool(name = "fixture_r7_no_gate",
          description = "R7 non-vacuity fixture: @Tool method with no gate and no @McpPublicTool")
    @McpAudience({Audience.CUSTOMER})
    public String fixtureMethod(
            @ToolParam(description = "mobile number") String mobileNumber) {
        // No gate call, no @McpPublicTool → R7 must reject this method.
        return mobileNumber;
    }
}
