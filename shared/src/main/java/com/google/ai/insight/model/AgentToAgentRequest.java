package com.google.ai.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request model for agent-to-agent communication.
 * Used when one agent needs to request data or actions from another agent.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentToAgentRequest {

    /**
     * Unique identifier for the request
     */
    private String requestId;

    /**
     * Action to be performed (e.g., "GET_METRICS")
     */
    private String action;

    /**
     * Request payload containing action-specific data
     */
    private Map<String, Object> payload;
}