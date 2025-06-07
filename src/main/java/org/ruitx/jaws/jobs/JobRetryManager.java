package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JobRetryManager - Handles job retry logic with error classification
 * 
 */
public class JobRetryManager {
    
    // Default fallback values (used when JobErrorClassifier doesn't provide specific values)
    private static final long DEFAULT_BASE_DELAY_MS = 1000L; // 1 second base delay
    private static final long DEFAULT_MAX_DELAY_MS = 300000L; // 5 minutes maximum delay
    private static final double DEFAULT_JITTER_FACTOR = 0.25; // ±25% jitter
    private static final int DEFAULT_EXPONENTIAL_BASE = 4; // 4x multiplier per retry
    
    private final Mimir mimir = new Mimir();
    private final JobErrorClassifier errorClassifier = new JobErrorClassifier();
    
    /**
     * Retry decision result with enhanced information
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final long retryDelayMs;
        private final String reason;
        private final JobErrorClassifier.ErrorType errorType;
        private final String strategy;
        private final int suggestedMaxRetries;
        
        public RetryDecision(boolean shouldRetry, long retryDelayMs, String reason, 
                           JobErrorClassifier.ErrorType errorType, String strategy, int suggestedMaxRetries) {
            this.shouldRetry = shouldRetry;
            this.retryDelayMs = retryDelayMs;
            this.reason = reason;
            this.errorType = errorType;
            this.strategy = strategy;
            this.suggestedMaxRetries = suggestedMaxRetries;
        }
        
        public boolean shouldRetry() { return shouldRetry; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public String getReason() { return reason; }
        public JobErrorClassifier.ErrorType getErrorType() { return errorType; }
        public String getStrategy() { return strategy; }
        public int getSuggestedMaxRetries() { return suggestedMaxRetries; }
    }
    
    /**
     * Determine if a failed job should be retried using error classification
     * 
     * @param jobId The job that failed
     * @param jobType The type of job that failed (for context-aware classification)
     * @param exception The exception that caused the failure
     * @param currentRetries Current number of retry attempts
     * @param maxRetries Maximum allowed retries for this job
     * @return RetryDecision with retry logic and delay calculation
     */
    public RetryDecision shouldRetry(String jobId, String jobType, Throwable exception, int currentRetries, int maxRetries) {
        
        // Use error classification with job context
        JobErrorClassifier.ClassificationResult classification = 
            errorClassifier.classify(exception, jobType, currentRetries, maxRetries);
        
        // TODO: Make this a better system
        //errorClassifier.recordErrorClassification(jobId, jobType, exception, classification);
        
        // Check if we should retry based on error classification
        if (!classification.shouldRetry()) {
            String reason = String.format("No retry: %s", classification.getReason());
            return new RetryDecision(false, 0, reason, classification.getErrorType(), 
                                   classification.getStrategy(), classification.getSuggestedMaxRetries());
        }
        
        long delayMs;
        if (classification.getSuggestedBaseDelayMs() > 0) {
            delayMs = calculateAdvancedRetryDelay(currentRetries, classification);
        } else {
            // Fallback
            delayMs = calculateRetryDelay(currentRetries);
        }
        
        String reason = String.format("%s (retry %d/%d in %dms, strategy: %s): %s", 
                                     classification.getReason(),
                                     currentRetries + 1, 
                                     Math.max(maxRetries, classification.getSuggestedMaxRetries()),
                                     delayMs, 
                                     classification.getStrategy(),
                                     exception.getClass().getSimpleName());
        
        Logger.info("Job {} will be retried with strategy: {}", jobId, reason);
        return new RetryDecision(true, delayMs, reason, classification.getErrorType(), 
                               classification.getStrategy(), classification.getSuggestedMaxRetries());
    }
    
    public RetryDecision shouldRetry(String jobId, Throwable exception, int currentRetries, int maxRetries) {
        return shouldRetry(jobId, "generic", exception, currentRetries, maxRetries);
    }
    
