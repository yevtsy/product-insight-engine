package com.google.ai.insight.aggregator.cli;

import com.google.ai.insight.aggregator.agents.FacebookScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.InstagramScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.RedditScoutLlmAgent;
import com.google.ai.insight.aggregator.service.AggregatorService;
import com.google.ai.insight.aggregator.service.AnalyticsAgentClient;
import com.google.ai.insight.model.FeatureInsight;
import com.google.ai.insight.model.InternalMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Arrays;
import java.util.List;

/**
 * Command-line interface for the Aggregator Agent.
 * Provides interactive commands to analyze features using LLM agents.
 */
@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class AgentCliCommands {

    private final AggregatorService aggregatorService;
    private final FacebookScoutLlmAgent facebookScoutAgent;
    private final InstagramScoutLlmAgent instagramScoutAgent;
    private final RedditScoutLlmAgent redditScoutAgent;
    private final AnalyticsAgentClient analyticsAgentClient;

    /**
     * Analyze a feature by aggregating insights from all sources.
     */
    @ShellMethod(key = "analyze", value = "Analyze a feature using all agents (social + analytics)")
    public String analyze(
            @ShellOption(value = "--feature-id", help = "Feature ID for metrics") String featureId,
            @ShellOption(value = "--feature-name", help = "Feature name for social analysis") String featureName,
            @ShellOption(value = "--keywords", defaultValue = "", help = "Comma-separated keywords") String keywords) {

        log.info("CLI: Analyzing feature {} ({})", featureName, featureId);

        List<String> keywordList = keywords.isEmpty()
                ? List.of()
                : Arrays.asList(keywords.split(","));

        try {
            FeatureInsight insight = aggregatorService.analyzeFeature(featureId, featureName, keywordList);

            return formatFeatureInsight(insight);
        } catch (Exception e) {
            log.error("CLI: Error analyzing feature", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Analyze Facebook comments only.
     */
    @ShellMethod(key = "facebook", value = "Analyze Facebook comments for a feature")
    public String analyzeFacebook(
            @ShellOption(value = "--feature", help = "Feature name") String featureName,
            @ShellOption(value = "--keywords", defaultValue = "", help = "Comma-separated keywords") String keywords,
            @ShellOption(value = "--max-comments", defaultValue = "100", help = "Maximum comments") int maxComments) {

        log.info("CLI: Analyzing Facebook for feature: {}", featureName);

        List<String> keywordList = keywords.isEmpty()
                ? List.of()
                : Arrays.asList(keywords.split(","));

        try {
            String analysis = facebookScoutAgent.analyzeFeedback(featureName, keywordList, maxComments);
            return formatSocialAnalysis("FACEBOOK", featureName, analysis);
        } catch (Exception e) {
            log.error("CLI: Error analyzing Facebook", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Analyze Instagram comments only.
     */
    @ShellMethod(key = "instagram", value = "Analyze Instagram comments for a feature")
    public String analyzeInstagram(
            @ShellOption(value = "--feature", help = "Feature name") String featureName,
            @ShellOption(value = "--keywords", defaultValue = "", help = "Comma-separated keywords") String keywords,
            @ShellOption(value = "--max-comments", defaultValue = "100", help = "Maximum comments") int maxComments) {

        log.info("CLI: Analyzing Instagram for feature: {}", featureName);

        List<String> keywordList = keywords.isEmpty()
                ? List.of()
                : Arrays.asList(keywords.split(","));

        try {
            String analysis = instagramScoutAgent.analyzeFeedback(featureName, keywordList, maxComments);
            return formatSocialAnalysis("INSTAGRAM", featureName, analysis);
        } catch (Exception e) {
            log.error("CLI: Error analyzing Instagram", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Analyze Reddit comments only.
     */
    @ShellMethod(key = "reddit", value = "Analyze Reddit comments for a feature")
    public String analyzeReddit(
            @ShellOption(value = "--feature", help = "Feature name") String featureName,
            @ShellOption(value = "--thread-url", help = "Reddit thread URL") String threadUrl,
            @ShellOption(value = "--keywords", defaultValue = "", help = "Comma-separated keywords") String keywords,
            @ShellOption(value = "--max-comments", defaultValue = "100", help = "Maximum comments") int maxComments) {

        log.info("CLI: Analyzing Reddit for feature: {}", featureName);

        List<String> keywordList = keywords.isEmpty()
                ? List.of()
                : Arrays.asList(keywords.split(","));

        try {
            String analysis = redditScoutAgent.analyzeFeedback(featureName, threadUrl, keywordList, maxComments);
            return formatSocialAnalysis("REDDIT", featureName, analysis);
        } catch (Exception e) {
            log.error("CLI: Error analyzing Reddit", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get internal metrics for a feature.
     */
    @ShellMethod(key = "metrics", value = "Get internal metrics for a feature from Analytics Agent")
    public String getMetrics(
            @ShellOption(value = "--feature-id", help = "Feature ID") String featureId) {

        log.info("CLI: Fetching metrics for feature: {}", featureId);

        try {
            InternalMetrics metrics = analyticsAgentClient.getMetrics(featureId);
            return formatMetrics(featureId, metrics);
        } catch (Exception e) {
            log.error("CLI: Error fetching metrics", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Display help information.
     */
    @ShellMethod(key = "agent-help", value = "Display help information for agent commands")
    public String agentHelp() {
        return """

                ╔═══════════════════════════════════════════════════════════════════════╗
                ║           PRODUCT INSIGHT ENGINE - AGENT CLI HELP                     ║
                ╚═══════════════════════════════════════════════════════════════════════╝

                MAIN COMMANDS:

                1. analyze - Full feature analysis (all agents)
                   Usage: analyze --feature-id <id> --feature-name <name> [--keywords <kw1,kw2>]
                   Example: analyze --feature-id "feat-123" --feature-name "Shopping Cart" --keywords "cart,checkout"

                   Executes all agents in parallel and provides:
                   - Social media analysis (Facebook, Instagram, Reddit)
                   - Internal metrics analysis
                   - Overall health score
                   - Actionable recommendations

                2. facebook - Analyze Facebook comments only
                   Usage: facebook --feature <name> [--keywords <kw1,kw2>] [--max-comments <n>]
                   Example: facebook --feature "Shopping Cart" --keywords "cart,buy" --max-comments 50

                3. instagram - Analyze Instagram comments only
                   Usage: instagram --feature <name> [--keywords <kw1,kw2>] [--max-comments <n>]
                   Example: instagram --feature "Shopping Cart" --keywords "cart" --max-comments 50

                4. reddit - Analyze Reddit comments only
                   Usage: reddit --feature <name> --thread-url <url> [--keywords <kw1,kw2>] [--max-comments <n>]
                   Example: reddit --feature "Cart" --thread-url "https://reddit.com/r/test/comments/abc123"

                5. metrics - Get internal analytics metrics
                   Usage: metrics --feature-id <id>
                   Example: metrics --feature-id "feat-123"

                6. agent-help - Display this help message
                   Usage: agent-help

                AGENT ARCHITECTURE:

                - FacebookScoutLlmAgent: Analyzes Facebook page comments using Gemini 2.5 Flash
                - InstagramScoutLlmAgent: Analyzes Instagram media comments using Gemini 2.5 Flash
                - RedditScoutLlmAgent: Analyzes Reddit thread comments using Gemini 2.5 Flash
                - AnalyticsLlmAgent: Analyzes internal metrics via A2A communication

                Each agent uses Google ADK with dedicated tools for data fetching.

                TIPS:

                - Use quotes for multi-word feature names: --feature-name "Shopping Cart"
                - Keywords are optional but improve filtering
                - Default max-comments is 100 per platform
                - Reddit requires thread URL
                - Full analysis runs agents in parallel for speed

                For more information, see architecture_summary.md

                ═══════════════════════════════════════════════════════════════════════
                """;
    }

    // Helper methods for formatting output

    private String formatFeatureInsight(FeatureInsight insight) {
        return String.format("""

                ╔═══════════════════════════════════════════════════════════════════════╗
                ║                      FEATURE ANALYSIS RESULTS                         ║
                ╚═══════════════════════════════════════════════════════════════════════╝

                Feature ID: %s
                Health Score: %.2f / 1.0

                ───────────────────────────────────────────────────────────────────────
                SOCIAL MEDIA ANALYSIS
                ───────────────────────────────────────────────────────────────────────

                %s

                ───────────────────────────────────────────────────────────────────────
                INTERNAL METRICS
                ───────────────────────────────────────────────────────────────────────

                %s

                ───────────────────────────────────────────────────────────────────────
                RECOMMENDATIONS
                ───────────────────────────────────────────────────────────────────────

                %s

                ═══════════════════════════════════════════════════════════════════════
                """,
                insight.getFeatureId(),
                insight.getOverallHealthScore(),
                insight.getSocialSummary(),
                insight.getMetricsSummary(),
                String.join("\n", insight.getRecommendations())
        );
    }

    private String formatSocialAnalysis(String platform, String featureName, String analysis) {
        return String.format("""

                ╔═══════════════════════════════════════════════════════════════════════╗
                ║                   %s ANALYSIS: %s
                ╚═══════════════════════════════════════════════════════════════════════╝

                %s

                ═══════════════════════════════════════════════════════════════════════
                """,
                platform,
                featureName,
                analysis
        );
    }

    private String formatMetrics(String featureId, InternalMetrics metrics) {
        return String.format("""

                ╔═══════════════════════════════════════════════════════════════════════╗
                ║                    INTERNAL METRICS: %s
                ╚═══════════════════════════════════════════════════════════════════════╝

                Purchase Count:           %d purchases (last 30 days)
                Login Count:              %d logins (last 30 days)
                Avg Session Duration:     %.2f minutes

                ═══════════════════════════════════════════════════════════════════════
                """,
                featureId,
                metrics.getPurchaseCount(),
                metrics.getLoginCount(),
                metrics.getAverageSessionDuration()
        );
    }
}