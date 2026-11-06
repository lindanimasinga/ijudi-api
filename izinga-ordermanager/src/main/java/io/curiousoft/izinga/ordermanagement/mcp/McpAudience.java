package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADR-026 D2 / R2: Method-level annotation declaring which {@link Audience} values
 * are permitted to call an MCP {@code @Tool} method.
 *
 * <p>Every {@code @Tool} method in the codebase MUST carry this annotation with a
 * NON-EMPTY value array. An empty array is treated as a configuration error caught
 * by ArchUnit rule R2.</p>
 *
 * <p>This annotation is a declarative contract — it is not enforced at runtime by
 * {@link GuardedToolCallback} in Phase 0. The runtime enforcement is done by each
 * tool method's existing gate logic (phase-by-phase migration). The annotation is
 * enforced at build time by ArchUnit rule R2 and is characterised by
 * {@code McpAccessMatrixTest}.</p>
 *
 * <p>When OQ-4 is eventually decided and the wrapper takes over audience gating,
 * the {@link GuardedToolCallback} will read this annotation and enforce the
 * allowlist centrally, at which point each controller method body shrinks.</p>
 *
 * <p>MUST use {@link RetentionPolicy#RUNTIME} so ArchUnit can read it via reflection.</p>
 *
 * <p>Example usage (once Phase 1 controller classes exist):</p>
 * <pre>
 * {@code @Tool(name = "find_order_by_id", ...)}
 * {@code @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN})}
 * public Order findOrderById(String orderId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpAudience {

    /**
     * The audience values permitted to invoke this tool method.
     * MUST be non-empty — an empty array is a configuration error caught by ArchUnit R2.
     */
    Audience[] value();
}