    /**
     * Calculate retry delay using error classification suggestions
     */
    private long calculateAdvancedRetryDelay(int retryCount, JobErrorClassifier.ClassificationResult classification) {
        long baseDelay = classification.getSuggestedBaseDelayMs();
        double backoffMultiplier = classification.getSuggestedBackoffMultiplier();
        
        // Calculate delay with suggested parameters
        long calculatedDelay = (long) (baseDelay * Math.pow(backoffMultiplier, retryCount));
        calculatedDelay = Math.min(calculatedDelay, DEFAULT_MAX_DELAY_MS);
        
        // Add jitter: ±25% random variation
        long jitterRange = (long) (calculatedDelay * DEFAULT_JITTER_FACTOR);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        
        // Ensure minimum delay of 100ms
        long finalDelay = Math.max(calculatedDelay + jitter, 100L);
        
        Logger.debug("Error classification retry delay calculation: base={}ms, multiplier={}, jitter={}ms, final={}ms (attempt {})", 
                    baseDelay, backoffMultiplier, jitter, finalDelay, retryCount + 1);
        
        return finalDelay;
    }

    /**
     * Calculate exponential backoff delay with jitter (fallback method)
     * Formula: baseDelay * (exponentialBase ^ retryCount) + jitter
     * Jitter prevents thundering herd when many jobs fail simultaneously
     */
    public long calculateRetryDelay(int retryCount) {
        // Calculate base exponential delay: 1s, 4s, 16s, 64s, 256s...
        long baseDelay = DEFAULT_BASE_DELAY_MS * (long) Math.pow(DEFAULT_EXPONENTIAL_BASE, retryCount);
        baseDelay = Math.min(baseDelay, DEFAULT_MAX_DELAY_MS);

        long jitterRange = (long) (baseDelay * DEFAULT_JITTER_FACTOR);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        
        long finalDelay = Math.max(baseDelay + jitter, 100L);
        
        Logger.debug("Fallback retry delay: base={}ms, jitter={}ms, final={}ms (attempt {})", 
                    baseDelay, jitter, finalDelay, retryCount + 1);
        
        return finalDelay;
    }
    
    /**
     * Schedule a job for retry by updating database fields
     * This method prepares the job for retry without immediately re-queuing it
     */
    public void scheduleRetry(String jobId, long delayMs, Throwable exception) {
        try {
            long now = Instant.now().toEpochMilli();
            long nextRetryAt = now + delayMs;
            
            // Update job in database with retry information
            int updated = mimir.executeSql("""
                UPDATE JOBS SET 
                    status = ?,
                    current_retries = current_retries + 1,
                    retry_backoff_ms = ?,
                    next_retry_at = ?,
                    last_retry_at = ?,
                    error_message = ?
                WHERE id = ?
                """,
                JobQueue.JobStatus.RETRY_SCHEDULED.name(),
                delayMs,
                nextRetryAt,
                now,
                exception.getMessage(),
                jobId
            );
            
            if (updated > 0) {
                Logger.info("Scheduled job {} for retry in {}ms (at {})", 
                          jobId, delayMs, Instant.ofEpochMilli(nextRetryAt));
            } else {
                Logger.error("Failed to schedule retry for job {} - job not found in database", jobId);
            }
            
        } catch (Exception e) {
            Logger.error("Failed to schedule retry for job {}: {}", jobId, e.getMessage(), e);
        }
    }
    
