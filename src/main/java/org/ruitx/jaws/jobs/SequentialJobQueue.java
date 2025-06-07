package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SequentialJobQueue - Processes jobs one at a time in FIFO order
 * 
 * This queue ensures that sequential jobs are processed exactly one at a time,
 * in the order they were submitted. Perfect for tasks that must not run concurrently,
 * such as database migrations, file system operations, or critical business processes.
 * 
 * Key features:
 * - Single worker thread
 * - FIFO processing order
 * - Graceful shutdown with job completion
 * - Integration with main JobQueue system
 */
public class SequentialJobQueue {
    
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    
    private final Mimir mimir = new Mimir();
    private final BlockingQueue<Job> sequentialQueue;
    private final ExecutorService singleWorker;
    private final RetryManager retryManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processingJob = new AtomicBoolean(false);
    
    // Statistics
    private final AtomicInteger totalJobs = new AtomicInteger(0);   
    private final AtomicInteger completedJobs = new AtomicInteger(0);
    private final AtomicInteger failedJobs = new AtomicInteger(0);
    private final AtomicInteger retriedJobs = new AtomicInteger(0);
    
    public SequentialJobQueue() {
        this.sequentialQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
        this.singleWorker = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "sequential-job-worker"));
        this.retryManager = new RetryManager();
        
        Logger.info("SequentialJobQueue initialized with queue capacity of {}", DEFAULT_QUEUE_CAPACITY);
    }
    
    /**
     * Submit a job for sequential processing
     */
    public boolean submit(Job job) {
        try {
            boolean added = sequentialQueue.offer(job);
            if (added) {
                totalJobs.incrementAndGet();
                Logger.info("Sequential job queued: {}", job);
            } else {
                Logger.warn("Sequential queue is full, rejected job: {}", job);
            }
            return added;
        } catch (Exception e) {
            Logger.error("Failed to submit sequential job: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Start the sequential processing system
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Logger.info("Starting SequentialJobQueue processing system...");
            singleWorker.execute(new SequentialJobWorker());
            Logger.info("SequentialJobQueue started with single worker thread");
        }
    }
    
    /**
     * Shutdown the sequential processing system
     * Waits for current job to complete before shutting down
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            Logger.info("Shutting down SequentialJobQueue processing system...");
            
            singleWorker.shutdown();
            
            try {
                // Wait for current job to complete (up to 60 seconds)
                if (!singleWorker.awaitTermination(60, TimeUnit.SECONDS)) {
                    Logger.warn("Sequential worker did not terminate gracefully, forcing shutdown");
                    singleWorker.shutdownNow();
                }
            } catch (InterruptedException e) {
                singleWorker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            Logger.info("SequentialJobQueue shutdown complete");
        }
    }
    
    /**
     * Get queue statistics
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "totalJobs", totalJobs.get(),
            "completedJobs", completedJobs.get(),
            "failedJobs", failedJobs.get(),
            "retriedJobs", retriedJobs.get(),
            "queueSize", sequentialQueue.size(),
            "processingJob", processingJob.get(),
            "running", running.get()
        );
    }
    
    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return sequentialQueue.size();
    }
    
    /**
     * Check if currently processing a job
     */
    public boolean isProcessingJob() {
        return processingJob.get();
    }
    
    /**
     * Worker thread that processes jobs sequentially
     */
    private class SequentialJobWorker implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("sequential-job-worker");
            Logger.info("Sequential job worker started");
            
            while (running.get()) {
                try {
                    // Wait for next job (with timeout to check running status)
                    Job job = sequentialQueue.poll(1, TimeUnit.SECONDS);
                    if (job != null) {
                        processJob(job);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Logger.info("Sequential job worker interrupted");
                    break;
                } catch (Exception e) {
                    Logger.error("Sequential job worker error: {}", e.getMessage(), e);
                }
            }
            
            Logger.info("Sequential job worker stopped");
        }
        
        private void processJob(Job job) {
            processingJob.set(true);
            
            try {
                Logger.info("Processing sequential job: {}", job);
                
                // Update status to processing
                updateJobStatus(job.getId(), JobQueue.JobStatus.PROCESSING, null, Instant.now().toEpochMilli(), null);
                
                // Execute the job
                job.execute();
                
                // Mark as completed
                updateJobStatus(job.getId(), JobQueue.JobStatus.COMPLETED, null, null, Instant.now().toEpochMilli());
                
                completedJobs.incrementAndGet();
                Logger.info("Completed sequential job: {}", job.getId());
                
            } catch (Exception e) {
                Logger.error("Failed to process sequential job {}: {}", job.getId(), e.getMessage(), e);
                
                // Get current retry count from database (needed for accurate decision)
                int currentRetries = retryManager.getCurrentRetryCount(job.getId());
                
                // Determine if we should retry this job
                RetryManager.RetryDecision decision = retryManager.shouldRetry(
                    job.getId(), e, currentRetries, job.getMaxRetries());
                
                if (decision.shouldRetry()) {
                    // Schedule for retry with exponential backoff
                    retryManager.scheduleRetry(job.getId(), decision.getRetryDelayMs(), e);
                    retriedJobs.incrementAndGet();
                    Logger.info("Sequential job {} scheduled for retry: {}", job.getId(), decision.getReason());
                } else {
                    // Mark as permanently failed
                    retryManager.markAsPermanentlyFailed(job.getId(), e, decision.getReason());
                    failedJobs.incrementAndGet();
                    Logger.warn("Sequential job {} permanently failed: {}", job.getId(), decision.getReason());
                }
                
            } finally {
                processingJob.set(false);
            }
        }
        
        private void updateJobStatus(String jobId, JobQueue.JobStatus status, String errorMessage, Long startedAt, Long completedAt) {
            try {
                StringBuilder sql = new StringBuilder("UPDATE JOBS SET status = ?");
                int paramIndex = 2;
                
                if (errorMessage != null) {
                    sql.append(", error_message = ?");
                }
                if (startedAt != null) {
                    sql.append(", started_at = ?");
                }
                if (completedAt != null) {
                    sql.append(", completed_at = ?");
                }
                
                sql.append(" WHERE id = ?");
                
                Object[] params = new Object[paramIndex + (errorMessage != null ? 1 : 0) + 
                                             (startedAt != null ? 1 : 0) + (completedAt != null ? 1 : 0)];
                int idx = 0;
                params[idx++] = status.name();
                
                if (errorMessage != null) {
                    params[idx++] = errorMessage;
                }
                if (startedAt != null) {
                    params[idx++] = startedAt;
                }
                if (completedAt != null) {
                    params[idx++] = completedAt;
                }
                
                params[idx] = jobId;
                
                mimir.executeSql(sql.toString(), params);
                
            } catch (Exception e) {
                Logger.error("Failed to update job status for sequential job {}: {}", jobId, e.getMessage());
            }
        }
    }
} 