package org.ruitx.jaws.components.freyr;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of a job execution.
 */
public class JobResult {
    
    private final String jobId;
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final String contentType;
    private final long createdAt;
    private final long expiresAt;
    
    /**
     * Constructor for job results
     */
    public JobResult(String jobId, int statusCode, Map<String, String> headers, 
                    String body, String contentType, long expiresAt) {
        this.jobId = jobId;
        this.statusCode = statusCode;
        this.headers = new HashMap<>(headers != null ? headers : Map.of());
        this.body = body;
        this.contentType = contentType;
        this.createdAt = Instant.now().toEpochMilli();
        this.expiresAt = expiresAt;
    }
    
    /**
     * Constructor with default expiration (1 hour)
     */
    public JobResult(String jobId, int statusCode, String body, String contentType) {
        this(jobId, statusCode, Map.of(), body, contentType, 
             Instant.now().toEpochMilli() + 3600000); // 1 hour from now
    }
    
    /**
     * Constructor for successful JSON results
     */
    public JobResult(String jobId, String jsonBody) {
        this(jobId, 200, jsonBody, "application/json");
    }
    
    /**
     * Constructor for error results
     */
    public static JobResult error(String jobId, int statusCode, String errorMessage) {
        Map<String, Object> errorBody = Map.of(
            "error", true,
            "message", errorMessage,
            "timestamp", Instant.now().toEpochMilli()
        );
        
        // Simple JSON serialization for error (avoiding Jackson dependency)
        String jsonBody = String.format(
            "{\"error\":true,\"message\":\"%s\",\"timestamp\":%d}",
            errorMessage.replace("\"", "\\\""), // Escape quotes
            Instant.now().toEpochMilli()
        );
        
        return new JobResult(jobId, statusCode, jsonBody, "application/json");
    }
    
    // Getters
    public String getJobId() { return jobId; }
    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public String getContentType() { return contentType; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    
    /**
     * Check if this result has expired
     */
    public boolean hasExpired() {
        return Instant.now().toEpochMilli() > expiresAt;
    }
    
    /**
     * Check if this is a successful result
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    @Override
    public String toString() {
        return String.format("JobResult{jobId='%s', statusCode=%d, contentType='%s'}", 
                           jobId, statusCode, contentType);
    }
} 