package org.ruitx.www.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.auth.LoginResponse;
import org.ruitx.www.dto.auth.UserCreateRequest;
import org.ruitx.www.dto.auth.UserUpdateRequest;
import org.ruitx.www.model.auth.Role;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserRole;
import org.ruitx.www.repository.AuthRepo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.ruitx.jaws.strings.ResponseCode.*;

/**
 * AuthService handles all authentication and authorization business logic.
 * This service combines user authentication, role management, and authorization checks.
 */
public class AuthService {

    private final AuthRepo authRepo;

    public AuthService() {
        this.authRepo = new AuthRepo();
    }

    // User Authentication Methods

    public APIResponse<String> createUser(UserCreateRequest request) {
        Optional<User> user = authRepo.getUserByUsername(request.username().toLowerCase());
        if (user.isPresent()) {
            return APIResponse.error(CONFLICT, "User already exists");
        }

        Optional<Integer> result = authRepo.createUser(
                request.username(),
                BCrypt.withDefaults().hashToString(12, request.password().toCharArray()),
                request.firstName(),
                request.lastName());

        if (result.isEmpty()) {
            return APIResponse.error(INTERNAL_SERVER_ERROR, "Cannot create user. Check the logs for more details");
        }

        // Assign default "user" role to new user
        boolean roleAssigned = assignDefaultRole(result.get());
        if (!roleAssigned) {
            // Log warning but don't fail user creation
            // The user can still be assigned roles manually later
            // This prevents breaking user creation if role system has issues
        }

        return APIResponse.success(CREATED, "User created successfully!");
    }

    public APIResponse<String> updateUser(Integer userId, UserUpdateRequest updateRequest) {
        Optional<User> userOpt = authRepo.getUserById(userId);
        if (userOpt.isEmpty()) {
            return APIResponse.error(NOT_FOUND, "User not found");
        }
        User currentUser = userOpt.get();

        // Hash password if present
        String passwordHash = updateRequest.password() != null
                ? BCrypt.withDefaults().hashToString(12, updateRequest.password().toCharArray())
                : currentUser.passwordHash();

        User updatedUser = User.builder()
                .id(currentUser.id())
                .user(currentUser.user())
                .passwordHash(passwordHash)
                .email(updateRequest.email() != null ? updateRequest.email() : currentUser.email())
                .firstName(updateRequest.firstName() != null ? updateRequest.firstName() : currentUser.firstName())
                .lastName(updateRequest.lastName() != null ? updateRequest.lastName() : currentUser.lastName())
                .birthdate(updateRequest.birthdate() != null ? updateRequest.birthdate() : currentUser.birthdate())
                .gender(updateRequest.gender() != null ? updateRequest.gender() : currentUser.gender())
                .phoneNumber(
                        updateRequest.phoneNumber() != null ? updateRequest.phoneNumber() : currentUser.phoneNumber())
                .profilePicture(updateRequest.profilePicture() != null ? updateRequest.profilePicture()
                        : currentUser.profilePicture())
                .bio(updateRequest.bio() != null ? updateRequest.bio() : currentUser.bio())
                .location(updateRequest.location() != null ? updateRequest.location() : currentUser.location())
                .website(updateRequest.website() != null ? updateRequest.website() : currentUser.website())
                .isActive(updateRequest.isActive() != null ? updateRequest.isActive() : currentUser.isActive())
                .lockoutUntil(updateRequest.lockoutUntil() != null ? updateRequest.lockoutUntil()
                        : currentUser.lockoutUntil())
                .createdAt(currentUser.createdAt())
                .updatedAt(System.currentTimeMillis())
                .build();

        Optional<Integer> result = authRepo.updateUser(updatedUser);
        return result.isEmpty()
                ? APIResponse.error(INTERNAL_SERVER_ERROR, "Error updating the user")
                : APIResponse.success(NO_CONTENT, "User updated sucessfully");
    }

    public APIResponse<LoginResponse> loginUser(String username, String password, String userAgent, String ipAddress) {
        Optional<User> user = authRepo.getUserByUsername(username.toLowerCase());
        if (user.isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        if (!BCrypt.verifyer()
                .verify(password.toCharArray(), user.get().passwordHash()).verified) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        // Get user roles for JWT token
        List<String> userRoles = authRepo.getUserRoles(user.get().id());
        
        Tyr.TokenPair tokenPair = Tyr.createTokenPair(
                user.get().id().toString(),
                userRoles,
                userAgent,
                ipAddress);

        authRepo.updateLastLogin(user.get().id());
        return APIResponse.success(OK, LoginResponse.fromTokenPair(tokenPair));
    }

    public APIResponse<LoginResponse> refreshToken(String refreshToken, String userAgent, String ipAddress) {
        Optional<Tyr.TokenPair> newTokens = Tyr.refreshToken(refreshToken, userAgent, ipAddress);
        if (newTokens.isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Invalid or expired refresh token");
        }

        return APIResponse.success(OK, LoginResponse.fromTokenPair(newTokens.get()));
    }

    public APIResponse<Void> logout(String refreshToken) {
        authRepo.deactivateSession(refreshToken);
        return APIResponse.success(OK, null);
    }

    public APIResponse<Void> logoutAll(String userId) {
        authRepo.deactivateAllUserSessions(Integer.parseInt(userId));
        return APIResponse.success(OK, null);
    }

    public APIResponse<List<User>> listUsers() {
        return APIResponse.success(OK,
                authRepo.getAllUsers().stream()
                        .map(User::defaultView)
                        .toList());
    }

    // Role-based Authorization Methods

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
        if (authRepo.hasRole(userId, "admin")) {
            return true;
        }

        return Arrays.stream(roleNames)
                .anyMatch(roleName -> authRepo.hasRole(userId, roleName));
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
        if (authRepo.hasRole(userId, "admin")) {
            return true;
        }

        return Arrays.stream(roleNames)
                .allMatch(roleName -> authRepo.hasRole(userId, roleName));
    }

