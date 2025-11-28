package com.google.ai.insight.constants;

/**
 * Constants for agent-to-agent communication actions.
 */
public final class AgentActions {

    private AgentActions() {
        // Prevent instantiation
    }

    /**
     * Action to retrieve metrics for a specific feature
     */
    public static final String GET_METRICS = "GET_METRICS";

    /**
     * Action to retrieve and analyze metrics using LLM agent
     */
    public static final String ANALYZE_METRICS = "ANALYZE_METRICS";

    /**
     * Action status constants
     */
    public static final class Status {
        private Status() {
            // Prevent instantiation
        }

        public static final String SUCCESS = "SUCCESS";
        public static final String ERROR = "ERROR";
    }
}