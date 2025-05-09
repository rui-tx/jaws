package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LogoutRequest(
        @JsonProperty("refreshToken")
        String refreshToken
) {
}


