package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.jobs.*;
import org.ruitx.www.dto.admin.*;
import org.tinylog.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;
import static org.ruitx.jaws.strings.ResponseType.JSON;


public class AdminController extends Bragi {
    
    private static final String API_ENDPOINT = "/api/admin/";
    
    private final JobQueue jobQueue = JobQueue.getInstance();
    private final DeadLetterQueue deadLetterQueue;
    private final JobRetryScheduler retryScheduler;
    private final JobErrorClassifier advancedErrorClassifier;
    
    public AdminController() {
        this.deadLetterQueue = jobQueue.getDeadLetterQueue();
        this.retryScheduler = jobQueue.getRetryScheduler();
        this.advancedErrorClassifier = new JobErrorClassifier();
    }
    
    // ========================================
    // Dead Letter Queue Management
    // ========================================
    
    /**
     * Get Dead Letter Queue entries with filtering
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq", method = GET, responseType = JSON)
    public void getDLQEntries() {
        try {
            String jobType = getQueryParam("jobType");
            String canBeRetriedParam = getQueryParam("canBeRetried");
            String limitParam = getQueryParam("limit");
            
            Boolean canBeRetried = null;
            if (canBeRetriedParam != null) {
                canBeRetried = Boolean.parseBoolean(canBeRetriedParam);
            }
            
            Integer limit = null;
            if (limitParam != null) {
                try {
                    limit = Integer.parseInt(limitParam);
                } catch (NumberFormatException e) {
                    limit = 50; // Default limit
                }
            }
            
            List<DeadLetterQueue.DLQEntry> entries = deadLetterQueue.getDLQEntries(jobType, canBeRetried, limit);
            
            // Convert to DTOs for API response
            List<DLQEntryDTO> entryDTOs = entries.stream()
                .map(this::convertToDLQEntryDTO)
                .collect(Collectors.toList());
            
            sendSucessfulResponse(OK, entryDTOs);
            
        } catch (Exception e) {
            Logger.error("Failed to get DLQ entries: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve DLQ entries");
        }
    }
    
    /**
     * Get specific DLQ entry by ID
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq/:id", method = GET, responseType = JSON)
    public void getDLQEntry() {
        try {
            String dlqEntryId = getPathParam("id");
            
            DeadLetterQueue.DLQEntry entry = deadLetterQueue.getDLQEntry(dlqEntryId);
            if (entry == null) {
                sendErrorResponse(NOT_FOUND, "DLQ entry not found");
                return;
            }
            
            DLQEntryDTO entryDTO = convertToDLQEntryDTO(entry);
            sendSucessfulResponse(OK, entryDTO);
            
        } catch (Exception e) {
            Logger.error("Failed to get DLQ entry: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve DLQ entry");
        }
    }
    
    /**
     * Manually retry a job from DLQ
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq/:id/retry", method = POST, responseType = JSON)
    public void retryDLQEntry(ManualRetryRequest request) {
        try {
            String dlqEntryId = getPathParam("id");
            boolean resetRetryCount = request.resetRetryCount() != null ? request.resetRetryCount() : true;
            
            String newJobId = deadLetterQueue.manualRetry(dlqEntryId, resetRetryCount);
            
            if (newJobId != null) {
                Map<String, Object> result = Map.of(
                    "success", true,
                    "message", "Job successfully retried from DLQ",
                    "newJobId", newJobId,
                    "dlqEntryId", dlqEntryId,
                    "resetRetryCount", resetRetryCount
                );
                
                Logger.info("Admin manual retry: DLQ entry {} retried as job {} (reset: {})", 
                          dlqEntryId, newJobId, resetRetryCount);
                
                sendSucessfulResponse(OK, result);
            } else {
                sendErrorResponse(BAD_REQUEST, "Failed to retry job from DLQ");
            }
            
        } catch (Exception e) {
            Logger.error("Failed to retry DLQ entry: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retry job from DLQ");
        }
    }
    
    /**
     * Batch retry multiple DLQ entries
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq/batch-retry", method = POST, responseType = JSON)
    public void batchRetryDLQEntries(BatchRetryRequest request) {
        try {
            List<String> dlqEntryIds = request.dlqEntryIds();
            boolean resetRetryCount = request.resetRetryCount() != null ? request.resetRetryCount() : true;
            
            if (dlqEntryIds == null || dlqEntryIds.isEmpty()) {
                sendErrorResponse(BAD_REQUEST, "DLQ entry IDs are required");
                return;
            }
            
            Map<String, String> results = deadLetterQueue.batchRetry(dlqEntryIds, resetRetryCount);
            
            int successful = (int) results.values().stream().filter(Objects::nonNull).count();
            int failed = dlqEntryIds.size() - successful;
            
            Map<String, Object> response = Map.of(
                "totalRequested", dlqEntryIds.size(),
                "successful", successful,
                "failed", failed,
                "results", results,
                "resetRetryCount", resetRetryCount
            );
            
            Logger.info("Admin batch retry: {} successful, {} failed out of {} total", 
                       successful, failed, dlqEntryIds.size());
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to batch retry DLQ entries: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to batch retry DLQ entries");
        }
    }
    
    /**
     * Get DLQ statistics
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq/stats", method = GET, responseType = JSON)
    public void getDLQStatistics() {
        try {
            DeadLetterQueue.DLQStatistics stats = deadLetterQueue.getStatistics();
            
            Map<String, Object> response = Map.of(
                "totalEntries", stats.getTotalEntries(),
                "retryableEntries", stats.getRetryableEntries(),
                "nonRetryableEntries", stats.getTotalEntries() - stats.getRetryableEntries(),
                "entriesByType", stats.getEntriesByType() != null ? stats.getEntriesByType() : Map.of(),
                "oldestEntryTimestamp", stats.getOldestEntryTimestamp()
            );
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to get DLQ statistics: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve DLQ statistics");
        }
    }
    
    /**
     * Clean up old DLQ entries
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "dlq/cleanup", method = POST, responseType = JSON)
    public void cleanupDLQ(DLQCleanupRequest request) {
        try {
            int retentionDays = request.retentionDays() != null ? request.retentionDays() : 30;
            
            int deletedCount = deadLetterQueue.cleanupOldEntries(retentionDays);
            
            Map<String, Object> response = Map.of(
                "deletedEntries", deletedCount,
                "retentionDays", retentionDays,
                "message", String.format("Cleaned up %d old DLQ entries", deletedCount)
            );
            
            Logger.info("Admin DLQ cleanup: {} entries deleted (retention: {} days)", deletedCount, retentionDays);
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to cleanup DLQ: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to cleanup DLQ entries");
        }
    }
    
    // ========================================
    // Circuit Breaker Management
    // ========================================
    
    /**
     * Get all circuit breaker statistics
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "circuit-breakers", method = GET, responseType = JSON)
    public void getCircuitBreakers() {
        try {
            Map<String, CircuitBreaker> circuitBreakers = CircuitBreaker.getAllCircuitBreakers();
            
            Map<String, CircuitBreaker.CircuitBreakerStats> stats = circuitBreakers.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getStatistics()
                ));
            
            sendSucessfulResponse(OK, stats);
            
        } catch (Exception e) {
            Logger.error("Failed to get circuit breaker statistics: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve circuit breaker statistics");
        }
    }
    
    /**
     * Get specific circuit breaker statistics
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "circuit-breakers/:service", method = GET, responseType = JSON)
    public void getCircuitBreaker() {
        try {
            String serviceName = getPathParam("service");
            Map<String, CircuitBreaker> circuitBreakers = CircuitBreaker.getAllCircuitBreakers();
            
            CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
            if (circuitBreaker == null) {
                sendErrorResponse(NOT_FOUND, "Circuit breaker not found for service: " + serviceName);
                return;
            }
            
            CircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStatistics();
            sendSucessfulResponse(OK, stats);
            
        } catch (Exception e) {
            Logger.error("Failed to get circuit breaker for service: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve circuit breaker");
        }
    }
    
    /**
     * Reset a circuit breaker (admin intervention)
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "circuit-breakers/:service/reset", method = POST, responseType = JSON)
    public void resetCircuitBreaker() {
        try {
            String serviceName = getPathParam("service");
            Map<String, CircuitBreaker> circuitBreakers = CircuitBreaker.getAllCircuitBreakers();
            
            CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
            if (circuitBreaker == null) {
                sendErrorResponse(NOT_FOUND, "Circuit breaker not found for service: " + serviceName);
                return;
            }
            
            circuitBreaker.reset();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Circuit breaker reset successfully",
                "serviceName", serviceName
            );
            
            Logger.info("Admin reset circuit breaker for service: {}", serviceName);
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to reset circuit breaker: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to reset circuit breaker");
        }
    }
    
    // ========================================
    // Retry System Management
    // ========================================
    
    /**
     * Get retry scheduler statistics
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "retry-scheduler/stats", method = GET, responseType = JSON)
    public void getRetrySchedulerStats() {
        try {
            JobRetryScheduler.RetrySchedulerStatistics stats = retryScheduler.getStatistics();
            
            Map<String, Object> response = Map.of(
                "totalRetriesProcessed", stats.getTotalRetriesProcessed(),
                "successfulRetries", stats.getSuccessfulRetries(),
                "failedRetries", stats.getFailedRetries(),
                "movedToDeadLetter", stats.getMovedToDeadLetter(),
                "running", stats.isRunning()
            );
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to get retry scheduler statistics: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve retry scheduler statistics");
        }
    }
    
    /**
     * Manually trigger retry processing
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "retry-scheduler/process", method = POST, responseType = JSON)
    public void triggerRetryProcessing() {
        try {
            int processedCount = retryScheduler.processNow();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "processedRetries", processedCount,
                "message", String.format("Manually processed %d retry jobs", processedCount)
            );
            
            Logger.info("Admin triggered retry processing: {} jobs processed", processedCount);
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to trigger retry processing: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to trigger retry processing");
        }
    }
    
    // ========================================
    // Advanced Error Analysis
    // ========================================
    
    /**
     * Get error classification analysis
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "error-analysis", method = GET, responseType = JSON)
    public void getErrorAnalysis() {
        try {
            String jobType = getQueryParam("jobType");
            String hoursParam = getQueryParam("hours");
            
            int hours = 24; // Default to last 24 hours
            if (hoursParam != null) {
                try {
                    hours = Integer.parseInt(hoursParam);
                } catch (NumberFormatException e) {
                    hours = 24;
                }
            }
            
            ErrorAnalysisResult analysis = analyzeErrors(jobType, hours);
            sendSucessfulResponse(OK, analysis);
            
        } catch (Exception e) {
            Logger.error("Failed to get error analysis: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve error analysis");
        }
    }
    
    /**
     * Test error classification for a given exception
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "error-classification/test", method = POST, responseType = JSON)
    public void testErrorClassification(ErrorClassificationTestRequest request) {
        try {
            String exceptionType = request.exceptionType();
            String exceptionMessage = request.exceptionMessage();
            String jobType = request.jobType();
            
            // Create a mock exception for testing
            Exception testException = createMockException(exceptionType, exceptionMessage);
            
            JobErrorClassifier.ClassificationResult result = 
                advancedErrorClassifier.classify(testException, jobType, 0, 3);
            
            Map<String, Object> response = Map.of(
                "exceptionType", exceptionType,
                "exceptionMessage", exceptionMessage,
                "jobType", jobType != null ? jobType : "unknown",
                "classification", Map.of(
                    "errorType", result.getErrorType().name(),
                    "shouldRetry", result.shouldRetry(),
                    "suggestedMaxRetries", result.getSuggestedMaxRetries(),
                    "suggestedBaseDelayMs", result.getSuggestedBaseDelayMs(),
                    "suggestedBackoffMultiplier", result.getSuggestedBackoffMultiplier(),
                    "reason", result.getReason(),
                    "strategy", result.getStrategy()
                )
            );
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to test error classification: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to test error classification");
        }
    }
    
    // ========================================
    // System Health & Diagnostics
    // ========================================
    
    /**
     * Get comprehensive system health overview
     */
    //@AccessControl(login = true, role = "admin")
    @Route(endpoint = API_ENDPOINT + "system/health", method = GET, responseType = JSON)
    public void getSystemHealth() {
        try {
            // Get job queue statistics
            Map<String, Object> jobStats = jobQueue.getStatistics();
            
            // Get DLQ statistics
            DeadLetterQueue.DLQStatistics dlqStats = deadLetterQueue.getStatistics();
            
            // Get retry scheduler statistics
            JobRetryScheduler.RetrySchedulerStatistics retryStats = retryScheduler.getStatistics();
            
            // Get circuit breaker overview
            Map<String, CircuitBreaker> circuitBreakers = CircuitBreaker.getAllCircuitBreakers();
            Map<String, String> circuitBreakerStates = circuitBreakers.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getStatistics().state.name()
                ));
            
