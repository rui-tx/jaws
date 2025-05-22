package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserUpdateRequest(
    @JsonProperty("password") String password,
    @JsonProperty("email") String email,
    @JsonProperty("firstName") String firstName,
    @JsonProperty("lastName") String lastName,
    @JsonProperty("birthdate") Long birthdate,
    @JsonProperty("gender") String gender,
    @JsonProperty("phoneNumber") String phoneNumber,
    @JsonProperty("profilePicture") String profilePicture,
    @JsonProperty("bio") String bio,
    @JsonProperty("location") String location,
    @JsonProperty("website") String website,
    @JsonProperty("isActive") Integer isActive,
    @JsonProperty("isSuperuser") Integer isSuperuser,
    @JsonProperty("lockoutUntil") Long lockoutUntil
) {}