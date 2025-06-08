package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.freyr.BaseJob;
import org.ruitx.jaws.components.freyr.ExecutionMode;
import org.ruitx.jaws.components.freyr.JobResultStore;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ParallelPingJob - Simple parallel job for testing the parallel processing system
 * 
 */
public class ParallelPingJob extends BaseJob {
    
    public static final String JOB_TYPE = "parallel-ping";
    
    /**
     * Constructor - uses PARALLEL execution mode
     */
    public ParallelPingJob(Map<String, Object> payload) {
        // Using PARALLEL execution mode with high priority (1) and short timeout
        super(JOB_TYPE, ExecutionMode.PARALLEL, 1, 1, 15000L, payload); // priority 1, 1 retry, 15s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting parallel ping job: {}", getId());
        
        try {
            // Get ping parameters from payload
            String message = getString("message");
            Integer delayMs = getInteger("delayMs");
            Integer pingNumber = getInteger("pingNumber");
            
            // Default values
            if (message == null) message = "Parallel Ping";
            if (delayMs == null) delayMs = 2000; // 2 second default delay
            if (pingNumber == null) pingNumber = 1;
            
            Logger.info("Processing {} #{} with {}ms delay", message, pingNumber, delayMs);
            
            // Simulate processing time
            Thread.sleep(delayMs);
            
            // Create successful result
            Map<String, Object> result = new HashMap<>();
            result.put("pingCompleted", true);
            result.put("message", message);
            result.put("pingNumber", pingNumber);
            result.put("delayMs", delayMs);
            result.put("executedAt", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            result.put("executionMode", getExecutionMode().name());
            result.put("processingTime", delayMs + "ms (simulated)");
            
            // Additional metadata from payload
            if (getString("submittedBy") != null) {
                result.put("submittedBy", getString("submittedBy"));
            }
            if (getString("clientId") != null) {
                result.put("clientId", getString("clientId"));
            }
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("Parallel ping job completed: {} (ping #{}, delay: {}ms)", getId(), pingNumber, delayMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Parallel ping job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Ping was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("Parallel ping job failed: {}", e.getMessage(), e);
            
            // Store error result with details
            String errorMessage = String.format("Ping failed: %s", e.getMessage());
            JobResultStore.storeError(getId(), 500, errorMessage);
            throw e;
        }
    }
} 