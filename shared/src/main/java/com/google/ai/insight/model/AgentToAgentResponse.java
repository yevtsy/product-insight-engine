 package com.google.ai.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response model for agent-to-agent communication.
 * Contains the result of a request from one agent to another.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentToAgentResponse {

    /**
     * Request identifier matching the original request
     */
    private String requestId;

    /**
     * Status of the request ("SUCCESS" or "ERROR")
     */
    private String status;

    /**
     * Response payload containing action-specific data
     */
    private Map<String, Object> payload;

    /**
     * Error message if status is "ERROR"
     */
    private String errorMessage;
}