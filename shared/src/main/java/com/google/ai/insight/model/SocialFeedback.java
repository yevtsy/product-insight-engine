package com.google.ai.insight.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SocialFeedback {
    private String source; // e.g., Facebook, Instagram, Reddit
    private String content;
    private double sentimentScore;
    private Instant timestamp;
}
