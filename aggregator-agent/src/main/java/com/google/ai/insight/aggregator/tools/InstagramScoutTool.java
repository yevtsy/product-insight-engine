package com.google.ai.insight.aggregator.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.ai.insight.aggregator.config.InstagramProperties;
import io.github.bucket4j.Bucket;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Instagram Scout Tool - fetches comments from Instagram user media.
 * This is a simple tool for Google ADK agents to use.
 */
@Slf4j
@Component
public class InstagramScoutTool {

    private final InstagramProperties instagramProperties;
    private final WebClient.Builder webClientBuilder;
    private final Bucket rateLimiter;

    public InstagramScoutTool(
            InstagramProperties instagramProperties,
            WebClient.Builder webClientBuilder,
            @Qualifier("instagramRateLimiter") Bucket rateLimiter) {
        this.instagramProperties = instagramProperties;
        this.webClientBuilder = webClientBuilder;
        this.rateLimiter = rateLimiter;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Media {
        private String id;
        private String caption;
        @JsonProperty("timestamp")
        private String timestamp;
        @JsonProperty("media_type")
        private String mediaType;
        private Comments comments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Comments {
        private List<Comment> data;
        private Paging paging;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Comment {
        private String id;
        private String text;
        @JsonProperty("timestamp")
        private String timestamp;
        private From from;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class From {
        private String id;
        private String username;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaResponse {
        private List<Media> data;
        private Paging paging;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Paging {
        private String next;
    }

    /**
     * Fetches comments from Instagram media based on keywords.
     *
     * @param featureName Feature name to search for (used as primary keyword)
     * @param keywords Additional keywords to filter comments
     * @param maxComments Maximum number of comments to fetch
     * @return List of comments matching the criteria
     */
    public List<Comment> fetchComments(String featureName, List<String> keywords, int maxComments) {
        log.info("Fetching Instagram comments for feature '{}' with keywords: {}", featureName, keywords);

        // Add feature name to keywords
        List<String> allKeywords = new ArrayList<>();
        if (featureName != null && !featureName.isEmpty()) {
            allKeywords.add(featureName);
        }
        if (keywords != null) {
            allKeywords.addAll(keywords);
        }

        List<Comment> allComments = new ArrayList<>();
        String url = buildMediaUrl();

        try {
            WebClient webClient = webClientBuilder.build();

            // Apply rate limiting - wait if necessary
            rateLimiter.asBlocking().consume(1);

            // Fetch media posts with comments
            MediaResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(MediaResponse.class)
                    .block();

            if (response != null && response.getData() != null) {
                for (Media media : response.getData()) {
                    if (media.getComments() != null && media.getComments().getData() != null) {
                        for (Comment comment : media.getComments().getData()) {
                            // Filter by keywords if provided
                            if (allKeywords.isEmpty() || matchesKeywords(comment.getText(), allKeywords)) {
                                allComments.add(comment);
                                if (allComments.size() >= maxComments) {
                                    return allComments;
                                }
                            }
                        }
                    }

                    // If media has more comments, fetch them with pagination
                    if (media.getComments() != null && media.getComments().getPaging() != null
                            && media.getComments().getPaging().getNext() != null) {
                        allComments.addAll(fetchPaginatedComments(
                                media.getComments().getPaging().getNext(),
                                allKeywords,
                                maxComments - allComments.size(),
                                webClient));

                        if (allComments.size() >= maxComments) {
                            return allComments;
                        }
                    }
                }
            }

            log.info("Successfully fetched {} Instagram comments", allComments.size());
        } catch (Exception e) {
            log.error("Error fetching Instagram comments", e);
        }

        return allComments;
    }

    private List<Comment> fetchPaginatedComments(String nextUrl, List<String> keywords, int remainingCount, WebClient webClient) {
        List<Comment> comments = new ArrayList<>();

        try {
            // Apply rate limiting - wait if necessary
            rateLimiter.asBlocking().consume(1);

            Comments response = webClient.get()
                    .uri(nextUrl)
                    .retrieve()
                    .bodyToMono(Comments.class)
                    .block();

            if (response != null && response.getData() != null) {
                for (Comment comment : response.getData()) {
                    if (keywords.isEmpty() || matchesKeywords(comment.getText(), keywords)) {
                        comments.add(comment);
                        if (comments.size() >= remainingCount) {
                            return comments;
                        }
                    }
                }

                // Continue pagination if needed
                if (response.getPaging() != null && response.getPaging().getNext() != null
                        && comments.size() < remainingCount) {
                    comments.addAll(fetchPaginatedComments(
                            response.getPaging().getNext(),
                            keywords,
                            remainingCount - comments.size(),
                            webClient));
                }
            }
        } catch (Exception e) {
            log.error("Error fetching paginated comments", e);
        }

        return comments;
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        return keywords.stream()
                .anyMatch(keyword -> lowerText.contains(keyword.toLowerCase()));
    }

    private String buildMediaUrl() {
        return String.format("%s/%s/media?fields=id,caption,timestamp,media_type,comments{id,text,timestamp,from}&limit=100&access_token=%s",
                instagramProperties.getGraphApiUrl(),
                instagramProperties.getUserId(),
                instagramProperties.getAccessToken());
    }

    public Instant parseTimestamp(String timestamp) {
        try {
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", timestamp);
            return Instant.now();
        }
    }
}