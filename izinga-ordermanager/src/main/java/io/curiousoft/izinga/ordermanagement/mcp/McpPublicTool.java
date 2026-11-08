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
 * <p><strong>Runtime behaviour:</strong> {@code GuardedToolCallback} does NOT read this annotation
 * at dispatch time. Its entire logic is {@code assertMcpRequest()}, which checks only
 * {@link io.curiousoft.izinga.commons.security.CallerIdentityContext#isMcpRequest()} and fails
 * closed. The public behaviour of a method carrying {@code @McpPublicTool} comes exclusively from
 * the deliberate absence of an inline identity gate in that method body. This annotation is a
 * documentation marker and ArchUnit R7 exemption only — it confers no runtime privilege by itself.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpPublicTool {
}
