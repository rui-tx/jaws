package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public record UserCreateRequest(
        @JsonProperty("username") String username,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("password") String password,
        @JsonProperty("passwordConfirmation") String passwordConfirmation
) {

    public static Optional<String> isValid(UserCreateRequest request) {
        if (request == null)
            return Optional.of("Request cannot be null");

        if (request.username() == null || request.username().isEmpty())
            return Optional.of("Username cannot be null or empty");

        if (request.password() == null || request.password().isEmpty())
            return Optional.of("Password cannot be null or empty");

        if (request.passwordConfirmation() == null || request.passwordConfirmation().isEmpty())
            return Optional.of("Password confirmation cannot be null or empty");

        if (!request.password().equals(request.passwordConfirmation()))
            return Optional.of("Passwords do not match");

        if (request.firstName() == null || request.firstName().isEmpty())
            return Optional.of("First name cannot be null or empty");

        if (request.lastName() == null || request.lastName().isEmpty())
            return Optional.of("Last name cannot be null or empty");

        if (request.username().length() < 3)
            return Optional.of("Username must be at least 3 characters long");

        if (request.password().length() < 8)
            return Optional.of("Password must be at least 8 characters long");

        return Optional.empty();
    }
}
