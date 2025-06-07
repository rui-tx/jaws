package org.ruitx.www.demo;

import org.ruitx.jaws.jobs.JobQueue;
import org.ruitx.jaws.jobs.JobResult;
import org.ruitx.www.jobs.*;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * RetrySystemDemo - Demonstrates the new retry functionality with exponential backoff
 * 
 * This demo shows:
 * 1. Jobs that fail with transient errors get retried automatically
 * 2. Jobs that fail with permanent errors are marked as failed immediately
 * 3. Exponential backoff with jitter prevents thundering herd
 * 4. Retry statistics are tracked and reported
 * 5. Both parallel and sequential jobs support retry logic
 */
public class RetrySystemDemo {
    
    private final JobQueue jobQueue = JobQueue.getInstance();
    
    public static void main(String[] args) {
        RetrySystemDemo demo = new RetrySystemDemo();
        demo.runRetryDemo();
    }
    
    public void runRetryDemo() {
        Logger.info("=== RETRY SYSTEM DEMO ===");
        
        // Start the job processing system
        jobQueue.start();
        
        try {
            // Demo 1: Submit a job that will succeed after retries (transient failure)
            demonstrateTransientFailureRetry();
            
            // Demo 2: Submit a job that will fail permanently (permanent failure)
            demonstratePermanentFailure();
            
            // Demo 3: Submit jobs to both parallel and sequential queues
            demonstrateRetryInBothQueues();
            
            // Demo 4: Monitor retry statistics
            monitorRetryStatistics();
            
            // Wait for jobs to complete and retry
            Logger.info("Waiting for jobs to complete and retry...");
            Thread.sleep(30000); // 30 seconds to see retries in action
            
        } catch (Exception e) {
            Logger.error("Demo failed: {}", e.getMessage(), e);
        } finally {
            // Shutdown the system
            jobQueue.shutdown();
        }
        
        Logger.info("=== RETRY DEMO COMPLETE ===");
    }
    
    /**
     * Demo 1: Job that fails with transient errors but eventually succeeds
     */
    private void demonstrateTransientFailureRetry() {
        Logger.info("--- Demo 1: Transient Failure with Retry ---");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("failTimes", 2); // Fail 2 times, succeed on attempt 3
        payload.put("failureType", "transient"); // IOException (network error)
        payload.put("testMessage", "Transient Failure Test");
        
        RetryTestJob job = new RetryTestJob(payload);
        String jobId = jobQueue.submit(job);
        
        Logger.info("Submitted transient failure test job: {}", jobId);
        Logger.info("This job will fail twice with IOException, then succeed on attempt 3");
        Logger.info("Expected retry delays: ~1s, ~4s (with jitter)");
    }
    
    /**
     * Demo 2: Job that fails with permanent error (no retry)
     */
    private void demonstratePermanentFailure() {
        Logger.info("--- Demo 2: Permanent Failure (No Retry) ---");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("failTimes", 5); // Would normally fail many times
        payload.put("failureType", "permanent"); // IllegalArgumentException
        payload.put("testMessage", "Permanent Failure Test");
        
        RetryTestJob job = new RetryTestJob(payload);
        String jobId = jobQueue.submit(job);
        
        Logger.info("Submitted permanent failure test job: {}", jobId);
        Logger.info("This job will fail immediately with IllegalArgumentException");
        Logger.info("Expected behavior: No retries, marked as FAILED immediately");
    }
    
    /**
     * Demo 3: Test retry behavior in both parallel and sequential queues
     */
    private void demonstrateRetryInBothQueues() {
        Logger.info("--- Demo 3: Retry in Both Queue Types ---");
        
        // Parallel queue retry test
        Map<String, Object> parallelPayload = new HashMap<>();
        parallelPayload.put("failTimes", 1); // Fail once, succeed on attempt 2
        parallelPayload.put("failureType", "transient");
        parallelPayload.put("testMessage", "Parallel Retry Test");
        
        RetryTestJob parallelJob = new RetryTestJob(parallelPayload);
        String parallelJobId = jobQueue.submit(parallelJob);
        
        // Sequential queue retry test (using SequentialPingJob with simulated failure)
        Map<String, Object> sequentialPayload = new HashMap<>();
        sequentialPayload.put("message", "Sequential Retry Test");
        sequentialPayload.put("delayMs", 500);
        sequentialPayload.put("pingNumber", 1);
        
        SequentialPingJob sequentialJob = new SequentialPingJob(sequentialPayload);
        String sequentialJobId = jobQueue.submit(sequentialJob);
        
        Logger.info("Submitted parallel retry test: {}", parallelJobId);
        Logger.info("Submitted sequential test: {}", sequentialJobId);
        Logger.info("Both jobs support the same retry logic!");
    }
    
    /**
     * Demo 4: Monitor and display retry statistics
     */
    private void monitorRetryStatistics() {
        Logger.info("--- Demo 4: Monitoring Retry Statistics ---");
        
        // Monitor statistics over time
        for (int i = 0; i < 6; i++) {
            try {
                Thread.sleep(5000); // Check every 5 seconds
                
                Map<String, Object> stats = jobQueue.getStatistics();
                
                Logger.info("=== Retry Statistics ({}s) ===", i * 5);
                Logger.info("Total Jobs: {}", stats.get("totalJobs"));
                Logger.info("Completed Jobs: {}", stats.get("completedJobs"));
                Logger.info("Failed Jobs: {}", stats.get("failedJobs"));
                Logger.info("Retried Jobs: {}", stats.get("retriedJobs"));
                
                @SuppressWarnings("unchecked")
                Map<String, Object> retryStats = (Map<String, Object>) stats.get("retry");
                if (retryStats != null) {
                    Logger.info("Retry Scheduled: {}", retryStats.get("retryScheduledJobs"));
                    Logger.info("Permanently Failed: {}", retryStats.get("permanentlyFailedJobs"));
                    Logger.info("Total Retry Attempts: {}", retryStats.get("totalRetryAttempts"));
                }
                
                Logger.info("=== End Statistics ===");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
} 