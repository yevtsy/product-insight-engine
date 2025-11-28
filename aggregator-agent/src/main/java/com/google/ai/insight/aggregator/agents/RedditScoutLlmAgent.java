package com.google.ai.insight.aggregator.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.ai.insight.aggregator.tools.RedditScoutTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM Agent for Reddit comment analysis using Google ADK.
 * This agent uses Gemini 2.5 Flash model to fetch and analyze Reddit comments from specific threads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedditScoutLlmAgent {

    private final RedditScoutTool redditScoutTool;
    private BaseAgent agent;
    private InMemoryRunner runner;

    @PostConstruct
    public void init() {
        log.info("Initializing Reddit Scout LLM Agent");

        this.agent = LlmAgent.builder()
                .name("reddit-scout")
                .description("Agent for fetching and analyzing comments from specific Reddit threads")
                .instruction("""
                        You are a Reddit comment analysis agent. Your role is to:
                        1. Fetch comments from specific Reddit threads using the fetchComments tool
                        2. Analyze the comments to understand user sentiment and feedback
                        3. Identify key themes, positive highlights, and negative concerns
                        4. Provide a comprehensive summary of user feedback

                        When asked to analyze feedback for a feature:
                        - Use fetchComments(featureName, threadUrl, keywords, maxComments) to get relevant comments
                        - IMPORTANT: Reddit requires a thread URL to fetch comments
                        - The tool will automatically filter by feature name and keywords
                        - Analyze the sentiment of each comment
                        - Extract insights and patterns from the comments
                        - Summarize findings in a structured format with:
                          * SUMMARY: Overall sentiment and key findings (2-3 sentences)
                          * KEY_THEMES: Main topics discussed (3-5 bullet points)
                          * POSITIVE_HIGHLIGHTS: What users love and appreciate
                          * NEGATIVE_HIGHLIGHTS: Concerns, issues, and improvement requests
                        """)
                .model("gemini-2.5-flash")
                .tools(FunctionTool.create(redditScoutTool, "fetchComments"))
                .build();

        this.runner = new InMemoryRunner(agent);

        log.info("Reddit Scout LLM Agent initialized successfully");
    }

    public BaseAgent getAgent() {
        return agent;
    }

    /**
     * Analyzes feedback for a feature by fetching and processing Reddit comments.
     *
     * @param featureName The name of the feature to analyze
     * @param threadUrl   The Reddit thread URL to fetch comments from (required)
     * @param keywords    Optional keywords to filter comments
     * @param maxComments Maximum number of comments to fetch
     * @return Analysis result as a string
     */
    public String analyzeFeedback(String featureName, String threadUrl, java.util.List<String> keywords, int maxComments) {
        log.info("Analyzing Reddit feedback for feature: {} from thread: {} with keywords: {}",
                featureName, threadUrl, keywords);

        if (threadUrl == null || threadUrl.isEmpty()) {
            return "Error: Reddit thread URL is required";
        }

        String prompt = String.format("""
                Analyze user feedback about the feature: "%s" from the Reddit thread: %s

                %s

                Fetch up to %d comments and provide a comprehensive analysis with:
                1. SUMMARY: Overall sentiment and key findings
                2. KEY_THEMES: Main topics discussed
                3. POSITIVE_HIGHLIGHTS: What users appreciate
                4. NEGATIVE_HIGHLIGHTS: Concerns and improvement requests
                """,
                featureName,
                threadUrl,
                keywords != null && !keywords.isEmpty() ?
                        "Focus on comments containing these keywords: " + String.join(", ", keywords) : "",
                maxComments > 0 ? maxComments : 1000
        );

        try {
            // Create session for this analysis
            Session session = runner.sessionService()
                    .createSession("reddit-scout-app", "aggregator")
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
            log.error("Error analyzing Reddit feedback", e);
            return "Error: " + e.getMessage();
        }
    }
}