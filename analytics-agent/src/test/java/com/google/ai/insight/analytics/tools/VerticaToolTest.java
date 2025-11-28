package com.google.ai.insight.analytics.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.ai.insight.analytics.mcp.DataHubMcpClient;
import com.google.ai.insight.analytics.mcp.VerticaMcpClient;
import com.google.ai.insight.model.InternalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VerticaTool.
 */
@ExtendWith(MockitoExtension.class)
class VerticaToolTest {

    @Mock
    private VerticaMcpClient verticaMcpClient;

    @Mock
    private DataHubMcpClient dataHubMcpClient;

    private VerticaTool verticaTool;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        verticaTool = new VerticaTool(verticaMcpClient, dataHubMcpClient, meterRegistry);
    }

    @Test
    void testGetMetricsForFeature_Success() throws Exception {
        // Arrange
        String featureId = "test-feature-123";
        JsonNode mockResponse = createMockVerticaResponse(100, 500, 15.5);

        when(verticaMcpClient.getFeatureMetrics(featureId)).thenReturn(mockResponse);
        when(dataHubMcpClient.getDatasetMetadata(anyString())).thenThrow(new RuntimeException("Not available"));

        // Act
        InternalMetrics result = verticaTool.getMetricsForFeature(featureId);

        // Assert
        assertNotNull(result);
        assertEquals(featureId, result.getFeatureId());
        assertEquals(100, result.getPurchaseCount());
        assertEquals(500, result.getLoginCount());
        assertEquals(15.5, result.getAverageSessionDuration(), 0.01);

        verify(verticaMcpClient, times(1)).getFeatureMetrics(featureId);
    }

    @Test
    void testGetMetricsForFeature_FallbackOnError() throws Exception {
        // Arrange
        String featureId = "test-feature-456";

        when(verticaMcpClient.getFeatureMetrics(featureId))
                .thenThrow(new RuntimeException("Connection failed"));

        // Act
        InternalMetrics result = verticaTool.getMetricsForFeature(featureId);

        // Assert
        assertNotNull(result);
        assertEquals(featureId, result.getFeatureId());
        // Mock data should be generated
        assertTrue(result.getPurchaseCount() >= 0);
        assertTrue(result.getLoginCount() >= 0);
        assertTrue(result.getAverageSessionDuration() >= 0.0);

        verify(verticaMcpClient, times(1)).getFeatureMetrics(featureId);
    }

    @Test
    void testGetMetricsForFeature_EmptyResponse() throws Exception {
        // Arrange
        String featureId = "test-feature-789";
        JsonNode emptyResponse = createEmptyVerticaResponse();

        when(verticaMcpClient.getFeatureMetrics(featureId)).thenReturn(emptyResponse);

        // Act
        InternalMetrics result = verticaTool.getMetricsForFeature(featureId);

        // Assert
        assertNotNull(result);
        assertEquals(featureId, result.getFeatureId());
        assertEquals(0, result.getPurchaseCount());
        assertEquals(0, result.getLoginCount());
        assertEquals(0.0, result.getAverageSessionDuration(), 0.01);

        verify(verticaMcpClient, times(1)).getFeatureMetrics(featureId);
    }

    @Test
    void testHealthCheck_BothHealthy() {
        // Arrange
        when(verticaMcpClient.isRunning()).thenReturn(true);
        when(verticaMcpClient.testConnection()).thenReturn(true);
        when(dataHubMcpClient.isRunning()).thenReturn(true);
        when(dataHubMcpClient.testConnection()).thenReturn(true);

        // Act
        boolean result = verticaTool.healthCheck();

        // Assert
        assertTrue(result);
        verify(verticaMcpClient).isRunning();
        verify(verticaMcpClient).testConnection();
        verify(dataHubMcpClient).isRunning();
        verify(dataHubMcpClient).testConnection();
    }

    @Test
    void testHealthCheck_OnlyVerticaHealthy() {
        // Arrange
        when(verticaMcpClient.isRunning()).thenReturn(true);
        when(verticaMcpClient.testConnection()).thenReturn(true);
        when(dataHubMcpClient.isRunning()).thenReturn(true);
        when(dataHubMcpClient.testConnection()).thenReturn(false);

        // Act
        boolean result = verticaTool.healthCheck();

        // Assert
        assertTrue(result);
    }

    @Test
    void testHealthCheck_BothUnhealthy() {
        // Arrange
        when(verticaMcpClient.isRunning()).thenReturn(true);
        when(verticaMcpClient.testConnection()).thenReturn(false);
        when(dataHubMcpClient.isRunning()).thenReturn(true);
        when(dataHubMcpClient.testConnection()).thenReturn(false);

        // Act
        boolean result = verticaTool.healthCheck();

        // Assert
        assertFalse(result);
    }

    /**
     * Helper method to create mock Vertica response.
     */
    private JsonNode createMockVerticaResponse(int purchaseCount, int loginCount, double avgSessionDuration) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode rows = objectMapper.createArrayNode();

        ObjectNode row = objectMapper.createObjectNode();
        row.put("purchase_count", purchaseCount);
        row.put("login_count", loginCount);
        row.put("avg_session_duration", avgSessionDuration);

        rows.add(row);
        root.set("rows", rows);

        return root;
    }

    /**
     * Helper method to create empty Vertica response.
     */
    private JsonNode createEmptyVerticaResponse() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode rows = objectMapper.createArrayNode();
        root.set("rows", rows);
        return root;
    }
}