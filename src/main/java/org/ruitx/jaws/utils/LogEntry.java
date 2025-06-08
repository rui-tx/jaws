package org.ruitx.jaws.utils;

/**
 * LogEntry - Represents a single log entry for batch processing
 */
public class LogEntry {
    private final long timestamp;
    private final String level;
    private final String logger;
    private final String thread;
    private final String message;
    private final String exception;
    private final String method;
    private final int lineNumber;
    private final long createdAt;
    
    public LogEntry(long timestamp, String level, String logger, String thread, 
                   String message, String exception, String method, int lineNumber) {
        this.timestamp = timestamp;
        this.level = level;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
        this.exception = exception;
        this.method = method;
        this.lineNumber = lineNumber;
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters
    public long getTimestamp() { return timestamp; }
    public String getLevel() { return level; }
    public String getLogger() { return logger; }
    public String getThread() { return thread; }
    public String getMessage() { return message; }
    public String getException() { return exception; }
    public String getMethod() { return method; }
    public int getLineNumber() { return lineNumber; }
    public long getCreatedAt() { return createdAt; }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s (%s:%d)", 
                           level, logger, message, method, lineNumber);
    }
} 