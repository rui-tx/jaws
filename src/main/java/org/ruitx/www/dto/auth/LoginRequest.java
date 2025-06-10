package org.ruitx.www.dto.auth;

import java.util.Optional;

import org.ruitx.www.validation.Validatable;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull(message = "User is required")
        @NotBlank(message = "User cannot be empty")
        @Size(min = 3, message = "User must be at least 3 characters long")
        @Size(max = 16, message = "User cannot exceed 16 characters")
        @JsonProperty("user") String user,
        
        @NotNull(message = "Password is required")
        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Size(max = 16, message = "Password cannot exceed 16 characters")
        @JsonProperty("password") String password
        
) implements Validatable {

    @Override
    public Optional<String> isValid() {
        return Optional.empty();
    }
}
