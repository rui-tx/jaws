package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LogoutRequest(
        @JsonProperty("refreshToken")
        String refreshToken
) {
}


