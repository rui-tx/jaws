package org.ruitx.www.service;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.APIResponse;
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
     * @return APIResponse with success/error message
     */
    public APIResponse<String> createRole(String roleName, String description) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return APIResponse.error("400 BAD REQUEST", "Role name cannot be empty");
        }

        try {
            // Check if role already exists
            if (getRoleByName(roleName).isPresent()) {
                return APIResponse.error("409 CONFLICT", "Role '" + roleName + "' already exists");
            }

            long now = Instant.now().getEpochSecond();
            int result = db.executeSql(
                    "INSERT INTO ROLE (name, description, created_at) VALUES (?, ?, ?)",
                    roleName.trim(), description, now
            );

            if (result > 0) {
                JawsLogger.info("Role {} created successfully", roleName);
                return APIResponse.success("201 CREATED", "Role '" + roleName + "' created successfully", null);
            } else {
                JawsLogger.error("Failed to create role {}", roleName);
                return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to create role");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to create role {}: {}", roleName, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Database error: " + e.getMessage());
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

    /**
     * Assign a role to a user by role ID.
     *
     * @param userId     the user ID to assign the role to
     * @param roleId     the role ID to assign
     * @param assignedBy the user ID who is assigning the role
     * @return APIResponse with success/error message
     */
    public APIResponse<String> assignRole(Integer userId, Integer roleId, Integer assignedBy) {
        if (userId == null || roleId == null) {
            return APIResponse.error("400 BAD REQUEST", "User ID and Role ID are required");
        }

        try {
            // Check if user already has this role
            List<Row> existing = db.getRows(
                    "SELECT id FROM USER_ROLE WHERE user_id = ? AND role_id = ?",
                    userId, roleId
            );
            
            if (!existing.isEmpty()) {
                return APIResponse.error("409 CONFLICT", "User already has this role");
            }

            // Assign the role
            long now = Instant.now().getEpochSecond();
            int result = db.executeSql(
                    "INSERT INTO USER_ROLE (user_id, role_id, assigned_at, assigned_by) VALUES (?, ?, ?, ?)",
                    userId, roleId, now, assignedBy
            );

            if (result > 0) {
                JawsLogger.info("Role {} assigned to user {} by user {}", roleId, userId, assignedBy);
                return APIResponse.success("201 CREATED", "Role assigned successfully", null);
            } else {
                return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to assign role");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to assign role {} to user {}: {}", roleId, userId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Database error: " + e.getMessage());
        }
    }

    /**
     * Get a role by its ID.
     *
     * @param roleId the role ID
     * @return Optional containing the role if found, empty otherwise
     */
    public Optional<Role> getRoleById(Integer roleId) {
        if (roleId == null) {
            return Optional.empty();
        }

        try {
            Row row = db.getRow("SELECT * FROM ROLE WHERE id = ?", roleId);
            return row != null ? Role.fromRow(row) : Optional.empty();
        } catch (Exception e) {
            JawsLogger.error("Failed to get role by ID {}: {}", roleId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all user role assignments.
     *
     * @return list of all user role assignments
     */
    public List<UserRole> getAllUserRoles() {
        try {
            List<Row> rows = db.getRows("SELECT * FROM USER_ROLE ORDER BY assigned_at DESC");
            return rows.stream()
                    .map(UserRole::fromRow)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (Exception e) {
            JawsLogger.error("Failed to get all user roles: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get the count of users for a specific role.
     *
     * @param roleId the role ID
     * @return count of users with this role
     */
    public int getUserCountForRole(Integer roleId) {
        if (roleId == null) {
            return 0;
        }

        try {
            Row row = db.getRow("SELECT COUNT(*) as count FROM USER_ROLE WHERE role_id = ?", roleId);
            return row != null ? row.getInt("count").orElse(0) : 0;
        } catch (Exception e) {
            JawsLogger.error("Failed to get user count for role {}: {}", roleId, e.getMessage());
            return 0;
        }
    }

    /**
     * Delete a role from the system.
     *
     * @param roleId the role ID to delete
     * @return APIResponse with success/error message
     */
    public APIResponse<String> deleteRole(Integer roleId) {
        if (roleId == null) {
            return APIResponse.error("400 BAD REQUEST", "Role ID is required");
        }

        try {
            // Check if role exists
            Optional<Role> role = getRoleById(roleId);
            if (role.isEmpty()) {
                return APIResponse.error("404 NOT FOUND", "Role not found");
            }

            // Check if role is assigned to any users
            int userCount = getUserCountForRole(roleId);
            if (userCount > 0) {
                return APIResponse.error("409 CONFLICT", 
                    "Cannot delete role '" + role.get().name() + "' because it is assigned to " + userCount + " user(s)");
            }

            // Delete the role
            int result = db.executeSql("DELETE FROM ROLE WHERE id = ?", roleId);

            if (result > 0) {
                JawsLogger.info("Role {} ({}) deleted successfully", roleId, role.get().name());
                return APIResponse.success("200 OK", "Role deleted successfully", null);
            } else {
                return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to delete role");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to delete role {}: {}", roleId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Database error: " + e.getMessage());
        }
    }

    /**
     * Remove a user role assignment.
     *
     * @param userRoleId the user role assignment ID to remove
     * @return APIResponse with success/error message
     */
    public APIResponse<String> removeUserRole(Integer userRoleId) {
        if (userRoleId == null) {
            return APIResponse.error("400 BAD REQUEST", "User role ID is required");
        }

        try {
            int result = db.executeSql("DELETE FROM USER_ROLE WHERE id = ?", userRoleId);

            if (result > 0) {
                JawsLogger.info("User role assignment {} removed successfully", userRoleId);
                return APIResponse.success("200 OK", "Role assignment removed successfully", null);
            } else {
                return APIResponse.error("404 NOT FOUND", "Role assignment not found");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to remove user role assignment {}: {}", userRoleId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Database error: " + e.getMessage());
        }
    }
} 