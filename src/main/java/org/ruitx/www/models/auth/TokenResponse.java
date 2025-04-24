package org.ruitx.www.models.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
    @JsonProperty("token") String token
) {} 