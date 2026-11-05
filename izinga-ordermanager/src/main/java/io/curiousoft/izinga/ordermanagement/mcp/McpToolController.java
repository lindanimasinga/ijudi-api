package io.curiousoft.izinga.ordermanagement.mcp;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADR-026 D1 / D2: Class-level marker annotation for MCP tool controller classes.
 *
 * <p>Classes annotated with {@code @McpToolController} are dedicated MCP boundary
 * classes that hold {@code @Tool} methods. They are NOT dual-use service classes.
 * No internal caller (REST controller, scheduler, event listener) may call methods
 * on an {@code @McpToolController} class — that is enforced by ArchUnit rule R3.</p>
 *
 * <p>Meta-annotated with {@link Component} so Spring component-scanning picks up
 * controller classes as beans. {@link McpConfig} assembles these beans into a single
 * {@link org.springframework.ai.tool.ToolCallbackProvider} and wraps each
 * {@link org.springframework.ai.tool.ToolCallback} in a {@link GuardedToolCallback}.</p>
 *
 * <p>Naming convention: classes annotated {@code @McpToolController} must follow
 * the {@code *McpController} naming pattern (e.g. {@code OrderMcpController},
 * {@code StoreMcpController}, {@code UserMcpController}). No controller classes
 * are created in Phase 0; this annotation is defined here as the foundation.</p>
 *
 * <p>Implementation note: Spring AI 1.1.5 {@link org.springframework.ai.tool.method.MethodToolCallbackProvider}
 * resolves {@code @Tool} methods on {@code AopUtils.getTargetClass(bean)} and invokes
 * them reflectively on the bean. Controllers must NOT be proxied (no interfaces,
 * no AOP advice) — a JDK proxy would cause the reflective invocation to fail.
 * {@link Component} without {@code @Transactional} or any other proxy-triggering
 * annotation ensures no proxy is created.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface McpToolController {
}
