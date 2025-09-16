package org.ruitx.www.base.repository;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.interfaces.Cacheable;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.logger.JawsLogger;
import org.ruitx.www.base.mapper.UserMapper;
import org.ruitx.www.base.mapper.RoleMapper;
import org.ruitx.www.base.mapper.UserRoleMapper;
import org.ruitx.www.base.mapper.UserSessionMapper;
import org.ruitx.www.base.model.auth.Role;
import org.ruitx.www.base.model.auth.User;
import org.ruitx.www.base.model.auth.UserRole;
import org.ruitx.www.base.model.auth.UserSession;
import org.ruitx.jaws.components.mimir.Repository;
import org.ruitx.jaws.components.mimir.RowBinder;
import org.ruitx.www.base.projection.UserSummaryProjection;

public class AuthRepo extends Repository {

  public AuthRepo() {
    this(Odin.getDB());
  }

  public AuthRepo(Mimir db) {
    super(db);
  }

  public AuthRepo(String dbAlias) {
    this(Odin.getDB(dbAlias));
  }

  public Optional<Integer> createUser(String username,
      String hashedPassword,
      String firstName,
      String lastName) {
    int result = db.execute(
        "INSERT INTO USER (user, password_hash, first_name, last_name, created_at) VALUES (?, ?, ?, ?, ?)",
        username, hashedPassword, firstName, lastName, Date.from(Instant.now()));
    return result > 0 ? Optional.of(result) : Optional.empty();
  }

  public Optional<Integer> updateUser(User user) {
    int result = db.execute(
        """
            UPDATE USER SET
                    password_hash = ?,
                    email = ?,
                    first_name = ?,
                    last_name = ?,
                    birthdate = ?,
                    gender = ?,
                    phone_number = ?,
                    profile_picture = ?,
                    bio = ?,
                    location = ?,
                    website = ?,
                    is_active = ?,
                    lockout_until = ?,
                    updated_at = ?
            WHERE id = ?
            """,
        user.passwordHash(),
        user.email(),
        user.firstName(),
        user.lastName(),
        user.birthdate(),
        user.gender(),
        user.phoneNumber(),
        user.profilePicture(),
        user.bio(),
        user.location(),
        user.website(),
        user.isActive(),
        user.lockoutUntil(),
        Date.from(Instant.now()),
        user.id()
    );

    return result > 0 ? Optional.of(result) : Optional.empty();
  }

  @Cacheable(tables = {"USER_SESSION"})
  public Optional<UserSession> findActiveSessionByRefreshToken(String refreshToken) {
    return getOne(
        "SELECT * FROM USER_SESSION WHERE refresh_token = ? AND is_active = 1",
        UserSessionMapper.INSTANCE,
        refreshToken
    );
  }

  public void deactivateSession(String refreshToken) {
    db.execute(
        "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
        refreshToken
    );
  }

  public void deactivateAllUserSessions(Integer userId) {
    db.execute(
        "UPDATE USER_SESSION SET is_active = 0 WHERE user_id = ?",
        userId
    );
  }

  public void updateLastLogin(Integer userId) {
    db.execute(
        "UPDATE USER SET last_login = ? WHERE id = ?",
        Date.from(Instant.now()),
        userId
    );
  }

  @Cacheable(tables = {"USER"})
  public Optional<User> getUserByUsername(String username) {
    return getOne(
        "SELECT * FROM USER WHERE user = ?",
        UserMapper.INSTANCE,
        username
    );
  }

  @Cacheable(tables = {"USER"})
  public Optional<User> getUserById(Long id) {
    return getOne(
        "SELECT * FROM USER WHERE id = ?",
        UserMapper.INSTANCE,
        id
    );
  }

  public Optional<User> getUserById(Integer id) {
    return getUserById(id.longValue());
  }

  @Cacheable(tables = {"USER"})
  public List<User> getAllUsers() {
    return getAll(
        "SELECT * FROM USER ORDER BY created_at DESC",
        UserMapper.INSTANCE
    );
  }

  /**
   * Lightweight list of users using RowBinder and a projection record.
   * Only selects the necessary columns and aliases them to match projection fields.
   */
  @Cacheable(tables = {"USER"})
  public List<UserSummaryProjection> listUserSummaries() {
    String sql = """
        SELECT
          id AS id,
          user AS user,
          first_name AS firstName,
          last_name AS lastName
        FROM USER
        ORDER BY created_at DESC
        """;
    return getAll(sql, RowBinder.mapper(UserSummaryProjection.class));
  }

  // schedule method
  public void cleanOldSessions() {
    db.execute("DELETE FROM USER_SESSION WHERE expires_at < ?", Date.from(Instant.now()));
  }

  // Role-related database operations (moved from AuthorizationService)

  /**
   * Get all roles assigned to a user.
   *
   * @param userId the user ID
   * @return list of role names assigned to the user
   */
  @Cacheable(tables = {"USER_ROLE"})
  public List<String> getUserRoles(Integer userId) {
    if (userId == null) {
      return new ArrayList<>();
    }
    return getAll(
        """
            SELECT r.name 
            FROM USER_ROLE ur 
            JOIN ROLE r ON ur.role_id = r.id 
            WHERE ur.user_id = ?
            ORDER BY r.name
            """,
        r -> r.getString("name").orElse(""),
        userId
    ).stream().filter(name -> !name.isEmpty()).toList();
  }

