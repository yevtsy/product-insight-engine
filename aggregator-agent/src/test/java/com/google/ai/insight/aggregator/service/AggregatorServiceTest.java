package com.google.ai.insight.aggregator.service;

import com.google.ai.insight.aggregator.agents.FacebookScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.InstagramScoutLlmAgent;
import com.google.ai.insight.aggregator.agents.RedditScoutLlmAgent;
import com.google.ai.insight.aggregator.config.FacebookProperties;
import com.google.ai.insight.aggregator.config.InstagramProperties;
import com.google.ai.insight.aggregator.config.RedditProperties;
import com.google.ai.insight.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregatorServiceTest {

    @Mock
    private FacebookScoutLlmAgent facebookScoutAgent;

    @Mock
    private InstagramScoutLlmAgent instagramScoutAgent;

    @Mock
    private RedditScoutLlmAgent redditScoutAgent;

    @Mock
    private AnalyticsAgentClient analyticsClient;

    @Mock
    private FacebookProperties facebookProperties;

    @Mock
    private InstagramProperties instagramProperties;

    @Mock
    private RedditProperties redditProperties;

    @Mock
    private Executor agentExecutor;

    private AggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        // Setup property mocks
        when(facebookProperties.getMaxComments()).thenReturn(1000);
        when(instagramProperties.getMaxComments()).thenReturn(1000);
        when(redditProperties.getMaxComments()).thenReturn(1000);
        when(redditProperties.getThreadUrl()).thenReturn("https://reddit.com/r/test/comments/123");

        // Setup LLM agent mocks - they now return String analysis directly
        when(facebookScoutAgent.analyzeFeedback(anyString(), anyList(), anyInt()))
                .thenReturn("Facebook analysis: Users love the feature");
        when(instagramScoutAgent.analyzeFeedback(anyString(), anyList(), anyInt()))
                .thenReturn("Instagram analysis: Great engagement");
        when(redditScoutAgent.analyzeFeedback(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("Reddit analysis: Mixed feedback");

        // Setup analytics client mock
        when(analyticsClient.getMetrics(anyString()))
                .thenReturn(createMockMetrics());

        // Use direct executor for testing (runs tasks synchronously)
        Executor directExecutor = Runnable::run;

        // Create service with mocks
        aggregatorService = new AggregatorService(
                facebookScoutAgent,
                instagramScoutAgent,
                redditScoutAgent,
                analyticsClient,
                facebookProperties,
                instagramProperties,
                redditProperties,
                directExecutor
        );
    }

    @Test
    void testAnalyzeFeature_WithKeywords() {
        String featureId = "feature-123";
        String featureName = "Shopping Cart";
        List<String> keywords = Arrays.asList("cart", "checkout");

        FeatureInsight insight = aggregatorService.analyzeFeature(featureId, featureName, keywords);

        assertNotNull(insight);
        assertEquals(featureId, insight.getFeatureId());
        assertNotNull(insight.getSocialSummary());
        assertTrue(insight.getSocialSummary().contains("FACEBOOK"));
        assertTrue(insight.getSocialSummary().contains("INSTAGRAM"));
        assertTrue(insight.getSocialSummary().contains("REDDIT"));
        assertNotNull(insight.getMetricsSummary());
        assertTrue(insight.getOverallHealthScore() > 0);
        assertFalse(insight.getRecommendations().isEmpty());

        verify(facebookScoutAgent).analyzeFeedback(eq(featureName), eq(keywords), eq(1000));
        verify(instagramScoutAgent).analyzeFeedback(eq(featureName), eq(keywords), eq(1000));
        verify(redditScoutAgent).analyzeFeedback(eq(featureName), anyString(), eq(keywords), eq(1000));
        verify(analyticsClient).getMetrics(featureId);
    }

    @Test
    void testAnalyzeFeature_BackwardCompatibility() {
        String featureId = "feature-123";
        String keyword = "cart";

        FeatureInsight insight = aggregatorService.analyzeFeature(featureId, keyword);

        assertNotNull(insight);
        assertEquals(featureId, insight.getFeatureId());

        verify(facebookScoutAgent).analyzeFeedback(eq(keyword), eq(List.of(keyword)), eq(1000));
        verify(instagramScoutAgent).analyzeFeedback(eq(keyword), eq(List.of(keyword)), eq(1000));
        verify(redditScoutAgent).analyzeFeedback(eq(keyword), anyString(), eq(List.of(keyword)), eq(1000));
    }

    @Test
    void testAnalyzeFeature_WithNullKeyword() {
        String featureId = "feature-123";

        FeatureInsight insight = aggregatorService.analyzeFeature(featureId, (String) null);

        assertNotNull(insight);
        assertEquals(featureId, insight.getFeatureId());
    }

    @Test
    void testAnalyzeFeature_IncludesAllSourceSummaries() {
        String featureId = "feature-123";
        String featureName = "Shopping Cart";
        List<String> keywords = Arrays.asList("cart");

        FeatureInsight insight = aggregatorService.analyzeFeature(featureId, featureName, keywords);

        assertTrue(insight.getSocialSummary().contains("Facebook analysis"));
        assertTrue(insight.getSocialSummary().contains("Instagram analysis"));
        assertTrue(insight.getSocialSummary().contains("Reddit analysis"));
    }

    @Test
    void testAnalyzeFeature_MetricsSummaryFormat() {
        String featureId = "feature-123";
        String featureName = "Shopping Cart";
        List<String> keywords = Arrays.asList("cart");

        FeatureInsight insight = aggregatorService.analyzeFeature(featureId, featureName, keywords);

        assertTrue(insight.getMetricsSummary().contains("Purchases"));
        assertTrue(insight.getMetricsSummary().contains("Logins"));
        assertTrue(insight.getMetricsSummary().contains("100"));
        assertTrue(insight.getMetricsSummary().contains("50"));
    }

    @Test
    void testAnalyzeFeature_CallsAllAgentsInParallel() {
        String featureId = "feature-123";
        String featureName = "Shopping Cart";
        List<String> keywords = Arrays.asList("cart");

        aggregatorService.analyzeFeature(featureId, featureName, keywords);

        // Verify all agents are called
        verify(facebookScoutAgent).analyzeFeedback(featureName, keywords, 1000);
        verify(instagramScoutAgent).analyzeFeedback(featureName, keywords, 1000);
        verify(redditScoutAgent).analyzeFeedback(eq(featureName), anyString(), eq(keywords), eq(1000));
        verify(analyticsClient).getMetrics(featureId);
    }

    private InternalMetrics createMockMetrics() {
        InternalMetrics metrics = new InternalMetrics();
        metrics.setPurchaseCount(100);
        metrics.setLoginCount(50);
        return metrics;
    }
}