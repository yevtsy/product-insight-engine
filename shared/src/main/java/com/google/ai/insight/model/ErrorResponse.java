package com.google.ai.insight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized error response structure for all API errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Timestamp when the error occurred
     */
    private Instant timestamp;

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Application-specific error code for programmatic error handling
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error information (optional)
     */
    private String details;

    /**
     * API path where the error occurred
     */
    private String path;

    /**
     * Correlation ID for request tracing (optional)
     */
    private String correlationId;

    /**
     * Additional context-specific error details (optional)
     */
    private Object additionalInfo;
}