-- =============================================
-- JAWS LOGS DATABASE SCHEMA
-- =============================================
-- This schema is specifically for the logging database,
-- separate from the main application database.

-- Log entries table for JawsLogger
CREATE TABLE IF NOT EXISTS LOG_ENTRIES (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp       INTEGER NOT NULL,           -- Log timestamp (epoch milliseconds)
    level           TEXT    NOT NULL,           -- Log level (ERROR, WARN, INFO, DEBUG, TRACE)
    logger          TEXT,                       -- Logger name/class
    thread          TEXT,                       -- Thread name
    message         TEXT    NOT NULL,           -- Log message
    exception       TEXT,                       -- Exception stack trace if present
    method          TEXT,                       -- Method name where log occurred
    line            INTEGER,                    -- Line number where log occurred
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

-- =============================================
-- INDEXES FOR LOGGING SYSTEM PERFORMANCE
-- =============================================

-- Index for querying by log level
CREATE INDEX IF NOT EXISTS idx_log_entries_level ON LOG_ENTRIES (level);

-- Index for querying by timestamp (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_log_entries_timestamp ON LOG_ENTRIES (timestamp);

-- Index for querying by logger/class name
CREATE INDEX IF NOT EXISTS idx_log_entries_logger ON LOG_ENTRIES (logger);

-- Composite index for level + timestamp queries
CREATE INDEX IF NOT EXISTS idx_log_entries_level_timestamp ON LOG_ENTRIES (level, timestamp);

-- Index for created_at for maintenance queries
CREATE INDEX IF NOT EXISTS idx_log_entries_created_at ON LOG_ENTRIES (created_at); 