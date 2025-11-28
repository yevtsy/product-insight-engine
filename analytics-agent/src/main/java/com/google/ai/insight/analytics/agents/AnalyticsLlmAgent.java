package com.google.ai.insight.analytics.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.ai.insight.analytics.tools.VerticaTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM Agent for analytics data retrieval and analysis using Google ADK.
 * This agent uses Gemini 2.5 Flash model to fetch and analyze internal metrics from Vertica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsLlmAgent {

    private final VerticaTool verticaTool;
    private BaseAgent agent;
    private InMemoryRunner runner;

    @PostConstruct
    public void init() {
        log.info("Initializing Analytics LLM Agent");

        this.agent = LlmAgent.builder()
                .name("analytics-agent")
                .description("Agent for fetching and analyzing internal metrics from Vertica database")
                .instruction("""
                        You are an analytics agent specialized in retrieving and analyzing internal metrics.
                        Your role is to:
                        1. Fetch internal metrics using the getMetricsForFeature tool
                        2. Analyze the metrics data to provide insights
                        3. Identify trends and patterns in user behavior
                        4. Provide actionable recommendations based on the data

                        When asked to retrieve metrics for a feature:
                        - Use getMetricsForFeature(featureId) to fetch the data
                        - The tool returns purchase count, login count, and average session duration
                        - Analyze these metrics to understand feature health
                        - Provide insights in a structured format with:
                          * METRICS_SUMMARY: Key metrics values
                          * ANALYSIS: What the metrics indicate about feature performance
                          * TRENDS: Notable patterns or trends in the data
                          * RECOMMENDATIONS: Actions to improve based on metrics
                        """)
                .model("gemini-2.5-flash")
                .tools(FunctionTool.create(verticaTool, "getMetricsForFeature"))
                .build();

        this.runner = new InMemoryRunner(agent);

        log.info("Analytics LLM Agent initialized successfully");
    }

    /**
     * Retrieves and analyzes metrics for a specific feature.
     *
     * @param featureId The feature identifier
     * @return Analysis result as a string
     */
    public String analyzeFeatureMetrics(String featureId) {
        log.info("Analyzing metrics for feature: {}", featureId);

        String prompt = String.format("""
                Retrieve and analyze internal metrics for feature: "%s"

                Use the getMetricsForFeature tool to fetch the data, then provide analysis with:
                1. METRICS_SUMMARY: Key metrics values (purchases, logins, session duration)
                2. ANALYSIS: What these metrics indicate about feature performance
                3. TRENDS: Notable patterns (high engagement, low conversion, etc.)
                4. RECOMMENDATIONS: Specific actions to improve metrics
                """,
                featureId
        );

        try {
            // Create session for this analysis
            Session session = runner.sessionService()
                    .createSession("analytics-app", "analytics")
                    .blockingGet();

            // Convert prompt to Content
            Content userMsg = Content.fromParts(Part.fromText(prompt));

            // Run agent and collect response
            Flowable<Event> events = runner.runAsync(session.userId(), session.id(), userMsg);

            // Collect all event content into a single response
            AtomicReference<StringBuilder> response = new AtomicReference<>(new StringBuilder());
            events.blockingForEach(event -> {
                String content = event.stringifyContent();
                if (content != null && !content.trim().isEmpty()) {
                    response.get().append(content).append("\n");
                }
            });

            return response.get().toString().trim();
        } catch (Exception e) {
            log.error("Error analyzing feature metrics", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get raw metrics for a feature without LLM analysis.
     * Used for simple metric retrieval.
     *
     * @param featureId The feature identifier
     * @return JSON string with metrics
     */
    public String getMetrics(String featureId) {
        log.debug("Fetching raw metrics for feature: {}", featureId);

        try {
            var metrics = verticaTool.getMetricsForFeature(featureId);
            return String.format("""
                    {
                      "featureId": "%s",
                      "purchaseCount": %d,
                      "loginCount": %d,
                      "averageSessionDuration": %.2f
                    }
                    """,
                    metrics.getFeatureId(),
                    metrics.getPurchaseCount(),
                    metrics.getLoginCount(),
                    metrics.getAverageSessionDuration()
            );
        } catch (Exception e) {
            log.error("Error fetching metrics for feature: {}", featureId, e);
            throw e;
        }
    }
}