package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.Cacheable;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserSession;

import java.util.ArrayList;
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
     * Retrieves the top log entries from the logs database.
     *
     * @param limit Maximum number of log entries to retrieve
     * @return List of log entries as maps
     */
    public List<Map<String, String>> getTopLogs(int limit) {
        try {
            // Create a separate Mimir instance for the logs database
            Mimir logsDb = new Mimir("src/main/resources/logs.db");

            List<Row> rows = logsDb.getRows(
                    "SELECT id, timestamp, level, message, logger as source " +
                            "FROM LOG_ENTRIES " +
                            "ORDER BY timestamp DESC " +
                            "LIMIT ?",
                    limit
            );

            return rows.stream()
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

        } catch (Exception e) {
            // If logs database is not available, return empty list
            return new ArrayList<>();
        }
    }

    /**
     * Retrieves paginated log entries from the logs database.
     *
     * @param pageRequest Pagination parameters
     * @return Page containing log entries and pagination metadata
     */
    public Page<Map<String, String>> getPaginatedLogs(PageRequest pageRequest) {
        try {
          
            String baseSql = "SELECT id, timestamp, level, message, logger as source FROM LOG_ENTRIES";
            
            // Use Mimir's built-in pagination support
            Page<Row> rowPage = logsDb.getPage(
                baseSql + " ORDER BY timestamp DESC",
                pageRequest
            );

            // Transform Row objects to Map<String, String>
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
            // If logs database is not available, return empty page
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
            java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMs);
            return instant.toString().replace("T", " ").substring(0, 19);
        } catch (Exception e) {
            return "Invalid timestamp";
        }
    }
}