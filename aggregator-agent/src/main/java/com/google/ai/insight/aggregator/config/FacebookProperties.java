package com.google.ai.insight.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "facebook")
public class FacebookProperties {
    private String pageId;
    private String accessToken;
    private String apiVersion;
    private String graphApiUrl;
    private int maxComments = 1000;
    private String defaultKeywords;
}