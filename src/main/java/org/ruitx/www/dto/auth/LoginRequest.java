package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        @JsonProperty("user") String user,
        @JsonProperty("password") String password
) {
}
