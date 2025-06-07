package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.jobs.JobQueue;
import org.ruitx.jaws.jobs.JobResult;
import org.ruitx.www.jobs.ExternalApiJob;
import org.ruitx.www.jobs.HeavyComputationJob;
import org.ruitx.www.jobs.ImageProcessingJob;

import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.*;
import static org.ruitx.jaws.strings.ResponseType.JSON;

/**
 * JobController - Replaces AsyncController with clean job queue system
 * 
 * This controller demonstrates the new approach:
 * 1. Sync endpoints queue jobs and return immediately
 * 2. Separate endpoints check job status and retrieve results
 * 3. No complex route resolution - just simple job creation
 * 
 * Benefits over AsyncController:
 * - No @Async annotation needed
 * - Jobs are self-contained
 * - Clean separation between web layer and job layer
 * - Much simpler implementation
 */
public class JobController extends Bragi {
    
    private static final String API_ENDPOINT = "/api/jobs/";
    private final JobQueue jobQueue;
    
    public JobController() {
        this.jobQueue = JobQueue.getInstance();
    }
    
    // ========================================
    // Job Queueing Endpoints (Sync - queue jobs and return immediately)
    // ========================================
    
    /**
     * Queue a heavy computation job
     * Replaces: @Async heavyComputationalTask()
     */
    @Route(endpoint = API_ENDPOINT + "heavy-computation", method = POST, responseType = JSON)
    public void queueHeavyComputation() {
        try {
            // Extract parameters from request body if available
            Map<String, Object> payload = new HashMap<>();
            payload.put("clientId", getClientIpAddress());
            payload.put("requestedBy", getCurrentToken());
            
            // Optional parameters from request body
            String requestBody = getRequestContext().getRequestBody();
            if (requestBody != null && !requestBody.trim().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bodyParams = getMapper().readValue(requestBody, Map.class);
                    payload.putAll(bodyParams);
                } catch (Exception e) {
                    // If body parsing fails, continue with default values
                }
            }
            
            // Create and submit job
            HeavyComputationJob job = new HeavyComputationJob(payload);
            String jobId = jobQueue.submit(job);
            
            // Return immediate response with job tracking info
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "QUEUED");
            response.put("message", "Heavy computation job queued for processing");
            response.put("endpoints", Map.of(
                "status", "/api/jobs/status/" + jobId,
                "result", "/api/jobs/result/" + jobId
            ));
            
