package com.google.ai.insight.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "reddit")
public class RedditProperties {
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private String userAgent;
    private String apiUrl;
    private String threadUrl;
    private int maxComments = 1000;
    private String defaultKeywords;
}