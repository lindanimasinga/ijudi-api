package io.curiousoft.izinga.ordermanagement.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-026 D2: Unit tests for {@link McpRefusal} constants.
 *
 * Tests verify:
 * - NOT_MCP_REQUEST constant exists, is non-null, and starts with IDENTITY_REFUSAL: prefix
 */
class McpRefusalTest {

    @Test
    void notMcpRequest_isNonNull() {
        assertThat(McpRefusal.NOT_MCP_REQUEST).isNotNull();
    }

    @Test
    void notMcpRequest_startsWithIdentityRefusalPrefix() {
        assertThat(McpRefusal.NOT_MCP_REQUEST)
                .startsWith("IDENTITY_REFUSAL:");
    }

    @Test
    void notMcpRequest_containsUsefulMessage() {
        assertThat(McpRefusal.NOT_MCP_REQUEST)
                .contains("MCP request path")
                .contains("not permitted");
    }
}
