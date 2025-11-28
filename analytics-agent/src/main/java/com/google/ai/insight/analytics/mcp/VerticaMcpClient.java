package com.google.ai.insight.analytics.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ai.insight.analytics.config.McpServerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP client for querying Vertica database via mcp-vertica server.
 * Provides high-level API for executing SQL queries and retrieving metrics.
 */
@Slf4j
@Component
public class VerticaMcpClient extends McpClient {

    private final McpServerProperties.VerticaConfig config;

    public VerticaMcpClient(ObjectMapper objectMapper,
                            MeterRegistry meterRegistry,
                            McpServerProperties properties) {
        super(objectMapper, meterRegistry, "vertica");
        this.config = properties.getVertica();

        if (config.isEnabled()) {
            try {
                initializeServer();
                log.info("Vertica MCP client initialized successfully");
            } catch (IOException e) {
                log.error("Failed to initialize Vertica MCP client", e);
            }
        } else {
            log.info("Vertica MCP client is disabled");
        }
    }

    @Override
    protected void initializeServer() throws IOException {
        Map<String, String> environment = new HashMap<>();
        environment.put("VERTICA_HOST", config.getHost());
        environment.put("VERTICA_PORT", String.valueOf(config.getPort()));
        environment.put("VERTICA_DATABASE", config.getDatabase());
        environment.put("VERTICA_USERNAME", config.getUsername());
        environment.put("VERTICA_PASSWORD", config.getPassword());

        List<String> args = List.of(config.getServerPackage());

        startProcess(config.getCommand(), args, environment);

        // Wait for server to be ready
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute SQL query against Vertica database.
     *
     * @param query SQL query to execute
     * @return Query results as JsonNode
     */
    public JsonNode executeQuery(String query) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Vertica MCP client is disabled");
        }

        log.debug("Executing Vertica query: {}", query);

        Map<String, Object> params = Map.of("query", query);

        return executeWithRetry(
                "vertica/query",
                params,
                config.getTimeout(),
                config.getMaxRetries()
        );
    }

    /**
     * Get feature metrics from Vertica by executing a predefined query.
     * Uses proper SQL sanitization to prevent SQL injection attacks.
     *
     * @param featureId Feature identifier
     * @return Metrics data as JsonNode
     * @throws IllegalArgumentException if featureId contains invalid characters
     */
    public JsonNode getFeatureMetrics(String featureId) throws IOException, InterruptedException {
        // Validate and sanitize featureId to prevent SQL injection
        String sanitizedFeatureId = sanitizeFeatureId(featureId);

        String query = String.format(
                "SELECT " +
                        "feature_id, " +
                        "COUNT(DISTINCT user_id) as active_users, " +
                        "SUM(CASE WHEN event_type = 'purchase' THEN 1 ELSE 0 END) as purchase_count, " +
                        "SUM(CASE WHEN event_type = 'login' THEN 1 ELSE 0 END) as login_count, " +
                        "AVG(session_duration_minutes) as avg_session_duration " +
                        "FROM feature_analytics " +
                        "WHERE feature_id = '%s' " +
                        "AND event_timestamp >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY feature_id",
                sanitizedFeatureId
        );

        log.info("Fetching metrics for feature: {}", sanitizedFeatureId);
        return executeQuery(query);
    }

    /**
     * Sanitizes feature ID to prevent SQL injection.
     * Only allows alphanumeric characters, hyphens, underscores, and dots.
     *
     * @param featureId The feature ID to sanitize
     * @return Sanitized feature ID
     * @throws IllegalArgumentException if featureId contains invalid characters
     */
    private String sanitizeFeatureId(String featureId) {
        if (featureId == null || featureId.trim().isEmpty()) {
            throw new IllegalArgumentException("Feature ID cannot be null or empty");
        }

        // Only allow alphanumeric characters, hyphens, underscores, and dots
        // This is a whitelist approach which is more secure than blacklisting
        if (!featureId.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException(
                    "Feature ID contains invalid characters. Only alphanumeric, dots, hyphens, and underscores are allowed: " + featureId
            );
        }

        // Limit length to prevent potential DoS
        if (featureId.length() > 100) {
            throw new IllegalArgumentException("Feature ID is too long (max 100 characters)");
        }

        return featureId;
    }

    /**
     * Test connection to Vertica via MCP server.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        try {
            JsonNode result = executeQuery("SELECT 1");
            log.info("Vertica connection test successful");
            return true;
        } catch (Exception e) {
            log.error("Vertica connection test failed", e);
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up Vertica MCP client");
        stopProcess();
    }
}