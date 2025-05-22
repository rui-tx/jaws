package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.types.Row;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSession(
        @JsonProperty("id") Integer id,
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("user_agent") String userAgent,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("is_active") Integer isActive,
        @JsonProperty("created_at") Long createdAt,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("last_used_at") Long lastUsedAt
) {
    public static Optional<UserSession> fromRow(Row row) {
        if (row == null) return Optional.empty();

        return Optional.of(new UserSession(
                row.getInt("id").orElse(null),
                row.getInt("user_id").orElse(null),
                row.getString("refresh_token").orElse(null),
                row.getString("access_token").orElse(null),
                row.getString("user_agent").orElse(null),
                row.getString("ip_address").orElse(null),
                row.getInt("is_active").orElse(1),
                row.getUnixTimestamp("created_at").orElse(null),
                row.getUnixTimestamp("expires_at").orElse(null),
                row.getUnixTimestamp("last_used_at").orElse(null)
        ));
    }
}