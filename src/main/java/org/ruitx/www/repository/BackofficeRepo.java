package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.Cacheable;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserSession;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BackofficeRepo {

    private final Mimir db;
    private final Mimir logsDb;

    public BackofficeRepo() {
        this.db = new Mimir();
        this.logsDb = new Mimir("src/main/resources/logs.db");
    }

    /**
     * Retrieves all users from the database.
     *
     * @return List of User objects representing all users in the system.
     */
    @Cacheable(tables = {"USER"})
    public List<User> getAllUsers() {
        List<Row> rows = db.getRows("SELECT * FROM USER ORDER BY created_at DESC");
        return rows.stream()
                .map(User::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Retrieves all user sessions from the database.
     *
     * @return List of UserSession objects representing all user sessions in the system.
     */
    @Cacheable(tables = {"USER_SESSION"})
    public List<UserSession> getAllUserSessions() {
        List<Row> rows = db.getRows("SELECT * FROM USER_SESSION ORDER BY created_at DESC");
        return rows.stream()
                .map(UserSession::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Retrieves paginated log entries from the logs database.
     *
     * @param pageRequest Pagination parameters
     * @return Page containing log entries and pagination metadata
     */
    public Page<Map<String, String>> getPaginatedLogs(PageRequest pageRequest) {
        try {
            String baseSql =
                    "SELECT id, timestamp, level, message, logger as source FROM LOG_ENTRIES ORDER BY timestamp DESC";
            Page<Row> rowPage = logsDb.getPage(
                    baseSql,
                    pageRequest
            );

            List<Map<String, String>> logEntries = rowPage.getContent().stream()
                    .map(row -> {
                        Map<String, String> logEntry = new HashMap<>();
                        logEntry.put("id", row.get("id").toString());
                        logEntry.put("timestamp", formatTimestamp(row.get("timestamp")));
                        logEntry.put("level", row.get("level").toString());
                        logEntry.put("message", row.get("message").toString());
                        logEntry.put("source", row.get("source") != null ? row.get("source").toString() : "System");
                        return logEntry;
                    })
                    .toList();

            return new Page<>(logEntries, pageRequest, rowPage.getTotalElements());

        } catch (Exception e) {
            Logger.error(e.getMessage(), e);
            return Page.empty(pageRequest);
        }
    }

    /**
     * Formats a timestamp from epoch milliseconds to readable format.
     */
    private String formatTimestamp(Object timestamp) {
        if (timestamp == null) return "Unknown";

        try {
            long epochMs = Long.parseLong(timestamp.toString());
            Instant instant = Instant.ofEpochMilli(epochMs);
            return instant.toString().replace("T", " ").substring(0, 19);
        } catch (Exception e) {
            return "Invalid timestamp";
        }
    }

    /**
     * Retrieves a single log entry by its ID.
     *
     * @param logId The ID of the log entry to retrieve
     * @return Map containing the complete log entry data, or null if not found
     */
    public Map<String, String> getLogById(String logId) {
        try {
            Row row = logsDb.getRow("SELECT * FROM LOG_ENTRIES WHERE id = ?", logId);
            if (row == null) {
                return null;
            }

            Map<String, String> logEntry = new HashMap<>();
            logEntry.put("id", row.get("id").toString());
            logEntry.put("timestamp", formatTimestamp(row.get("timestamp")));
            logEntry.put("level", row.get("level").toString());
            logEntry.put("message", row.get("message").toString());
            logEntry.put("source", row.get("logger") != null ? row.get("logger").toString() : "System");
            logEntry.put("thread", row.get("thread") != null ? row.get("thread").toString() : "");
            logEntry.put("method", row.get("method") != null ? row.get("method").toString() : "");
            logEntry.put("line", row.get("line") != null ? row.get("line").toString() : "");
            logEntry.put("exception", row.get("exception") != null ? row.get("exception").toString() : "");
            logEntry.put("created_at", formatTimestamp(row.get("created_at")));

            return logEntry;

        } catch (Exception e) {
            Logger.error("Failed to get log entry {}: {}", logId, e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves paginated log entries with optional filtering.
     *
     * @param pageRequest Pagination parameters
     * @param level       Filter by log level (null for all levels)
     * @param source      Filter by logger/source (null for all sources)
     * @param search      Search term in message (null for no search)
     * @return Page containing filtered log entries and pagination metadata
     */
    public Page<Map<String, String>> getFilteredLogs(PageRequest pageRequest, String level, String source, String search) {
        try {
            StringBuilder sqlBuilder =
                    new StringBuilder("SELECT id, timestamp, level, message, logger as source FROM LOG_ENTRIES WHERE 1=1");
            java.util.List<Object> params = new java.util.ArrayList<>();

            if (level != null && !level.trim().isEmpty()) {
                sqlBuilder.append(" AND level = ?");
                params.add(level);
            }

            if (source != null && !source.trim().isEmpty()) {
                sqlBuilder.append(" AND logger LIKE ?");
                params.add("%" + source + "%");
            }

            if (search != null && !search.trim().isEmpty()) {
                sqlBuilder.append(" AND message LIKE ?");
                params.add("%" + search + "%");
            }

            sqlBuilder.append(" ORDER BY timestamp DESC");

            String baseSql = sqlBuilder.toString();
            Page<Row> rowPage = logsDb.getPage(baseSql, pageRequest, params.toArray());

            List<Map<String, String>> logEntries = rowPage.getContent().stream()
                    .map(row -> {
                        Map<String, String> logEntry = new HashMap<>();
                        logEntry.put("id", row.get("id").toString());
                        logEntry.put("timestamp", formatTimestamp(row.get("timestamp")));
                        logEntry.put("level", row.get("level").toString());
                        logEntry.put("message", row.get("message").toString());
                        logEntry.put("source", row.get("source") != null ? row.get("source").toString() : "System");
                        return logEntry;
                    })
                    .toList();

            return new Page<>(logEntries, pageRequest, rowPage.getTotalElements());

        } catch (Exception e) {
            Logger.error("Failed to get filtered logs: {}", e.getMessage());
            return Page.empty(pageRequest);
        }
    }

    /**
     * Retrieves distinct log levels for filtering.
     *
     * @return List of distinct log levels
     */
    public List<String> getDistinctLogLevels() {
        try {
            List<Row> rows = logsDb.getRows("SELECT DISTINCT level FROM LOG_ENTRIES ORDER BY level");
            return rows.stream()
                    .map(row -> row.get("level").toString())
                    .toList();
        } catch (Exception e) {
            Logger.error("Failed to get distinct log levels: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Retrieves distinct log sources for filtering.
     *
     * @return List of distinct log sources
     */
    public List<String> getDistinctLogSources() {
        try {
            List<Row> rows = logsDb.getRows("SELECT DISTINCT logger FROM LOG_ENTRIES WHERE logger IS NOT NULL ORDER BY logger");
            return rows.stream()
                    .map(row -> row.get("logger").toString())
                    .toList();
        } catch (Exception e) {
            Logger.error("Failed to get distinct log sources: {}", e.getMessage());
            return List.of();
        }
    }
}