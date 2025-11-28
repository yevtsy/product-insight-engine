package com.google.ai.insight.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedbackSummary {
    private String source;
    private String featureName;
    private int totalComments;
    private double averageSentiment;
    private String summary;
    private String keyThemes;
    private String positiveHighlights;
    private String negativeHighlights;
}