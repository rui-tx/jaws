package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenRequest(
        @JsonProperty("refresh_token") String refreshToken
) {
}

