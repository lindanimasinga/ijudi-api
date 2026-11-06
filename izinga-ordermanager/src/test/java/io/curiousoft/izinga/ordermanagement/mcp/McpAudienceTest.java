package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-026 D1 / D2: Unit tests for {@link McpAudience} and {@link McpToolController} annotations.
 *
 * Tests verify:
 * - McpAudience has RUNTIME retention (required for ArchUnit and future runtime enforcement)
 * - McpAudience can carry Audience[] values readable via reflection
 * - McpAudience.value() with multiple audiences is readable
 * - McpToolController has RUNTIME retention
 * - McpToolController is meta-annotated with @Component
 */
class McpAudienceTest {

    // ---- McpAudience annotation behavior ----

    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN})
    @Tool(name = "sample_tool", description = "sample description")
    public void sampleMethod() { }

    @McpAudience({Audience.ADMIN})
    @Tool(name = "admin_only_tool", description = "admin only")
    public void adminOnlyMethod() { }

    @Test
    void mcpAudience_hasRuntimeRetention() throws Exception {
        Retention retention = McpAudience.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void mcpAudience_isReadableViaReflection() throws Exception {
        Method method = McpAudienceTest.class.getMethod("sampleMethod");
        McpAudience annotation = method.getAnnotation(McpAudience.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactlyInAnyOrder(
                Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN);
    }

    @Test
    void mcpAudience_singleAudience_isReadable() throws Exception {
        Method method = McpAudienceTest.class.getMethod("adminOnlyMethod");
        McpAudience annotation = method.getAnnotation(McpAudience.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(Audience.ADMIN);
    }

    @Test
    void mcpAudience_isMethodLevel() {
        java.lang.annotation.Target target = McpAudience.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(java.lang.annotation.ElementType.METHOD);
        assertThat(target.value()).doesNotContain(java.lang.annotation.ElementType.TYPE);
    }

    // ---- McpToolController annotation behavior ----

    @Test
    void mcpToolController_hasRuntimeRetention() {
        Retention retention = McpToolController.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void mcpToolController_isTypeLevel() {
        java.lang.annotation.Target target = McpToolController.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(java.lang.annotation.ElementType.TYPE);
        assertThat(target.value()).doesNotContain(java.lang.annotation.ElementType.METHOD);
    }

    @Test
    void mcpToolController_isMetaAnnotatedWithComponent() {
        // @McpToolController must be meta-annotated with @Component so Spring scans it
        org.springframework.stereotype.Component component =
                McpToolController.class.getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(component).isNotNull();
    }
}
