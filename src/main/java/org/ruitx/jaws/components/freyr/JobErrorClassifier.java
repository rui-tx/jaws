package org.ruitx.jaws.components.freyr;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * JobErrorClassifier - Error classification 
 */
public class JobErrorClassifier {
    
    private final Mimir mimir = new Mimir();
    
    /**
     * Error classification with retry strategy
     */
    public enum ErrorType {
        PERMANENT_VALIDATION("permanent", 0, "Validation or input errors"),
        PERMANENT_AUTH("permanent", 0, "Authentication/authorization errors"),
        PERMANENT_NOT_FOUND("permanent", 0, "Resource not found errors"),
        TRANSIENT_NETWORK("transient", 3, "Network connectivity issues"),
        TRANSIENT_TIMEOUT("transient", 5, "Timeout errors"),
        TRANSIENT_RATE_LIMIT("transient", 2, "Rate limiting errors"),
        TRANSIENT_SERVICE_UNAVAILABLE("transient", 4, "Service temporarily unavailable"),
        TRANSIENT_DATABASE("transient", 3, "Database connectivity issues"),
        PERMANENT_SYSTEM("permanent", 0, "System/programming errors"),
        TRANSIENT_RESOURCE_EXHAUSTION("transient", 2, "Resource exhaustion (memory, CPU)"),
        UNKNOWN_INVESTIGATE("transient", 1, "Unknown errors requiring investigation");
        
        public final String type;
        public final int defaultMaxRetries;
        public final String reason;
        
        ErrorType(String type, int defaultMaxRetries, String reason) {
            this.type = type;
            this.defaultMaxRetries = defaultMaxRetries;
            this.reason = reason;
        }
    }
    
    /**
     * Error classification result with retry strategy
     */
    public static class ClassificationResult {
        private final ErrorType errorType;
        private final boolean shouldRetry;
        private final int suggestedMaxRetries;
        private final long suggestedBaseDelayMs;
        private final double suggestedBackoffMultiplier;
        private final String reason;
        private final String strategy;
        
        public ClassificationResult(ErrorType errorType, boolean shouldRetry,
                                          int suggestedMaxRetries, long suggestedBaseDelayMs,
                                          double suggestedBackoffMultiplier, String reason, String strategy) {
            this.errorType = errorType;
            this.shouldRetry = shouldRetry;
            this.suggestedMaxRetries = suggestedMaxRetries;
            this.suggestedBaseDelayMs = suggestedBaseDelayMs;
            this.suggestedBackoffMultiplier = suggestedBackoffMultiplier;
            this.reason = reason;
            this.strategy = strategy;
        }
        
        // Getters
        public ErrorType getErrorType() { return errorType; }
        public boolean shouldRetry() { return shouldRetry; }
        public int getSuggestedMaxRetries() { return suggestedMaxRetries; }
        public long getSuggestedBaseDelayMs() { return suggestedBaseDelayMs; }
        public double getSuggestedBackoffMultiplier() { return suggestedBackoffMultiplier; }
        public String getReason() { return reason; }
        public String getStrategy() { return strategy; }
    }


    /**
     * Job-specific error classification
     * We can add job-specific error classification here
     */
    private ErrorType classifyByJobType(Throwable exception, String jobType, ErrorPattern pattern) {
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        switch (jobType.toLowerCase()) {
            case "example1":
                if (exception instanceof IOException) {
                    return ErrorType.TRANSIENT_NETWORK;
                }
                break;
                
            case "example2":
                if (message.contains("connection") || message.contains("timeout")) {
                    return ErrorType.TRANSIENT_DATABASE;
                }
                break;
                
        }
        
        return null; // No job-specific classification
    }
    
    /**
     * Error classification with job-specific context
     * 
     * @param exception The exception that caused the job to fail
     * @param jobType The type of job that failed
     * @param currentRetries Current number of retry attempts
     * @param maxRetries Maximum allowed retries for this job
     * @return Error classification result with retry strategy
     */
    public ClassificationResult classify(Throwable exception, String jobType, 
                                                        int currentRetries, int maxRetries) {
        
        // First check retry limits
        if (currentRetries >= maxRetries) {
            return new ClassificationResult(
                ErrorType.PERMANENT_SYSTEM, false, 0, 0L, 1.0,
                String.format("Max retries exceeded: %d/%d", currentRetries, maxRetries),
                "max_retries_exceeded"
            );
        }
        
        // Get job-specific error history for pattern analysis and exception type
        ErrorPattern pattern = analyzeErrorPattern(jobType, exception);
        ErrorType errorType = classifyByExceptionType(exception, jobType, pattern);
        
        // Determine retry strategy based on error type and pattern
        RetryStrategy strategy = determineRetryStrategy(errorType, pattern, jobType);
        
        boolean shouldRetry = !errorType.name().startsWith("PERMANENT");
        String reason = generateClassificationReason(errorType, pattern, strategy);
        
        Logger.info("Error classification for {}: {} (retry: {}, strategy: {})", 
                   exception.getClass().getSimpleName(), errorType, shouldRetry, strategy.name);
        
        return new ClassificationResult(
            errorType, shouldRetry,
            strategy.maxRetries, strategy.baseDelayMs, strategy.backoffMultiplier,
            reason, strategy.name
        );
    }
    
