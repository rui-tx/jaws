package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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