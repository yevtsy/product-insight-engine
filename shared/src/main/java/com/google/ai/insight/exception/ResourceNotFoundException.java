package com.google.ai.insight.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends BaseServiceException {

    private static final String ERROR_CODE_PREFIX = "RESOURCE_";

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId),
                ERROR_CODE_PREFIX + "NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, ERROR_CODE_PREFIX + "NOT_FOUND");
    }
}