            // Calculate health indicators
            int totalJobs = (Integer) jobStats.getOrDefault("totalJobs", 0);
            int failedJobs = (Integer) jobStats.getOrDefault("failedJobs", 0);
            double failureRate = totalJobs > 0 ? (double) failedJobs / totalJobs * 100.0 : 0.0;
            
            String healthStatus = determineHealthStatus(failureRate, dlqStats, circuitBreakerStates);
            
            Map<String, Object> response = Map.of(
                "healthStatus", healthStatus,
                "timestamp", System.currentTimeMillis(),
                "jobQueue", jobStats,
                "deadLetterQueue", Map.of(
                    "totalEntries", dlqStats.getTotalEntries(),
                    "retryableEntries", dlqStats.getRetryableEntries()
                ),
                "retryScheduler", Map.of(
                    "running", retryStats.isRunning(),
                    "totalProcessed", retryStats.getTotalRetriesProcessed(),
                    "successfulRetries", retryStats.getSuccessfulRetries()
                ),
                "circuitBreakers", circuitBreakerStates,
                "metrics", Map.of(
                    "overallFailureRate", String.format("%.2f%%", failureRate),
                    "dlqSize", dlqStats.getTotalEntries(),
                    "activeCircuitBreakers", circuitBreakers.size(),
                    "openCircuits", circuitBreakerStates.values().stream()
                        .mapToLong(state -> "OPEN".equals(state) ? 1 : 0).sum()
                )
            );
            
