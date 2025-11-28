package com.google.ai.insight.exception;

/**
 * Exception thrown when communication with external services fails.
 * This includes social media APIs, analytics agents, and other external dependencies.
 */
public class ExternalServiceException extends BaseServiceException {

    private static final String ERROR_CODE_PREFIX = "EXT_SERVICE_";

    public ExternalServiceException(String serviceName, String message) {
        super(String.format("External service '%s' error: %s", serviceName, message),
                ERROR_CODE_PREFIX + "ERROR");
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(String.format("External service '%s' error: %s", serviceName, message),
                ERROR_CODE_PREFIX + "ERROR", cause);
    }

    public ExternalServiceException(String serviceName, String message, Object details) {
        super(String.format("External service '%s' error: %s", serviceName, message),
                ERROR_CODE_PREFIX + "ERROR", details);
    }

    public ExternalServiceException(String serviceName, String message, Object details, Throwable cause) {
        super(String.format("External service '%s' error: %s", serviceName, message),
                ERROR_CODE_PREFIX + "ERROR", details, cause);
    }
}