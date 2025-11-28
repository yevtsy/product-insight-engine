package com.google.ai.insight.aggregator.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for rate limiting across different APIs.
 * Uses Token Bucket algorithm via Bucket4j library.
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    // Facebook Rate Limits
    @Value("${rate-limit.facebook.requests-per-minute:200}")
    private int facebookRpm;

    @Value("${rate-limit.facebook.burst-capacity:10}")
    private int facebookBurst;

    // Instagram Rate Limits
    @Value("${rate-limit.instagram.requests-per-minute:200}")
    private int instagramRpm;

    @Value("${rate-limit.instagram.burst-capacity:10}")
    private int instagramBurst;

    // Reddit Rate Limits
    @Value("${rate-limit.reddit.requests-per-minute:60}")
    private int redditRpm;

    @Value("${rate-limit.reddit.burst-capacity:5}")
    private int redditBurst;

    // Gemini Rate Limits
    @Value("${rate-limit.gemini.requests-per-minute:60}")
    private int geminiRpm;

    @Value("${rate-limit.gemini.burst-capacity:10}")
    private int geminiBurst;

    @Bean(name = "facebookRateLimiter")
    public Bucket facebookRateLimiter() {
        log.info("Creating Facebook rate limiter: {} req/min, burst: {}", facebookRpm, facebookBurst);

        Bandwidth limit = Bandwidth.classic(facebookRpm, Refill.intervally(facebookRpm, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean(name = "instagramRateLimiter")
    public Bucket instagramRateLimiter() {
        log.info("Creating Instagram rate limiter: {} req/min, burst: {}", instagramRpm, instagramBurst);

        Bandwidth limit = Bandwidth.classic(instagramRpm, Refill.intervally(instagramRpm, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean(name = "redditRateLimiter")
    public Bucket redditRateLimiter() {
        log.info("Creating Reddit rate limiter: {} req/min, burst: {}", redditRpm, redditBurst);

        Bandwidth limit = Bandwidth.classic(redditRpm, Refill.intervally(redditRpm, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean(name = "geminiRateLimiter")
    public Bucket geminiRateLimiter() {
        log.info("Creating Gemini rate limiter: {} req/min, burst: {}", geminiRpm, geminiBurst);

        Bandwidth limit = Bandwidth.classic(geminiRpm, Refill.intervally(geminiRpm, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}