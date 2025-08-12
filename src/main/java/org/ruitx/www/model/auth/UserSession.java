package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

}