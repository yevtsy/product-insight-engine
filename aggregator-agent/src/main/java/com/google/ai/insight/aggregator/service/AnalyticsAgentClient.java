package com.google.ai.insight.aggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ai.insight.constants.AgentActions;
import com.google.ai.insight.exception.ExternalServiceException;
import com.google.ai.insight.exception.ValidationException;
import com.google.ai.insight.model.AgentToAgentRequest;
import com.google.ai.insight.model.AgentToAgentResponse;
import com.google.ai.insight.model.InternalMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client for communicating with the Analytics Agent.
 * Handles metric retrieval via agent-to-agent protocol.
 */
@Slf4j
@Service
public class AnalyticsAgentClient {

    private static final String SERVICE_NAME = "Analytics Agent";

    private final String analyticsAgentUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsAgentClient(
            @Value("${agent.analytics-agent-url}") String analyticsAgentUrl,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.analyticsAgentUrl = analyticsAgentUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves internal metrics for a specific feature.
     *
     * @param featureId The feature identifier
     * @return Internal metrics for the feature
     * @throws ValidationException if featureId is null or empty
     * @throws ExternalServiceException if communication with Analytics Agent fails
     */
    public InternalMetrics getMetrics(String featureId) {
        if (featureId == null || featureId.trim().isEmpty()) {
            throw new ValidationException("Feature ID cannot be null or empty");
        }

        log.debug("Requesting metrics for feature: {}", featureId);

        AgentToAgentRequest request = buildMetricsRequest(featureId);

        try {
            AgentToAgentResponse response = restTemplate.postForObject(
                    analyticsAgentUrl,
                    request,
                    AgentToAgentResponse.class
            );

            return parseMetricsResponse(response, featureId);

        } catch (HttpClientErrorException e) {
            log.error("Client error when requesting metrics for feature {}: {}", featureId, e.getMessage());
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Invalid request to analytics service",
                    Map.of("featureId", featureId, "statusCode", e.getStatusCode()),
                    e
            );
        } catch (HttpServerErrorException e) {
            log.error("Server error when requesting metrics for feature {}: {}", featureId, e.getMessage());
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Analytics service encountered an error",
                    Map.of("featureId", featureId, "statusCode", e.getStatusCode()),
                    e
            );
        } catch (ResourceAccessException e) {
            log.error("Connection error when requesting metrics for feature {}: {}", featureId, e.getMessage());
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Unable to connect to analytics service",
                    Map.of("featureId", featureId, "url", analyticsAgentUrl),
                    e
            );
        } catch (Exception e) {
            log.error("Unexpected error when requesting metrics for feature {}: {}", featureId, e.getMessage(), e);
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Unexpected error occurred",
                    Map.of("featureId", featureId),
                    e
            );
        }
    }

    private AgentToAgentRequest buildMetricsRequest(String featureId) {
        AgentToAgentRequest request = new AgentToAgentRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setAction(AgentActions.GET_METRICS);

        Map<String, Object> payload = new HashMap<>();
        payload.put("featureId", featureId);
        request.setPayload(payload);

        return request;
    }

    private InternalMetrics parseMetricsResponse(AgentToAgentResponse response, String featureId) {
        if (response == null) {
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Received null response from analytics service",
                    Map.of("featureId", featureId)
            );
        }

        if (!AgentActions.Status.SUCCESS.equals(response.getStatus())) {
            String errorMessage = response.getErrorMessage() != null
                    ? response.getErrorMessage()
                    : "Unknown error";
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Analytics service returned error: " + errorMessage,
                    Map.of("featureId", featureId, "responseStatus", response.getStatus())
            );
        }

        if (response.getPayload() == null || !response.getPayload().containsKey("metrics")) {
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Analytics service response missing metrics data",
                    Map.of("featureId", featureId)
            );
        }

        try {
            return objectMapper.convertValue(response.getPayload().get("metrics"), InternalMetrics.class);
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse metrics response for feature {}: {}", featureId, e.getMessage());
            throw new ExternalServiceException(
                    SERVICE_NAME,
                    "Failed to parse metrics data from analytics service",
                    Map.of("featureId", featureId),
                    e
            );
        }
    }
}
