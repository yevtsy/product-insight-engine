package com.google.ai.insight.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeatureInsight {
    private String featureId;
    private String socialSummary;
    private String metricsSummary;
    private List<String> recommendations;
    private double overallHealthScore;
}
