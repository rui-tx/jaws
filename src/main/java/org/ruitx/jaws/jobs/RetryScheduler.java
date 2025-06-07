package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.types.Row;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RetryScheduler - Background service for processing delayed retries
 * 
 * This class handles the scheduled retry processing for jobs that have been marked
 * as RETRY_SCHEDULED. It periodically polls the database for jobs ready to retry
 * and re-queues them to the appropriate job queue.
 * 
 * Key features:
 * - Configurable retry check interval (default: 30 seconds)
 * - Batch processing for efficiency
 * - Proper error handling and logging
 * - Integration with both parallel and sequential queues
 * - Statistics tracking for monitoring
 * - Graceful shutdown with job completion
 */
public class RetryScheduler {
    
    private static final long DEFAULT_CHECK_INTERVAL_MS = 30000L; // 30 seconds
    private static final int DEFAULT_BATCH_SIZE = 50; // Process up to 50 retries at once
    
    private final Mimir mimir = new Mimir();
    private final JobRegistry jobRegistry = new JobRegistry();
    private final RetryManager retryManager = new RetryManager();
    private final DeadLetterQueue deadLetterQueue = new DeadLetterQueue();
    
    private final long checkIntervalMs;
    private final int batchSize;
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Statistics
    private final AtomicInteger totalRetriesProcessed = new AtomicInteger(0);
    private final AtomicInteger successfulRetries = new AtomicInteger(0);
    private final AtomicInteger failedRetries = new AtomicInteger(0);
    private final AtomicInteger movedToDeadLetter = new AtomicInteger(0);
    
    /**
     * Constructor with default settings
     */
    public RetryScheduler() {
        this(DEFAULT_CHECK_INTERVAL_MS, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * Constructor with custom settings
     * 
     * @param checkIntervalMs How often to check for ready retries (milliseconds)
     * @param batchSize Maximum number of retries to process per batch
     */
    public RetryScheduler(long checkIntervalMs, int batchSize) {
        this.checkIntervalMs = checkIntervalMs;
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "retry-scheduler"));
        
        Logger.info("RetryScheduler initialized with {}ms interval and batch size {}", 
                   checkIntervalMs, batchSize);
    }
    
    /**
     * Start the retry scheduler
     * This will begin periodic processing of scheduled retries
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Logger.info("Starting RetryScheduler...");
            
            // Schedule the retry processing task
            scheduler.scheduleAtFixedRate(
                this::processScheduledRetries,
                0, // Start immediately
                checkIntervalMs,
                TimeUnit.MILLISECONDS
            );
            
            Logger.info("RetryScheduler started successfully");
        } else {
            Logger.warn("RetryScheduler is already running");
        }
    }
    
    /**
     * Stop the retry scheduler
     * Waits for current processing to complete before shutting down
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            Logger.info("Stopping RetryScheduler...");
            
            scheduler.shutdown();
            
            try {
                // Wait for current processing to complete (up to 60 seconds)
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    Logger.warn("RetryScheduler did not terminate gracefully, forcing shutdown");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            Logger.info("RetryScheduler stopped successfully");
        }
    }
    
    /**
     * Check if the scheduler is running
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Get retry scheduler statistics
     */
    public RetrySchedulerStatistics getStatistics() {
        return new RetrySchedulerStatistics(
            totalRetriesProcessed.get(),
            successfulRetries.get(),
            failedRetries.get(),
            movedToDeadLetter.get(),
            running.get()
        );
    }
    
    /**
     * Manually trigger a retry processing cycle (for testing/admin use)
     * Returns the number of retries processed
     */
    public int processNow() {
        Logger.info("Manual retry processing triggered");
        return processScheduledRetries();
    }
    
    // Private implementation methods
    
