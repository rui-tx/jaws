package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserCreateRequest(
        @JsonProperty("username") String username,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("password") String password,
        @JsonProperty("passwordConfirmation") String passwordConfirmation
) {
}
