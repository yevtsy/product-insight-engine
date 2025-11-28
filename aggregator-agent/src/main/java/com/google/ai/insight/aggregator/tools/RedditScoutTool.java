package com.google.ai.insight.aggregator.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.ai.insight.aggregator.config.RedditProperties;
import io.github.bucket4j.Bucket;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Reddit Scout Tool - fetches comments from Reddit threads.
 * This is a simple tool for Google ADK agents to use.
 */
@Slf4j
@Component
public class RedditScoutTool {

    private final RedditProperties redditProperties;
    private final WebClient.Builder webClientBuilder;
    private final Bucket rateLimiter;
    private String accessToken;
    private Instant tokenExpiry;

    public RedditScoutTool(
            RedditProperties redditProperties,
            WebClient.Builder webClientBuilder,
            @Qualifier("redditRateLimiter") Bucket rateLimiter) {
        this.redditProperties = redditProperties;
        this.webClientBuilder = webClientBuilder;
        this.rateLimiter = rateLimiter;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("expires_in")
        private long expiresIn;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentsResponse {
        private Data data;

        @lombok.Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Data {
            private List<Child> children;
            private String after;
        }

        @lombok.Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Child {
            private String kind;
            private CommentData data;
        }

        @lombok.Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CommentData {
            private String id;
            private String body;
            private String author;
            private Long created;
            @JsonProperty("created_utc")
            private Long createdUtc;
        }
    }

    /**
     * Fetches comments from Reddit thread based on keywords.
     *
     * @param featureName Feature name to search for (used as primary keyword)
     * @param threadUrl Reddit thread URL to fetch comments from
     * @param keywords Additional keywords to filter comments
     * @param maxComments Maximum number of comments to fetch
     * @return List of comments matching the criteria
     */
    public List<CommentsResponse.CommentData> fetchComments(String featureName, String threadUrl, List<String> keywords, int maxComments) {
        log.info("Fetching Reddit comments for feature '{}' from thread: {}", featureName, threadUrl);

        // Add feature name to keywords
        List<String> allKeywords = new ArrayList<>();
        if (featureName != null && !featureName.isEmpty()) {
            allKeywords.add(featureName);
        }
        if (keywords != null) {
            allKeywords.addAll(keywords);
        }

        ensureAccessToken();

        List<CommentsResponse.CommentData> allComments = new ArrayList<>();

        try {
            // Extract thread ID from URL
            String threadId = extractThreadId(threadUrl);
            if (threadId == null) {
                log.error("Invalid thread URL: {}", threadUrl);
                return allComments;
            }

            WebClient webClient = webClientBuilder
                    .baseUrl(redditProperties.getApiUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .defaultHeader(HttpHeaders.USER_AGENT, redditProperties.getUserAgent())
                    .build();

            // Fetch comments from thread
            String url = "/comments/" + threadId + "?limit=100&depth=10";

            // Apply rate limiting - wait if necessary
            rateLimiter.asBlocking().consume(1);

            Object[] response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .block();

            if (response != null && response.length > 1) {
                // Response[0] is the post, response[1] is comments
                CommentsResponse commentsResponse = parseCommentsResponse(response[1]);

                if (commentsResponse != null && commentsResponse.getData() != null
                        && commentsResponse.getData().getChildren() != null) {
                    for (CommentsResponse.Child child : commentsResponse.getData().getChildren()) {
                        if (child.getKind() != null && child.getKind().equals("t1")
                                && child.getData() != null) {
                            CommentsResponse.CommentData comment = child.getData();

                            // Filter by keywords if provided
                            if (allKeywords.isEmpty() || matchesKeywords(comment.getBody(), allKeywords)) {
                                allComments.add(comment);
                                if (allComments.size() >= maxComments) {
                                    return allComments;
                                }
                            }
                        }
                    }
                }
            }

            log.info("Successfully fetched {} Reddit comments", allComments.size());
        } catch (Exception e) {
            log.error("Error fetching Reddit comments", e);
        }

        return allComments;
    }

    private void ensureAccessToken() {
        if (accessToken == null || tokenExpiry == null || Instant.now().isAfter(tokenExpiry)) {
            log.info("Obtaining new Reddit access token");
            obtainAccessToken();
        }
    }

    private void obtainAccessToken() {
        try {
            String auth = redditProperties.getClientId() + ":" + redditProperties.getClientSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("username", redditProperties.getUsername());
            formData.add("password", redditProperties.getPassword());

            WebClient webClient = webClientBuilder
                    .baseUrl("https://www.reddit.com")
                    .build();

            // Apply rate limiting - wait if necessary
            rateLimiter.asBlocking().consume(1);

            TokenResponse tokenResponse = webClient.post()
                    .uri("/api/v1/access_token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                    .header(HttpHeaders.USER_AGENT, redditProperties.getUserAgent())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                this.accessToken = tokenResponse.getAccessToken();
                this.tokenExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn() - 60);
                log.info("Successfully obtained Reddit access token");
            }
        } catch (Exception e) {
            log.error("Error obtaining Reddit access token", e);
        }
    }

    private String extractThreadId(String threadUrl) {
        // Reddit thread URLs: https://www.reddit.com/r/subreddit/comments/thread_id/...
        // Extract the thread_id part
        if (threadUrl == null || !threadUrl.contains("/comments/")) {
            return null;
        }

        String[] parts = threadUrl.split("/comments/");
        if (parts.length < 2) {
            return null;
        }

        String afterComments = parts[1];
        String[] idParts = afterComments.split("/");
        return idParts.length > 0 ? idParts[0] : null;
    }

    private CommentsResponse parseCommentsResponse(Object responseObj) {
        // This is a simplified parsing - in production you'd use proper JSON mapping
        try {
            if (responseObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) responseObj;

                CommentsResponse response = new CommentsResponse();
                CommentsResponse.Data data = new CommentsResponse.Data();

                if (map.containsKey("data") && map.get("data") instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) map.get("data");

                    if (dataMap.containsKey("children") && dataMap.get("children") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> children = (List<Object>) dataMap.get("children");
                        List<CommentsResponse.Child> childList = new ArrayList<>();

                        for (Object childObj : children) {
                            if (childObj instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> childMap = (java.util.Map<String, Object>) childObj;

                                CommentsResponse.Child child = new CommentsResponse.Child();
                                child.setKind((String) childMap.get("kind"));

                                if (childMap.containsKey("data") && childMap.get("data") instanceof java.util.Map) {
                                    @SuppressWarnings("unchecked")
                                    java.util.Map<String, Object> commentDataMap = (java.util.Map<String, Object>) childMap.get("data");

                                    CommentsResponse.CommentData commentData = new CommentsResponse.CommentData();
                                    commentData.setId((String) commentDataMap.get("id"));
                                    commentData.setBody((String) commentDataMap.get("body"));
                                    commentData.setAuthor((String) commentDataMap.get("author"));

                                    Object createdUtc = commentDataMap.get("created_utc");
                                    if (createdUtc instanceof Number) {
                                        commentData.setCreatedUtc(((Number) createdUtc).longValue());
                                    }

                                    child.setData(commentData);
                                }

                                childList.add(child);
                            }
                        }

                        data.setChildren(childList);
                    }
                }

                response.setData(data);
                return response;
            }
        } catch (Exception e) {
            log.error("Error parsing Reddit comments response", e);
        }

        return null;
    }

    private boolean matchesKeywords(String body, List<String> keywords) {
        if (body == null) {
            return false;
        }

        String lowerBody = body.toLowerCase();
        return keywords.stream()
                .anyMatch(keyword -> lowerBody.contains(keyword.toLowerCase()));
    }

    public Instant parseTimestamp(Long createdUtc) {
        try {
            if (createdUtc != null) {
                return Instant.ofEpochSecond(createdUtc);
            }
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", createdUtc);
        }
        return Instant.now();
    }
}