    /**
     * Mark a job as permanently failed
     * This is called when retry limits are exceeded or permanent errors occur
     */
    public void markAsPermanentlyFailed(String jobId, Throwable exception, String reason) {
        try {
            long now = Instant.now().toEpochMilli();
            
            int updated = mimir.executeSql("""
                UPDATE JOBS SET 
                    status = ?,
                    completed_at = ?,
                    error_message = ?
                WHERE id = ?
                """,
                JobQueue.JobStatus.FAILED.name(),
                now,
                String.format("%s - %s", reason, exception.getMessage()),
                jobId
            );
            
            if (updated > 0) {
                Logger.info("Job {} marked as permanently failed: {}", jobId, reason);
            } else {
                Logger.error("Failed to mark job {} as failed - job not found in database", jobId);
            }
            
        } catch (Exception e) {
            Logger.error("Failed to mark job {} as failed: {}", jobId, e.getMessage(), e);
        }
    }
    
    /**
     * Get the current retry count for a job from the database
     * This is needed because jobs are reconstructed from the database during retry
     */
    public int getCurrentRetryCount(String jobId) {
        try {
            var row = mimir.getRow("SELECT current_retries FROM JOBS WHERE id = ?", jobId);
            if (row != null) {
                return row.getInt("current_retries").orElse(0);
            }
        } catch (Exception e) {
            Logger.error("Failed to get retry count for job {}: {}", jobId, e.getMessage());
        }
        return 0;
    }
    
    /**
     * Check if enough time has passed for a scheduled retry
     */
    public boolean isRetryReady(String jobId) {
        try {
            var row = mimir.getRow("""
                SELECT next_retry_at, status 
                FROM JOBS 
                WHERE id = ? AND status = ?
                """, jobId, JobQueue.JobStatus.RETRY_SCHEDULED.name());
            
            if (row != null) {
                long nextRetryAt = row.getLong("next_retry_at").orElse(0L);
                long now = Instant.now().toEpochMilli();
                return now >= nextRetryAt;
            }
        } catch (Exception e) {
            Logger.error("Failed to check retry readiness for job {}: {}", jobId, e.getMessage());
        }
        return false;
    }
    
    /**
     * Get statistics about retry operations
     */
    public RetryStatistics getRetryStatistics() {
        try {
            // Count jobs by status
            var retryScheduledRow = mimir.getRow("SELECT COUNT(*) as count FROM JOBS WHERE status = ?", 
                                                JobQueue.JobStatus.RETRY_SCHEDULED.name());
            var failedRow = mimir.getRow("SELECT COUNT(*) as count FROM JOBS WHERE status = ?", 
                                        JobQueue.JobStatus.FAILED.name());
            
            // Count total retry attempts
            var totalRetriesRow = mimir.getRow("SELECT SUM(current_retries) as total FROM JOBS WHERE current_retries > 0");
            
            int retryScheduled = retryScheduledRow != null ? retryScheduledRow.getInt("count").orElse(0) : 0;
            int permanentlyFailed = failedRow != null ? failedRow.getInt("count").orElse(0) : 0;
            int totalRetryAttempts = totalRetriesRow != null ? totalRetriesRow.getLong("total").orElse(0L).intValue() : 0;
            
            return new RetryStatistics(retryScheduled, permanentlyFailed, totalRetryAttempts);
            
        } catch (Exception e) {
            Logger.error("Failed to get retry statistics: {}", e.getMessage());
            return new RetryStatistics(0, 0, 0);
        }
    }
    
    /**
     * Statistics about retry operations
     */
    public static class RetryStatistics {
        private final int retryScheduledJobs;
        private final int permanentlyFailedJobs;
        private final int totalRetryAttempts;
        
        public RetryStatistics(int retryScheduledJobs, int permanentlyFailedJobs, int totalRetryAttempts) {
            this.retryScheduledJobs = retryScheduledJobs;
            this.permanentlyFailedJobs = permanentlyFailedJobs;
            this.totalRetryAttempts = totalRetryAttempts;
        }
        
        public int getRetryScheduledJobs() { return retryScheduledJobs; }
        public int getPermanentlyFailedJobs() { return permanentlyFailedJobs; }
        public int getTotalRetryAttempts() { return totalRetryAttempts; }
    }
} 