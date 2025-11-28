package com.google.ai.insight.aggregator.controller;

import com.google.ai.insight.aggregator.service.AggregatorService;
import com.google.ai.insight.model.FeatureInsight;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for feature analysis operations.
 * Coordinates social media feedback aggregation and analysis.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class AggregatorController {

    private final AggregatorService aggregatorService;

    /**
     * Analyzes feature feedback from multiple social media sources.
     *
     * @param featureId Unique identifier for the feature (max 100 characters)
     * @param keyword   Keyword to filter social media comments (max 200 characters)
     * @return Feature insight with aggregated analysis
     */
    @GetMapping("/analyze")
    public FeatureInsight analyze(
            @RequestParam @NotBlank(message = "Feature ID cannot be blank")
            @Size(max = 100, message = "Feature ID must not exceed 100 characters")
            String featureId,
            @RequestParam @NotBlank(message = "Keyword cannot be blank")
            @Size(max = 200, message = "Keyword must not exceed 200 characters")
            String keyword) {

        log.info("Received analysis request for feature: {}, keyword: {}", featureId, keyword);

        return aggregatorService.analyzeFeature(featureId, keyword);
    }
}
