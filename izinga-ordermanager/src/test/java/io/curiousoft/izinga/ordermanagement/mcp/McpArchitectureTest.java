package io.curiousoft.izinga.ordermanagement.mcp;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import fixtures.izinga.mcp.EmptyMcpAudienceFixture;
import fixtures.izinga.violations.R4ViolatorFixture;
import fixtures.izinga.violations.R6ViolatorFixture;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-026 D5: ArchUnit architectural rules R1–R6 for MCP controller-layer separation.
 *
 * <p>Phase 6 lock-down complete — all 18 tools now live on {@code @McpToolController} beans.
 * LEGACY_EXCLUSIONS removed. R1, R4 hard-fail with no exemptions. {@code allowEmptyShould}
 * removed everywhere. Rule R6 passes unconditionally.</p>
 *
 * <p>Non-vacuity guards ensure:
 * <ul>
 *   <li>(a) Zero R1 violators — no class outside {@code ..mcp..} has {@code @Tool} methods.</li>
 *   <li>(b) The importer finds exactly 18 {@code @Tool} methods across exactly 6 controller classes.</li>
 * </ul>
 * </p>
 */
@AnalyzeClasses(
        packages = {
                "io.curiousoft.izinga.ordermanagement",
                "io.curiousoft.izinga.recon",
                "io.curiousoft.izinga.usermanagement",
                "io.curiousoft.izinga.commons"
        },
        importOptions = ImportOption.DoNotIncludeTests.class
)
class McpArchitectureTest {

    // ---- LEGACY_EXCLUSIONS removed in Phase 6 -------------------------------------------
    //
    // All 18 @Tool methods now reside on @McpToolController beans in ..ordermanagement.mcp..
    // R1, R4, and the non-vacuity guards carry no exemptions as of ADR-026 Phase 6 lock-down.

    private static final DescribedPredicate<JavaClass> HAS_TOOL_METHODS =
            new DescribedPredicate<JavaClass>("has at least one @Tool method") {
                @Override
                public boolean test(JavaClass javaClass) {
                    // anyMatch is correct here: even if a Kotlin-generated bridge method carries a
                    // copied @Tool annotation, the class genuinely does have @Tool methods and must
                    // be counted for R1. No bridge-filtering needed for a class-level predicate.
                    return javaClass.getMethods().stream()
                            .anyMatch(m -> m.isAnnotatedWith(Tool.class));
                }
            };

    // ---- R4 restricted classes -----------------------------------------------------------

    private static final Set<String> R4_RESTRICTED_CLASS_NAMES = new HashSet<>(Arrays.asList(
            "io.curiousoft.izinga.ordermanagement.security.StoreScopeContext",
            "io.curiousoft.izinga.commons.security.CallerIdentityContext",
            "io.curiousoft.izinga.commons.security.CallerIdentityValidator",
            "io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator"
    ));

