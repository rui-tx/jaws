package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.jobs.BaseJob;
import org.ruitx.jaws.jobs.JobResultStore;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * RetryTestJob - Demonstrates retry functionality with exponential backoff
 * 
 * This job intentionally fails a configurable number of times before succeeding,
 * allowing us to test the retry mechanism, exponential backoff, and jitter.
 * 
 * Perfect for testing and demonstrating the production-ready retry system.
 */
public class RetryTestJob extends BaseJob {
    
    public static final String JOB_TYPE = "retry-test";
    
    /**
     * Constructor - configurable retry behavior for testing
     */
    public RetryTestJob(Map<String, Object> payload) {
        super(JOB_TYPE, 5, 3, 30000L, payload); // priority 5, 3 retries, 30s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting retry test job: {}", getId());
        
        try {
            // Get test parameters from payload
            Integer failTimes = getInteger("failTimes");
            String failureType = getString("failureType");
            String testMessage = getString("testMessage");
            
            // Default values
            if (failTimes == null) failTimes = 2; // Fail 2 times, succeed on 3rd attempt
            if (failureType == null) failureType = "transient"; // "transient" or "permanent"
            if (testMessage == null) testMessage = "Retry Test";
            
            // Simulate some processing time
            Thread.sleep(1000);
            
            // Check if we should fail this time
            boolean shouldFail = shouldFailThisAttempt(failTimes);
            
            if (shouldFail) {
                // Throw appropriate exception type based on test configuration
                if ("permanent".equals(failureType)) {
                    throw new IllegalArgumentException("Permanent error: Invalid test configuration");
                } else {
                    throw new java.io.IOException("Transient error: Network temporarily unavailable");
                }
            }
            
            // Success! Create result
            Map<String, Object> result = new HashMap<>();
            result.put("testCompleted", true);
            result.put("testMessage", testMessage);
            result.put("failureType", failureType);
            result.put("configuredFailTimes", failTimes);
            result.put("actualAttempts", getCurrentAttemptNumber());
            result.put("executedAt", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            result.put("message", "Retry test completed successfully!");
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("Retry test job completed successfully after {} attempts: {}", 
                       getCurrentAttemptNumber(), getId());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Retry test job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Test was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("Retry test job failed (attempt {}): {}", getCurrentAttemptNumber(), e.getMessage(), e);
            
            // Store error result with details
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("testFailed", true);
            errorResult.put("attemptNumber", getCurrentAttemptNumber());
            errorResult.put("errorType", e.getClass().getSimpleName());
            errorResult.put("errorMessage", e.getMessage());
            errorResult.put("jobId", getId());
            
            String errorJson = Odin.getMapper().writeValueAsString(errorResult);
            JobResultStore.storeError(getId(), 500, errorJson);
            throw e;
        }
    }
    
    /**
     * Determine if this job should fail based on the current attempt number
     */
    private boolean shouldFailThisAttempt(int configuredFailTimes) {
        int currentAttempt = getCurrentAttemptNumber();
        boolean shouldFail = currentAttempt <= configuredFailTimes;
        
        Logger.info("Retry test job {} - attempt {} of max {}, should fail: {}", 
                   getId(), currentAttempt, configuredFailTimes + 1, shouldFail);
        
        return shouldFail;
    }
    
    /**
     * Get the current attempt number (1-based)
     * This simulates checking how many times this specific job has been attempted
     */
    private int getCurrentAttemptNumber() {
        // For testing purposes, we'll use a simple simulation
        // In a real scenario, this would be tracked in the database
        
        // Check if this is stored in the payload (for testing)
        Integer storedAttempt = getInteger("currentAttempt");
        if (storedAttempt != null) {
            return storedAttempt;
        }
        
        // Default to attempt 1
        return 1;
    }
} 