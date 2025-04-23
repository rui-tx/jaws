package org.ruitx.www.models.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
    @JsonProperty("token") String token
) {} 