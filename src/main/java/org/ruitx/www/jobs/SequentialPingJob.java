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
 * SequentialPingJob - Simple sequential job for testing the sequential processing system
 */
public class SequentialPingJob extends BaseJob {
    
    public static final String JOB_TYPE = "sequential-ping";
    
    /**
     * Constructor - uses SEQUENTIAL execution mode
     */
    public SequentialPingJob(Map<String, Object> payload) {
        // Using SEQUENTIAL execution mode with high priority (1) and short timeout
        super(JOB_TYPE, ExecutionMode.SEQUENTIAL, 1, 1, 15000L, payload); // priority 1, 1 retry, 15s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting sequential ping job: {}", getId());
        
        try {
            // Get ping parameters from payload
            String message = getString("message");
            Integer delayMs = getInteger("delayMs");
            Integer pingNumber = getInteger("pingNumber");
            
            // Default values
            if (message == null) message = "Sequential Ping";
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
            
            Logger.info("Sequential ping job completed: {} (ping #{}, delay: {}ms)", getId(), pingNumber, delayMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Sequential ping job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Ping was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("Sequential ping job failed: {}", e.getMessage(), e);
            
            // Store error result with details
            String errorMessage = String.format("Ping failed: %s", e.getMessage());
            JobResultStore.storeError(getId(), 500, errorMessage);
            throw e;
        }
    }
} 