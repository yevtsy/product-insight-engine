package com.google.ai.insight.exception;

/**
 * Base exception class for all service-level exceptions in the Product Insight Engine.
 * Provides common error handling structure across all modules.
 */
public abstract class BaseServiceException extends RuntimeException {

    private final String errorCode;
    private final transient Object details;

    protected BaseServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    protected BaseServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    protected BaseServiceException(String message, String errorCode, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    protected BaseServiceException(String message, String errorCode, Object details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}