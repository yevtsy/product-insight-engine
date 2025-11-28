package com.google.ai.insight.aggregator.service;

import com.google.ai.insight.aggregator.agents.FacebookScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.InstagramScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.RedditScoutLlmAgent;
import com.google.ai.insight.aggregator.config.FacebookProperties;
import com.google.ai.insight.aggregator.config.InstagramProperties;
import com.google.ai.insight.aggregator.config.RedditProperties;
import com.google.ai.insight.model.FeatureInsight;
import com.google.ai.insight.model.InternalMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Aggregator Service that coordinates multiple LLM agents to analyze feature feedback.
 * Uses Google ADK agents for Facebook, Instagram, and Reddit comment analysis.
 */
@Slf4j
@Service
public class AggregatorService {

    // Health Score Calculation Constants
    private static final double PURCHASE_WEIGHT = 0.3;
    private static final double PURCHASE_NORMALIZATION_FACTOR = 1000.0;
    private static final double LOGIN_WEIGHT = 0.3;
    private static final double LOGIN_NORMALIZATION_FACTOR = 5000.0;
    private static final double SESSION_DURATION_WEIGHT = 0.2;
    private static final double SESSION_DURATION_NORMALIZATION_FACTOR = 30.0;
    private static final double SENTIMENT_WEIGHT = 0.2;
    private static final double NEUTRAL_SENTIMENT_SCORE = 0.1;

    // Health Score Thresholds
    private static final double HEALTH_EXCELLENT_THRESHOLD = 0.8;
    private static final double HEALTH_GOOD_THRESHOLD = 0.6;
    private static final double HEALTH_MODERATE_THRESHOLD = 0.4;

    private final FacebookScoutLlmAgent facebookScoutAgent;
    private final InstagramScoutLlmAgent instagramScoutAgent;
    private final RedditScoutLlmAgent redditScoutAgent;
    private final AnalyticsAgentClient analyticsClient;
    private final FacebookProperties facebookProperties;
    private final InstagramProperties instagramProperties;
    private final RedditProperties redditProperties;
    private final Executor agentExecutor;

    public AggregatorService(
            FacebookScoutLlmAgent facebookScoutAgent,
            InstagramScoutLlmAgent instagramScoutAgent,
            RedditScoutLlmAgent redditScoutAgent,
            AnalyticsAgentClient analyticsClient,
            FacebookProperties facebookProperties,
            InstagramProperties instagramProperties,
            RedditProperties redditProperties,
            @Qualifier("agentExecutor") Executor agentExecutor) {
        this.facebookScoutAgent = facebookScoutAgent;
        this.instagramScoutAgent = instagramScoutAgent;
        this.redditScoutAgent = redditScoutAgent;
        this.analyticsClient = analyticsClient;
        this.facebookProperties = facebookProperties;
        this.instagramProperties = instagramProperties;
        this.redditProperties = redditProperties;
        this.agentExecutor = agentExecutor;
    }

