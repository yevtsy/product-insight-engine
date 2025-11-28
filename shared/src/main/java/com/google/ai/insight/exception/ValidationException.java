package com.google.ai.insight.exception;

/**
 * Exception thrown when input validation fails.
 */
public class ValidationException extends BaseServiceException {

    private static final String ERROR_CODE_PREFIX = "VALIDATION_";

    public ValidationException(String message) {
        super(message, ERROR_CODE_PREFIX + "ERROR");
    }

    public ValidationException(String message, Object details) {
        super(message, ERROR_CODE_PREFIX + "ERROR", details);
    }
}