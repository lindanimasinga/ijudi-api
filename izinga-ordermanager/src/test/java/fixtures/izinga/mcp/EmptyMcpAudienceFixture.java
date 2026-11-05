package fixtures.izinga.mcp;

import io.curiousoft.izinga.ordermanagement.mcp.McpAudience;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * FIX-04: Fixture class proving that ArchUnit R2 catches {@code @McpAudience({})} (empty value array).
 *
 * <p>This class is in test source under a package ({@code fixtures.izinga.mcp}) that is
 * intentionally OUTSIDE the Spring Boot component-scan base ({@code io.curiousoft.izinga.*}).
 * This prevents it from being registered as a bean by the application context, so it cannot
 * accidentally be collected by {@code McpConfig.userProfileToolCallbackProvider()}'s
 * {@code getBeansWithAnnotation(McpToolController.class)} call and registered as a 19th tool.</p>
 *
 * <p>It is imported directly in
 * {@code McpArchitectureTest#r2RejectsEmptyMcpAudienceAnnotation()} to exercise the
 * custom {@code McpArchitectureTest#HAVE_NON_EMPTY_MCP_AUDIENCE} ArchCondition.</p>
 *
 * <p>It is NOT part of the production package scan (guarded by
 * {@code ImportOption.DoNotIncludeTests.class} on the ArchUnit scan) and must NEVER be
 * used in production code.</p>
 *
 * <p>The intent is to prove non-vacuity: before FIX-04, an empty value array would have
 * been silently accepted by the original R2 (which only checked presence of @McpAudience,
 * not its content). The custom ArchCondition in FIX-04 must flag this fixture.</p>
 */
@McpToolController
@SuppressWarnings("unused") // Used via ClassFileImporter in McpArchitectureTest
public class EmptyMcpAudienceFixture {

    /**
     * Deliberately misconfigured tool with an EMPTY {@code @McpAudience} value array.
     * This is the configuration error that R2's custom ArchCondition must reject.
     */
    @Tool(name = "fixture_empty_audience", description = "FIX-04 fixture: @McpAudience with empty value")
    @McpAudience({}) // <-- deliberately empty — this MUST be caught by ArchUnit R2
    public String fixtureMethod(@ToolParam(description = "input") String input) {
        return input;
    }
}