    /**
     * Classify exception by type with domain-specific logic
     */
    private ErrorType classifyByExceptionType(Throwable exception, String jobType, ErrorPattern pattern) {
        
        Class<?> exceptionClass = exception.getClass();
        String exceptionName = exceptionClass.getSimpleName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        // === NETWORK & CONNECTIVITY ERRORS ===
        if (exception instanceof ConnectException ||
            exception instanceof SocketTimeoutException ||
            exceptionName.contains("Connection") ||
            message.contains("connection refused") ||
            message.contains("connection reset") ||
            message.contains("network unreachable")) {
            return ErrorType.TRANSIENT_NETWORK;
        }
        
        // === TIMEOUT ERRORS ===
        if (exception instanceof TimeoutException ||
            exceptionName.contains("Timeout") ||
            message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("read timeout")) {
            return ErrorType.TRANSIENT_TIMEOUT;
        }
        
        // === RATE LIMITING ===
        if (message.contains("rate limit") ||
            message.contains("too many requests") ||
            message.contains("429") ||
            message.contains("quota exceeded") ||
            message.contains("throttle")) {
            return ErrorType.TRANSIENT_RATE_LIMIT;
        }
        
        // === SERVICE UNAVAILABLE ===
        if (message.contains("503") ||
            message.contains("502") ||
            message.contains("504") ||
            message.contains("service unavailable") ||
            message.contains("temporarily unavailable") ||
            message.contains("maintenance")) {
            return ErrorType.TRANSIENT_SERVICE_UNAVAILABLE;
        }
        
        // === DATABASE ERRORS ===
        if (exception instanceof SQLException ||
            exceptionName.contains("SQL") ||
            message.contains("database") ||
            message.contains("connection pool") ||
            message.contains("deadlock")) {
            
            // Check if it's a transient DB error
            if (message.contains("timeout") ||
                message.contains("busy") ||
                message.contains("lock") ||
                message.contains("connection")) {
                return ErrorType.TRANSIENT_DATABASE;
            }
            // Permanent DB errors (constraint violations, etc.)
            return ErrorType.PERMANENT_VALIDATION;
        }
        
        // === VALIDATION & INPUT ERRORS ===
        if (exception instanceof IllegalArgumentException ||
            exception instanceof IllegalStateException ||
            exception instanceof NumberFormatException ||
            exceptionName.contains("Validation") ||
            exceptionName.contains("Parse") ||
            message.contains("invalid") ||
            message.contains("malformed") ||
            message.contains("bad request") ||
            message.contains("400")) {
            return ErrorType.PERMANENT_VALIDATION;
        }
        
        // === AUTHENTICATION & AUTHORIZATION ===
        if (exception instanceof SecurityException ||
            exceptionName.contains("Authentication") ||
            exceptionName.contains("Authorization") ||
            exceptionName.contains("Forbidden") ||
            message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthorized") ||
            message.contains("forbidden")) {
            return ErrorType.PERMANENT_AUTH;
        }
        
        // === NOT FOUND ERRORS ===
        if (exceptionName.contains("NotFound") ||
            message.contains("not found") ||
            message.contains("404") ||
            message.contains("does not exist")) {
            return ErrorType.PERMANENT_NOT_FOUND;
        }
        
        // === RESOURCE EXHAUSTION ===
        if (exception instanceof OutOfMemoryError ||
            exceptionName.contains("OutOfMemory") ||
            message.contains("out of memory") ||
            message.contains("disk full") ||
            message.contains("no space left") ||
            message.contains("resource exhausted")) {
            return ErrorType.TRANSIENT_RESOURCE_EXHAUSTION;
        }
        
        // === SYSTEM/PROGRAMMING ERRORS ===
        if (exception instanceof NullPointerException ||
            exception instanceof ClassCastException ||
            exception instanceof ArrayIndexOutOfBoundsException ||
            exceptionName.contains("ClassNotFound") ||
            exceptionName.contains("NoSuchMethod")) {
            return ErrorType.PERMANENT_SYSTEM;
        }
        
        // === INTERRUPTION ===
        if (exception instanceof InterruptedException ||
            exceptionName.contains("Interrupt")) {
            return ErrorType.TRANSIENT_NETWORK; // Treat as transient
        }
        
        // === ANALYZE CAUSE CHAIN ===
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            return classifyByExceptionType(cause, jobType, pattern);
        }
        
