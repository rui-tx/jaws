package org.ruitx.www.base.mapper;

import java.util.Optional;
import org.ruitx.jaws.components.mimir.Row;
import org.ruitx.jaws.components.mimir.RowMapper;
import org.ruitx.www.base.model.auth.UserRole;

/**
 * Authoritative Row -> UserRole mapper.
 */
public final class UserRoleMapper implements RowMapper<UserRole> {

  public static final UserRoleMapper INSTANCE = new UserRoleMapper();

  private UserRoleMapper() {
  }

  @Override
  public UserRole map(Row row) {
    if (row == null) {
      return null;
    }

    Optional<Integer> id = row.getInt("id");
    Optional<Integer> userId = row.getInt("user_id");
    Optional<Integer> roleId = row.getInt("role_id");
    Optional<Long> assignedAt = row.getUnixTimestamp("assigned_at");
    Optional<Integer> assignedBy = row.getInt("assigned_by");

    if (id.isEmpty() || userId.isEmpty() || roleId.isEmpty() || assignedAt.isEmpty()) {
      return null; // Essential fields must be present
    }

    return UserRole.builder()
        .id(id.get())
        .userId(userId.get())
        .roleId(roleId.get())
        .assignedAt(assignedAt.get())
        .assignedBy(assignedBy.orElse(null))
        .build();
  }
}
