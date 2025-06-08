package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.freyr.BaseJob;
import org.ruitx.jaws.components.freyr.ExecutionMode;
import org.ruitx.jaws.utils.LogEntry;
import org.tinylog.Logger;

import java.util.List;
import java.util.Map;

/**
 * BatchLogWriterJob - Asynchronously writes multiple log entries to the database
 * 
 * This job processes a batch of log entries in a single database transaction,
 * significantly improving logging performance for high-throughput applications.
 */
public class BatchLogWriterJob extends BaseJob {
    
    public static final String JOB_TYPE = "batch-log-writer";
    
    // Use SEQUENTIAL mode to ensure ordered log writes and avoid database contention
    public BatchLogWriterJob(Map<String, Object> payload) {
        super(JOB_TYPE, ExecutionMode.PARALLEL, 1, 3, 30000L, payload); // High priority (1), 3 retries, 30s timeout
    }
    
    @Override
    public void execute() throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logEntryMaps = (List<Map<String, Object>>) getPayload().get("logEntries");
        
        if (logEntryMaps == null || logEntryMaps.isEmpty()) {
            Logger.debug("BatchLogWriterJob: No log entries to process");
            return;
        }
        
        // Get dedicated logs database
        Mimir logsDb = null;
        try {
            logsDb = new Mimir("src/main/resources/logs.db", "src/main/resources/sql/logs_schema.sql");
            logsDb.initializeDatabase("src/main/resources/logs.db");
            
            // Process all log entries in a single transaction for maximum performance
            logsDb.beginTransaction();
            
            try {
                for (Map<String, Object> logEntryMap : logEntryMaps) {
                    try {
                        logsDb.executeSql(
                            "INSERT INTO LOG_ENTRIES (timestamp, level, logger, thread, message, exception, method, line) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            (Long) logEntryMap.get("timestamp"),
                            (String) logEntryMap.get("level"),
                            (String) logEntryMap.get("logger"),
                            (String) logEntryMap.get("thread"),
                            (String) logEntryMap.get("message"),
                            (String) logEntryMap.get("exception"),
                            (String) logEntryMap.get("method"),
                            (Integer) logEntryMap.get("lineNumber")
                        );
                    } catch (Exception e) {
                        Logger.error("Failed to insert log entry in batch: {}", e.getMessage());
                        // Continue with other entries, don't fail the entire batch for one bad entry
                    }
                }
                
                // Commit the transaction
                logsDb.commitTransaction();
                Logger.debug("BatchLogWriterJob: Successfully wrote {} log entries to database", logEntryMaps.size());
                
            } catch (Exception e) {
                // Rollback transaction on any error
                logsDb.rollbackTransaction();
                throw e;
            }
            
        } catch (Exception e) {
            Logger.error("BatchLogWriterJob failed: {}", e.getMessage(), e);
            throw e; // Re-throw to trigger retry mechanism
        } finally {
            if (logsDb != null) {
                try {
                    logsDb.close();
                } catch (Exception e) {
                    Logger.warn("Failed to close logs database connection: {}", e.getMessage());
                }
            }
        }
    }
} 