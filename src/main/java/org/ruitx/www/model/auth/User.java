package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.RowMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record User(
        @JsonProperty("id") Integer id,
        @JsonProperty("user") String user,
        @JsonProperty("password_hash") String passwordHash,
        @JsonProperty("email") String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("birthdate") Long birthdate,
        @JsonProperty("gender") String gender,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("profile_picture") String profilePicture,
        @JsonProperty("bio") String bio,
        @JsonProperty("location") String location,
        @JsonProperty("website") String website,
        @JsonProperty("last_login") Long lastLogin,
        @JsonProperty("is_active") Integer isActive,
        @JsonProperty("is_superuser") Integer isSuperuser,
        @JsonProperty("failed_login_attempts") Integer failedLoginAttempts,
        @JsonProperty("lockout_until") Long lockoutUntil,
        @JsonProperty("created_at") Long createdAt,
        @JsonProperty("updated_at") Long updatedAt) {

    /**
     * Creates a new User instance with the builder pattern.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a User instance from a database row.
     *
     * @param row the database row containing user data
     * @return an Optional containing the User if all required fields are present, empty otherwise
     */
    public static Optional<User> fromRow(Row row) {
        // Check required fields first
        if (!RowMapper.hasRequiredField(row, "id", row::getInt) ||
            !RowMapper.hasRequiredField(row, "user", row::getString) ||
            !RowMapper.hasRequiredField(row, "password_hash", row::getString) ||
            !RowMapper.hasRequiredField(row, "created_at", row::getUnixTimestamp)) {
            return Optional.empty();
        }

        return Optional.of(User.builder()
                .id(row.getInt("id").get())
                .user(row.getString("user").get())
                .passwordHash(row.getString("password_hash").get())
                .email(RowMapper.getOrNull(row, "email", row::getString))
                .firstName(RowMapper.getOrNull(row, "first_name", row::getString))
                .lastName(RowMapper.getOrNull(row, "last_name", row::getString))
                .birthdate(RowMapper.getOrNull(row, "birthdate", row::getUnixTimestamp))
                .gender(RowMapper.getOrNull(row, "gender", row::getString))
                .phoneNumber(RowMapper.getOrNull(row, "phone_number", row::getString))
                .profilePicture(RowMapper.getOrNull(row, "profile_picture", row::getString))
                .bio(RowMapper.getOrNull(row, "bio", row::getString))
                .location(RowMapper.getOrNull(row, "location", row::getString))
                .website(RowMapper.getOrNull(row, "website", row::getString))
                .lastLogin(RowMapper.getOrNull(row, "last_login", row::getUnixTimestamp))
                .isActive(RowMapper.getOrDefault(row, "is_active", row::getInt, 1))
                .isSuperuser(RowMapper.getOrDefault(row, "is_superuser", row::getInt, 0))
                .failedLoginAttempts(RowMapper.getOrDefault(row, "failed_login_attempts", row::getInt, 0))
                .lockoutUntil(RowMapper.getOrNull(row, "lockout_until", row::getUnixTimestamp))
                .createdAt(row.getUnixTimestamp("created_at").get())
                .updatedAt(RowMapper.getOrNull(row, "updated_at", row::getUnixTimestamp))
                .build());
    }

    /**
     * Returns a view of the user without sensitive information.
     * This is the default view used for most API responses.
     *
     * @return a new User instance without the password hash and potentially other sensitive fields
     */
    public User defaultView() {
        return User.builder()
                .id(id)
                .user(user)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .profilePicture(profilePicture) 
                .createdAt(createdAt)
                .build();
    }

    public static final class Builder {
        private Integer id;
        private String user;
        private String passwordHash;
        private String email;
        private String firstName;
        private String lastName;
        private Long birthdate;
        private String gender;
        private String phoneNumber;
        private String profilePicture;
        private String bio;
        private String location;
        private String website;
        private Long lastLogin;
        private Integer isActive;
        private Integer isSuperuser;
        private Integer failedLoginAttempts;
        private Long lockoutUntil;
        private Long createdAt;
        private Long updatedAt;

        private Builder() {
        }

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder birthdate(Long birthdate) {
            this.birthdate = birthdate;
            return this;
        }

        public Builder gender(String gender) {
            this.gender = gender;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder profilePicture(String profilePicture) {
            this.profilePicture = profilePicture;
            return this;
        }

        public Builder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder website(String website) {
            this.website = website;
            return this;
        }

        public Builder lastLogin(Long lastLogin) {
            this.lastLogin = lastLogin;
            return this;
        }

        public Builder isActive(Integer isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder isSuperuser(Integer isSuperuser) {
            this.isSuperuser = isSuperuser;
            return this;
        }

        public Builder failedLoginAttempts(Integer failedLoginAttempts) {
            this.failedLoginAttempts = failedLoginAttempts;
            return this;
        }

        public Builder lockoutUntil(Long lockoutUntil) {
            this.lockoutUntil = lockoutUntil;
            return this;
        }

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds a new User instance with validation for essential fields.
         *
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
            return new User(
                    id,
                    user,
                    passwordHash,
                    email,
                    firstName,
                    lastName,
                    birthdate,
                    gender,
                    phoneNumber,
                    profilePicture,
                    bio,
                    location,
                    website,
                    lastLogin,
                    isActive,
                    isSuperuser,
                    failedLoginAttempts,
                    lockoutUntil,
                    createdAt,
                    updatedAt
            );
        }
    }
}