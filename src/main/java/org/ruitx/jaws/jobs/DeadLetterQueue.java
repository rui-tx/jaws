package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.types.Row;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.*;

/**
 * DeadLetterQueue - Manages permanently failed jobs and provides manual retry functionality
 * 
 * This class handles jobs that have exhausted all retry attempts or failed with permanent errors.
 */
public class DeadLetterQueue {
    
    private final Mimir mimir = new Mimir();
    private final JobRegistry jobRegistry;
    
    public DeadLetterQueue() {
        // Use singleton JobRegistry instance
        this.jobRegistry = JobRegistry.getInstance();
    }
    
    /**
     * Dead Letter Queue entry representation
     */
    public static class DLQEntry {
        private final String id;
        private final String originalJobId;
        private final String jobType;
        private final String executionMode;
        private final Map<String, Object> payload;
        private final int priority;
        private final int maxRetries;
        private final String failureReason;
        private final long failedAt;
        private final int retryAttempts;
        private final List<String> retryHistory;
        private final boolean canBeRetried;
        private final long createdAt;
        
        public DLQEntry(String id, String originalJobId, String jobType, String executionMode,
                       Map<String, Object> payload, int priority, int maxRetries, 
                       String failureReason, long failedAt, int retryAttempts,
                       List<String> retryHistory, boolean canBeRetried, long createdAt) {
            this.id = id;
            this.originalJobId = originalJobId;
            this.jobType = jobType;
            this.executionMode = executionMode;
            this.payload = payload;
            this.priority = priority;
            this.maxRetries = maxRetries;
            this.failureReason = failureReason;
            this.failedAt = failedAt;
            this.retryAttempts = retryAttempts;
            this.retryHistory = retryHistory;
            this.canBeRetried = canBeRetried;
            this.createdAt = createdAt;
        }
        
        // Getters
        public String getId() { return id; }
        public String getOriginalJobId() { return originalJobId; }
        public String getJobType() { return jobType; }
        public String getExecutionMode() { return executionMode; }
        public Map<String, Object> getPayload() { return payload; }
        public int getPriority() { return priority; }
        public int getMaxRetries() { return maxRetries; }
        public String getFailureReason() { return failureReason; }
        public long getFailedAt() { return failedAt; }
        public int getRetryAttempts() { return retryAttempts; }
        public List<String> getRetryHistory() { return retryHistory; }
        public boolean canBeRetried() { return canBeRetried; }
        public long getCreatedAt() { return createdAt; }
    }
    