    /**
     * Check if a user has a specific role.
     *
     * @param userId   the user ID
     * @param roleName the role name to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(Integer userId, String roleName) {
        return authRepo.hasRole(userId, roleName);
    }

    /**
     * Get all roles assigned to a user.
     *
     * @param userId the user ID
     * @return list of role names assigned to the user
     */
    public List<String> getUserRoles(Integer userId) {
        return authRepo.getUserRoles(userId);
    }

    /**
     * Assign a role to a user by role name.
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

        // Check if role exists
        Optional<Role> role = authRepo.getRoleByName(roleName.trim());
        if (role.isEmpty()) {
            return false;
        }

        // Check if user already has this role
        if (authRepo.hasRole(userId, roleName)) {
            return true; // Consider this success
        }

        // Assign the role
        return authRepo.assignRole(userId, role.get().id(), assignedBy);
    }

    /**
     * Remove a role from a user.
     *
     * @param userId   the user ID to remove the role from
     * @param roleName the role name to remove
     * @return true if role was removed successfully, false otherwise
     */
    public boolean removeRole(Integer userId, String roleName) {
        return authRepo.removeRole(userId, roleName);
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

    // Role Management Methods

    /**
     * Get all available roles in the system.
     *
     * @return list of all roles
     */
    public List<Role> getAllRoles() {
        return authRepo.getAllRoles();
    }

    /**
     * Get a role by its name.
     *
     * @param roleName the role name
     * @return Optional containing the role if found, empty otherwise
     */
    public Optional<Role> getRoleByName(String roleName) {
        return authRepo.getRoleByName(roleName);
    }

    /**
     * Get a role by its ID.
     *
     * @param roleId the role ID
     * @return Optional containing the role if found, empty otherwise
     */
    public Optional<Role> getRoleById(Integer roleId) {
        return authRepo.getRoleById(roleId);
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

        // Check if role already exists
        if (authRepo.getRoleByName(roleName).isPresent()) {
            return APIResponse.error("409 CONFLICT", "Role '" + roleName + "' already exists");
        }

        boolean success = authRepo.createRole(roleName.trim(), description);
        if (success) {
            return APIResponse.success("201 CREATED", "Role '" + roleName + "' created successfully", null);
        } else {
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to create role");
        }
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

        boolean success = authRepo.assignRole(userId, roleId, assignedBy);
        if (success) {
            return APIResponse.success("201 CREATED", "Role assigned successfully", null);
        } else {
            return APIResponse.error("409 CONFLICT", "User already has this role");
        }
    }

    /**
     * Get all user role assignments.
     *
     * @return list of all user role assignments
     */
    public List<UserRole> getAllUserRoles() {
        return authRepo.getAllUserRoles();
    }

    /**
     * Get the count of users for a specific role.
     *
     * @param roleId the role ID
     * @return count of users with this role
     */
    public int getUserCountForRole(Integer roleId) {
        return authRepo.getUserCountForRole(roleId);
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

        // Check if role exists
        Optional<Role> role = authRepo.getRoleById(roleId);
        if (role.isEmpty()) {
            return APIResponse.error("404 NOT FOUND", "Role not found");
        }

        // Check if role is assigned to any users
        int userCount = authRepo.getUserCountForRole(roleId);
        if (userCount > 0) {
            return APIResponse.error("409 CONFLICT",
                    "Cannot delete role '" + role.get().name() + "' because it is assigned to " + userCount + " user(s)");
        }

        boolean success = authRepo.deleteRole(roleId);
        if (success) {
            return APIResponse.success("200 OK", "Role deleted successfully", null);
        } else {
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to delete role");
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

        boolean success = authRepo.removeUserRole(userRoleId);
        if (success) {
            return APIResponse.success("200 OK", "Role assignment removed successfully", null);
        } else {
            return APIResponse.error("404 NOT FOUND", "Role assignment not found");
        }
    }

    // Schedule method
    public void cleanOldSessions() {
        authRepo.cleanOldSessions();
    }
}
