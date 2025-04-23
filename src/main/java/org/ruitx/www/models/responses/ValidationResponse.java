package org.ruitx.www.models.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValidationResponse(
    @JsonProperty("valid") boolean valid
) {} 