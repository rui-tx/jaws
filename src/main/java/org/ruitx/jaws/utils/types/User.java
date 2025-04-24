package org.ruitx.jaws.utils.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public record User(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("user")
        String user,
        @JsonProperty("password_hash")
        String passwordHash,
        @JsonProperty("created_at")
        String createAt) {
}
