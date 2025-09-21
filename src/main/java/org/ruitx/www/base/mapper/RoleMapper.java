package org.ruitx.www.base.mapper;

import java.util.Optional;
import org.ruitx.jaws.components.mimir.Row;
import org.ruitx.jaws.components.mimir.RowMapper;
import org.ruitx.www.base.model.auth.Role;

/**
 * Authoritative Row -> Role mapper.
 */
public final class RoleMapper implements RowMapper<Role> {

  public static final RoleMapper INSTANCE = new RoleMapper();

  private RoleMapper() {
  }

  @Override
  public Role map(Row row) {
    if (row == null) {
      return null;
    }

    Optional<Integer> id = row.getInt("id");
    Optional<String> name = row.getString("name");
    Optional<String> description = row.getString("description");
    Optional<Long> createdAt = row.getUnixTimestamp("created_at");
    Optional<Long> updatedAt = row.getUnixTimestamp("updated_at");

    if (id.isEmpty() || name.isEmpty() || createdAt.isEmpty()) {
      return null; // Essential fields must be present
    }

    return Role.builder()
        .id(id.get())
        .name(name.get())
        .description(description.orElse(null))
        .createdAt(createdAt.get())
        .updatedAt(updatedAt.orElse(null))
        .build();
  }
}
