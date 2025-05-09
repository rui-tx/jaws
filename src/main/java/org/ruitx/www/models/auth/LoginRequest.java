package org.ruitx.www.models.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        @JsonProperty("user") String user,
        @JsonProperty("password") String password
) {
}