    /**
     * Move a permanently failed job to the Dead Letter Queue
     * This is called when a job exhausts all retry attempts or fails with permanent errors
     * 
     * @param jobId The ID of the job that permanently failed
     * @param failureReason Why the job was moved to DLQ
     * @return True if successfully moved to DLQ, false otherwise
     */
    public boolean moveToDeadLetterQueue(String jobId, String failureReason) {
        try {
            // Get the job details from the JOBS table
            Row jobRow = mimir.getRow("SELECT * FROM JOBS WHERE id = ?", jobId);
            if (jobRow == null) {
                Logger.error("Cannot move job {} to DLQ - job not found in database", jobId);
                return false;
            }
            
            // Extract job information
            String jobType = jobRow.getString("type").orElse("unknown");
            String executionMode = jobRow.getString("execution_mode").orElse("PARALLEL");
            String payloadJson = jobRow.getString("payload").orElse("{}");
            int priority = jobRow.getInt("priority").orElse(5);
            int maxRetries = jobRow.getInt("max_retries").orElse(3);
            int retryAttempts = jobRow.getInt("current_retries").orElse(0);
            long failedAt = Instant.now().toEpochMilli();
            
            // Parse payload
            Map<String, Object> payload;
            try {
                payload = Odin.getMapper().readValue(payloadJson, Map.class);
            } catch (Exception e) {
                Logger.warn("Failed to parse payload for job {}, using empty payload", jobId);
                payload = new HashMap<>();
            }
            
            // Build retry history from job metadata
            List<String> retryHistory = buildRetryHistory(jobId, retryAttempts);
            
            // Determine if job can be manually retried
            boolean canBeRetried = determineIfRetryable(failureReason, jobType);
            String dlqId = UUID.randomUUID().toString();
            
            String retryHistoryJson = Odin.getMapper().writeValueAsString(retryHistory);
            int inserted = mimir.executeSql("""
                INSERT INTO DEAD_LETTER_QUEUE 
                (id, original_job_id, job_type, execution_mode, payload, priority, 
                 max_retries, failure_reason, failed_at, retry_attempts, retry_history, 
                 can_be_retried, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                dlqId, jobId, jobType, executionMode, payloadJson, priority,
                maxRetries, failureReason, failedAt, retryAttempts, retryHistoryJson,
                canBeRetried ? 1 : 0, failedAt
            );
            
            if (inserted > 0) {
                // Update the job status to DEAD_LETTER
                mimir.executeSql(
                    "UPDATE JOBS SET status = ? WHERE id = ?", 
                    JobQueue.JobStatus.DEAD_LETTER.name(), jobId
                );
                
                Logger.info("Job {} moved to Dead Letter Queue with ID: {} (reason: {})", 
                          jobId, dlqId, failureReason);
                return true;
            } else {
                Logger.error("Failed to insert job {} into Dead Letter Queue", jobId);
                return false;
            }
            
        } catch (Exception e) {
            Logger.error("Failed to move job {} to Dead Letter Queue: {}", jobId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Manually retry a job from the Dead Letter Queue
     * This allows administrators to retry jobs after fixing underlying issues
     * 
     * @param dlqEntryId The DLQ entry ID to retry
     * @param resetRetryCount Whether to reset the retry count (default: true)
     * @return The new job ID if successful, null otherwise
     */
    public String manualRetry(String dlqEntryId, boolean resetRetryCount) {
        try {
            // Get the DLQ entry
            DLQEntry entry = getDLQEntry(dlqEntryId);
            if (entry == null) {
                Logger.error("Cannot retry DLQ entry {} - entry not found", dlqEntryId);
                return null;
            }
            
            if (!entry.canBeRetried()) {
                Logger.error("Cannot retry DLQ entry {} - job is marked as non-retryable", dlqEntryId);
                return null;
            }
            
            // Create a new job based on the DLQ entry
            Job newJob = jobRegistry.createJob(entry.getJobType(), entry.getPayload());
            if (newJob == null) {
                Logger.error("Cannot create job for retry - unknown job type: {}", entry.getJobType());
                return null;
            }
            
            // The new job already has a new UUID from the BaseJob constructor
            String newJobId = newJob.getId();
            int initialRetries = resetRetryCount ? 0 : entry.getRetryAttempts();
            
            // Insert the new job into the JOBS table
            String payloadJson = Odin.getMapper().writeValueAsString(entry.getPayload());
            long now = Instant.now().toEpochMilli();
            
            int inserted = mimir.executeSql("""
                INSERT INTO JOBS 
                (id, type, payload, priority, max_retries, current_retries, timeout_ms, 
                 execution_mode, status, created_at, client_id, user_id) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                newJobId, entry.getJobType(), payloadJson, entry.getPriority(),
                entry.getMaxRetries(), initialRetries, newJob.getTimeoutMs(),
                entry.getExecutionMode(), JobQueue.JobStatus.PENDING.name(),
                now, newJob.getClientId(), newJob.getUserId()
            );
            
            if (inserted > 0) {
                // Submit the job to the appropriate queue
                JobQueue jobQueue = JobQueue.getInstance();
                String submittedJobId = jobQueue.submit(newJob);
                
                if (submittedJobId != null) {
                    // Update DLQ entry to mark it as retried
                    mimir.executeSql("""
                        UPDATE DEAD_LETTER_QUEUE 
                        SET can_be_retried = 0 
                        WHERE id = ?
                        """, dlqEntryId);
                    
                    Logger.info("Successfully retried DLQ entry {} as new job: {} (reset retries: {})", 
                              dlqEntryId, newJobId, resetRetryCount);
                    return newJobId;
                } else {
                    Logger.error("Failed to submit retry job to queue");
                    return null;
                }
            } else {
                Logger.error("Failed to insert retry job into database");
                return null;
            }
            
        } catch (Exception e) {
            Logger.error("Failed to manually retry DLQ entry {}: {}", dlqEntryId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get a specific DLQ entry by ID
     */
    public DLQEntry getDLQEntry(String dlqEntryId) {
        try {
            Row row = mimir.getRow("SELECT * FROM DEAD_LETTER_QUEUE WHERE id = ?", dlqEntryId);
            if (row != null) {
                return createDLQEntryFromRow(row);
            }
            return null;
        } catch (Exception e) {
            Logger.error("Failed to get DLQ entry {}: {}", dlqEntryId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get all DLQ entries with optional filtering
     * 
     * @param jobType Filter by job type (null for all)
     * @param canBeRetried Filter by retry status (null for all)
     * @param limit Maximum number of entries to return (null for all)
     * @return List of DLQ entries
     */
    public List<DLQEntry> getDLQEntries(String jobType, Boolean canBeRetried, Integer limit) {
        try {
            StringBuilder query = new StringBuilder("SELECT * FROM DEAD_LETTER_QUEUE WHERE 1=1");
            List<Object> params = new ArrayList<>();
            
            if (jobType != null) {
                query.append(" AND job_type = ?");
                params.add(jobType);
            }
            
            if (canBeRetried != null) {
                query.append(" AND can_be_retried = ?");
                params.add(canBeRetried ? 1 : 0);
            }
            
            query.append(" ORDER BY failed_at DESC");
            
            if (limit != null && limit > 0) {
                query.append(" LIMIT ?");
                params.add(limit);
            }
            
            List<Row> rows = mimir.getRows(query.toString(), params.toArray());
            List<DLQEntry> entries = new ArrayList<>();
            
            for (Row row : rows) {
                DLQEntry entry = createDLQEntryFromRow(row);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            
            return entries;
        } catch (Exception e) {
            Logger.error("Failed to get DLQ entries: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Get Dead Letter Queue statistics
     */
    public DLQStatistics getStatistics() {
        try {
            // Count total entries
            Row totalRow = mimir.getRow("SELECT COUNT(*) as count FROM DEAD_LETTER_QUEUE");
            int totalEntries = totalRow != null ? totalRow.getInt("count").orElse(0) : 0;
            
            // Count retryable entries
            Row retryableRow = mimir.getRow("SELECT COUNT(*) as count FROM DEAD_LETTER_QUEUE WHERE can_be_retried = 1");
            int retryableEntries = retryableRow != null ? retryableRow.getInt("count").orElse(0) : 0;
            
            // Count by job type
            List<Row> typeRows = mimir.getRows("SELECT job_type, COUNT(*) as count FROM DEAD_LETTER_QUEUE GROUP BY job_type");
            Map<String, Integer> entriesByType = new HashMap<>();
            for (Row row : typeRows) {
                String type = row.getString("job_type").orElse("unknown");
                int count = row.getInt("count").orElse(0);
                entriesByType.put(type, count);
            }
            
            // Get oldest entry timestamp
            Row oldestRow = mimir.getRow("SELECT MIN(failed_at) as oldest FROM DEAD_LETTER_QUEUE");
            Long oldestEntry = oldestRow != null ? oldestRow.getLong("oldest").orElse(null) : null;
            
            return new DLQStatistics(totalEntries, retryableEntries, entriesByType, oldestEntry);
            
        } catch (Exception e) {
            Logger.error("Failed to get DLQ statistics: {}", e.getMessage());
            return new DLQStatistics(0, 0, new HashMap<>(), null);
        }
    }
    
    /**
     * Clean up old DLQ entries based on retention policy
     * 
     * @param retentionDays Number of days to retain entries (default: 30)
     * @return Number of entries cleaned up
     */
    public int cleanupOldEntries(int retentionDays) {
        try {
            long cutoffTime = Instant.now().toEpochMilli() - (retentionDays * 24 * 60 * 60 * 1000L);
            
            int deleted = mimir.executeSql(
                "DELETE FROM DEAD_LETTER_QUEUE WHERE failed_at < ? AND can_be_retried = 0",
                cutoffTime
            );
            
            if (deleted > 0) {
                Logger.info("Cleaned up {} old DLQ entries (older than {} days)", deleted, retentionDays);
            }
            
            return deleted;
        } catch (Exception e) {
            Logger.error("Failed to cleanup old DLQ entries: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Batch retry multiple DLQ entries
     * 
     * @param dlqEntryIds List of DLQ entry IDs to retry
     * @param resetRetryCount Whether to reset retry counts
     * @return Map of DLQ entry ID to new job ID (null if failed)
     */
    public Map<String, String> batchRetry(List<String> dlqEntryIds, boolean resetRetryCount) {
        Map<String, String> results = new HashMap<>();
        
        for (String dlqEntryId : dlqEntryIds) {
            String newJobId = manualRetry(dlqEntryId, resetRetryCount);
            results.put(dlqEntryId, newJobId);
        }
        
        int successful = (int) results.values().stream().filter(Objects::nonNull).count();
        Logger.info("Batch retry completed: {} successful out of {} total", successful, dlqEntryIds.size());
        
        return results;
    }
    
    // Private helper methods
    
    private DLQEntry createDLQEntryFromRow(Row row) {
        try {
            String id = row.getString("id").orElse("");
            String originalJobId = row.getString("original_job_id").orElse("");
            String jobType = row.getString("job_type").orElse("");
            String executionMode = row.getString("execution_mode").orElse("PARALLEL");
            String payloadJson = row.getString("payload").orElse("{}");
            int priority = row.getInt("priority").orElse(5);
            int maxRetries = row.getInt("max_retries").orElse(3);
            String failureReason = row.getString("failure_reason").orElse("");
            long failedAt = row.getLong("failed_at").orElse(0L);
            int retryAttempts = row.getInt("retry_attempts").orElse(0);
            String retryHistoryJson = row.getString("retry_history").orElse("[]");
            boolean canBeRetried = row.getInt("can_be_retried").orElse(1) == 1;
            long createdAt = row.getLong("created_at").orElse(0L);
            
            // Parse payload and retry history
            Map<String, Object> payload = Odin.getMapper().readValue(payloadJson, Map.class);
            List<String> retryHistory = Odin.getMapper().readValue(retryHistoryJson, List.class);
            
            return new DLQEntry(id, originalJobId, jobType, executionMode, payload, priority,
                              maxRetries, failureReason, failedAt, retryAttempts, retryHistory,
                              canBeRetried, createdAt);
                              
        } catch (Exception e) {
            Logger.error("Failed to create DLQ entry from database row: {}", e.getMessage());
            return null;
        }
    }
    
    private List<String> buildRetryHistory(String jobId, int retryAttempts) {
        List<String> history = new ArrayList<>();
        // This could be enhanced to track actual retry timestamps from job logs
        // For now, we'll create a simple history based on retry count
        for (int i = 1; i <= retryAttempts; i++) {
            history.add(String.format("Retry attempt %d", i));
        }
        return history;
    }
    
    private boolean determineIfRetryable(String failureReason, String jobType) {
        // Jobs with permanent errors should not be retryable by default
        if (failureReason != null) {
            String reason = failureReason.toLowerCase();
            if (reason.contains("permanent") || 
                reason.contains("validation") || 
                reason.contains("authentication") ||
                reason.contains("authorization")) {
                return false;
            }
        }
        
        // Most jobs can be retried manually after investigation
        return true;
    }
    
    /**
     * DLQ Statistics container
     */
    public static class DLQStatistics {
        private final int totalEntries;
        private final int retryableEntries;
        private final Map<String, Integer> entriesByType;
        private final Long oldestEntryTimestamp;
        
        public DLQStatistics(int totalEntries, int retryableEntries, 
                           Map<String, Integer> entriesByType, Long oldestEntryTimestamp) {
            this.totalEntries = totalEntries;
            this.retryableEntries = retryableEntries;
            this.entriesByType = entriesByType;
            this.oldestEntryTimestamp = oldestEntryTimestamp;
        }
        
        public int getTotalEntries() { return totalEntries; }
        public int getRetryableEntries() { return retryableEntries; }
        public Map<String, Integer> getEntriesByType() { return entriesByType; }
        public Long getOldestEntryTimestamp() { return oldestEntryTimestamp; }
    }
} 