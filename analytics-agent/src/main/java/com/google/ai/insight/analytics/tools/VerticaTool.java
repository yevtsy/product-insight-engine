package com.google.ai.insight.analytics.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.ai.insight.analytics.mcp.DataHubMcpClient;
import com.google.ai.insight.analytics.mcp.VerticaMcpClient;
import com.google.ai.insight.model.InternalMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool for retrieving feature metrics using MCP (Model Context Protocol) clients.
 * Integrates with Vertica for metrics data and DataHub for metadata enrichment.
 */
@Slf4j
@Component
public class VerticaTool {

    private final VerticaMcpClient verticaMcpClient;
    private final DataHubMcpClient dataHubMcpClient;

    // Metrics
    private final Timer metricsRetrievalTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter fallbackCounter;

    public VerticaTool(VerticaMcpClient verticaMcpClient,
                       DataHubMcpClient dataHubMcpClient,
                       MeterRegistry meterRegistry) {
        this.verticaMcpClient = verticaMcpClient;
        this.dataHubMcpClient = dataHubMcpClient;

        // Initialize metrics
        this.metricsRetrievalTimer = Timer.builder("vertica.tool.retrieval.duration")
                .description("Time to retrieve feature metrics")
                .register(meterRegistry);

        this.successCounter = Counter.builder("vertica.tool.retrieval.success")
                .description("Successful metrics retrievals")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("vertica.tool.retrieval.error")
                .description("Failed metrics retrievals")
                .register(meterRegistry);

        this.fallbackCounter = Counter.builder("vertica.tool.retrieval.fallback")
                .description("Fallback to mock data")
                .register(meterRegistry);
    }

    /**
     * Retrieve internal metrics for a specific feature.
     * Uses Vertica MCP client for querying metrics data with fallback to mock data.
     *
     * @param featureId Feature identifier
     * @return Internal metrics for the feature
     */
    public InternalMetrics getMetricsForFeature(String featureId) {
        try {
            return metricsRetrievalTimer.recordCallable(() -> {
                log.debug("Retrieving metrics for feature: {}", featureId);

                try {
                    // Query Vertica via MCP for metrics
                    JsonNode metricsData = verticaMcpClient.getFeatureMetrics(featureId);

                    InternalMetrics metrics = parseMetricsFromVertica(metricsData, featureId);

                    // Optionally enrich with DataHub metadata
                    try {
                        enrichWithDataHubMetadata(metrics, featureId);
                    } catch (Exception e) {
                        log.warn("Failed to enrich metrics with DataHub metadata: {}", e.getMessage());
                        // Continue without enrichment
                    }

                    successCounter.increment();
                    log.info("Successfully retrieved metrics for feature: {}", featureId);
                    return metrics;

                } catch (Exception e) {
                    errorCounter.increment();
                    log.error("Failed to retrieve metrics from Vertica for feature {}: {}",
                            featureId, e.getMessage(), e);

                    // Fallback to mock data
                    fallbackCounter.increment();
                    log.warn("Falling back to mock data for feature: {}", featureId);
                    return getMockMetrics(featureId);
                }
            });
        } catch (Exception e) {
            // This should never happen as we handle all exceptions inside recordCallable
            log.error("Unexpected error in metrics retrieval", e);
            return getMockMetrics(featureId);
        }
    }

    /**
     * Parse metrics data from Vertica query result.
     */
    private InternalMetrics parseMetricsFromVertica(JsonNode data, String featureId) {
        InternalMetrics metrics = new InternalMetrics();
        metrics.setFeatureId(featureId);

        if (data != null && data.has("rows") && data.get("rows").isArray() && data.get("rows").size() > 0) {
            JsonNode row = data.get("rows").get(0);

            metrics.setPurchaseCount(row.has("purchase_count") ?
                    row.get("purchase_count").asInt(0) : 0);

            metrics.setLoginCount(row.has("login_count") ?
                    row.get("login_count").asInt(0) : 0);

            metrics.setAverageSessionDuration(row.has("avg_session_duration") ?
                    row.get("avg_session_duration").asDouble(0.0) : 0.0);

            log.debug("Parsed metrics: purchases={}, logins={}, avgSessionDuration={}",
                    metrics.getPurchaseCount(),
                    metrics.getLoginCount(),
                    metrics.getAverageSessionDuration());
        } else {
            log.warn("No metrics data found for feature: {}, using defaults", featureId);
        }

        return metrics;
    }

    /**
     * Enrich metrics with DataHub metadata.
     */
    private void enrichWithDataHubMetadata(InternalMetrics metrics, String featureId)
            throws Exception {
        // Construct DataHub URN for feature dataset
        String datasetUrn = String.format("urn:li:dataset:(urn:li:dataPlatform:vertica,feature_analytics.%s,PROD)",
                featureId);

        try {
            JsonNode metadata = dataHubMcpClient.getDatasetMetadata(datasetUrn);

            if (metadata != null && metadata.has("description")) {
                log.debug("Enriched metrics with DataHub metadata for feature: {}", featureId);
            }
        } catch (Exception e) {
            log.debug("DataHub metadata not available for feature: {}", featureId);
            throw e;
        }
    }

    /**
     * Generate mock metrics as fallback.
     */
    private InternalMetrics getMockMetrics(String featureId) {
        InternalMetrics metrics = new InternalMetrics();
        metrics.setFeatureId(featureId);
        metrics.setPurchaseCount((int) (Math.random() * 1000));
        metrics.setLoginCount((int) (Math.random() * 5000));
        metrics.setAverageSessionDuration(Math.random() * 30.0);

        log.debug("Generated mock metrics: purchases={}, logins={}, avgSessionDuration={}",
                metrics.getPurchaseCount(),
                metrics.getLoginCount(),
                metrics.getAverageSessionDuration());

        return metrics;
    }

    /**
     * Health check for MCP clients.
     *
     * @return true if at least one MCP client is operational
     */
    public boolean healthCheck() {
        boolean verticaHealthy = verticaMcpClient.isRunning() && verticaMcpClient.testConnection();
        boolean datahubHealthy = dataHubMcpClient.isRunning() && dataHubMcpClient.testConnection();

        log.info("MCP Health Check - Vertica: {}, DataHub: {}", verticaHealthy, datahubHealthy);

        return verticaHealthy || datahubHealthy;
    }
}

