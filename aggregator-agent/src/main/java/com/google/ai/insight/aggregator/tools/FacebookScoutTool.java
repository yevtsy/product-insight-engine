package com.google.ai.insight.aggregator.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.ai.insight.aggregator.config.FacebookProperties;
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
 * Facebook Scout Tool - fetches comments from Facebook pages.
 * This is a simple tool for Google ADK agents to use.
 */
@Slf4j
@Component
public class FacebookScoutTool {

    private final FacebookProperties facebookProperties;
    private final WebClient.Builder webClientBuilder;
    private final Bucket rateLimiter;

    public FacebookScoutTool(
            FacebookProperties facebookProperties,
            WebClient.Builder webClientBuilder,
            @Qualifier("facebookRateLimiter") Bucket rateLimiter) {
        this.facebookProperties = facebookProperties;
        this.webClientBuilder = webClientBuilder;
        this.rateLimiter = rateLimiter;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Post {
        private String id;
        private String message;
        @JsonProperty("created_time")
        private String createdTime;
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
        private String message;
        @JsonProperty("created_time")
        private String createdTime;
        private From from;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class From {
        private String id;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostsResponse {
        private List<Post> data;
        private Paging paging;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Paging {
        private String next;
    }

    /**
     * Fetches comments from Facebook page posts based on keywords.
     *
     * @param featureName Feature name to search for (used as primary keyword)
     * @param keywords Additional keywords to filter comments
     * @param maxComments Maximum number of comments to fetch
     * @return List of comments matching the criteria
     */
    public List<Comment> fetchComments(String featureName, List<String> keywords, int maxComments) {
        log.info("Fetching Facebook comments for feature '{}' with keywords: {}", featureName, keywords);

        // Add feature name to keywords
        List<String> allKeywords = new ArrayList<>();
        if (featureName != null && !featureName.isEmpty()) {
            allKeywords.add(featureName);
        }
        if (keywords != null) {
            allKeywords.addAll(keywords);
        }

        List<Comment> allComments = new ArrayList<>();
        String url = buildPostsUrl();

        try {
            WebClient webClient = webClientBuilder.build();

            // Apply rate limiting - wait if necessary
            rateLimiter.asBlocking().consume(1);

            // Fetch posts with comments
            PostsResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(PostsResponse.class)
                    .block();

            if (response != null && response.getData() != null) {
                for (Post post : response.getData()) {
                    if (post.getComments() != null && post.getComments().getData() != null) {
                        for (Comment comment : post.getComments().getData()) {
                            // Filter by keywords if provided
                            if (allKeywords.isEmpty() || matchesKeywords(comment.getMessage(), allKeywords)) {
                                allComments.add(comment);
                                if (allComments.size() >= maxComments) {
                                    return allComments;
                                }
                            }
                        }
                    }

                    // If post has more comments, fetch them with pagination
                    if (post.getComments() != null && post.getComments().getPaging() != null
                            && post.getComments().getPaging().getNext() != null) {
                        allComments.addAll(fetchPaginatedComments(
                                post.getComments().getPaging().getNext(),
                                allKeywords,
                                maxComments - allComments.size(),
                                webClient));

                        if (allComments.size() >= maxComments) {
                            return allComments;
                        }
                    }
                }
            }

            log.info("Successfully fetched {} Facebook comments", allComments.size());
        } catch (Exception e) {
            log.error("Error fetching Facebook comments", e);
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
                    if (keywords.isEmpty() || matchesKeywords(comment.getMessage(), keywords)) {
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

    private boolean matchesKeywords(String message, List<String> keywords) {
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return keywords.stream()
                .anyMatch(keyword -> lowerMessage.contains(keyword.toLowerCase()));
    }

    private String buildPostsUrl() {
        return String.format("%s/%s/%s/posts?fields=id,message,created_time,comments{id,message,created_time,from}&limit=100&access_token=%s",
                facebookProperties.getGraphApiUrl(),
                facebookProperties.getApiVersion(),
                facebookProperties.getPageId(),
                facebookProperties.getAccessToken());
    }

    public Instant parseTimestamp(String createdTime) {
        try {
            return ZonedDateTime.parse(createdTime, DateTimeFormatter.ISO_DATE_TIME).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", createdTime);
            return Instant.now();
        }
    }
}