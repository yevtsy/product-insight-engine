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
 * MCP client for querying DataHub metadata via @acryldata/mcp-server-datahub.
 * Provides high-level API for retrieving dataset metadata, lineage, and usage statistics.
 */
@Slf4j
@Component
public class DataHubMcpClient extends McpClient {

    private final McpServerProperties.DataHubConfig config;

    public DataHubMcpClient(ObjectMapper objectMapper,
                            MeterRegistry meterRegistry,
                            McpServerProperties properties) {
        super(objectMapper, meterRegistry, "datahub");
        this.config = properties.getDatahub();

        if (config.isEnabled()) {
            try {
                initializeServer();
                log.info("DataHub MCP client initialized successfully");
            } catch (IOException e) {
                log.error("Failed to initialize DataHub MCP client", e);
            }
        } else {
            log.info("DataHub MCP client is disabled");
        }
    }

    @Override
    protected void initializeServer() throws IOException {
        Map<String, String> environment = new HashMap<>();
        environment.put("DATAHUB_GMS_URL", config.getGmsUrl());

        if (config.getToken() != null && !config.getToken().isEmpty()) {
            environment.put("DATAHUB_TOKEN", config.getToken());
        }

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
     * Get dataset metadata from DataHub.
     *
     * @param datasetUrn DataHub dataset URN
     * @return Dataset metadata as JsonNode
     */
    public JsonNode getDatasetMetadata(String datasetUrn) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("DataHub MCP client is disabled");
        }

        log.debug("Fetching dataset metadata for: {}", datasetUrn);

        Map<String, Object> params = Map.of("urn", datasetUrn);

        return executeWithRetry(
                "datahub/getDataset",
                params,
                config.getTimeout(),
                config.getMaxRetries()
        );
    }

    /**
     * Get dataset usage statistics from DataHub.
     *
     * @param datasetUrn DataHub dataset URN
     * @return Usage statistics as JsonNode
     */
    public JsonNode getDatasetUsage(String datasetUrn) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("DataHub MCP client is disabled");
        }

        log.debug("Fetching dataset usage for: {}", datasetUrn);

        Map<String, Object> params = Map.of("urn", datasetUrn);

        return executeWithRetry(
                "datahub/getUsageStats",
                params,
                config.getTimeout(),
                config.getMaxRetries()
        );
    }

    /**
     * Search datasets by keyword in DataHub.
     *
     * @param keyword Search keyword
     * @param limit   Maximum number of results
     * @return Search results as JsonNode
     */
    public JsonNode searchDatasets(String keyword, int limit) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("DataHub MCP client is disabled");
        }

        log.debug("Searching datasets with keyword: {}, limit: {}", keyword, limit);

        Map<String, Object> params = Map.of(
                "query", keyword,
                "limit", limit
        );

        return executeWithRetry(
                "datahub/search",
                params,
                config.getTimeout(),
                config.getMaxRetries()
        );
    }

    /**
     * Get lineage information for a dataset.
     *
     * @param datasetUrn DataHub dataset URN
     * @param direction  Lineage direction (UPSTREAM or DOWNSTREAM)
     * @return Lineage information as JsonNode
     */
    public JsonNode getLineage(String datasetUrn, String direction) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("DataHub MCP client is disabled");
        }

        log.debug("Fetching lineage for dataset: {}, direction: {}", datasetUrn, direction);

        Map<String, Object> params = Map.of(
                "urn", datasetUrn,
                "direction", direction
        );

        return executeWithRetry(
                "datahub/getLineage",
                params,
                config.getTimeout(),
                config.getMaxRetries()
        );
    }

    /**
     * Test connection to DataHub via MCP server.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        try {
            // Try to search with empty query to test connection
            JsonNode result = searchDatasets("", 1);
            log.info("DataHub connection test successful");
            return true;
        } catch (Exception e) {
            log.error("DataHub connection test failed", e);
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up DataHub MCP client");
        stopProcess();
    }
}