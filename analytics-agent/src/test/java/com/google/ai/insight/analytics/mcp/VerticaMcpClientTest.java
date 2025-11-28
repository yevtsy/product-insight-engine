package com.google.ai.insight.analytics.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ai.insight.analytics.config.McpServerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VerticaMcpClient.
 * Note: These tests require npx and mcp-vertica to be available.
 * Set SKIP_MCP_TESTS=true to skip these tests.
 */
class VerticaMcpClientTest {

    private VerticaMcpClient client;
    private McpServerProperties properties;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        properties = new McpServerProperties();

        // Configure test properties
        McpServerProperties.VerticaConfig config = properties.getVertica();
        config.setEnabled(false); // Disabled by default for tests
        config.setCommand("npx");
        config.setServerPackage("mcp-vertica");
        config.setHost("localhost");
        config.setPort(5433);
        config.setDatabase("test_db");
        config.setUsername("test_user");
        config.setPassword("test_pass");
        config.setTimeout(5000);
        config.setMaxRetries(2);

        client = new VerticaMcpClient(objectMapper, meterRegistry, properties);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.stopProcess();
        }
    }

    @Test
    void testClientCreationWithDisabledConfig() {
        assertNotNull(client);
        assertFalse(client.isRunning());
    }

    @Test
    void testIsRunningWhenNotStarted() {
        assertFalse(client.isRunning());
    }

    @Test
    @EnabledIf("isMcpTestsEnabled")
    void testExecuteQuery() throws Exception {
        // Enable client for this test
        properties.getVertica().setEnabled(true);
        client = new VerticaMcpClient(objectMapper, meterRegistry, properties);

        JsonNode result = client.executeQuery("SELECT 1 as test");
        assertNotNull(result);
    }

    @Test
    @EnabledIf("isMcpTestsEnabled")
    void testGetFeatureMetrics() throws Exception {
        properties.getVertica().setEnabled(true);
        client = new VerticaMcpClient(objectMapper, meterRegistry, properties);

        JsonNode result = client.getFeatureMetrics("test-feature-id");
        assertNotNull(result);
    }

    @Test
    void testExecuteQueryWhenDisabled() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            client.executeQuery("SELECT 1");
        });

        assertTrue(exception.getMessage().contains("disabled"));
    }

    @Test
    void testGetFeatureMetricsWhenDisabled() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            client.getFeatureMetrics("test-feature");
        });

        assertTrue(exception.getMessage().contains("disabled"));
    }

    /**
     * Condition method for enabling MCP tests.
     * Tests are only enabled if SKIP_MCP_TESTS environment variable is not set to "true".
     */
    static boolean isMcpTestsEnabled() {
        String skipTests = System.getenv("SKIP_MCP_TESTS");
        return skipTests == null || !skipTests.equalsIgnoreCase("true");
    }
}