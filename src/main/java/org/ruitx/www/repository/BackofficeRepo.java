package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.types.SortDirection;
import org.ruitx.www.model.auth.Role;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * BackofficeRepo - Database operations for backoffice management
 * 
 * Consolidates all database queries and operations specific to the backoffice interface.
 * This includes job management, log retrieval, role management, and user listing operations.
 */
public class BackofficeRepo {

    private final Mimir db;
    private final Mimir logsDb;

    public BackofficeRepo() {
        this.db = new Mimir();
        this.logsDb = new Mimir("src/main/resources/logs.db");
    }

    // =============================================
    // JOB OPERATIONS
    // =============================================

    /**
     * Get paginated jobs with optional filtering
     */
    public Page<Row> getJobsPage(PageRequest pageRequest, String statusFilter, String typeFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM JOBS WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(statusFilter);
        }

        if (typeFilter != null && !typeFilter.isEmpty()) {
            sql.append(" AND type = ?");
            params.add(typeFilter);
        }

        return db.getPage(sql.toString(), pageRequest, params.toArray());
    }

    /**
     * Get job by ID
     */
    public Optional<Row> getJobById(String jobId) {
        Row row = db.getRow("SELECT * FROM JOBS WHERE id = ?", jobId);
        return Optional.ofNullable(row);
    }

    /**
     * Update job status for reprocessing
     */
    public int updateJobForReprocessing(String jobId) {
        return db.executeSql("""
            UPDATE JOBS SET 
                status = 'PENDING', 
                retry_count = retry_count + 1, 
                started_at = NULL, 
                completed_at = NULL 
            WHERE id = ?
            """, jobId);
    }

    /**
     * Delete job by ID
     */
    public int deleteJob(String jobId) {
        return db.executeSql("DELETE FROM JOBS WHERE id = ?", jobId);
    }

    /**
     * Get job statistics grouped by status
     */
    public List<Row> getJobStatusCounts() {
        return db.getRows("SELECT status, COUNT(*) as count FROM JOBS GROUP BY status");
    }

    // =============================================
    // LOG OPERATIONS
    // =============================================

    /**
     * Get paginated logs with optional filtering
     */
    public Page<Row> getLogsPage(PageRequest pageRequest, String levelFilter, String loggerFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM LOG_ENTRIES WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (levelFilter != null && !levelFilter.isEmpty()) {
            sql.append(" AND level = ?");
            params.add(levelFilter);
        }

        if (loggerFilter != null && !loggerFilter.isEmpty()) {
            sql.append(" AND logger LIKE ?");
            params.add("%" + loggerFilter + "%");
        }

        return logsDb.getPage(sql.toString(), pageRequest, params.toArray());
    }

    /**
     * Get log entry by ID
     */
    public Optional<Row> getLogById(String logId) {
        Row row = logsDb.getRow("SELECT * FROM LOG_ENTRIES WHERE id = ?", logId);
        return Optional.ofNullable(row);
    }

    // =============================================
    // ROLE OPERATIONS
    // =============================================

    /**
     * Get paginated roles
     */
    public Page<Row> getRolesPage(PageRequest pageRequest) {
        return db.getPage("SELECT * FROM ROLE", pageRequest);
    }

    /**
     * Create a new role
     */
    public int createRole(String name, String description) {
        long timestamp = System.currentTimeMillis();
        return db.executeSql(
            "INSERT INTO ROLE (name, description, created_at, updated_at) VALUES (?, ?, ?, ?)",
            name, description, timestamp, timestamp
        );
    }

    /**
     * Delete role by ID
     */
    public int deleteRole(Integer roleId) {
        return db.executeSql("DELETE FROM ROLE WHERE id = ?", roleId);
    }

    /**
     * Get role by ID
     */
    public Optional<Role> getRoleById(Integer roleId) {
        Row row = db.getRow("SELECT * FROM ROLE WHERE id = ?", roleId);
        if (row == null) {
            return Optional.empty();
        }
        
        return Optional.of(Role.builder()
            .id(row.getInt("id").orElse(0))
            .name(row.getString("name").orElse(""))
            .description(row.getString("description").orElse(null))
            .createdAt(row.getLong("created_at").orElse(null))
            .updatedAt(row.getLong("updated_at").orElse(null))
            .build());
    }

    /**
     * Get all roles
     */
    public List<Role> getAllRoles() {
        List<Row> rows = db.getRows("SELECT * FROM ROLE ORDER BY name");
        return rows.stream()
            .map(row -> Role.builder()
                .id(row.getInt("id").orElse(0))
                .name(row.getString("name").orElse(""))
                .description(row.getString("description").orElse(null))
                .createdAt(row.getLong("created_at").orElse(null))
                .updatedAt(row.getLong("updated_at").orElse(null))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Assign role to user
     */
    public int assignUserRole(Integer userId, Integer roleId, Integer assignedBy) {
        long timestamp = System.currentTimeMillis();
        return db.executeSql(
            "INSERT INTO USER_ROLE (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?)",
            userId, roleId, assignedBy, timestamp
        );
    }

    /**
     * Remove user role assignment
     */
    public int removeUserRole(Integer userRoleId) {
        return db.executeSql("DELETE FROM USER_ROLE WHERE id = ?", userRoleId);
    }

    /**
     * Get all user-role assignments with user and role details
     */
    public List<UserRole> getAllUserRoles() {
        String sql = """
            SELECT ur.*, u.user as username, r.name as role_name 
            FROM USER_ROLE ur 
            JOIN USER u ON ur.user_id = u.id 
            JOIN ROLE r ON ur.role_id = r.id 
            ORDER BY ur.assigned_at DESC
            """;
        
        List<Row> rows = db.getRows(sql);
        return rows.stream()
            .map(row -> UserRole.builder()
                .id(row.getInt("id").orElse(0))
                .userId(row.getInt("user_id").orElse(0))
                .roleId(row.getInt("role_id").orElse(0))
                .assignedBy(row.getInt("assigned_by").orElse(null))
                .assignedAt(row.getLong("assigned_at").orElse(null))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Get user count for a specific role
     */
    public int getUserCountForRole(Integer roleId) {
        Row row = db.getRow("SELECT COUNT(*) as count FROM USER_ROLE WHERE role_id = ?", roleId);
        return row != null ? row.getInt("count").orElse(0) : 0;
    }

    // =============================================
    // USER OPERATIONS FOR BACKOFFICE
    // =============================================

    /**
     * Get paginated users for backoffice management
     */
    public Page<Row> getUsersPage(PageRequest pageRequest) {
        return db.getPage("SELECT * FROM USER", pageRequest);
    }

    /**
     * Transform Row objects to User objects for backoffice display
     */
    public List<User> transformRowsToUsers(List<Row> rows) {
        return rows.stream()
            .map(row -> User.builder()
                .id(row.getInt("id").orElse(0))
                .user(row.getString("user").orElse(""))
                .passwordHash(row.getString("password_hash").orElse(""))
                .email(row.getString("email").orElse(null))
                .firstName(row.getString("first_name").orElse(null))
                .lastName(row.getString("last_name").orElse(null))
                .birthdate(row.getLong("birthdate").orElse(null))
                .gender(row.getString("gender").orElse(null))
                .phoneNumber(row.getString("phone_number").orElse(null))
                .profilePicture(row.getString("profile_picture").orElse(null))
                .bio(row.getString("bio").orElse(null))
                .location(row.getString("location").orElse(null))
                .website(row.getString("website").orElse(null))
                .lastLogin(row.getLong("last_login").orElse(null))
                .isActive(row.getInt("is_active").orElse(1))
                .failedLoginAttempts(row.getInt("failed_login_attempts").orElse(0))
                .lockoutUntil(row.getLong("lockout_until").orElse(null))
                .createdAt(row.getLong("created_at").orElse(0L))
                .updatedAt(row.getLong("updated_at").orElse(null))
                .build())
            .collect(Collectors.toList());
    }

    // =============================================
    // UTILITY METHODS
    // =============================================

    /**
     * Parse pagination parameters with defaults
     */
    public PageRequest parsePageRequest(String pageParam, String sizeParam, String sortParam, String directionParam) {
        int page = 0;
        int size = 10;
        String sortBy = "created_at";
        SortDirection direction = SortDirection.DESC;

        if (pageParam != null) {
            try {
                page = Integer.parseInt(pageParam);
            } catch (NumberFormatException e) {
                page = 0;
            }
        }

        if (sizeParam != null) {
            try {
                size = Integer.parseInt(sizeParam);
                if (size < 1) size = 10;
                if (size > 100) size = 100; // Max 100 per page
            } catch (NumberFormatException e) {
                size = 10;
            }
        }

        if (sortParam != null && !sortParam.trim().isEmpty()) {
            sortBy = sortParam.trim();
        }

        if (directionParam != null && "ASC".equalsIgnoreCase(directionParam)) {
            direction = SortDirection.ASC;
        }

        return new PageRequest(page, size, sortBy, direction);
    }

    /**
     * Parse pagination parameters for logs (larger default page size)
     */
    public PageRequest parsePageRequestForLogs(String pageParam, String sizeParam, String sortParam, String directionParam) {
        int page = 0;
        int size = 25; // Default 25 for logs
        String sortBy = "timestamp";
        SortDirection direction = SortDirection.DESC;

        if (pageParam != null) {
            try {
                page = Integer.parseInt(pageParam);
            } catch (NumberFormatException e) {
                page = 0;
            }
        }

        if (sizeParam != null) {
            try {
                size = Integer.parseInt(sizeParam);
                if (size < 1) size = 25;
                if (size > 100) size = 100;
            } catch (NumberFormatException e) {
                size = 25;
            }
        }

        if (sortParam != null && !sortParam.trim().isEmpty()) {
            sortBy = sortParam.trim();
        }

        if (directionParam != null && "ASC".equalsIgnoreCase(directionParam)) {
            direction = SortDirection.ASC;
        }

        return new PageRequest(page, size, sortBy, direction);
    }
} 