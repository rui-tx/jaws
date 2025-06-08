package org.ruitx.jaws.utils;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.www.jobs.BatchLogWriterJob;
import org.tinylog.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JawsLogger - Log utility with asynchronous batch database logging
 * Wraps TinyLog's Logger class and adds high-performance database logging capabilities through Freyr job system.
 */
public class JawsLogger {
    
    // Batch logging configuration
    private static final int BATCH_SIZE = 1000;                   // Submit job when buffer reaches this size 
    private static final long FLUSH_INTERVAL_MS = 5000;           // Submit job every 500ms for faster processing 
    private static final int BUFFER_CAPACITY = 10000;             // Maximum buffer size before dropping logs
    
    // Dedicated Mimir instance for logs database (fallback only)
    private static final Mimir logsDb;
    private static final boolean dbAvailable;
    
    // Batch processing components
    private static final BlockingQueue<LogEntry> logBuffer = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
    private static final AtomicInteger bufferSize = new AtomicInteger(0);
    private static final ScheduledExecutorService batchScheduler = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "jaws-logger-batch-scheduler"));
    private static volatile boolean batchingEnabled = true;
        
    static {
        Mimir tempDb = null;
        boolean tempDbAvailable = false;
        
        try {
            // Initialize logs database with schema - use proper resources path
            tempDb = new Mimir("src/main/resources/logs.db", "src/main/resources/sql/logs_schema.sql");
            tempDb.initializeDatabase("src/main/resources/logs.db");
            
            // Enable WAL mode for better concurrency
            tempDb.executeSql("PRAGMA journal_mode=WAL");
            tempDb.executeSql("PRAGMA synchronous=NORMAL"); // Better performance than FULL
            tempDb.executeSql("PRAGMA cache_size=10000"); // Increase cache size
            tempDb.executeSql("PRAGMA temp_store=memory"); // Store temp tables in memory
            tempDb.executeSql("PRAGMA busy_timeout=5000"); // 5 second timeout for BUSY errors
            
            tempDbAvailable = true;
            Logger.info("JawsLogger: Database logging initialized successfully with WAL mode");
        } catch (Exception e) {
            Logger.warn("JawsLogger: Failed to initialize database logging, falling back to console only: {}", e.getMessage());
        }
        
        logsDb = tempDb;
        dbAvailable = tempDbAvailable;
        
        // Start the batch scheduler for time-based flushing
        if (dbAvailable) {
            batchScheduler.scheduleAtFixedRate(JawsLogger::flushBufferIfNeeded, 
                                             FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, 
                                             TimeUnit.MILLISECONDS);
            Logger.info("JawsLogger: Batch logging enabled (batch_size={}, flush_interval={}ms)", 
                       BATCH_SIZE, FLUSH_INTERVAL_MS);
        }
        
        // Shutdown hook to flush remaining logs
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            batchingEnabled = false;
            flushBufferForce();
            batchScheduler.shutdown();
            try {
                if (!batchScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    batchScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                batchScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }
    
    // ============================================================================
    // TRACE LEVEL METHODS
    // ============================================================================
    
    public static void trace(String message) {
        Logger.trace(message);
        queueLogEntry("TRACE", message, null);
    }
    
    public static void trace(String message, Object... arguments) {
        Logger.trace(message, arguments);
        queueLogEntry("TRACE", formatMessage(message, arguments), null);
    }
    
    public static void trace(Throwable exception) {
        Logger.trace(exception);
        queueLogEntry("TRACE", exception.getMessage(), exception);
    }
    
    public static void trace(Throwable exception, String message) {
        Logger.trace(exception, message);
        queueLogEntry("TRACE", message, exception);
    }
    
    public static void trace(Throwable exception, String message, Object... arguments) {
        Logger.trace(exception, message, arguments);
        queueLogEntry("TRACE", formatMessage(message, arguments), exception);
    }
    
    // ============================================================================
    // DEBUG LEVEL METHODS
    // ============================================================================
    
    public static void debug(String message) {
        Logger.debug(message);
        queueLogEntry("DEBUG", message, null);
    }
    
    public static void debug(String message, Object... arguments) {
        Logger.debug(message, arguments);
        queueLogEntry("DEBUG", formatMessage(message, arguments), null);
    }
    
    public static void debug(Throwable exception) {
        Logger.debug(exception);
        queueLogEntry("DEBUG", exception.getMessage(), exception);
    }
    
    public static void debug(Throwable exception, String message) {
        Logger.debug(exception, message);
        queueLogEntry("DEBUG", message, exception);
    }
    
    public static void debug(Throwable exception, String message, Object... arguments) {
        Logger.debug(exception, message, arguments);
        queueLogEntry("DEBUG", formatMessage(message, arguments), exception);
    }
    
    // ============================================================================
    // INFO LEVEL METHODS
    // ============================================================================
    
    public static void info(String message) {
        Logger.info(message);
        queueLogEntry("INFO", message, null);
    }
    
    public static void info(String message, Object... arguments) {
        Logger.info(message, arguments);
        queueLogEntry("INFO", formatMessage(message, arguments), null);
    }
    
    public static void info(Throwable exception) {
        Logger.info(exception);
        queueLogEntry("INFO", exception.getMessage(), exception);
    }
    
    public static void info(Throwable exception, String message) {
        Logger.info(exception, message);
        queueLogEntry("INFO", message, exception);
    }
    
    public static void info(Throwable exception, String message, Object... arguments) {
        Logger.info(exception, message, arguments);
        queueLogEntry("INFO", formatMessage(message, arguments), exception);
    }
    
    // ============================================================================
    // WARN LEVEL METHODS
    // ============================================================================
    
    public static void warn(String message) {
        Logger.warn(message);
        queueLogEntry("WARN", message, null);
    }
    
    public static void warn(String message, Object... arguments) {
        Logger.warn(message, arguments);
        queueLogEntry("WARN", formatMessage(message, arguments), null);
    }
    
    public static void warn(Throwable exception) {
        Logger.warn(exception);
        queueLogEntry("WARN", exception.getMessage(), exception);
    }
    
    public static void warn(Throwable exception, String message) {
        Logger.warn(exception, message);
        queueLogEntry("WARN", message, exception);
    }
    
    public static void warn(Throwable exception, String message, Object... arguments) {
        Logger.warn(exception, message, arguments);
        queueLogEntry("WARN", formatMessage(message, arguments), exception);
    }
    
    // ============================================================================
    // ERROR LEVEL METHODS
    // ============================================================================
    
    public static void error(String message) {
        Logger.error(message);
        queueLogEntry("ERROR", message, null);
    }
    
    public static void error(String message, Object... arguments) {
        Logger.error(message, arguments);
        queueLogEntry("ERROR", formatMessage(message, arguments), null);
    }
    
    public static void error(Throwable exception) {
        Logger.error(exception);
        queueLogEntry("ERROR", exception.getMessage(), exception);
    }
    
    public static void error(Throwable exception, String message) {
        Logger.error(exception, message);
        queueLogEntry("ERROR", message, exception);
    }
    
    public static void error(Throwable exception, String message, Object... arguments) {
        Logger.error(exception, message, arguments);
        queueLogEntry("ERROR", formatMessage(message, arguments), exception);
    }
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Check if database logging is available
     */
    public static boolean isDatabaseLoggingAvailable() {
        return dbAvailable;
    }
    
    /**
     * Get the logs database path
     */
    public static String getLogsDatabasePath() {
        return dbAvailable ? logsDb.getDatabasePath() : null;
    }
    
    /**
     * Force flush any remaining log entries in the buffer
     * Useful for testing or shutdown scenarios
     */
    public static void forceFlush() {
        flushBufferForce();
    }
    
    /**
     * Get current buffer statistics for monitoring
     */
    public static Map<String, Object> getBufferStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("bufferSize", bufferSize.get());
        stats.put("bufferCapacity", BUFFER_CAPACITY);
        stats.put("batchSize", BATCH_SIZE);
        stats.put("flushIntervalMs", FLUSH_INTERVAL_MS);
        stats.put("batchingEnabled", batchingEnabled);
        stats.put("dbAvailable", dbAvailable);
        return stats;
    }
    
    // ============================================================================
    // PRIVATE BATCH PROCESSING METHODS
    // ============================================================================
    
    /**
     * Queue a log entry for batch processing
     */
    private static void queueLogEntry(String level, String message, Throwable exception) {
        if (!dbAvailable || !batchingEnabled) {
            return; // Skip queuing if database unavailable or batching disabled
        }
        
        try {
            // Get caller information
            CallerInfo caller = getCallerInfo();
            
            // Get current thread info
            Thread currentThread = Thread.currentThread();
            String threadName = currentThread.getName();
            
            // Prepare exception string if present
            String exceptionStr = null;
            if (exception != null) {
                exceptionStr = getStackTraceAsString(exception);
            }
            
            // Create log entry
            LogEntry logEntry = new LogEntry(
                System.currentTimeMillis(),
                level,
                caller.className,
                threadName,
                message,
                exceptionStr,
                caller.methodName,
                caller.lineNumber
            );
            
            // Try to add to buffer (non-blocking)
            boolean added = logBuffer.offer(logEntry);
            if (added) {
                int currentSize = bufferSize.incrementAndGet();
                
                // Check if we should flush based on size
                if (currentSize >= BATCH_SIZE) {
                    flushBuffer();
                }
            } else {
                // Buffer is full - try to flush it first, then drop if still full
                flushBuffer();
                
                // Try one more time after flush
                boolean retryAdded = logBuffer.offer(logEntry);
                if (!retryAdded) {
                    // Still full - drop this log entry but don't spam warnings
                    if (Math.random() < 0.01) { // Only warn 1% of the time to avoid log spam
                        Logger.warn("JawsLogger: Log buffer consistently full, dropping log entries");
                    }
                }
            }
            
        } catch (Exception e) {
            // Avoid infinite recursion - don't use JawsLogger here
            Logger.warn("JawsLogger: Failed to queue log entry: {}", e.getMessage());
        }
    }
    
    /**
     * Flush the buffer by submitting a batch job (if buffer has entries)
     */
    private static void flushBufferIfNeeded() {
        if (bufferSize.get() > 0) {
            flushBuffer();
        }
    }
    
    /**
     * Flush the buffer by submitting a batch job
     */
    private static void flushBuffer() {
        if (!batchingEnabled) {
            return;
        }
        
        List<LogEntry> batch = drainBuffer();
        if (!batch.isEmpty()) {
            submitBatchJob(batch);
        }
    }
    
    /**
     * Force flush the buffer (used during shutdown)
     */
    private static void flushBufferForce() {
        List<LogEntry> batch = drainBuffer();
        if (!batch.isEmpty()) {
            // During shutdown, try job submission first, then fallback to direct DB write
            try {
                submitBatchJob(batch);
            } catch (Exception e) {
                Logger.warn("JawsLogger: Failed to submit batch job during shutdown, writing directly to DB");
                writeBatchDirectly(batch);
            }
        }
    }
    
    /**
     * Drain the buffer and return all log entries
     */
    private static List<LogEntry> drainBuffer() {
        List<LogEntry> batch = new ArrayList<>();
        LogEntry entry;
        
        // Drain all entries from buffer
        while ((entry = logBuffer.poll()) != null) {
            batch.add(entry);
            bufferSize.decrementAndGet();
        }
        
        return batch;
    }
    
    /**
     * Submit a batch job to Freyr
     */
    private static void submitBatchJob(List<LogEntry> batch) {
        try {
            // Convert LogEntry objects to Maps for job payload
            List<Map<String, Object>> logEntryMaps = new ArrayList<>();
            for (LogEntry entry : batch) {
                Map<String, Object> entryMap = new HashMap<>();
                entryMap.put("timestamp", entry.getTimestamp());
                entryMap.put("level", entry.getLevel());
                entryMap.put("logger", entry.getLogger());
                entryMap.put("thread", entry.getThread());
                entryMap.put("message", entry.getMessage());
                entryMap.put("exception", entry.getException());
                entryMap.put("method", entry.getMethod());
                entryMap.put("lineNumber", entry.getLineNumber());
                logEntryMaps.add(entryMap);
            }
            
            // Create job payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("logEntries", logEntryMaps);
            
            // Submit to Freyr
            BatchLogWriterJob job = new BatchLogWriterJob(payload);
            Freyr.getInstance().submit(job);
            
            Logger.debug("JawsLogger: Submitted batch job with {} log entries", batch.size());
            
        } catch (Exception e) {
            Logger.error("JawsLogger: Failed to submit batch logging job: {}", e.getMessage());
            // Fallback to direct database write
            writeBatchDirectly(batch);
        }
    }
    
    /**
     * Fallback method to write logs directly to database (bypassing job system)
     */
    private static void writeBatchDirectly(List<LogEntry> batch) {
        if (logsDb == null) {
            return;
        }
        
        int maxRetries = 3;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                for (LogEntry entry : batch) {
                    logsDb.executeSql(
                        "INSERT INTO LOG_ENTRIES (timestamp, level, logger, thread, message, exception, method, line) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        entry.getTimestamp(),
                        entry.getLevel(),
                        entry.getLogger(),
                        entry.getThread(),
                        entry.getMessage(),
                        entry.getException(),
                        entry.getMethod(),
                        entry.getLineNumber()
                    );
                }
                Logger.debug("JawsLogger: Direct database write completed for {} entries", batch.size());
                return; // Success - exit retry loop
                
            } catch (Exception e) {
                boolean isBusyError = e.getMessage() != null && 
                    (e.getMessage().contains("SQLITE_BUSY") || e.getMessage().contains("database is locked"));
                
                if (isBusyError && attempt < maxRetries) {
                    // Exponential backoff: 50ms, 200ms, 800ms
                    long delayMs = 50L * (long) Math.pow(4, attempt);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Logger.warn("JawsLogger: Retry interrupted, dropping {} log entries", batch.size());
                        return;
                    }
                    Logger.debug("JawsLogger: Database busy, retrying in {}ms (attempt {}/{})", delayMs, attempt + 1, maxRetries);
                } else {
                    // Final attempt failed or non-retryable error
                    if (isBusyError) {
                        Logger.warn("JawsLogger: Database busy after {} retries, dropping {} log entries", maxRetries, batch.size());
                    } else {
                        Logger.error("JawsLogger: Failed to write logs directly to database: {}", e.getMessage());
                    }
                    return;
                }
            }
        }
    }
    
    // ============================================================================
    // PRIVATE HELPER METHODS (unchanged)
    // ============================================================================
    
    /**
     * Format message with arguments using simple placeholder replacement
     */
    private static String formatMessage(String message, Object... arguments) {
        if (arguments == null || arguments.length == 0) {
            return message;
        }
        
        try {
            // Simple {} placeholder replacement
            String result = message;
            for (Object arg : arguments) {
                if (result.contains("{}")) {
                    String replacement = arg != null ? arg.toString() : "null";
                    result = result.replaceFirst("\\{\\}", replacement);
                }
            }
            return result;
        } catch (Exception e) {
            // If formatting fails, return original message
            return message + " [formatting failed]";
        }
    }
    
    /**
     * Get caller information from stack trace
     */
    private static CallerInfo getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        // Find the first non-JawsLogger caller
        for (int i = 2; i < stackTrace.length; i++) { // Skip getStackTrace() and getCallerInfo()
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            
            // Skip JawsLogger methods
            if (!className.equals(JawsLogger.class.getName())) {
                return new CallerInfo(
                    className,
                    element.getMethodName(),
                    element.getLineNumber()
                );
            }
        }
        
        // Fallback if no caller found
        return new CallerInfo("Unknown", "unknown", 0);
    }
    
    /**
     * Convert exception stack trace to string
     */
    private static String getStackTraceAsString(Throwable exception) {
        if (exception == null) {
            return null;
        }
        
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            exception.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return exception.toString();
        }
    }
    
    /**
     * Helper class to store caller information
     */
    private static class CallerInfo {
        final String className;
        final String methodName;
        final int lineNumber;
        
        CallerInfo(String className, String methodName, int lineNumber) {
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
        }
    }
} 