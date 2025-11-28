package com.google.ai.insight.aggregator;

import com.google.ai.insight.model.FeatureInsight;
import com.google.ai.insight.model.InternalMetrics;
import com.google.ai.insight.aggregator.service.AnalyticsAgentClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Disabled("Requires Docker to be running")
class AggregatorIntegrationTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:10.6");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private AnalyticsAgentClient analyticsClient;

    @Test
    void testAnalyzeFeature() {
        // Mock Analytics Agent response
        InternalMetrics mockMetrics = new InternalMetrics("feature-123", 100, 500, 12.5);
        when(analyticsClient.getMetrics(anyString())).thenReturn(mockMetrics);

        // Call the API
        FeatureInsight insight = restTemplate.getForObject("/analyze?featureId=feature-123&keyword=cool",
                FeatureInsight.class);

        // Verify response
        assertThat(insight).isNotNull();
        assertThat(insight.getFeatureId()).isEqualTo("feature-123");
        assertThat(insight.getMetricsSummary()).contains("Purchases: 100");
        assertThat(insight.getSocialSummary()).contains("Analyzed");
        assertThat(insight.getRecommendations()).isNotEmpty();
    }
}
