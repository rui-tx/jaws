package org.ruitx.jaws.components.freyr;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JobResultStore handles the persistence of job results.
 */
public class JobResultStore {
    
    private static final Mimir mimir = new Mimir();
    
    /**
     * Store a job result
     */
    public static void store(JobResult result) {
        try {
            String headersJson = null;
            if (result.getHeaders() != null && !result.getHeaders().isEmpty()) {
                headersJson = Odin.getMapper().writeValueAsString(result.getHeaders());
            }
            
            mimir.executeSql(
                "INSERT OR REPLACE INTO JOB_RESULTS (id, job_id, status_code, headers, body, content_type, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                result.getJobId(),
                result.getStatusCode(),
                headersJson,
                result.getBody(),
                result.getContentType(),
                result.getCreatedAt(),
                result.getExpiresAt()
            );
            
            Logger.info("Stored result for job: {}", result.getJobId());
            
        } catch (Exception e) {
            Logger.error("Failed to store job result for {}: {}", result.getJobId(), e.getMessage(), e);
            throw new RuntimeException("Failed to store job result", e);
        }
    }
    
    /**
     * Store a successful JSON result
     */
    public static void storeSuccess(String jobId, String jsonBody) {
        store(new JobResult(jobId, jsonBody));
    }
    
    /**
     * Store a successful result with custom content type
     */
    public static void storeSuccess(String jobId, String body, String contentType) {
        store(new JobResult(jobId, 200, body, contentType));
    }
    
    /**
     * Store an error result
     */
    public static void storeError(String jobId, int statusCode, String errorMessage) {
        store(JobResult.error(jobId, statusCode, errorMessage));
    }
    
    /**
     * Store a result with custom headers
     */
    public static void store(String jobId, int statusCode, Map<String, String> headers, String body, String contentType) {
        store(new JobResult(jobId, statusCode, headers, body, contentType, 
                           Instant.now().toEpochMilli() + 3600000)); // 1 hour expiration
    }
} 