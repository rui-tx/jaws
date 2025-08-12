package org.ruitx.www.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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