package io.curiousoft.izinga.ordermanagement.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADR-029: Marks a {@link org.springframework.ai.tool.annotation.Tool @Tool} method on a
 * {@link McpToolController @McpToolController} as explicitly public — meaning the tool accepts
 * unauthenticated (tokenless) MCP requests in addition to authenticated ones.
 *
 * <p><strong>Security contract:</strong> Use of this annotation is a deliberate security decision
 * and must be reviewed by the Security team before merging. It must NOT be added to an existing
 * tool solely to satisfy ArchUnit R7 — adding {@code @McpPublicTool} to a tool that currently
 * enforces identity gates is a serious security regression. ArchUnit R7 exempts methods carrying
 * this annotation; all other {@code @Tool} methods must call an identity gate method.</p>
 *
 * <p><strong>R2 still applies:</strong> Tools annotated with {@code @McpPublicTool} must still
 * carry a non-empty {@link McpAudience @McpAudience} annotation.</p>
 *
 * <p>The {@link io.curiousoft.izinga.ordermanagement.McpConfig} {@code GuardedToolCallback}
 * inspects this annotation at dispatch time and skips the identity-gate check for methods
 * that carry it, allowing tokenless requests through to the tool body.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpPublicTool {
}
