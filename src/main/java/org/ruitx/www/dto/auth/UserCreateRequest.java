package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.ruitx.www.validation.Validatable;

import java.util.Optional;

public record UserCreateRequest(
        @JsonProperty("username") 
        @NotNull(message = "Username is required")
        @NotBlank(message = "Username cannot be empty")
        @Size(min = 3, message = "Username must be at least 3 characters long")
        @Size(max = 16, message = "Username cannot exceed 16 characters")
        String username,

        @JsonProperty("firstName") 
        @NotNull(message = "First name is required")
        @NotBlank(message = "First name cannot be empty")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @JsonProperty("lastName") 
        @NotNull(message = "Last name is required")
        @NotBlank(message = "Last name cannot be empty")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @JsonProperty("password") 
        @NotNull(message = "Password is required")
        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Size(max = 16, message = "Password cannot exceed 16 characters")
        String password,

        @JsonProperty("passwordConfirmation") 
        @NotNull(message = "Password confirmation is required")
        @NotBlank(message = "Password confirmation cannot be empty")
        @Size(min = 8, message = "Password confirmation must be at least 8 characters long")
        @Size(max = 16, message = "Password confirmation cannot exceed 16 characters")
        String passwordConfirmation
        
) implements Validatable {

    @Override
    public Optional<String> isValid() {
        if (!this.password().equals(this.passwordConfirmation()))
            return Optional.of("Passwords do not match");

        return Optional.empty();
    }
}
