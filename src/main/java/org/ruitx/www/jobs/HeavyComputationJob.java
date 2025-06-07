package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.jobs.BaseJob;
import org.ruitx.jaws.jobs.JobResultStore;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * HeavyComputationJob - Replaces heavyComputationalTask async route method
 * 
 * This job performs heavy computational work without depending on the web framework.
 * All data needed for execution is contained in the payload.
 * 
 * Migration from:
 * @Route(endpoint = API_ENDPOINT + "heavy-task", method = POST)
 * @Async(timeout = 60000, priority = 7)
 * public void heavyComputationalTask()
 */
public class HeavyComputationJob extends BaseJob {
    
    public static final String JOB_TYPE = "heavy-computation";
    
    /**
     * Constructor - jobs must have a constructor that takes Map<String, Object>
     */
    public HeavyComputationJob(Map<String, Object> payload) {
        super(JOB_TYPE, 7, 3, 60000L, payload); // priority 7, 3 retries, 60s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting heavy computation job: {}", getId());
        
        try {
            // Simulate heavy computational work
            // In the original async method, this was Thread.sleep(60000)
            // For demonstration, we'll use a shorter time
            long computationTime = getLong("computationTimeMs") != null ? getLong("computationTimeMs") : 5000L;
            
            Logger.info("Performing heavy computation for {} ms...", computationTime);
            Thread.sleep(computationTime);
            
            // Create result - this replaces sendSuccessfulResponse(OK, result)
            Map<String, Object> result = new HashMap<>();
            result.put("taskCompleted", true);
            result.put("processingTime", computationTime + " ms");
            result.put("result", "Heavy computation completed successfully");
            result.put("timestamp", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            
            // Additional data from payload if available
            if (getString("taskName") != null) {
                result.put("taskName", getString("taskName"));
            }
            
            // Store the result (replaces the web framework response)
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("Heavy computation job completed: {}", getId());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Heavy computation job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Task was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("Heavy computation job failed: {}", e.getMessage(), e);
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Computation failed: " + e.getMessage());
            throw e;
        }
    }
} 