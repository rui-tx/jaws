package org.ruitx.www.dto.auth;

import java.util.Optional;

import org.ruitx.jaws.interfaces.Validatable;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshTokenRequest(
        @JsonProperty("refresh_token") 
        @NotNull(message = "Refresh token is required")
        @NotBlank(message = "Refresh token cannot be empty")
        String refreshToken
        
) implements Validatable {

    @Override
    public Optional<String> isValid() {
        return Optional.empty();
    }
}

