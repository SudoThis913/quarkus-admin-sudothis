package com.sudothis.resource;

import org.jboss.logging.MDC;

import java.time.Instant;

/**
 * Standard envelope for error responses. Success responses return entity data directly
 * so existing clients and tests don't need to unwrap a wrapper object.
 *
 * Used by ExceptionMappers and explicit error returns.
 */
public record ApiResponse<T>(
    T data,
    String error,
    String correlationId,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, currentCorrelationId(), Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(null, message, currentCorrelationId(), Instant.now());
    }

    public static <T> ApiResponse<T> validationError(T fieldErrors) {
        return new ApiResponse<>(fieldErrors, "Validation failed", currentCorrelationId(), Instant.now());
    }

    private static String currentCorrelationId() {
        Object id = MDC.get("correlationId");
        return id != null ? id.toString() : null;
    }
}
