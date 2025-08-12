package org.ruitx.www.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.db.mapping.LogEntryMapper;
import org.ruitx.jaws.db.mapping.UserMapper;
import org.ruitx.jaws.db.mapping.UserSessionMapper;
import org.ruitx.jaws.db.repo.BaseRepo;
import org.ruitx.jaws.interfaces.Cacheable;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.LogEntry;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserSession;
import org.tinylog.Logger;

public class BackofficeRepo extends BaseRepo {

  private final Mimir logsDb;

  public BackofficeRepo() {
    super(Odin.getDB("db"));
    this.logsDb = Odin.getDB("logs");
  }

  /**
   * Retrieves all users from the database.
   *
   * @return List of User objects representing all users in the system.
   */
  @Cacheable(tables = {"USER"})
  public List<User> getAllUsers() {
    return getAll("SELECT * FROM USER ORDER BY created_at DESC", UserMapper.INSTANCE);
  }

  /**
   * Retrieves all user sessions from the database.
   *
   * @return List of UserSession objects representing all user sessions in the system.
   */
  @Cacheable(tables = {"USER_SESSION"})
  public List<UserSession> getAllUserSessions() {
    return getAll(
        "SELECT * FROM USER_SESSION ORDER BY created_at DESC",
        UserSessionMapper.INSTANCE);
  }

  /**
   * Retrieves paginated log entries from the logs database.
   *
   * @param pageRequest Pagination parameters
   * @return Page containing log entries and pagination metadata
   */
  public Page<Map<String, String>> getPaginatedLogs(PageRequest pageRequest) {
    try {
      String sql =
          "SELECT id, timestamp, level, message, logger FROM LOG_ENTRIES ORDER BY timestamp DESC";
      Page<LogEntry> entryPage = logsDb.getPage(sql, pageRequest, LogEntryMapper.INSTANCE::map);

      List<Map<String, String>> logEntries = entryPage.getContent().stream()
          .map(entry -> {
            Map<String, String> log = new HashMap<>();
            if (entry.getId() != null) {
              log.put("id", entry.getId().toString());
            }
            log.put("timestamp", formatTimestamp(entry.getTimestamp()));
            log.put("level", entry.getLevel() != null ? entry.getLevel() : "");
            log.put("message", entry.getMessage() != null ? entry.getMessage() : "");
            log.put("source", entry.getLogger() != null ? entry.getLogger() : "System");
            return log;
          })
          .toList();

      return new Page<>(logEntries, pageRequest, entryPage.getTotalElements());

    } catch (Exception e) {
      Logger.error(e.getMessage(), e);
      return Page.empty(pageRequest);
    }
  }