            sendSucessfulResponse(ACCEPTED, response);
            
        } catch (Exception e) {
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to queue heavy computation job: " + e.getMessage());
        }
    }
    
    /**
     * Queue an external API call job
     * Replaces: @Async fetchExternalDataAsync()
     */
    @Route(endpoint = API_ENDPOINT + "external-api", method = POST, responseType = JSON)
    public void queueExternalApiCall() {
        try {
            // Extract parameters from request body
            Map<String, Object> payload = new HashMap<>();
            payload.put("clientId", getClientIpAddress());
            payload.put("requestedBy", getCurrentToken());
            
            // Parse request body for URL and other parameters
            String requestBody = getRequestContext().getRequestBody();
            if (requestBody != null && !requestBody.trim().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bodyParams = getMapper().readValue(requestBody, Map.class);
                    payload.putAll(bodyParams);
                } catch (Exception e) {
                    sendErrorResponse(BAD_REQUEST, "Invalid request body format");
                    return;
                }
            }
            
            // Create and submit job
            ExternalApiJob job = new ExternalApiJob(payload);
            String jobId = jobQueue.submit(job);
            
            // Return immediate response
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "QUEUED");
            response.put("message", "External API call job queued for processing");
            response.put("endpoints", Map.of(
                "status", "/api/jobs/status/" + jobId,
                "result", "/api/jobs/result/" + jobId
            ));
            
            sendSucessfulResponse(ACCEPTED, response);
            
        } catch (Exception e) {
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to queue external API job: " + e.getMessage());
        }
    }
    
    /**
     * Queue an image processing job
     * 
     * Instead of: @Async processImageAsync() in APIController
     * Now: Sync endpoint that queues job and returns immediately
     */
    @Route(endpoint = API_ENDPOINT + "process-image", method = POST, responseType = JSON)
    public void queueImageProcessing() {
        try {
            // Extract parameters from request body
            Map<String, Object> payload = new HashMap<>();
            payload.put("clientId", getClientIpAddress());
            payload.put("requestedBy", getCurrentToken());
            
            // Parse request body for image data and processing options
            String requestBody = getRequestContext().getRequestBody();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(BAD_REQUEST, "Request body with image data is required");
                return;
            }
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> bodyParams = getMapper().readValue(requestBody, Map.class);
                
                // Validate required fields
                if (!bodyParams.containsKey("imageData")) {
                    sendErrorResponse(BAD_REQUEST, "imageData field is required");
                    return;
                }
                
                payload.putAll(bodyParams);
            } catch (Exception e) {
                sendErrorResponse(BAD_REQUEST, "Invalid request body format");
                return;
            }
            
            // Create and submit job
            ImageProcessingJob job = new ImageProcessingJob(payload);
            String jobId = jobQueue.submit(job);
            
            // Return immediate response with job tracking info
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "QUEUED");
            response.put("message", "Image processing job queued");
            response.put("estimatedTime", "30-60 seconds");
            response.put("endpoints", Map.of(
                "status", "/api/jobs/status/" + jobId,
                "result", "/api/jobs/result/" + jobId
            ));
            
            sendSucessfulResponse(ACCEPTED, response);
            
        } catch (Exception e) {
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to queue image processing job: " + e.getMessage());
        }
    }
    
    // ========================================
    // Job Management Endpoints
    // ========================================
    
    /**
     * Get job status
     */
    @Route(endpoint = API_ENDPOINT + "status/:jobId", responseType = JSON)
    public void getJobStatus() {
        String jobId = getPathParam("jobId");
        if (jobId == null || jobId.isEmpty()) {
            sendErrorResponse(BAD_REQUEST, "Job ID is required");
            return;
        }
        
        JobQueue.JobStatus status = jobQueue.getJobStatus(jobId);
        if (status == null) {
            sendErrorResponse(NOT_FOUND, "Job not found");
            return;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", status.name());
        response.put("statusDescription", getStatusDescription(status));
        
        sendSucessfulResponse(OK, response);
    }
    
    /**
     * Get job result
     */
    @Route(endpoint = API_ENDPOINT + "result/:jobId", responseType = JSON)
    public void getJobResult() {
        String jobId = getPathParam("jobId");
        if (jobId == null || jobId.isEmpty()) {
            sendErrorResponse(BAD_REQUEST, "Job ID is required");
            return;
        }
        
        JobQueue.JobStatus status = jobQueue.getJobStatus(jobId);
        if (status == null) {
            sendErrorResponse(NOT_FOUND, "Job not found");
            return;
        }
        
        if (status != JobQueue.JobStatus.COMPLETED) {
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", status.name());
            response.put("message", "Job not yet completed");
            
            sendSucessfulResponse(OK, response);
            return;
        }
        
        JobResult jobResult = jobQueue.getJobResult(jobId);
        if (jobResult == null) {
            sendErrorResponse(NOT_FOUND, "Job result not found or expired");
            return;
        }
        
        // Return the job result with metadata
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "COMPLETED");
        response.put("statusCode", jobResult.getStatusCode());
        response.put("headers", jobResult.getHeaders());
        response.put("contentType", jobResult.getContentType());
        response.put("createdAt", jobResult.getCreatedAt());
        response.put("expiresAt", jobResult.getExpiresAt());
        
        // Try to parse the body as JSON, fallback to string
        String body = jobResult.getBody();
        try {
            Object parsedBody = getMapper().readValue(body, Object.class);
            response.put("data", parsedBody);
        } catch (Exception e) {
            response.put("data", body);
        }
        
        sendSucessfulResponse(OK, response);
    }
    
    /**
     * Get job queue statistics
     */
    @Route(endpoint = API_ENDPOINT + "stats", responseType = JSON)
    public void getJobStatistics() {
        Map<String, Object> stats = jobQueue.getStatistics();
        sendSucessfulResponse(OK, stats);
    }
    
    // ========================================
    // Helper Methods
    // ========================================
    
    /**
     * Get a human-readable description for each job status
     */
    private String getStatusDescription(JobQueue.JobStatus status) {
        return switch (status) {
            case PENDING -> "Job is queued and waiting to be processed";
            case PROCESSING -> "Job is currently being processed";
            case COMPLETED -> "Job has been completed successfully";
            case FAILED -> "Job processing failed";
            case TIMEOUT -> "Job processing timed out";
        };
    }
} 