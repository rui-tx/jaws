package org.ruitx.jaws.utils.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.utils.Row;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record User(
        @JsonProperty("id") Integer id,
        @JsonProperty("user") String user,
        @JsonProperty("password_hash") String passwordHash,
        @JsonProperty("created_at") Long createdAt) {

    /**
     * Creates a new User instance with the builder pattern.
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a view of the user without sensitive information.
     * This is the default view used for most API responses.
     * @return a new User instance without the password hash
     */
    public User defaultView() {
        return User.builder()
            .id(id)
            .user(user)
            .createdAt(createdAt)
            .build();
    }

    /**
     * Creates a User instance from a database row.
     * @param row the database row containing user data
     * @return an Optional containing the User if all required fields are present, empty otherwise
     */
    public static Optional<User> fromRow(Row row) {
        Optional<Integer> id = row.getInt("id");
        Optional<String> usernameOpt = row.getString("user");
        Optional<String> passwordHash = row.getString("password_hash");
        Optional<Long> createdAt = row.getUnixTimestamp("created_at");

        if (id.isEmpty() || usernameOpt.isEmpty() || passwordHash.isEmpty() || createdAt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(User.builder()
            .id(id.get())
            .user(usernameOpt.get())
            .passwordHash(passwordHash.get())
            .createdAt(createdAt.get())
            .build());
    }

    public static final class Builder {
        private final Integer id;
        private final String user;
        private final String passwordHash;
        private final Long createdAt;

        private Builder() {
            this.id = null;
            this.user = null;
            this.passwordHash = null;
            this.createdAt = null;
        }

        private Builder(Integer id, String user, String passwordHash, Long createdAt) {
            this.id = id;
            this.user = user;
            this.passwordHash = passwordHash;
            this.createdAt = createdAt;
        }

        public Builder id(Integer id) {
            return new Builder(id, this.user, this.passwordHash, this.createdAt);
        }

        public Builder user(String user) {
            return new Builder(this.id, user, this.passwordHash, this.createdAt);
        }

        public Builder passwordHash(String passwordHash) {
            return new Builder(this.id, this.user, passwordHash, this.createdAt);
        }

        public Builder createdAt(Long createdAt) {
            return new Builder(this.id, this.user, this.passwordHash, createdAt);
        }

        /**
         * Builds a new User instance with validation.
         * @return a new User instance
         * @throws IllegalStateException if required fields are missing or invalid
         */
        public User build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (user == null || user.isBlank()) {
                throw new IllegalStateException("user is required and cannot be blank");
            }
            if (createdAt == null) {
                throw new IllegalStateException("createdAt is required");
            }
            if (createdAt < 0) {
                throw new IllegalStateException("createdAt must be a positive timestamp");
            }
            return new User(id, user, passwordHash, createdAt);
        }
    }
}
