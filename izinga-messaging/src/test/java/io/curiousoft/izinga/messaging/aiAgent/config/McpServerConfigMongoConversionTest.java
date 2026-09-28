package io.curiousoft.izinga.messaging.aiAgent.config;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the production startup crash caused by McpServerConfig
 * lacking a no-arg constructor.
 *
 * Root cause: Spring Data MongoDB's MappingMongoConverter requires a no-arg
 * constructor (or a @PersistenceCreator-annotated constructor) to deserialize
 * embedded sub-documents. Without @NoArgsConstructor on McpServerConfig,
 * application startup crashed when AiAgentConfigInitializer called
 * repository.findByAgentName() and the converter tried to instantiate
 * McpServerConfig from each element in the mcpServers BSON array:
 *
 *   BeanInstantiationException: Failed to instantiate [McpServerConfig]:
 *     No default constructor found
 *   Caused by: java.lang.NoSuchMethodException: McpServerConfig.<init>()
 *
 * These tests use MappingMongoConverter directly — the exact code path that
 * failed in production — without requiring an embedded Mongo server.
 * A failure here (NoSuchMethodException or similar) means the no-arg
 * constructor is missing again.
 */
class McpServerConfigMongoConversionTest {

    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoMappingContext context = new MongoMappingContext();
        context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.afterPropertiesSet();
    }

    // ---- McpServerConfig round-trip (no headers) ----

    @Test
    void mcpServerConfig_roundTrip_withoutHeaders() {
        McpServerConfig original = new McpServerConfig(
                "mcp",
                "order-and-user-management-api",
                "API for managing orders and users",
                "https://api.izinga.co.za/mcp",
                "never",
                null);

        Document doc = new Document();
        converter.write(original, doc);

        // This line was the one that threw NoSuchMethodException in production
        McpServerConfig read = converter.read(McpServerConfig.class, doc);

        assertNotNull(read, "deserialized McpServerConfig must not be null");
        assertEquals("mcp", read.getType());
        assertEquals("order-and-user-management-api", read.getServerLabel());
        assertEquals("API for managing orders and users", read.getServerDescription());
        assertEquals("https://api.izinga.co.za/mcp", read.getServerUrl());
        assertEquals("never", read.getRequireApproval());
        assertNull(read.getHeaders(), "headers must be null when not set");
        assertFalse(read.getOptionalHeaders().isPresent(), "Optional<headers> must be empty when null");
    }

    // ---- McpServerConfig round-trip (with headers) ----

    @Test
    void mcpServerConfig_roundTrip_withHeaders() {
        Map<String, String> headers = Map.of("X-Agent-Scope", "driver_support");
        McpServerConfig original = new McpServerConfig(
                "mcp", "server-with-headers", "desc", "https://example.com/mcp", "never", headers);

        Document doc = new Document();
        converter.write(original, doc);

        McpServerConfig read = converter.read(McpServerConfig.class, doc);

        assertNotNull(read);
        assertEquals("server-with-headers", read.getServerLabel());
        assertNotNull(read.getHeaders(), "headers must be preserved when set");
        assertEquals("driver_support", read.getHeaders().get("X-Agent-Scope"));
        assertTrue(read.getOptionalHeaders().isPresent());
    }

    // ---- McpServerConfig convenience constructor (no-headers overload) ----

    @Test
    void mcpServerConfig_convenienceConstructor_roundTrip() {
        // Exercises the 5-arg backward-compatible constructor used in AiAgentConfigService
        McpServerConfig original = new McpServerConfig(
                "mcp", "server-no-headers", "desc", "https://example.com/mcp", "never");

        Document doc = new Document();
        converter.write(original, doc);

        McpServerConfig read = converter.read(McpServerConfig.class, doc);

        assertNotNull(read);
        assertEquals("server-no-headers", read.getServerLabel());
        assertNull(read.getHeaders());
    }

    // ---- Simulate production failure path: read from raw BSON (as MongoDB returns it) ----

    @Test
    void mcpServerConfig_readFromRawBson_simulatesProductionFailurePath() {
        // Spring Data MongoDB stores the McpServerConfig sub-document using the Java field
        // names (camelCase), since McpServerConfig has @JsonProperty but no @Field annotations.
        // @JsonProperty is a Jackson annotation; Spring Data ignores it for its own mapping.
        //
        // This test directly simulates the BSON document shape that MongoDB returns
        // when the app calls repository.findByAgentName("driver_support") — the exact
        // moment the crash was triggered before this fix.
        Document bson = new Document()
                .append("type", "mcp")
                .append("serverLabel", "order-and-user-management-api")
                .append("serverDescription", "API for managing orders and users")
                .append("serverUrl", "https://api.izinga.co.za/mcp")
                .append("requireApproval", "never");

        // Before the @NoArgsConstructor fix this threw NoSuchMethodException
        McpServerConfig read = converter.read(McpServerConfig.class, bson);

        assertNotNull(read, "converter.read must not return null");
        assertEquals("mcp", read.getType());
        assertEquals("order-and-user-management-api", read.getServerLabel());
        assertEquals("API for managing orders and users", read.getServerDescription());
        assertEquals("https://api.izinga.co.za/mcp", read.getServerUrl());
        assertEquals("never", read.getRequireApproval());
        assertNull(read.getHeaders(), "absent headers field must deserialize to null");
    }

    // ---- No-arg constructor produces a usable empty instance ----

    @Test
    void noArgConstructor_producesNonNullInstance() {
        // Verify that Lombok generated the no-arg constructor and it produces a valid instance.
        // All fields default to null — that is the correct behavior for Mongo deserialization
        // (the converter populates fields after construction via setters or reflection).
        McpServerConfig empty = new McpServerConfig();
        assertNotNull(empty, "no-arg constructor must produce a non-null instance");
        assertNull(empty.getType());
        assertNull(empty.getServerLabel());
        assertNull(empty.getServerUrl());
        assertNull(empty.getRequireApproval());
        assertNull(empty.getHeaders());
    }
}