    private static final DescribedPredicate<JavaClass> IS_R4_RESTRICTED =
            new DescribedPredicate<JavaClass>("is an R4-restricted context or validator class") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return R4_RESTRICTED_CLASS_NAMES.contains(javaClass.getName());
                }
            };

    // ---- R6 setter predicate -------------------------------------------------------------

    private static final DescribedPredicate<JavaMethodCall> IS_SCOPE_SETTER =
            new DescribedPredicate<JavaMethodCall>("is a StoreScopeContext setter or CallerIdentityContext.setMcpRequest") {
                @Override
                public boolean test(JavaMethodCall call) {
                    String owner = call.getTarget().getOwner().getName();
                    String method = call.getName();

                    // StoreScopeContext setters
                    if ("io.curiousoft.izinga.ordermanagement.security.StoreScopeContext".equals(owner)) {
                        return method.equals("setPermittedStoreId")
                                || method.equals("setAudience")
                                || method.equals("setCallerPhone");
                    }
                    // CallerIdentityContext.setMcpRequest is the only setter we guard here;
                    // setCallerPhone and setAudience are called by StoreScopeContext (allowed)
                    if ("io.curiousoft.izinga.commons.security.CallerIdentityContext".equals(owner)) {
                        return method.equals("setMcpRequest");
                    }
                    return false;
                }
            };

    // ---- FIX-04: custom ArchCondition for R2 — non-empty @McpAudience.value() ----------

    /**
     * FIX-04: Custom {@link ArchCondition} that fails any {@code @Tool} method whose
     * {@code @McpAudience.value()} array is empty (a configuration error).
     *
     * <p>An empty {@code @McpAudience({})} is a silent misconfiguration — the method would
     * carry the annotation but no audience would ever be allowed, which is equivalent to
     * a permanent deny-all without any error at startup. ArchUnit R2 uses this condition
     * to catch empty-value arrays at build time.</p>
     *
     * <p>Non-vacuity proof: {@link #r2RejectsEmptyMcpAudienceAnnotation()} imports
     * {@link EmptyMcpAudienceFixture} (a {@code @McpToolController} with a {@code @Tool}
     * method carrying {@code @McpAudience({})}) and asserts that R2 flags it.</p>
     */
    static final ArchCondition<JavaMethod> HAVE_NON_EMPTY_MCP_AUDIENCE =
            new ArchCondition<JavaMethod>("be annotated with @McpAudience with at least one value") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean hasAnnotation = method.isAnnotatedWith(McpAudience.class);
                    if (!hasAnnotation) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " is missing @McpAudience annotation"));
                        return;
                    }
                    McpAudience annotation = method.getAnnotationOfType(McpAudience.class);
                    if (annotation.value().length == 0) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " has @McpAudience with empty value() array — " +
                                "this is a configuration error; specify at least one Audience value"));
                    } else {
                        events.add(SimpleConditionEvent.satisfied(method,
                                method.getFullName() + " has @McpAudience with " +
                                annotation.value().length + " audience value(s)"));
                    }
                }
            };

    // ---- R1: @Tool methods must be in @McpToolController (no exemptions) ----------------

    /**
     * R1: Any class that declares a {@code @Tool} method must be annotated {@code @McpToolController}
     * and reside in the {@code ..ordermanagement.mcp..} package.
     *
     * <p>ADR-026 Phase 6 lock-down: LEGACY_EXCLUSIONS removed. This rule now hard-fails for any
     * {@code @Tool} method outside the controller package — no exemptions remain.</p>
     */
    @ArchTest
    static final ArchRule R1_toolMethodsMustBeInMcpControllerPackage =
            classes()
                    .that(HAS_TOOL_METHODS)
                    .should().beAnnotatedWith(McpToolController.class)
                    .andShould().resideInAPackage("..ordermanagement.mcp..")
                    .as("R1: All @Tool classes must be @McpToolController in ..mcp.. (no exemptions)");

    // ---- R2: @Tool methods on @McpToolController must carry @McpAudience ----------------

    /**
     * R2: Every {@code @Tool} method on an {@code @McpToolController} class must carry
     * a non-empty {@code @McpAudience} annotation.
     *
     * <p>FIX-04: Uses {@link #HAVE_NON_EMPTY_MCP_AUDIENCE} instead of a plain presence check,
     * so an empty {@code @McpAudience({})} is also rejected.
     * Non-vacuity is proven by {@link #r2RejectsEmptyMcpAudienceAnnotation()}.</p>
     */
    @ArchTest
    static final ArchRule R2_toolMethodsMustHaveMcpAudience =
            methods()
                    .that().areAnnotatedWith(Tool.class)
                    .and().areDeclaredInClassesThat().areAnnotatedWith(McpToolController.class)
                    .should(HAVE_NON_EMPTY_MCP_AUDIENCE)
                    .as("R2: @Tool methods on @McpToolController must carry non-empty @McpAudience");

    // ---- R3: Nothing outside ..mcp.. depends on @McpToolController ----------------------

    /**
     * R3: No class outside the {@code ..mcp..} package may access an {@code @McpToolController} class.
     */
    @ArchTest
    static final ArchRule R3_mcpControllersOnlyAccessedFromMcpPackage =
            classes()
                    .that().areAnnotatedWith(McpToolController.class)
                    .should().onlyBeAccessed().byClassesThat()
                    .resideInAPackage("..ordermanagement.mcp..")
                    .as("R3: @McpToolController classes may only be accessed from within ..mcp..");

    // ---- R4: Context/validator calls restricted (no exemptions) -------------------------

    /**
     * R4: Outside the {@code ..mcp..} and {@code ..security..} packages, no class may depend on
     * the R4-restricted context or validator classes: {@code StoreScopeContext},
     * {@code CallerIdentityContext}, {@code CallerIdentityValidator}, {@code StoreScopeValidator}.
     *
     * <p>ADR-026 Phase 6 lock-down: LEGACY_EXCLUSIONS removed. This rule now hard-fails for any
     * such dependency outside the allowed packages — no exemptions remain.</p>
     */
    @ArchTest
    static final ArchRule R4_onlyMcpAndSecurityMayUseContextClasses =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "..ordermanagement.mcp..",
                            "..ordermanagement.security..",
                            // CallerIdentityValidator and CallerIdentityContext are co-located in commons.security;
                            // the validator calls the context — both are security infrastructure, not application code.
                            "..commons.security..")
                    .should().dependOnClassesThat(IS_R4_RESTRICTED)
                    .as("R4: Only ..mcp.., ..security.., and commons security infrastructure may depend on context/validator classes (no exemptions)");

    // ---- R5: @McpToolController classes must not depend on repo types --------------------

    /**
     * R5: {@code @McpToolController} classes must not depend on {@code *Repo} or {@code *Repository}
     * types. All persistence must go through service classes.
     */
    @ArchTest
    static final ArchRule R5_mcpControllersDoNotUseRepos =
            noClasses()
                    .that().areAnnotatedWith(McpToolController.class)
                    .should().dependOnClassesThat(
                            new DescribedPredicate<JavaClass>("is a *Repo or *Repository") {
                                @Override
                                public boolean test(JavaClass javaClass) {
                                    String name = javaClass.getSimpleName();
                                    return name.endsWith("Repo") || name.endsWith("Repository");
                                }
                            })
                    .as("R5: @McpToolController classes must not depend on repository types");

    // ---- R6: Only StoreScopeValidationFilter may call StoreScopeContext setters ----------

    /**
     * R6: Only {@code StoreScopeValidationFilter} may call {@code StoreScopeContext} setters
     * or {@code CallerIdentityContext.setMcpRequest}. All other classes are forbidden from
     * setting the per-request scope state.
     */
    @ArchTest
    static final ArchRule R6_onlyFilterMaySetScopeState =
            noClasses()
                    .that().haveNameNotMatching(
                            "io\\.curiousoft\\.izinga\\.ordermanagement\\.security\\.StoreScopeValidationFilter")
                    .should().callMethodWhere(IS_SCOPE_SETTER)
                    .as("R6: Only StoreScopeValidationFilter may call StoreScopeContext setters or setMcpRequest");

    // ---- Non-vacuity guard (a): zero R1 violators — all @Tool classes are controllers ----

    /**
     * Non-vacuity guard (a): ADR-026 Phase 6 lock-down — LEGACY_EXCLUSIONS removed.
     *
     * <p>Verifies that there are ZERO classes with {@code @Tool} methods that are NOT also
     * annotated {@code @McpToolController}. Any class failing this check has leaked a {@code @Tool}
     * method outside the controller layer and must be migrated.</p>
     *
     * <p>Expected controllers (6): {@code OrderCreationMcpController}, {@code DocumentUploadMcpController},
     * {@code UserMcpController}, {@code OrderMcpController}, {@code StoreMcpController},
     * {@code PayoutMcpController}.</p>
     */
    @Test
    void nonVacuityGuard_a_zeroR1ViolatorsAllToolClassesAreControllers() {
        JavaClasses allProductionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "io.curiousoft.izinga.ordermanagement",
                        "io.curiousoft.izinga.recon",
                        "io.curiousoft.izinga.usermanagement",
                        "io.curiousoft.izinga.commons"
                );

        // Find classes with @Tool methods that are NOT annotated @McpToolController.
        // Phase 6: this set must be empty — every @Tool method lives on a controller.
        Set<String> violatingClassNames = allProductionClasses.stream()
                .filter(c -> c.getMethods().stream()
                        .anyMatch(m -> m.isAnnotatedWith(Tool.class)))
                .filter(c -> !c.isAnnotatedWith(McpToolController.class))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertThat(violatingClassNames)
                .as("Phase 6 lock-down: zero classes may hold @Tool methods outside @McpToolController. " +
                        "If a new class has @Tool methods outside the controller layer, it violates ADR-026 R1.")
                .isEmpty();
    }

    // ---- Non-vacuity guard (b): exactly 18 @Tool methods across 6 controller classes ----

    /**
     * Non-vacuity guard (b): ADR-026 Phase 6 lock-down.
     *
     * <p>Sanity assertion that the importer finds exactly 18 {@code @Tool} methods across exactly
     * 6 {@code @McpToolController} classes (all in {@code ..ordermanagement.mcp..}). No legacy
     * service classes hold {@code @Tool} methods as of Phase 6.</p>
     *
     * <p>Expected controllers: {@code OrderCreationMcpController} (1), {@code DocumentUploadMcpController}
     * (4), {@code UserMcpController} (4), {@code OrderMcpController} (5), {@code StoreMcpController} (3),
     * {@code PayoutMcpController} (1) = 18 tools, 6 classes.</p>
     *
     * <p>If a new tool is added, {@code McpToolContractSnapshotTest}'s golden file must be updated
     * and this count must be bumped.</p>
     */
    @Test
    void nonVacuityGuard_b_exactly18ToolMethodsAcross6Classes() {
        JavaClasses allProductionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "io.curiousoft.izinga.ordermanagement",
                        "io.curiousoft.izinga.recon",
                        "io.curiousoft.izinga.usermanagement",
                        "io.curiousoft.izinga.commons"
                );

        // De-duplicate by @Tool name attribute: Kotlin covariant-return bridge methods copy the
        // @Tool annotation verbatim (same name= value). Collecting to a Set<String> of tool names
        // naturally collapses the bridge + real pair into a single entry without relying on
        // JavaMethod.reflect().isBridge(), which resolves ambiguously when two overloads share the
        // same (name, paramTypes) signature but differ only in return type.
        Set<String> distinctToolNames = allProductionClasses.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(Tool.class))
                .map(m -> (String) m.getAnnotationOfType(Tool.class).name())
                .collect(Collectors.toSet());

        Set<String> classesWithToolMethods = allProductionClasses.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(Tool.class))
                .map(m -> m.getOwner().getName())
                .collect(Collectors.toSet());

        assertThat(distinctToolNames)
                .as("Expected exactly 18 distinct @Tool names across all production classes. " +
                        "Update the golden snapshot and this count if the tool count changes.")
                .hasSize(18);

        assertThat(classesWithToolMethods)
                .as("Expected exactly 6 @McpToolController classes to hold @Tool methods " +
                        "(Phase 6: OrderCreationMcpController, DocumentUploadMcpController, UserMcpController, " +
                        "OrderMcpController, StoreMcpController, PayoutMcpController). " +
                        "Update this guard if a new controller is introduced.")
                .hasSize(6);
    }

    // ---- FIX-04: R2 non-vacuity proof — rejects empty @McpAudience -----------------------

    /**
     * FIX-04 non-vacuity proof: Verifies that R2 (with {@link #HAVE_NON_EMPTY_MCP_AUDIENCE})
     * flags a {@code @McpToolController} class whose {@code @Tool} method carries
     * {@code @McpAudience({})}.
     *
     * <p>The fixture {@link EmptyMcpAudienceFixture} is in test source and is imported
     * directly (bypassing {@code DoNotIncludeTests}) to prove R2's gate is live.
     * Without this test, R2 would be vacuously green because no {@code @McpToolController}
     * exists in Phase 0 production code.</p>
     */
    @Test
    void r2RejectsEmptyMcpAudienceAnnotation() {
        // Import just the fixture class — it is in test source, so we bypass DoNotIncludeTests here
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importClasses(EmptyMcpAudienceFixture.class);

        // Build a non-vacuous version of R2 (no allowEmptyShould) for the fixture import
        ArchRule r2ForFixture = methods()
                .that().areAnnotatedWith(Tool.class)
                .and().areDeclaredInClassesThat().areAnnotatedWith(McpToolController.class)
                .should(HAVE_NON_EMPTY_MCP_AUDIENCE)
                .as("R2: @Tool methods on @McpToolController must carry non-empty @McpAudience");

        assertThatThrownBy(() -> r2ForFixture.check(fixtureClasses))
                .as("R2 must reject @McpAudience({}) — the custom ArchCondition must be live")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("empty value() array");
    }

    // ---- Sanity: tool names are the expected 18 names -----------------------------------

    /**
     * Verifies that the 18 {@code @Tool} method names exactly match the documented set.
     * Any rename breaks this test, which also breaks the contract snapshot.
     */
    @Test
    void sanity_toolNamesAreTheExpected18() {
        JavaClasses allProductionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "io.curiousoft.izinga.ordermanagement",
                        "io.curiousoft.izinga.recon",
                        "io.curiousoft.izinga.usermanagement",
                        "io.curiousoft.izinga.commons"
                );

        // Collecting to Set<String> by tool name naturally de-duplicates Kotlin bridge methods
        // (which carry a copied @Tool annotation with the same name= value as the real method).
        Set<String> toolNames = allProductionClasses.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(Tool.class))
                .map(m -> (String) m.getAnnotationOfType(Tool.class).name())
                .collect(Collectors.toSet());

        assertThat(toolNames).containsExactlyInAnyOrder(
                // OrderMcpController (5) — migrated from OrderServiceImpl in Phase 3
                "find_order_by_id",
                "find_orders_by_user_id",
                "find_order_for_messenger",
                "find_orders_by_phone_number",
                "find_orders_by_messenger_id",
                // OrderCreationMcpController (1) — migrated from McpOrderService in Phase 1
                "start_order",
                // StoreMcpController (3) — migrated from StoreService in Phase 4
                "find_store_or_shops_by_id",
                "find_stores_by_owner",
                "find_stores",
                // UserMcpController (4) — migrated from UserProfileService in Phase 2
                "find_user_by_phone",
                "find_users",
                "create_user",
                "get_missing_fields_by_phone",
                // DocumentUploadMcpController (4) — migrated from DocumentUploadMcpService in Phase 1
                "get_required_documents_for_user",
                "create_document_upload_session",
                "attach_uploaded_document_to_user_tag",
                "recheck_required_documents",
                // PayoutMcpController (1) — migrated from ReconServiceImpl in Phase 5
                "get_payouts_for_user"
        );
    }

    // ---- Non-vacuity guard (c): R4 gate is live — detects R4-restricted dependency ----------

    /**
     * Gap-E non-vacuity proof (c): Verifies that R4 is live and not vacuous.
     *
     * <p>{@link R4ViolatorFixture} is in package {@code fixtures.izinga.violations}, which is
     * outside all R4-allowed packages ({@code ..mcp..}, {@code ..security..}, {@code ..commons.security..}).
     * It depends on {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeContext},
     * which is R4-restricted. R4 must therefore flag it.</p>
     *
     * <p>Without this proof, R4 might appear vacuously passing if the DescribedPredicates or
     * the allowed-package list were accidentally misconfigured.</p>
     */
    @Test
    void nonVacuityGuard_r4_detectsR4RestrictedDependency() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importClasses(R4ViolatorFixture.class);

        ArchRule r4ForFixture = noClasses()
                .that().resideOutsideOfPackages(
                        "..ordermanagement.mcp..",
                        "..ordermanagement.security..",
                        "..commons.security..")
                .should().dependOnClassesThat(IS_R4_RESTRICTED)
                .as("R4: Only ..mcp.., ..security.., and commons security infrastructure may depend on context/validator classes (no exemptions)");

        assertThatThrownBy(() -> r4ForFixture.check(fixtureClasses))
                .as("R4 must flag R4ViolatorFixture — it depends on StoreScopeContext from outside the allowed packages")
                .isInstanceOf(AssertionError.class);
    }

    // ---- Non-vacuity guard (d): R6 gate is live — detects scope-setter call ----------------

    /**
     * Gap-E non-vacuity proof (d): Verifies that R6 is live and not vacuous.
     *
     * <p>{@link R6ViolatorFixture} calls {@code StoreScopeContext.setAudience()} — a scope setter
     * that only {@code StoreScopeValidationFilter} is permitted to call per R6. R6 must flag it.</p>
     *
     * <p>Without this proof, R6 might appear vacuously passing if {@code IS_SCOPE_SETTER} had
     * a bug in its method-name matching or the predicate was otherwise misconfigured.</p>
     */
    @Test
    void nonVacuityGuard_r6_detectsScopeSetterCall() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importClasses(R6ViolatorFixture.class);

        ArchRule r6ForFixture = noClasses()
                .that().haveNameNotMatching(
                        "io\\.curiousoft\\.izinga\\.ordermanagement\\.security\\.StoreScopeValidationFilter")
                .should().callMethodWhere(IS_SCOPE_SETTER)
                .as("R6: Only StoreScopeValidationFilter may call StoreScopeContext setters or setMcpRequest");

        assertThatThrownBy(() -> r6ForFixture.check(fixtureClasses))
                .as("R6 must flag R6ViolatorFixture — it calls StoreScopeContext.setAudience() without being the filter")
                .isInstanceOf(AssertionError.class);
    }
}
