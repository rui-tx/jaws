package org.ruitx.jaws.jobs;

import org.tinylog.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

/**
 * ErrorClassifier - Determines whether exceptions are permanent or transient
 * 
 * This class implements production-ready error classification logic to decide
 * whether a failed job should be retried or permanently failed.
 * 
 * Classification strategy:
 * - Permanent errors: Don't retry (bad input, programming errors, auth failures)
 * - Transient errors: Do retry (network issues, timeouts, resource unavailable)
 * 
 * This is critical for production systems to avoid infinite retry loops
 * while still handling temporary failures gracefully.
 */
public class ErrorClassifier {
    
    /**
     * Error classification result
     */
    public enum ErrorType {
        PERMANENT,   // Don't retry - job will be marked as FAILED
        TRANSIENT    // Do retry - job will be retried with exponential backoff
    }
    
    /**
     * Classify an exception to determine retry behavior
     * 
     * @param exception The exception that caused the job to fail
     * @param currentRetries Current number of retry attempts
     * @param maxRetries Maximum allowed retries for this job
     * @return ErrorType indicating whether to retry or fail permanently
     */
    public static ErrorType classify(Throwable exception, int currentRetries, int maxRetries) {
        
        // If we've already exceeded max retries, treat as permanent
        if (currentRetries >= maxRetries) {
            Logger.info("Max retries ({}) exceeded for exception: {}", maxRetries, exception.getClass().getSimpleName());
            return ErrorType.PERMANENT;
        }
        
        // Classify by exception type
        ErrorType classification = classifyByExceptionType(exception);
        
        Logger.debug("Classified {} as {} (retry {}/{})", 
                    exception.getClass().getSimpleName(), 
                    classification, 
                    currentRetries, 
                    maxRetries);
        
        return classification;
    }
    
    /**
     * Classify exception by its type and characteristics
     */
    private static ErrorType classifyByExceptionType(Throwable exception) {
        
        // Handle specific exception types
        Class<?> exceptionClass = exception.getClass();
        String exceptionName = exceptionClass.getSimpleName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        // === PERMANENT ERRORS (Don't retry) ===
        
        // Programming/Logic errors
        if (exception instanceof IllegalArgumentException ||
            exception instanceof IllegalStateException ||
            exception instanceof ClassCastException ||
            exception instanceof NullPointerException ||
            exception instanceof NumberFormatException) {
            return ErrorType.PERMANENT;
        }
        
        // Security/Authentication errors
        if (exception instanceof SecurityException ||
            exceptionName.contains("Authentication") ||
            exceptionName.contains("Authorization") ||
            exceptionName.contains("Forbidden")) {
            return ErrorType.PERMANENT;
        }
        
        // Resource not found errors
        if (exceptionName.contains("NotFound") ||
            message.contains("not found") ||
            message.contains("does not exist")) {
            return ErrorType.PERMANENT;
        }
        
        // Bad input/validation errors
        if (exceptionName.contains("Validation") ||
            exceptionName.contains("Parse") ||
            message.contains("invalid") ||
            message.contains("malformed") ||
            message.contains("bad request")) {
            return ErrorType.PERMANENT;
        }
        
        // === TRANSIENT ERRORS (Do retry) ===
        
        // Network/IO errors
        if (exception instanceof IOException ||
            exception instanceof SocketTimeoutException ||
            exceptionName.contains("Network") ||
            exceptionName.contains("Connection") ||
            message.contains("connection") ||
            message.contains("network") ||
            message.contains("timeout")) {
            return ErrorType.TRANSIENT;
        }
        
        // Database/Resource temporary issues
        if (exception instanceof SQLException ||
            exception instanceof TimeoutException ||
            exceptionName.contains("Timeout") ||
            message.contains("busy") ||
            message.contains("unavailable") ||
            message.contains("rate limit") ||
            message.contains("too many requests")) {
            return ErrorType.TRANSIENT;
        }
        
        // System/Threading issues
        if (exception instanceof InterruptedException ||
            exceptionName.contains("Interrupt") ||
            message.contains("interrupted")) {
            return ErrorType.TRANSIENT;
        }
        
        // HTTP-like temporary errors (503, 429, etc.)
        if (message.contains("503") ||
            message.contains("429") ||
            message.contains("502") ||
            message.contains("504") ||
            message.contains("service unavailable") ||
            message.contains("temporarily unavailable")) {
            return ErrorType.TRANSIENT;
        }
        
        // === DEFAULT BEHAVIOR ===
        
        // For unknown exceptions, check the cause chain
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            return classifyByExceptionType(cause);
        }
        
        // Conservative default: treat unknown exceptions as transient
        // This ensures we attempt retries for unexpected failures
        Logger.warn("Unknown exception type {}, defaulting to TRANSIENT: {}", 
                   exceptionName, exception.getMessage());
        return ErrorType.TRANSIENT;
    }
    
    /**
     * Get a human-readable reason for the classification
     */
    public static String getClassificationReason(Throwable exception) {
        ErrorType type = classifyByExceptionType(exception);
        String exceptionName = exception.getClass().getSimpleName();
        
        return switch (type) {
            case PERMANENT -> String.format("Permanent failure - %s indicates a non-recoverable error", exceptionName);
            case TRANSIENT -> String.format("Transient failure - %s may be recoverable with retry", exceptionName);
        };
    }
} 