        // === JOB-SPECIFIC CLASSIFICATION ===
        if (jobType != null) {
            ErrorType jobSpecific = classifyByJobType(exception, jobType, pattern);
            if (jobSpecific != null) {
                return jobSpecific;
            }
        }
        
        // === UNKNOWN - CONSERVATIVE DEFAULT ===
        Logger.warn("Unknown exception type {} for job {}, defaulting to investigation required", 
                   exceptionName, jobType);
        return ErrorType.UNKNOWN_INVESTIGATE;
    }
    
   
    /**
     * Analyze error patterns for this job type
     */
    private ErrorPattern analyzeErrorPattern(String jobType, Throwable exception) {
        try {
            String exceptionType = exception.getClass().getSimpleName();
            
            // Get recent error history for this job type and exception
            List<Row> recentErrors = mimir.getRows("""
                SELECT error_message, created_at FROM JOBS 
                WHERE type = ? AND error_message LIKE ? 
                AND created_at > ? 
                ORDER BY created_at DESC LIMIT 10
                """, 
                jobType, 
                "%" + exceptionType + "%",
                Instant.now().toEpochMilli() - 86400000L // Last 24 hours
            );
            
            int recentFailureCount = recentErrors.size();
            
            // Check for escalating failure pattern
            boolean escalatingPattern = isEscalatingPattern(recentErrors);
            
            // Check for specific error message patterns
            long firstOccurrence = recentErrors.isEmpty() ? 
                Instant.now().toEpochMilli() : 
                recentErrors.get(recentErrors.size() - 1).getLong("created_at").orElse(0L);
            
            return new ErrorPattern(recentFailureCount, escalatingPattern, firstOccurrence);
            
        } catch (Exception e) {
            Logger.warn("Failed to analyze error pattern: {}", e.getMessage());
            return new ErrorPattern(0, false, Instant.now().toEpochMilli());
        }
    }
    
    /**
     * Check if errors are escalating (increasing frequency)
     */
    private boolean isEscalatingPattern(List<Row> recentErrors) {
        if (recentErrors.size() < 3) return false;
        
        // Check if errors are becoming more frequent (simple heuristic)
        long now = Instant.now().toEpochMilli();
        long recent = recentErrors.get(0).getLong("created_at").orElse(now);
        long older = recentErrors.get(recentErrors.size() - 1).getLong("created_at").orElse(now);
        
        // If more than 5 errors in the last hour, consider it escalating
        long oneHourAgo = now - 3600000L;
        long errorsInLastHour = recentErrors.stream()
            .mapToLong(row -> row.getLong("created_at").orElse(0L))
            .filter(timestamp -> timestamp > oneHourAgo)
            .count();
        
        return errorsInLastHour >= 5;
    }
    
    /**
     * Determine retry strategy based on error type and pattern
     */
    private RetryStrategy determineRetryStrategy(ErrorType errorType, ErrorPattern pattern, String jobType) {
        
        // Base strategy from error type
        RetryStrategy baseStrategy = getBaseRetryStrategy(errorType);
        
        // Adjust based on error pattern
        if (pattern.escalatingPattern) {
            // Reduce aggressiveness for escalating errors
            return new RetryStrategy(
                baseStrategy.name + "_conservative",
                Math.max(1, baseStrategy.maxRetries - 1),
                Math.min(baseStrategy.baseDelayMs * 2, 30000L), // Longer delays
                Math.max(baseStrategy.backoffMultiplier, 3.0) // More aggressive backoff
            );
        }
        
        if (pattern.recentFailureCount > 5) {
            // Many recent failures - be more conservative
            return new RetryStrategy(
                baseStrategy.name + "_cautious",
                Math.max(1, baseStrategy.maxRetries - 1),
                baseStrategy.baseDelayMs * 2,
                baseStrategy.backoffMultiplier + 1.0
            );
        }
        
        // Job-specific adjustments
        return adjustStrategyForJobType(baseStrategy, jobType);
    }
    
    /**
     * Get base retry strategy for error type
     */
    private RetryStrategy getBaseRetryStrategy(ErrorType errorType) {
        return switch (errorType) {
            case TRANSIENT_NETWORK -> new RetryStrategy("network", 3, 2000L, 2.0);
            case TRANSIENT_TIMEOUT -> new RetryStrategy("timeout", 5, 5000L, 2.5);
            case TRANSIENT_RATE_LIMIT -> new RetryStrategy("rate_limit", 2, 30000L, 4.0); // Long delays for rate limits
            case TRANSIENT_SERVICE_UNAVAILABLE -> new RetryStrategy("service_unavailable", 4, 10000L, 3.0);
            case TRANSIENT_DATABASE -> new RetryStrategy("database", 3, 1000L, 2.0);
            case TRANSIENT_RESOURCE_EXHAUSTION -> new RetryStrategy("resource_exhaustion", 2, 15000L, 3.0);
            case UNKNOWN_INVESTIGATE -> new RetryStrategy("unknown", 1, 5000L, 2.0); // Conservative for unknown
            default -> new RetryStrategy("none", 0, 0L, 1.0); // No retry for permanent errors
        };
    }
    
    /**
     * Adjust strategy based on job type
     */
    private RetryStrategy adjustStrategyForJobType(RetryStrategy baseStrategy, String jobType) {
        if (jobType == null) return baseStrategy;
        
        switch (jobType.toLowerCase()) {
            case "critical":
            case "urgent":
                // More aggressive retries for critical jobs
                return new RetryStrategy(
                    baseStrategy.name + "_critical",
                    baseStrategy.maxRetries + 2,
                    baseStrategy.baseDelayMs / 2,
                    baseStrategy.backoffMultiplier
                );
                
            case "heavy-computation":
            case "batch-processing":
                // Less aggressive for heavy jobs
                return new RetryStrategy(
                    baseStrategy.name + "_heavy",
                    Math.max(1, baseStrategy.maxRetries - 1),
                    baseStrategy.baseDelayMs * 2,
                    baseStrategy.backoffMultiplier + 0.5
                );
                
            default:
                return baseStrategy;
        }
    }
    
    /**
     * Generate human-readable classification reason
     */
    private String generateClassificationReason(ErrorType errorType, ErrorPattern pattern, RetryStrategy strategy) {
        StringBuilder reason = new StringBuilder();
        reason.append(errorType.reason);
        
        if (pattern.recentFailureCount > 0) {
            reason.append(String.format(" (seen %d times recently)", pattern.recentFailureCount));
        }
        
        if (pattern.escalatingPattern) {
            reason.append(" - escalating pattern detected");
        }
        
        reason.append(String.format(" - strategy: %s", strategy.name));
        
        return reason.toString();
    }
    
    // Helper classes
    
    private static class ErrorPattern {
        final int recentFailureCount;
        final boolean escalatingPattern;
        final long firstOccurrence;
        
        ErrorPattern(int recentFailureCount, boolean escalatingPattern, long firstOccurrence) {
            this.recentFailureCount = recentFailureCount;
            this.escalatingPattern = escalatingPattern;
            this.firstOccurrence = firstOccurrence;
        }
    }
    
    private static class RetryStrategy {
        final String name;
        final int maxRetries;
        final long baseDelayMs;
        final double backoffMultiplier;
        
        RetryStrategy(String name, int maxRetries, long baseDelayMs, double backoffMultiplier) {
            this.name = name;
            this.maxRetries = maxRetries;
            this.baseDelayMs = baseDelayMs;
            this.backoffMultiplier = backoffMultiplier;
        }
    }
    
    /**
     * Save error classification for future pattern analysis
     */
    // public void recordErrorClassification(String jobId, String jobType, Throwable exception, 
    //                                     ClassificationResult result) {
    //     try {
    //         String classificationData = Odin.getMapper().writeValueAsString(Map.of(
    //             "jobId", jobId,
    //             "jobType", jobType,
    //             "exceptionType", exception.getClass().getSimpleName(),
    //             "errorType", result.getErrorType().name(),
    //             "strategy", result.getStrategy(),
    //             "timestamp", Instant.now().toEpochMilli()
    //         ));
            
    //         // This could be stored in a dedicated error_classifications table
    //         // For now, we'll log it for pattern analysis
    //         Logger.info("Error classification recorded: {}", classificationData);
            
    //     } catch (Exception e) {
    //         Logger.warn("Failed to record error classification: {}", e.getMessage());
    //     }
    // }
} 