package com.google.ai.insight.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "instagram")
public class InstagramProperties {
    private String userId;
    private String accessToken;
    private String apiVersion;
    private String graphApiUrl;
    private int maxComments = 1000;
    private String defaultKeywords;
}