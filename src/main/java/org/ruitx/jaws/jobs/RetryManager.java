package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RetryManager - Handles job retry logic with exponential backoff and jitter
 * 
 * This class implements production-ready retry mechanisms:
 * - Exponential backoff: 1s, 4s, 16s, 64s, 256s (capped at 5 minutes)
 * - Jitter: ±25% random variation to prevent thundering herd
 * - Error classification: Different strategies for permanent vs transient errors
 * - Database persistence: Tracks retry attempts and scheduling
 * 
 * Compatible with both parallel and sequential job queues.
 */
public class RetryManager {
    
    private static final long BASE_DELAY_MS = 1000L; // 1 second base delay
    private static final long MAX_DELAY_MS = 300000L; // 5 minutes maximum delay
    private static final double JITTER_FACTOR = 0.25; // ±25% jitter
    private static final int EXPONENTIAL_BASE = 4; // 4x multiplier per retry
    
    private final Mimir mimir = new Mimir();
    
    /**
     * Retry decision result
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final long retryDelayMs;
        private final String reason;
        private final ErrorClassifier.ErrorType errorType;
        
        public RetryDecision(boolean shouldRetry, long retryDelayMs, String reason, ErrorClassifier.ErrorType errorType) {
            this.shouldRetry = shouldRetry;
            this.retryDelayMs = retryDelayMs;
            this.reason = reason;
            this.errorType = errorType;
        }
        
        public boolean shouldRetry() { return shouldRetry; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public String getReason() { return reason; }
        public ErrorClassifier.ErrorType getErrorType() { return errorType; }
    }
    
    /**
     * Determine if a failed job should be retried and calculate the delay
     * 
     * @param jobId The job that failed
     * @param exception The exception that caused the failure
     * @param currentRetries Current number of retry attempts
     * @param maxRetries Maximum allowed retries for this job
     * @return RetryDecision with retry logic and delay calculation
     */
    public RetryDecision shouldRetry(String jobId, Throwable exception, int currentRetries, int maxRetries) {
        
        // Classify the error to determine retry eligibility
        ErrorClassifier.ErrorType errorType = ErrorClassifier.classify(exception, currentRetries, maxRetries);
        
        // Check if we should retry based on error classification
        if (errorType == ErrorClassifier.ErrorType.PERMANENT) {
            String reason = String.format("Permanent error: %s (no retry)", exception.getClass().getSimpleName());
            return new RetryDecision(false, 0, reason, errorType);
        }
        
        // Check retry count limits
        if (currentRetries >= maxRetries) {
            String reason = String.format("Max retries exceeded: %d/%d", currentRetries, maxRetries);
            return new RetryDecision(false, 0, reason, errorType);
        }
        
        // Calculate exponential backoff delay with jitter
        long delayMs = calculateRetryDelay(currentRetries);
        
        String reason = String.format("Transient error (retry %d/%d in %dms): %s", 
                                     currentRetries + 1, maxRetries, delayMs, 
                                     exception.getClass().getSimpleName());
        
        Logger.info("Job {} will be retried: {}", jobId, reason);
        return new RetryDecision(true, delayMs, reason, errorType);
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     * Formula: baseDelay * (exponentialBase ^ retryCount) + jitter
     * Jitter prevents thundering herd when many jobs fail simultaneously
     */
    public long calculateRetryDelay(int retryCount) {
        // Calculate base exponential delay: 1s, 4s, 16s, 64s, 256s...
        long baseDelay = BASE_DELAY_MS * (long) Math.pow(EXPONENTIAL_BASE, retryCount);
        
        // Cap at maximum delay
        baseDelay = Math.min(baseDelay, MAX_DELAY_MS);
        
        // Add jitter: ±25% random variation
        long jitterRange = (long) (baseDelay * JITTER_FACTOR);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        
        // Ensure minimum delay of 100ms
        long finalDelay = Math.max(baseDelay + jitter, 100L);
        
        Logger.debug("Calculated retry delay: base={}ms, jitter={}ms, final={}ms (attempt {})", 
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
     * This will be used by the RetryScheduler in Phase 2
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
     * This will be integrated into the main JobQueue statistics
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