    /**
     * Analyzes feature feedback by coordinating multiple LLM agents in parallel.
     * Uses configured thread pool and Reddit thread URL from application properties.
     *
     * @param featureId     Unique identifier for the feature
     * @param featureName   Human-readable name of the feature
     * @param keywords      Keywords to filter social media comments
     * @return FeatureInsight with aggregated analysis from all sources
     */
    public FeatureInsight analyzeFeature(String featureId, String featureName, List<String> keywords) {
        log.info("Analyzing feature: {} with keywords: {}", featureName, keywords);

        // Use configured Reddit thread URL if not provided
        String redditThreadUrl = redditProperties.getThreadUrl();

        // Fan-Out: Parallel execution of LLM agents using configured executor
        CompletableFuture<String> facebookFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return facebookScoutAgent.analyzeFeedback(
                                featureName,
                                keywords,
                                facebookProperties.getMaxComments()
                        );
                    } catch (Exception e) {
                        log.error("Facebook agent error", e);
                        return "Facebook analysis unavailable: " + e.getMessage();
                    }
                }, agentExecutor);

        CompletableFuture<String> instagramFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return instagramScoutAgent.analyzeFeedback(
                                featureName,
                                keywords,
                                instagramProperties.getMaxComments()
                        );
                    } catch (Exception e) {
                        log.error("Instagram agent error", e);
                        return "Instagram analysis unavailable: " + e.getMessage();
                    }
                }, agentExecutor);

        CompletableFuture<String> redditFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        if (redditThreadUrl != null && !redditThreadUrl.isEmpty()) {
                            return redditScoutAgent.analyzeFeedback(
                                    featureName,
                                    redditThreadUrl,
                                    keywords,
                                    redditProperties.getMaxComments()
                            );
                        } else {
                            return "Reddit analysis skipped: No thread URL configured";
                        }
                    } catch (Exception e) {
                        log.error("Reddit agent error", e);
                        return "Reddit analysis unavailable: " + e.getMessage();
                    }
                }, agentExecutor);

        CompletableFuture<InternalMetrics> metricsFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return analyticsClient.getMetrics(featureId);
                    } catch (Exception e) {
                        log.error("Analytics client error", e);
                        return new InternalMetrics(featureId, 0, 0, 0.0);
                    }
                }, agentExecutor);

        // Fan-In: Wait for all agents to complete
        CompletableFuture.allOf(facebookFuture, instagramFuture, redditFuture, metricsFuture).join();

        String facebookAnalysis = facebookFuture.join();
        String instagramAnalysis = instagramFuture.join();
        String redditAnalysis = redditFuture.join();
        InternalMetrics metrics = metricsFuture.join();

        // Build social summary from all agent responses
        String socialSummary = String.format("""
                === Social Media Analysis ===

                FACEBOOK:
                %s

                INSTAGRAM:
                %s

                REDDIT:
                %s
                """,
                facebookAnalysis,
                instagramAnalysis,
                redditAnalysis
        );

        String metricsSummary = String.format("Purchases: %d, Logins: %d, Avg Session Duration: %.2f min",
                metrics.getPurchaseCount(),
                metrics.getLoginCount(),
                metrics.getAverageSessionDuration());

        // Create final insight with aggregated analysis
        FeatureInsight insight = new FeatureInsight();
        insight.setFeatureId(featureId);
        insight.setSocialSummary(socialSummary);
        insight.setMetricsSummary(metricsSummary);

        // Calculate health score based on metrics
        // Simple scoring: normalize metrics and create a composite score
        double healthScore = calculateHealthScore(metrics, facebookAnalysis, instagramAnalysis, redditAnalysis);
        insight.setOverallHealthScore(healthScore);

        // Generate recommendations based on analysis
        String recommendations = generateRecommendations(socialSummary, metricsSummary, healthScore);
        insight.setRecommendations(List.of(recommendations));

        return insight;
    }

    /**
     * Backward compatibility method - converts single keyword to list.
     */
    public FeatureInsight analyzeFeature(String featureId, String keyword) {
        return analyzeFeature(featureId, keyword,
                keyword != null ? List.of(keyword) : List.of());
    }

    /**
     * Calculate health score based on metrics and sentiment analysis.
     * Score ranges from 0.0 (poor) to 1.0 (excellent).
     *
     * Scoring algorithm:
     * - Purchase count: up to 0.3 points (normalized by 1000 purchases)
     * - Login count: up to 0.3 points (normalized by 5000 logins)
     * - Session duration: up to 0.2 points (normalized by 30 minutes)
     * - Sentiment analysis: up to 0.2 points
     */
    private double calculateHealthScore(InternalMetrics metrics, String facebookAnalysis,
                                       String instagramAnalysis, String redditAnalysis) {
        // Base score from metrics (normalized)
        double metricsScore = 0.0;

        // Purchase count contribution
        if (metrics.getPurchaseCount() > 0) {
            metricsScore += Math.min(PURCHASE_WEIGHT,
                    metrics.getPurchaseCount() / PURCHASE_NORMALIZATION_FACTOR * PURCHASE_WEIGHT);
        }

        // Login count contribution
        if (metrics.getLoginCount() > 0) {
            metricsScore += Math.min(LOGIN_WEIGHT,
                    metrics.getLoginCount() / LOGIN_NORMALIZATION_FACTOR * LOGIN_WEIGHT);
        }

        // Session duration contribution
        if (metrics.getAverageSessionDuration() > 0) {
            metricsScore += Math.min(SESSION_DURATION_WEIGHT,
                    metrics.getAverageSessionDuration() / SESSION_DURATION_NORMALIZATION_FACTOR * SESSION_DURATION_WEIGHT);
        }

        // Sentiment score from social analysis
        double sentimentScore = calculateSentimentScore(facebookAnalysis, instagramAnalysis, redditAnalysis);

        double totalScore = metricsScore + sentimentScore;

        log.debug("Health Score Calculation - Metrics: {}, Sentiment: {}, Total: {}",
                  metricsScore, sentimentScore, totalScore);

        return Math.min(1.0, totalScore);
    }

    /**
     * Calculate sentiment score by analyzing keywords in social media analysis.
     */
    private double calculateSentimentScore(String... analyses) {
        int positiveCount = 0;
        int negativeCount = 0;

        for (String analysis : analyses) {
            if (analysis == null) continue;

            String lowerAnalysis = analysis.toLowerCase();

            // Count positive indicators
            if (lowerAnalysis.contains("positive")) positiveCount++;
            if (lowerAnalysis.contains("love")) positiveCount++;
            if (lowerAnalysis.contains("appreciate")) positiveCount++;
            if (lowerAnalysis.contains("excellent")) positiveCount++;
            if (lowerAnalysis.contains("great")) positiveCount++;

            // Count negative indicators
            if (lowerAnalysis.contains("negative")) negativeCount++;
            if (lowerAnalysis.contains("concern")) negativeCount++;
            if (lowerAnalysis.contains("issue")) negativeCount++;
            if (lowerAnalysis.contains("problem")) negativeCount++;
            if (lowerAnalysis.contains("bug")) negativeCount++;
        }

        // Calculate sentiment score using sentiment weight constant
        if (positiveCount + negativeCount == 0) {
            return NEUTRAL_SENTIMENT_SCORE;
        }

        double sentimentRatio = (double) positiveCount / (positiveCount + negativeCount);
        return sentimentRatio * SENTIMENT_WEIGHT;
    }

    /**
     * Generate recommendations based on analysis and health score.
     */
    private String generateRecommendations(String socialSummary, String metricsSummary, double healthScore) {
        StringBuilder recommendations = new StringBuilder();

        recommendations.append("=== Recommendations ===\n\n");

        // Overall assessment using threshold constants
        if (healthScore >= HEALTH_EXCELLENT_THRESHOLD) {
            recommendations.append("OVERALL: Feature is performing excellently. ");
            recommendations.append("Focus on maintaining current quality and expanding reach.\n\n");
        } else if (healthScore >= HEALTH_GOOD_THRESHOLD) {
            recommendations.append("OVERALL: Feature is performing well with room for improvement. ");
            recommendations.append("Address identified concerns while building on strengths.\n\n");
        } else if (healthScore >= HEALTH_MODERATE_THRESHOLD) {
            recommendations.append("OVERALL: Feature shows moderate performance. ");
            recommendations.append("Prioritize addressing key issues and improving user experience.\n\n");
        } else {
            recommendations.append("OVERALL: Feature requires immediate attention. ");
            recommendations.append("Focus on critical issues and user concerns.\n\n");
        }

        // Analyze social feedback for specific recommendations
        String lowerSocial = socialSummary.toLowerCase();

        if (lowerSocial.contains("performance") || lowerSocial.contains("slow")) {
            recommendations.append("- Address performance concerns mentioned in social feedback\n");
        }

        if (lowerSocial.contains("bug") || lowerSocial.contains("crash") || lowerSocial.contains("error")) {
            recommendations.append("- Prioritize bug fixes and stability improvements\n");
        }

        if (lowerSocial.contains("documentation") || lowerSocial.contains("unclear")) {
            recommendations.append("- Improve documentation and user guidance\n");
        }

        if (lowerSocial.contains("feature request") || lowerSocial.contains("would like")) {
            recommendations.append("- Consider implementing frequently requested features\n");
        }

        // Marketing recommendations based on platform engagement
        if (lowerSocial.contains("facebook") && lowerSocial.contains("positive")) {
            recommendations.append("- Increase marketing spend on Facebook based on positive engagement\n");
        }

        if (lowerSocial.contains("instagram") && lowerSocial.contains("positive")) {
            recommendations.append("- Leverage Instagram's positive sentiment for promotional campaigns\n");
        }

        if (lowerSocial.contains("reddit") && lowerSocial.contains("active")) {
            recommendations.append("- Engage with Reddit community to build advocacy\n");
        }

        // Metrics-based recommendations
        if (metricsSummary.contains("0") || metricsSummary.contains("Purchases: 0")) {
            recommendations.append("- Focus on conversion optimization to drive purchases\n");
        }

        recommendations.append("\nConsolidated Social Summary:\n").append(socialSummary);
        recommendations.append("\n\nInternal Metrics: ").append(metricsSummary);

        return recommendations.toString();
    }
}