  /**
   * Formats a timestamp from epoch milliseconds to readable format.
   */
  private String formatTimestamp(Object timestamp) {
    if (timestamp == null) {
      return "Unknown";
    }

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
      Optional<Row> row = logsDb.getRow("SELECT * FROM LOG_ENTRIES WHERE id = ?", logId);
      if (row.isEmpty()) {
        return null;
      }

      Map<String, String> logEntry = new HashMap<>();
      logEntry.put("id", row.get().get("id").toString());
      logEntry.put("timestamp", formatTimestamp(row.get().get("timestamp")));
      logEntry.put("level", row.get().get("level").toString());
      logEntry.put("message", row.get().get("message").toString());
      logEntry.put("source",
          row.get().get("logger") != null ? row.get().get("logger").toString() : "System");
      logEntry.put("thread",
          row.get().get("thread") != null ? row.get().get("thread").toString() : "");
      logEntry.put("method",
          row.get().get("method") != null ? row.get().get("method").toString() : "");
      logEntry.put("line", row.get().get("line") != null ? row.get().get("line").toString() : "");
      logEntry.put("exception",
          row.get().get("exception") != null ? row.get().get("exception").toString() : "");
      logEntry.put("created_at", formatTimestamp(row.get().get("created_at")));

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
  public Page<Map<String, String>> getFilteredLogs(PageRequest pageRequest, String level,
      String source, String search) {
    try {
      StringBuilder sqlBuilder =
          new StringBuilder(
              "SELECT id, timestamp, level, message, logger as source FROM LOG_ENTRIES WHERE 1=1");
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
            logEntry.put("source",
                row.get("source") != null ? row.get("source").toString() : "System");
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
      List<Row> rows = logsDb.getRows(
          "SELECT DISTINCT logger FROM LOG_ENTRIES WHERE logger IS NOT NULL ORDER BY logger");
      return rows.stream()
          .map(row -> row.get("logger").toString())
          .toList();
    } catch (Exception e) {
      Logger.error("Failed to get distinct log sources: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Retrieves paginated users from the database.
   *
   * @param pageRequest Pagination parameters
   * @return Page containing user entries and pagination metadata
   */
  public Page<Map<String, String>> getPaginatedUsers(PageRequest pageRequest) {
    try {
      String sql = "SELECT id, user, email, first_name, last_name, is_active, last_login, created_at FROM USER ORDER BY created_at DESC";
      Page<Row> rowPage = db.getPage(sql, pageRequest);

      List<Map<String, String>> users = rowPage.getContent().stream()
          .map(this::mapUserRow)
          .toList();

      return new Page<>(users, pageRequest, rowPage.getTotalElements());

    } catch (Exception e) {
      Logger.error("Failed to get paginated users: {}", e.getMessage());
      return Page.empty(pageRequest);
    }
  }

  /**
   * Retrieves a single user by ID.
   *
   * @param userId The ID of the user to retrieve
   * @return Map containing user data or null if not found
   */
  public Map<String, String> getUserById(String userId) {
    try {
      Optional<Row> row = db.getRow("SELECT * FROM USER WHERE id = ?", userId);
      if (row.isEmpty()) {
        return null;
      }

      return mapUserDetailRow(row.get());

    } catch (Exception e) {
      Logger.error("Failed to get user {}: {}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * Retrieves paginated users with optional filtering.
   *
   * @param pageRequest Pagination parameters
   * @param status      Filter by user status (null for all statuses)
   * @param role        Filter by user role (null for all roles)
   * @param search      Search term in username, email, or name (null for no search)
   * @return Page containing filtered user entries and pagination metadata
   */
  public Page<Map<String, String>> getFilteredUsers(PageRequest pageRequest, String status,
      String role, String search) {
    try {
      StringBuilder sqlBuilder = new StringBuilder(
          "SELECT DISTINCT u.id, u.user, u.email, u.first_name, u.last_name, u.is_active, u.last_login, u.created_at, u.lockout_until "
              +
              "FROM USER u " +
              "LEFT JOIN USER_ROLE ur ON u.id = ur.user_id " +
              "LEFT JOIN ROLE r ON ur.role_id = r.id " +
              "WHERE 1=1");
      java.util.List<Object> params = new java.util.ArrayList<>();

      if (status != null && !status.trim().isEmpty()) {
        if ("active".equals(status)) {
          sqlBuilder.append(
              " AND u.is_active = 1 AND (u.lockout_until IS NULL OR u.lockout_until < ?)");
          params.add(System.currentTimeMillis() / 1000);
        } else if ("inactive".equals(status)) {
          sqlBuilder.append(" AND u.is_active = 0");
        } else if ("locked".equals(status)) {
          sqlBuilder.append(" AND u.lockout_until > ?");
          params.add(System.currentTimeMillis() / 1000);
        }
      }

      if (role != null && !role.trim().isEmpty()) {
        sqlBuilder.append(" AND r.name = ?");
        params.add(role);
      }

      if (search != null && !search.trim().isEmpty()) {
        sqlBuilder.append(
            " AND (u.user LIKE ? OR u.email LIKE ? OR u.first_name LIKE ? OR u.last_name LIKE ?)");
        String searchPattern = "%" + search + "%";
        params.add(searchPattern);
      }

      sqlBuilder.append(" ORDER BY u.created_at DESC");

      String baseSql = sqlBuilder.toString();
      Page<Row> rowPage = db.getPage(baseSql, pageRequest, params.toArray());

      List<Map<String, String>> users = rowPage.getContent().stream()
          .map(this::mapUserRow)
          .toList();

      return new Page<>(users, pageRequest, rowPage.getTotalElements());

    } catch (Exception e) {
      Logger.error("Failed to get filtered users: {}", e.getMessage());
      return Page.empty(pageRequest);
    }
  }

  /**
   * Retrieves distinct user statuses for filtering.
   *
   * @return List of distinct user statuses
   */
  public List<String> getDistinctUserStatuses() {
    return List.of("active", "inactive", "locked");
  }

  /**
   * Retrieves distinct user roles for filtering.
   *
   * @return List of distinct user roles
   */
  public List<String> getDistinctUserRoles() {
    try {
      List<Row> rows = db.getRows("SELECT DISTINCT name FROM ROLE ORDER BY name");
      return rows.stream()
          .map(row -> row.get("name").toString())
          .toList();
    } catch (Exception e) {
      Logger.error("Failed to get distinct user roles: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Maps a database row to a user map for table display.
   *
   * @param row Database row
   * @return Map containing user data for table display
   */
  private Map<String, String> mapUserRow(Row row) {
    Map<String, String> user = new HashMap<>();
    user.put("id", row.get("id").toString());
    user.put("username", row.get("user").toString());
    user.put("email", row.get("email") != null ? row.get("email").toString() : "");
    user.put("name", formatUserName(row.get("first_name"), row.get("last_name")));
    user.put("status", determineUserStatus(row.get("is_active"), row.get("lockout_until")));
    user.put("last_login", formatTimestamp(row.get("last_login")));
    user.put("created", formatTimestamp(row.get("created_at")));
    return user;
  }

  /**
   * Maps a database row to a detailed user map for detail view.
   *
   * @param row Database row
   * @return Map containing detailed user data
   */
  private Map<String, String> mapUserDetailRow(Row row) {
    Map<String, String> user = new HashMap<>();
    user.put("id", row.get("id").toString());
    user.put("username", row.get("user").toString());
    user.put("email", row.get("email") != null ? row.get("email").toString() : "");
    user.put("first_name", row.get("first_name") != null ? row.get("first_name").toString() : "");
    user.put("last_name", row.get("last_name") != null ? row.get("last_name").toString() : "");
    user.put("name", formatUserName(row.get("first_name"), row.get("last_name")));
    user.put("status", determineUserStatus(row.get("is_active"), row.get("lockout_until")));
    user.put("birthdate", formatTimestamp(row.get("birthdate")));
    user.put("gender", row.get("gender") != null ? row.get("gender").toString() : "");
    user.put("phone_number",
        row.get("phone_number") != null ? row.get("phone_number").toString() : "");
    user.put("profile_picture",
        row.get("profile_picture") != null ? row.get("profile_picture").toString() : "");
    user.put("bio", row.get("bio") != null ? row.get("bio").toString() : "");
    user.put("location", row.get("location") != null ? row.get("location").toString() : "");
    user.put("website", row.get("website") != null ? row.get("website").toString() : "");
    user.put("last_login", formatTimestamp(row.get("last_login")));
    user.put("failed_login_attempts",
        row.get("failed_login_attempts") != null ? row.get("failed_login_attempts").toString()
            : "0");
    user.put("lockout_until", formatTimestamp(row.get("lockout_until")));
    user.put("created_at", formatTimestamp(row.get("created_at")));
    user.put("updated_at", formatTimestamp(row.get("updated_at")));
    return user;
  }

  /**
   * Formats a user's full name from first and last name.
   *
   * @param firstName First name
   * @param lastName  Last name
   * @return Formatted full name
   */
  private String formatUserName(Object firstName, Object lastName) {
    String first = firstName != null ? firstName.toString() : "";
    String last = lastName != null ? lastName.toString() : "";

    if (first.isEmpty() && last.isEmpty()) {
      return "N/A";
    } else if (first.isEmpty()) {
      return last;
    } else if (last.isEmpty()) {
      return first;
    } else {
      return first + " " + last;
    }
  }

  /**
   * Determines the user status based on is_active and lockout_until fields.
   *
   * @param isActive     Active status
   * @param lockoutUntil Lockout timestamp
   * @return Status string (active, inactive, or locked)
   */
  private String determineUserStatus(Object isActive, Object lockoutUntil) {
    if (lockoutUntil != null) {
      try {
        long lockoutTime = Long.parseLong(lockoutUntil.toString());
        if (lockoutTime > System.currentTimeMillis() / 1000) {
          return "locked";
        }
      } catch (NumberFormatException e) {
        // Ignore parsing errors
      }
    }

    if (isActive != null && "1".equals(isActive.toString())) {
      return "active";
    } else {
      return "inactive";
    }
  }
}