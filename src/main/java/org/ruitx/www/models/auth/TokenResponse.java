package org.ruitx.www.models.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.components.Tyr;

public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn
) {
    public static TokenResponse fromTokenPair(Tyr.TokenPair tokenPair) {
        return new TokenResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                15 * 60L // 15 minutes in seconds
        );
    }
}