            sendSucessfulResponse(OK, response);
            
        } catch (Exception e) {
            Logger.error("Failed to get system health: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to retrieve system health");
        }
    }
    
    // ========================================
    // Helper Methods
    // ========================================
    
    private DLQEntryDTO convertToDLQEntryDTO(DeadLetterQueue.DLQEntry entry) {
        return new DLQEntryDTO(
            entry.getId(),
            entry.getOriginalJobId(),
            entry.getJobType(),
            entry.getExecutionMode(),
            entry.getPayload(),
            entry.getPriority(),
            entry.getMaxRetries(),
            entry.getFailureReason(),
            entry.getFailedAt(),
            entry.getRetryAttempts(),
            entry.getRetryHistory(),
            entry.canBeRetried(),
            entry.getCreatedAt()
        );
    }
    
    private ErrorAnalysisResult analyzeErrors(String jobType, int hours) {
        // This would typically query the database for error patterns
        // For now, return a placeholder implementation
        Map<String, Integer> errorTypeCount = Map.of(
            "TRANSIENT_NETWORK", 15,
            "TRANSIENT_TIMEOUT", 8,
            "PERMANENT_VALIDATION", 3,
            "TRANSIENT_SERVICE_UNAVAILABLE", 5
        );
        
        Map<String, Double> errorTrends = Map.of(
            "last_hour", 2.5,
            "last_4_hours", 4.1,
            "last_24_hours", 3.8
        );
        
        List<String> recommendations = List.of(
            "High network error rate detected - check external API health",
            "Consider increasing timeout thresholds for heavy computation jobs",
            "Review validation errors for potential input sanitization issues"
        );
        
        return new ErrorAnalysisResult(
            jobType != null ? jobType : "all",
            hours,
            errorTypeCount,
            errorTrends,
            recommendations,
            System.currentTimeMillis()
        );
    }
    
    private Exception createMockException(String exceptionType, String message) {
        // Create appropriate exception type for testing
        return switch (exceptionType.toLowerCase()) {
            case "nullpointerexception" -> new NullPointerException(message);
            case "illegalargumentexception" -> new IllegalArgumentException(message);
            case "sockettimeoutexception" -> new java.net.SocketTimeoutException(message);
            case "ioexception" -> new java.io.IOException(message);
            case "sqlexception" -> new RuntimeException("SQLException: " + message);
            default -> new RuntimeException(message);
        };
    }
    
    private String determineHealthStatus(double failureRate, DeadLetterQueue.DLQStatistics dlqStats, 
                                       Map<String, String> circuitBreakerStates) {
        
        long openCircuits = circuitBreakerStates.values().stream()
            .mapToLong(state -> "OPEN".equals(state) ? 1 : 0).sum();
        
        if (openCircuits > 0 || failureRate > 20.0 || dlqStats.getTotalEntries() > 100) {
            return "CRITICAL";
        } else if (failureRate > 10.0 || dlqStats.getTotalEntries() > 50) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }
} 