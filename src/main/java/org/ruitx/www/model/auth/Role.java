package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.types.Row;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Role(
        @JsonProperty("id") Integer id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("created_at") Long createdAt,
        @JsonProperty("updated_at") Long updatedAt
) {

    /**
     * Creates a new Role instance with the builder pattern.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Role instance from a database row.
     *
     * @param row the database row containing role data
     * @return an Optional containing the Role if all required fields are present, empty otherwise
     */
    public static Optional<Role> fromRow(Row row) {
        Optional<Integer> id = row.getInt("id");
        Optional<String> name = row.getString("name");
        Optional<String> description = row.getString("description");
        Optional<Long> createdAt = row.getUnixTimestamp("created_at");
        Optional<Long> updatedAt = row.getUnixTimestamp("updated_at");

        if (id.isEmpty() || name.isEmpty() || createdAt.isEmpty()) {
            return Optional.empty(); // Essential fields must be present
        }

        return Optional.of(Role.builder()
                .id(id.get())
                .name(name.get())
                .description(description.orElse(null))
                .createdAt(createdAt.get())
                .updatedAt(updatedAt.orElse(null))
                .build());
    }

    public static final class Builder {
        private Integer id;
        private String name;
        private String description;
        private Long createdAt;
        private Long updatedAt;

        private Builder() {
        }

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
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
         * Builds a new Role instance with validation for essential fields.
         *
         * @return a new Role instance
         * @throws IllegalStateException if required fields are missing or invalid
         */
        public Role build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("name is required and cannot be blank");
            }
            if (createdAt == null) {
                throw new IllegalStateException("createdAt is required");
            }
            if (createdAt < 0) {
                throw new IllegalStateException("createdAt must be a positive timestamp");
            }
            
            return new Role(
                    id,
                    name,
                    description,
                    createdAt,
                    updatedAt
            );
        }
    }
} 