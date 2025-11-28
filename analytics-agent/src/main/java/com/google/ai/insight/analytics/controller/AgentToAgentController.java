package com.google.ai.insight.analytics.controller;

import com.google.ai.insight.analytics.agents.AnalyticsLlmAgent;
import com.google.ai.insight.analytics.tools.VerticaTool;
import com.google.ai.insight.constants.AgentActions;
import com.google.ai.insight.exception.ValidationException;
import com.google.ai.insight.model.AgentToAgentRequest;
import com.google.ai.insight.model.AgentToAgentResponse;
import com.google.ai.insight.model.InternalMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent-to-Agent communication controller.
 * Handles requests from other agents in the system via Google ADK A2A protocol.
 */
@Slf4j
@RestController
@RequestMapping("/agent-to-agent")
@RequiredArgsConstructor
public class AgentToAgentController {

    private final AnalyticsLlmAgent analyticsLlmAgent;
    private final VerticaTool verticaTool;

    /**
     * Handles agent-to-agent requests.
     *
     * @param request The agent-to-agent request containing action and payload
     * @return Agent-to-agent response with results or error information
     */
    @PostMapping
    public AgentToAgentResponse handleRequest(@RequestBody AgentToAgentRequest request) {
        log.debug("Received agent-to-agent request: action={}, requestId={}",
                request.getAction(), request.getRequestId());

        validateRequest(request);

        AgentToAgentResponse response = new AgentToAgentResponse();
        response.setRequestId(request.getRequestId());

        if (AgentActions.GET_METRICS.equals(request.getAction())) {
            return handleGetMetrics(request, response);
        } else if (AgentActions.ANALYZE_METRICS.equals(request.getAction())) {
            return handleAnalyzeMetrics(request, response);
        } else {
            response.setStatus(AgentActions.Status.ERROR);
            response.setErrorMessage("Unknown action: " + request.getAction());
            log.warn("Unknown action requested: {}", request.getAction());
            return response;
        }
    }

    /**
     * Handles GET_METRICS action - retrieves raw metrics without LLM analysis.
     */
    private AgentToAgentResponse handleGetMetrics(AgentToAgentRequest request, AgentToAgentResponse response) {
        String featureId = (String) request.getPayload().get("featureId");

        if (featureId == null || featureId.trim().isEmpty()) {
            throw new ValidationException("Feature ID is required in payload");
        }

        InternalMetrics metrics = verticaTool.getMetricsForFeature(featureId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("metrics", metrics);

        response.setStatus(AgentActions.Status.SUCCESS);
        response.setPayload(payload);

        log.debug("Successfully retrieved metrics for feature: {}", featureId);
        return response;
    }

    /**
     * Handles ANALYZE_METRICS action - uses LLM agent to analyze metrics with insights.
     */
    private AgentToAgentResponse handleAnalyzeMetrics(AgentToAgentRequest request, AgentToAgentResponse response) {
        String featureId = (String) request.getPayload().get("featureId");

        if (featureId == null || featureId.trim().isEmpty()) {
            throw new ValidationException("Feature ID is required in payload");
        }

        // Use LLM agent to analyze metrics
        String analysis = analyticsLlmAgent.analyzeFeatureMetrics(featureId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("featureId", featureId);
        payload.put("analysis", analysis);

        response.setStatus(AgentActions.Status.SUCCESS);
        response.setPayload(payload);

        log.debug("Successfully analyzed metrics for feature: {}", featureId);
        return response;
    }

    private void validateRequest(AgentToAgentRequest request) {
        if (request == null) {
            throw new ValidationException("Request cannot be null");
        }
        if (request.getAction() == null || request.getAction().trim().isEmpty()) {
            throw new ValidationException("Action cannot be null or empty");
        }
        if (request.getPayload() == null) {
            throw new ValidationException("Payload cannot be null");
        }
    }
}