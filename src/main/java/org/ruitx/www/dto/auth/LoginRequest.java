package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotNull
        @NotBlank
        @JsonProperty("user") String user,

        @NotNull(message = "Password is required")
        @NotBlank(message = "Password cannot be empty")
        @JsonProperty("password") String password
) {
}
