package org.ruitx.www.demo;

import org.ruitx.jaws.jobs.DeadLetterQueue;
import org.ruitx.jaws.jobs.JobQueue;
import org.ruitx.jaws.jobs.RetryScheduler;
import org.ruitx.www.jobs.RetryTestJob;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase2Demo - Demonstrates the complete retry system with Dead Letter Queue and Retry Scheduler
 * 
 * This demo showcases all Phase 2 features:
 * 1. Jobs that fail permanently go to Dead Letter Queue
 * 2. Jobs that fail transiently get retried by the Retry Scheduler
 * 3. Manual retry operations from DLQ
 * 4. Comprehensive monitoring and statistics
 * 5. Batch operations and DLQ management
 */
public class Phase2Demo {
    
    private final JobQueue jobQueue = JobQueue.getInstance();
    private final DeadLetterQueue deadLetterQueue;
    private final RetryScheduler retryScheduler;
    
    public Phase2Demo() {
        this.deadLetterQueue = jobQueue.getDeadLetterQueue();
        this.retryScheduler = jobQueue.getRetryScheduler();
    }
    
    public static void main(String[] args) {
        Phase2Demo demo = new Phase2Demo();
        demo.runPhase2Demo();
    }
    
    public void runPhase2Demo() {
        Logger.info("=== PHASE 2 RETRY & DEAD LETTER QUEUE DEMO ===");
        
        // Start the job processing system
        jobQueue.start();
        
        try {
            // Demo 1: Submit jobs that will succeed after retries
            demonstrateTransientFailuresWithRetryScheduler();
            
            // Demo 2: Submit jobs that will fail permanently and go to DLQ
            demonstratePermanentFailuresAndDLQ();
            
            // Demo 3: Monitor retry scheduler in action
            monitorRetryScheduler();
            
            // Demo 4: DLQ operations - view, filter, and manual retry
            demonstrateDLQOperations();
            
            // Demo 5: Show comprehensive monitoring
            demonstratePhase2Monitoring();
            
            // Wait for retries and processing to complete
            Logger.info("Waiting for retries and DLQ processing...");
            Thread.sleep(60000); // 1 minute to see retries happen
            
            // Final statistics
            showFinalStatistics();
            
        } catch (Exception e) {
            Logger.error("Demo failed: {}", e.getMessage(), e);
        } finally {
            // Shutdown the system
            jobQueue.shutdown();
        }
        
        Logger.info("=== PHASE 2 DEMO COMPLETE ===");
    }
    
    /**
     * Demo 1: Jobs that fail transiently and get retried automatically
     */
    private void demonstrateTransientFailuresWithRetryScheduler() {
        Logger.info("--- Demo 1: Transient Failures with Retry Scheduler ---");
        
        // Submit jobs that will fail 2 times, then succeed on attempt 3
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("failTimes", 2); // Fail twice, succeed on 3rd attempt
            payload.put("failureType", "transient");
            payload.put("testMessage", "Transient Test " + i);
            
            RetryTestJob job = new RetryTestJob(payload);
            String jobId = jobQueue.submit(job);
            
            Logger.info("Submitted transient failure job {}: {}", i, jobId);
        }
        
