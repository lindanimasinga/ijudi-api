package io.curiousoft.izinga.messaging.aiAgent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the production bug where StoreAiAgent's OpenAI "tools" request body
 * included a bogus "optionalHeaders" property, which OpenAI's Responses API rejected with:
 *
 *   400 Bad Request: "Unknown parameter: 'tools[0].optionalHeaders'."
 *
 * Root cause: getOptionalHeaders() follows JavaBean getter naming, so Jackson's default bean
 * serialization treated it as a real property "optionalHeaders" alongside "headers" — even
 * though it exists purely as a Java-side convenience accessor. This only reproduced for agents
 * whose MCP tools carry a non-null headers map (e.g. StoreAiAgent's per-store scope JWT via
 * SA-021-17), which is why the default driver/customer agents were unaffected.
 *
 * This test serializes McpServerConfig with the same ObjectMapper used by RestTemplate to
 * build the OpenAI request body, proving "optionalHeaders" never appears in the output.
 */
class McpServerConfigJsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialization_withHeaders_omitsOptionalHeadersKey() throws Exception {
        Map<String, String> headers = Map.of("X-Agent-Scope", "eyJhbGciOiJIUzI1NiJ9.fake.jwt");
        McpServerConfig config = new McpServerConfig(
                "mcp", "store-scoped-api", "desc", "https://api.izinga.co.za/mcp", "never", headers);

        String json = mapper.writeValueAsString(config);

        assertFalse(json.contains("optionalHeaders"),
                "serialized JSON must not contain the bean-convention 'optionalHeaders' property: " + json);
        assertTrue(json.contains("\"headers\""),
                "serialized JSON must still contain the real 'headers' property: " + json);
    }

    @Test
    void serialization_withoutHeaders_omitsBothHeadersKeys() throws Exception {
        McpServerConfig config = new McpServerConfig(
                "mcp", "no-headers-api", "desc", "https://api.izinga.co.za/mcp", "never");

        String json = mapper.writeValueAsString(config);

        assertFalse(json.contains("optionalHeaders"),
                "serialized JSON must not contain 'optionalHeaders' even when headers is null: " + json);
        assertFalse(json.contains("\"headers\""),
                "null headers must be omitted entirely (JsonInclude.NON_NULL): " + json);
    }
}
