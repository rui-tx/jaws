package org.ruitx.jaws.utils.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.utils.Row;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Todo(
        @JsonProperty("id") Integer id,
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("content") String content,
        @JsonProperty("created_at") Long createdAt) {

    /**
     * Creates a new Todo instance with the builder pattern.
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Todo instance from a database row.
     * @param row the database row containing todo data
     * @return an Optional containing the Todo if all required fields are present, empty otherwise
     */
    public static Optional<Todo> fromRow(Row row) {
        Optional<Integer> id = row.getInt("id");
        Optional<Integer> userId = row.getInt("user_id");
        Optional<String> content = row.getString("content");
        Optional<Long> createdAt = row.getUnixTimestamp("created_at");

        if (id.isEmpty() || userId.isEmpty() || content.isEmpty() || createdAt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Todo.builder()
            .id(id.get())
            .userId(userId.get())
            .content(content.get())
            .createdAt(createdAt.get())
            .build());
    }

    public static final class Builder {
        private final Integer id;
        private final Integer userId;
        private final String content;
        private final Long createdAt;

        private Builder() {
            this.id = null;
            this.userId = null;
            this.content = null;
            this.createdAt = null;
        }

        private Builder(Integer id, Integer userId, String content, Long createdAt) {
            this.id = id;
            this.userId = userId;
            this.content = content;
            this.createdAt = createdAt;
        }

        public Builder id(Integer id) {
            return new Builder(id, this.userId, this.content, this.createdAt);
        }

        public Builder userId(Integer userId) {
            return new Builder(this.id, userId, this.content, this.createdAt);
        }

        public Builder content(String content) {
            return new Builder(this.id, this.userId, content, this.createdAt);
        }

        public Builder createdAt(Long createdAt) {
            return new Builder(this.id, this.userId, this.content, createdAt);
        }

        /**
         * Builds a new Todo instance with validation.
         * @return a new Todo instance
         * @throws IllegalStateException if required fields are missing or invalid
         */
        public Todo build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (userId == null) {
                throw new IllegalStateException("userId is required");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("content is required and cannot be blank");
            }
            if (createdAt == null) {
                throw new IllegalStateException("createdAt is required");
            }
            if (createdAt < 0) {
                throw new IllegalStateException("createdAt must be a positive timestamp");
            }
            return new Todo(id, userId, content, createdAt);
        }
    }
} 