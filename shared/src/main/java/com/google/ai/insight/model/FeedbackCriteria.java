package com.google.ai.insight.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedbackCriteria {
    private String featureName;
    private List<String> keywords;
    private int maxComments;
    private String threadUrl; // Optional: For Reddit thread-specific fetching

    public FeedbackCriteria(String featureName, List<String> keywords, int maxComments) {
        this.featureName = featureName;
        this.keywords = keywords;
        this.maxComments = maxComments;
    }
}