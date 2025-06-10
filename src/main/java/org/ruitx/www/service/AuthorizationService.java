package org.ruitx.www.service;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.www.model.auth.Role;
import org.ruitx.www.model.auth.UserRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * AuthorizationService handles all role-based authorization logic.
 * This service manages role assignments, checks, and related operations.
 */
public class AuthorizationService {

    private final Mimir db;

    public AuthorizationService() {
        this.db = new Mimir();
    }

    /**
     * Get all roles assigned to a user.
     *
     * @param userId the user ID
     * @return list of role names assigned to the user
     */
    public List<String> getUserRoles(Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        try {
            List<Row> rows = db.getRows(
                    """
                    SELECT r.name 
                    FROM USER_ROLE ur 
                    JOIN ROLE r ON ur.role_id = r.id 
                    WHERE ur.user_id = ?
                    ORDER BY r.name
                    """,
                    userId
            );

            return rows.stream()
                    .map(row -> row.getString("name").orElse(""))
                    .filter(name -> !name.isEmpty())
                    .toList();
        } catch (Exception e) {
            JawsLogger.error("Failed to get user roles for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Check if a user has a specific role.
     *
     * @param userId   the user ID
     * @param roleName the role name to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(Integer userId, String roleName) {
        if (userId == null || roleName == null || roleName.trim().isEmpty()) {
            return false;
        }

        try {
            Row row = db.getRow(
                    """
                    SELECT COUNT(*) as count 
                    FROM USER_ROLE ur 
                    JOIN ROLE r ON ur.role_id = r.id 
                    WHERE ur.user_id = ? AND r.name = ?
                    """,
                    userId, roleName.trim()
            );

            return row != null && row.getInt("count").orElse(0) > 0;
        } catch (Exception e) {
            JawsLogger.error("Failed to check role {} for user {}: {}", roleName, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a user has ANY of the specified roles.
     *
     * @param userId    the user ID
     * @param roleNames the role names to check (varargs)
     * @return true if user has any of the roles, false otherwise
     */
    public boolean hasAnyRole(Integer userId, String... roleNames) {
        if (userId == null || roleNames == null || roleNames.length == 0) {
            return false;
        }

        // Admin always has access to everything
        if (hasRole(userId, "admin")) {
            return true;
        }

        return Arrays.stream(roleNames)
                .anyMatch(roleName -> hasRole(userId, roleName));
    }

    /**
     * Check if a user has ALL of the specified roles.
     *
     * @param userId    the user ID
     * @param roleNames the role names to check (varargs)
     * @return true if user has all roles, false otherwise
     */
    public boolean hasAllRoles(Integer userId, String... roleNames) {
        if (userId == null || roleNames == null || roleNames.length == 0) {
            return false;
        }

        // Admin always has access to everything
        if (hasRole(userId, "admin")) {
            return true;
        }

        return Arrays.stream(roleNames)
                .allMatch(roleName -> hasRole(userId, roleName));
    }

    /**
     * Assign a role to a user.
     *
     * @param userId     the user ID to assign the role to
     * @param roleName   the role name to assign
     * @param assignedBy the user ID who is assigning the role (optional)
     * @return true if role was assigned successfully, false otherwise
     */
    public boolean assignRole(Integer userId, String roleName, Integer assignedBy) {
        if (userId == null || roleName == null || roleName.trim().isEmpty()) {
            return false;
        }

        try {
            // Check if role exists
            Optional<Role> role = getRoleByName(roleName.trim());
            if (role.isEmpty()) {
                JawsLogger.warn("Cannot assign non-existent role {} to user {}", roleName, userId);
                return false;
            }

            // Check if user already has this role
            if (hasRole(userId, roleName)) {
                JawsLogger.debug("User {} already has role {}", userId, roleName);
                return true; // Consider this success
            }

            // Assign the role
            long now = Instant.now().getEpochSecond();
            int result = db.executeSql(
                    "INSERT INTO USER_ROLE (user_id, role_id, assigned_at, assigned_by) VALUES (?, ?, ?, ?)",
                    userId, role.get().id(), now, assignedBy
            );

            if (result > 0) {
                JawsLogger.info("Role {} assigned to user {} by user {}", roleName, userId, assignedBy);
                return true;
            } else {
                JawsLogger.error("Failed to assign role {} to user {}", roleName, userId);
                return false;
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to assign role {} to user {}: {}", roleName, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Remove a role from a user.
     *
     * @param userId   the user ID to remove the role from
     * @param roleName the role name to remove
     * @return true if role was removed successfully, false otherwise
     */
    public boolean removeRole(Integer userId, String roleName) {
        if (userId == null || roleName == null || roleName.trim().isEmpty()) {
            return false;
        }

        try {
            int result = db.executeSql(
                    """
                    DELETE FROM USER_ROLE 
                    WHERE user_id = ? AND role_id = (
                        SELECT id FROM ROLE WHERE name = ?
                    )
                    """,
                    userId, roleName.trim()
            );

            if (result > 0) {
                JawsLogger.info("Role {} removed from user {}", roleName, userId);
                return true;
            } else {
                JawsLogger.debug("Role {} was not assigned to user {} (nothing to remove)", roleName, userId);
                return true; // Consider this success
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to remove role {} from user {}: {}", roleName, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all available roles in the system.
     *
     * @return list of all roles
     */
    public List<Role> getAllRoles() {
        try {
            List<Row> rows = db.getRows("SELECT * FROM ROLE ORDER BY name");
            return rows.stream()
                    .map(Role::fromRow)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (Exception e) {
            JawsLogger.error("Failed to get all roles: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get a role by its name.
     *
     * @param roleName the role name
     * @return Optional containing the role if found, empty otherwise
     */
    public Optional<Role> getRoleByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            Row row = db.getRow("SELECT * FROM ROLE WHERE name = ?", roleName.trim());
            return row != null ? Role.fromRow(row) : Optional.empty();
        } catch (Exception e) {
            JawsLogger.error("Failed to get role by name {}: {}", roleName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a new role.
     *
     * @param roleName    the role name (must be unique)
     * @param description the role description (optional)
     * @return true if role was created successfully, false otherwise
     */
    public boolean createRole(String roleName, String description) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return false;
        }

        try {
            // Check if role already exists
            if (getRoleByName(roleName).isPresent()) {
                JawsLogger.warn("Role {} already exists", roleName);
                return false;
            }

            long now = Instant.now().getEpochSecond();
            int result = db.executeSql(
                    "INSERT INTO ROLE (name, description, created_at) VALUES (?, ?, ?)",
                    roleName.trim(), description, now
            );

            if (result > 0) {
                JawsLogger.info("Role {} created successfully", roleName);
                return true;
            } else {
                JawsLogger.error("Failed to create role {}", roleName);
                return false;
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to create role {}: {}", roleName, e.getMessage());
            return false;
        }
    }

    /**
     * Assign default "user" role to a new user.
     * This is typically called during user registration.
     *
     * @param userId the new user ID
     * @return true if default role was assigned successfully, false otherwise
     */
    public boolean assignDefaultRole(Integer userId) {
        return assignRole(userId, "user", null);
    }
} 