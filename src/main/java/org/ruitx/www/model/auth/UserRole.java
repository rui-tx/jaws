package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.types.Row;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserRole(
        @JsonProperty("id") Integer id,
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("role_id") Integer roleId,
        @JsonProperty("assigned_at") Long assignedAt,
        @JsonProperty("assigned_by") Integer assignedBy
) {

    /**
     * Creates a new UserRole instance with the builder pattern.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a UserRole instance from a database row.
     *
     * @param row the database row containing user role data
     * @return an Optional containing the UserRole if all required fields are present, empty otherwise
     */
    public static Optional<UserRole> fromRow(Row row) {
        Optional<Integer> id = row.getInt("id");
        Optional<Integer> userId = row.getInt("user_id");
        Optional<Integer> roleId = row.getInt("role_id");
        Optional<Long> assignedAt = row.getUnixTimestamp("assigned_at");
        Optional<Integer> assignedBy = row.getInt("assigned_by");

        if (id.isEmpty() || userId.isEmpty() || roleId.isEmpty() || assignedAt.isEmpty()) {
            return Optional.empty(); // Essential fields must be present
        }

        return Optional.of(UserRole.builder()
                .id(id.get())
                .userId(userId.get())
                .roleId(roleId.get())
                .assignedAt(assignedAt.get())
                .assignedBy(assignedBy.orElse(null))
                .build());
    }

    public static final class Builder {
        private Integer id;
        private Integer userId;
        private Integer roleId;
        private Long assignedAt;
        private Integer assignedBy;

        private Builder() {
        }

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        public Builder roleId(Integer roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder assignedAt(Long assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }

        public Builder assignedBy(Integer assignedBy) {
            this.assignedBy = assignedBy;
            return this;
        }

        /**
         * Builds a new UserRole instance with validation for essential fields.
         *
         * @return a new UserRole instance
         * @throws IllegalStateException if required fields are missing or invalid
         */
        public UserRole build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (userId == null) {
                throw new IllegalStateException("userId is required");
            }
            if (roleId == null) {
                throw new IllegalStateException("roleId is required");
            }
            if (assignedAt == null) {
                throw new IllegalStateException("assignedAt is required");
            }
            if (assignedAt < 0) {
                throw new IllegalStateException("assignedAt must be a positive timestamp");
            }
            
            return new UserRole(
                    id,
                    userId,
                    roleId,
                    assignedAt,
                    assignedBy
            );
        }
    }
} 