        Logger.info("These jobs will fail initially, get scheduled for retry, and eventually succeed!");
    }
    
    /**
     * Demo 2: Jobs that fail permanently and get moved to DLQ
     */
    private void demonstratePermanentFailuresAndDLQ() {
        Logger.info("--- Demo 2: Permanent Failures and Dead Letter Queue ---");
        
        // Submit jobs that will fail permanently
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("failTimes", 0); // Fail immediately
            payload.put("failureType", "permanent");
            payload.put("testMessage", "Permanent Failure Test " + i);
            
            RetryTestJob job = new RetryTestJob(payload);
            String jobId = jobQueue.submit(job);
            
            Logger.info("Submitted permanent failure job {}: {}", i, jobId);
        }
        
        Logger.info("These jobs will fail permanently and move to Dead Letter Queue!");
    }
    
    /**
     * Demo 3: Monitor the retry scheduler processing scheduled retries
     */
    private void monitorRetryScheduler() {
        Logger.info("--- Demo 3: Retry Scheduler Monitoring ---");
        
        // Show retry scheduler statistics
        RetryScheduler.RetrySchedulerStatistics stats = retryScheduler.getStatistics();
        Logger.info("Retry Scheduler Status:");
        Logger.info("- Running: {}", stats.isRunning());
        Logger.info("- Total Retries Processed: {}", stats.getTotalRetriesProcessed());
        Logger.info("- Successful Retries: {}", stats.getSuccessfulRetries());
        Logger.info("- Failed Retries: {}", stats.getFailedRetries());
        Logger.info("- Moved to DLQ: {}", stats.getMovedToDeadLetter());
        
        // Manually trigger retry processing (for demo purposes)
        Logger.info("Manually triggering retry processing...");
        int processed = retryScheduler.processNow();
        Logger.info("Processed {} retries immediately", processed);
    }
    
    /**
     * Demo 4: Dead Letter Queue operations
     */
    private void demonstrateDLQOperations() {
        Logger.info("--- Demo 4: Dead Letter Queue Operations ---");
        
        // Wait a bit for jobs to reach DLQ
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Show DLQ statistics
        DeadLetterQueue.DLQStatistics dlqStats = deadLetterQueue.getStatistics();
        Logger.info("DLQ Statistics:");
        Logger.info("- Total Entries: {}", dlqStats.getTotalEntries());
        Logger.info("- Retryable Entries: {}", dlqStats.getRetryableEntries());
        Logger.info("- Entries by Type: {}", dlqStats.getEntriesByType());
        
        // Get all DLQ entries
        List<DeadLetterQueue.DLQEntry> entries = deadLetterQueue.getDLQEntries(null, null, 10);
        Logger.info("Found {} entries in Dead Letter Queue:", entries.size());
        
        for (DeadLetterQueue.DLQEntry entry : entries) {
            Logger.info("- DLQ Entry: {} (Job: {}, Type: {}, Reason: {})", 
                       entry.getId(), entry.getOriginalJobId(), entry.getJobType(), entry.getFailureReason());
            
            // Try to manually retry this entry if it's retryable
            if (entry.canBeRetried()) {
                Logger.info("  Attempting manual retry of DLQ entry: {}", entry.getId());
                String newJobId = deadLetterQueue.manualRetry(entry.getId(), true);
                if (newJobId != null) {
                    Logger.info("  Successfully retried as new job: {}", newJobId);
                } else {
                    Logger.warn("  Failed to retry DLQ entry: {}", entry.getId());
                }
            } else {
                Logger.info("  Entry is marked as non-retryable");
            }
        }
    }
    
    /**
     * Demo 5: Comprehensive Phase 2 monitoring
     */
    private void demonstratePhase2Monitoring() {
        Logger.info("--- Demo 5: Phase 2 Comprehensive Monitoring ---");
        
        Map<String, Object> fullStats = jobQueue.getStatistics();
        
        // Basic job statistics
        Logger.info("Job Processing Statistics:");
        Logger.info("- Total Jobs: {}", fullStats.get("totalJobs"));
        Logger.info("- Completed: {}", fullStats.get("completedJobs"));
        Logger.info("- Failed: {}", fullStats.get("failedJobs"));
        Logger.info("- Retried: {}", fullStats.get("retriedJobs"));
        
        // Retry system statistics
        @SuppressWarnings("unchecked")
        Map<String, Object> retryStats = (Map<String, Object>) fullStats.get("retry");
        if (retryStats != null) {
            Logger.info("Retry System Statistics:");
            Logger.info("- Retry Scheduled Jobs: {}", retryStats.get("retryScheduledJobs"));
            Logger.info("- Permanently Failed Jobs: {}", retryStats.get("permanentlyFailedJobs"));
            Logger.info("- Total Retry Attempts: {}", retryStats.get("totalRetryAttempts"));
        }
        
        // Dead Letter Queue statistics
        @SuppressWarnings("unchecked")
        Map<String, Object> dlqStats = (Map<String, Object>) fullStats.get("deadLetterQueue");
        if (dlqStats != null) {
            Logger.info("Dead Letter Queue Statistics:");
            Logger.info("- Total Entries: {}", dlqStats.get("totalEntries"));
            Logger.info("- Retryable Entries: {}", dlqStats.get("retryableEntries"));
            Logger.info("- Entries by Type: {}", dlqStats.get("entriesByType"));
        }
        
        // Retry Scheduler statistics
        @SuppressWarnings("unchecked")
        Map<String, Object> schedulerStats = (Map<String, Object>) fullStats.get("retryScheduler");
        if (schedulerStats != null) {
            Logger.info("Retry Scheduler Statistics:");
            Logger.info("- Total Retries Processed: {}", schedulerStats.get("totalRetriesProcessed"));
            Logger.info("- Successful Retries: {}", schedulerStats.get("successfulRetries"));
            Logger.info("- Failed Retries: {}", schedulerStats.get("failedRetries"));
            Logger.info("- Moved to DLQ: {}", schedulerStats.get("movedToDeadLetter"));
            Logger.info("- Running: {}", schedulerStats.get("running"));
        }
    }
    
    /**
     * Show final statistics after all processing
     */
    private void showFinalStatistics() {
        Logger.info("--- Final Statistics Summary ---");
        
        Map<String, Object> stats = jobQueue.getStatistics();
        
        Logger.info("FINAL RESULTS:");
        Logger.info("✓ Total Jobs Submitted: {}", stats.get("totalJobs"));
        Logger.info("✓ Successfully Completed: {}", stats.get("completedJobs"));
        Logger.info("✓ Jobs Retried: {}", stats.get("retriedJobs"));
        Logger.info("✓ Permanently Failed: {}", stats.get("failedJobs"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> dlqStats = (Map<String, Object>) stats.get("deadLetterQueue");
        if (dlqStats != null) {
            Logger.info("✓ Jobs in Dead Letter Queue: {}", dlqStats.get("totalEntries"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> schedulerStats = (Map<String, Object>) stats.get("retryScheduler");
        if (schedulerStats != null) {
            Logger.info("✓ Retries Processed by Scheduler: {}", schedulerStats.get("totalRetriesProcessed"));
        }
        
        Logger.info("Phase 2 retry system is production-ready! 🚀");
    }
} 