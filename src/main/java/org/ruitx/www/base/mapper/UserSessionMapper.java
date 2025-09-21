package org.ruitx.www.base.mapper;

import java.util.Optional;
import org.ruitx.jaws.components.mimir.Row;
import org.ruitx.jaws.components.mimir.RowMapper;
import org.ruitx.www.base.model.auth.UserSession;

/**
 * Authoritative Row -> UserSession mapper.
 */
public final class UserSessionMapper implements RowMapper<UserSession> {

  public static final UserSessionMapper INSTANCE = new UserSessionMapper();

  private UserSessionMapper() {
  }

  @Override
  public UserSession map(Row row) {
    if (row == null) {
      return null;
    }

    Optional<Integer> id = row.getInt("id");
    Optional<Integer> userId = row.getInt("user_id");
    Optional<String> refreshToken = row.getString("refresh_token");
    Optional<String> accessToken = row.getString("access_token");
    Optional<String> userAgent = row.getString("user_agent");
    Optional<String> ipAddress = row.getString("ip_address");
    Optional<Integer> isActive = row.getInt("is_active");
    Optional<Long> createdAt = row.getUnixTimestamp("created_at");
    Optional<Long> expiresAt = row.getUnixTimestamp("expires_at");
    Optional<Long> lastUsedAt = row.getUnixTimestamp("last_used_at");

    if (id.isEmpty() || userId.isEmpty() || refreshToken.isEmpty() || createdAt.isEmpty()) {
      return null; // Essential fields must be present
    }

    return new UserSession(
        id.get(),
        userId.get(),
        refreshToken.orElse(null),
        accessToken.orElse(null),
        userAgent.orElse(null),
        ipAddress.orElse(null),
        isActive.orElse(1),
        createdAt.get(),
        expiresAt.orElse(null),
        lastUsedAt.orElse(null)
    );
  }
}
