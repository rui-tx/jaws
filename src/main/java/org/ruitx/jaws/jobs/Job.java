package org.ruitx.jaws.jobs;

import java.util.Map;

/**
 * Core interface for background jobs.
 * 
 * This replaces the complex route-based async system with a clean,
 * framework-agnostic job system. Jobs are self-contained units of work
 * that carry all the data they need to execute.
 */
public interface Job {
    
    /**
     * Unique identifier for this job instance
     */
    String getId();
    
    /**
     * Job type identifier used for routing to appropriate handlers
     * Examples: "heavy-computation", "external-api-call", "user-processing"
     */
    String getType();
    
    /**
     * Priority level (1-10, where 1 is highest priority)
     */
    int getPriority();
    
    /**
     * Maximum number of retry attempts if the job fails
     */
    int getMaxRetries();
    
    /**
     * Timeout for job execution in milliseconds
     */
    long getTimeoutMs();
    
    /**
     * Job payload containing all data needed for execution
     * This includes request data, user context, etc.
     */
    Map<String, Object> getPayload();
    
    /**
     * Execute the job logic.
     * 
     * This method should contain all the business logic that was previously
     * in the async route methods. It has access to all data through the payload
     * and should store its result using JobResultStore.
     * 
     * @throws Exception if job execution fails
     */
    void execute() throws Exception;
    
    /**
     * Optional: Client ID for tracking (e.g., user session, IP address)
     */
    default String getClientId() {
        return (String) getPayload().get("clientId");
    }
    
    /**
     * Optional: User ID for tracking
     */
    default Integer getUserId() {
        Object userId = getPayload().get("userId");
        return userId instanceof Integer ? (Integer) userId : null;
    }
    
    /**
     * Optional: Custom metadata for the job
     */
    default Map<String, Object> getMetadata() {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) getPayload().get("metadata");
        return metadata != null ? metadata : Map.of();
    }
} 