  /**
   * Check if a user has a specific role.
   *
   * @param userId   the user ID
   * @param roleName the role name to check
   * @return true if user has the role, false otherwise
   */
  @Cacheable(tables = {"USER_ROLE"})
  public boolean hasRole(Integer userId, String roleName) {
    if (userId == null || roleName == null || roleName.trim().isEmpty()) {
      return false;
    }
    return getOne(
        """
            SELECT COUNT(*) as count 
            FROM USER_ROLE ur 
            JOIN ROLE r ON ur.role_id = r.id 
            WHERE ur.user_id = ? AND r.name = ?
            """,
        r -> r.getInt("count").orElse(0) > 0,
        userId, roleName.trim()
    ).orElse(false);
  }

  /**
   * Get all available roles in the system.
   *
   * @return list of all roles
   */
  @Cacheable(tables = {"ROLE"})
  public List<Role> getAllRoles() {
    return getAll(
        "SELECT * FROM ROLE ORDER BY name",
        RoleMapper.INSTANCE
    );
  }

  /**
   * Get a role by its name.
   *
   * @param roleName the role name
   * @return Optional containing the role if found, empty otherwise
   */
  @Cacheable(tables = {"ROLE"})
  public Optional<Role> getRoleByName(String roleName) {
    if (roleName == null || roleName.trim().isEmpty()) {
      return Optional.empty();
    }
    return getOne(
        "SELECT * FROM ROLE WHERE name = ?",
        RoleMapper.INSTANCE,
        roleName.trim()
    );
  }

  /**
   * Get a role by its ID.
   *
   * @param roleId the role ID
   * @return Optional containing the role if found, empty otherwise
   */
  @Cacheable(tables = {"ROLE"})
  public Optional<Role> getRoleById(Integer roleId) {
    if (roleId == null) {
      return Optional.empty();
    }
    return getOne(
        "SELECT * FROM ROLE WHERE id = ?",
        RoleMapper.INSTANCE,
        roleId
    );
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
        return false;
      }

      long now = Instant.now().getEpochSecond();
      int result = db.execute(
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
   * Assign a role to a user by role ID.
   *
   * @param userId     the user ID to assign the role to
   * @param roleId     the role ID to assign
   * @param assignedBy the user ID who is assigning the role
   * @return true if role was assigned successfully, false otherwise
   */
  public boolean assignRole(Integer userId, Integer roleId, Integer assignedBy) {
    if (userId == null || roleId == null) {
      return false;
    }

    try {
      // Check if user already has this role
      List<Row> existing = db.getRows(
          "SELECT id FROM USER_ROLE WHERE user_id = ? AND role_id = ?",
          userId, roleId
      );

      if (!existing.isEmpty()) {
        return false; // User already has this role
      }

      // Assign the role
      long now = Instant.now().getEpochSecond();
      int result = db.execute(
          "INSERT INTO USER_ROLE (user_id, role_id, assigned_at, assigned_by) VALUES (?, ?, ?, ?)",
          userId, roleId, now, assignedBy
      );

      if (result > 0) {
        JawsLogger.info("Role {} assigned to user {} by user {}", roleId, userId, assignedBy);
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      JawsLogger.error("Failed to assign role {} to user {}: {}", roleId, userId, e.getMessage());
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
      int result = db.execute(
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
        JawsLogger.debug("Role {} was not assigned to user {} (nothing to remove)", roleName,
            userId);
        return true; // Consider this success
      }
    } catch (Exception e) {
      JawsLogger.error("Failed to remove role {} from user {}: {}", roleName, userId,
          e.getMessage());
      return false;
    }
  }

  /**
   * Get all user role assignments.
   *
   * @return list of all user role assignments
   */
  @Cacheable(tables = {"USER_ROLE"}, ttl = 60000)
  public List<UserRole> getAllUserRoles() {
    return getAll(
        "SELECT * FROM USER_ROLE ORDER BY assigned_at DESC",
        UserRoleMapper.INSTANCE
    );
  }

  /**
   * Get the count of users for a specific role.
   *
   * @param roleId the role ID
   * @return count of users with this role
   */
  @Cacheable(tables = {"USER_ROLE"})
  public int getUserCountForRole(Integer roleId) {
    if (roleId == null) {
      return 0;
    }
    return getOne(
        "SELECT COUNT(*) as count FROM USER_ROLE WHERE role_id = ?",
        r -> r.getInt("count").orElse(0),
        roleId
    ).orElse(0);
  }

  /**
   * Delete a role from the system.
   *
   * @param roleId the role ID to delete
   * @return true if role was deleted successfully, false otherwise
   */
  public boolean deleteRole(Integer roleId) {
    if (roleId == null) {
      return false;
    }

    try {
      // Check if role exists
      Optional<Role> role = getRoleById(roleId);
      if (role.isEmpty()) {
        return false;
      }

      // Check if role is assigned to any users
      int userCount = getUserCountForRole(roleId);
      if (userCount > 0) {
        return false; // Cannot delete role that is assigned to users
      }

      // Delete the role
      int result = db.execute("DELETE FROM ROLE WHERE id = ?", roleId);

      if (result > 0) {
        JawsLogger.info("Role {} ({}) deleted successfully", roleId, role.get().name());
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      JawsLogger.error("Failed to delete role {}: {}", roleId, e.getMessage());
      return false;
    }
  }

  /**
   * Remove a user role assignment.
   *
   * @param userRoleId the user role assignment ID to remove
   * @return true if role assignment was removed successfully, false otherwise
   */
  public boolean removeUserRole(Integer userRoleId) {
    if (userRoleId == null) {
      return false;
    }

    try {
      int result = db.execute("DELETE FROM USER_ROLE WHERE id = ?", userRoleId);

      if (result > 0) {
        JawsLogger.info("User role assignment {} removed successfully", userRoleId);
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      JawsLogger.error("Failed to remove user role assignment {}: {}", userRoleId, e.getMessage());
      return false;
    }
  }
}