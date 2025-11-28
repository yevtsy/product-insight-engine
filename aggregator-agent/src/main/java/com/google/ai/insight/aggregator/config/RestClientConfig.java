package com.google.ai.insight.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST client beans.
 */
@Configuration
public class RestClientConfig {

    private final RestClientProperties properties;

    public RestClientConfig(RestClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a RestTemplate bean with configurable timeout settings.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    /**
     * Configuration properties for REST client timeouts.
     */
    @Data
    @Component
    @ConfigurationProperties(prefix = "rest-client")
    public static class RestClientProperties {
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;
    }
}