    /**
     * Main processing method - finds and processes ready retries
     */
    private int processScheduledRetries() {
        if (!running.get()) {
            return 0; // Skip if shutting down
        }
        
        try {
            long now = Instant.now().toEpochMilli();
            
            // Find jobs ready for retry
            List<Row> readyJobs = mimir.getRows("""
                SELECT * FROM JOBS 
                WHERE status = ? AND next_retry_at <= ? 
                ORDER BY next_retry_at ASC 
                LIMIT ?
                """, 
                JobQueue.JobStatus.RETRY_SCHEDULED.name(), now, batchSize
            );
            
            if (readyJobs.isEmpty()) {
                return 0; // No retries ready
            }
            
            Logger.info("Processing {} scheduled retries", readyJobs.size());
            
            int processedCount = 0;
            for (Row jobRow : readyJobs) {
                try {
                    if (processRetryJob(jobRow)) {
                        processedCount++;
                    }
                } catch (Exception e) {
                    Logger.error("Failed to process retry for job {}: {}", 
                               jobRow.getString("id").orElse("unknown"), e.getMessage(), e);
                    failedRetries.incrementAndGet();
                }
            }
            
            totalRetriesProcessed.addAndGet(processedCount);
            
            if (processedCount > 0) {
                Logger.info("Successfully processed {} retry jobs", processedCount);
            }
            
            return processedCount;
            
        } catch (Exception e) {
            Logger.error("Failed to process scheduled retries: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Process a single retry job
     * Returns true if successfully processed, false otherwise
     */
    private boolean processRetryJob(Row jobRow) {
        String jobId = jobRow.getString("id").orElse(null);
        if (jobId == null) {
            Logger.error("Cannot process retry - job ID is null");
            return false;
        }
        
        try {
            // Extract job information
            String jobType = jobRow.getString("type").orElse("");
            String payloadJson = jobRow.getString("payload").orElse("{}");
            String executionMode = jobRow.getString("execution_mode").orElse("PARALLEL");
            int currentRetries = jobRow.getInt("current_retries").orElse(0);
            int maxRetries = jobRow.getInt("max_retries").orElse(3);
            
            // Check if we've exceeded max retries (safety check)
            if (currentRetries >= maxRetries) {
                Logger.warn("Job {} has exceeded max retries ({}/{}), moving to dead letter queue", 
                          jobId, currentRetries, maxRetries);
                
                String reason = String.format("Max retries exceeded during scheduled retry (%d/%d)", 
                                             currentRetries, maxRetries);
                if (deadLetterQueue.moveToDeadLetterQueue(jobId, reason)) {
                    movedToDeadLetter.incrementAndGet();
                }
                return true; // Processed (moved to DLQ)
            }
            
            // Parse the job payload
            Map<String, Object> payload = Odin.getMapper().readValue(payloadJson, Map.class);
            
            // Create a new job instance for retry
            Job retryJob = jobRegistry.createJob(jobType, payload);
            if (retryJob == null) {
                Logger.error("Cannot create retry job - unknown job type: {}", jobType);
                return false;
            }
            
            // Update the job status back to PENDING and reset for retry
            long now = Instant.now().toEpochMilli();
            int updated = mimir.executeSql("""
                UPDATE JOBS SET 
                    status = ?,
                    error_message = NULL,
                    next_retry_at = 0,
                    retry_backoff_ms = 1000,
                    last_retry_at = ?
                WHERE id = ?
                """,
                JobQueue.JobStatus.PENDING.name(),
                now,
                jobId
            );
            
            if (updated == 0) {
                Logger.error("Failed to update job status for retry: {}", jobId);
                return false;
            }
            
            // Submit the job to the appropriate queue
            JobQueue jobQueue = JobQueue.getInstance();
            
            // Submit normally - JobQueue will route to the correct queue based on execution mode
            jobQueue.submit(retryJob);
            
            successfulRetries.incrementAndGet();
            Logger.info("Successfully re-queued retry job: {} (attempt {}/{})", 
                       jobId, currentRetries + 1, maxRetries);
            
            return true;
            
        } catch (Exception e) {
            Logger.error("Failed to process retry job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Retry scheduler statistics
     */
    public static class RetrySchedulerStatistics {
        private final int totalRetriesProcessed;
        private final int successfulRetries;
        private final int failedRetries;
        private final int movedToDeadLetter;
        private final boolean running;
        
        public RetrySchedulerStatistics(int totalRetriesProcessed, int successfulRetries, 
                                      int failedRetries, int movedToDeadLetter, boolean running) {
            this.totalRetriesProcessed = totalRetriesProcessed;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
            this.movedToDeadLetter = movedToDeadLetter;
            this.running = running;
        }
        
        public int getTotalRetriesProcessed() { return totalRetriesProcessed; }
        public int getSuccessfulRetries() { return successfulRetries; }
        public int getFailedRetries() { return failedRetries; }
        public int getMovedToDeadLetter() { return movedToDeadLetter; }
        public boolean isRunning() { return running; }
    }
} 