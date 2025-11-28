package com.google.ai.insight.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InternalMetrics {
    private String featureId;
    private int purchaseCount;
    private int loginCount;
    private double averageSessionDuration;
}
