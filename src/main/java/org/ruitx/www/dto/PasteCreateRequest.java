package org.ruitx.www.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasteCreateRequest(
        @JsonProperty("content") String content,
        @JsonProperty("title") String title,
        @JsonProperty("language") String language,
        @JsonProperty("expiresInHours") Integer expiresInHours,
        @JsonProperty("isPrivate") Boolean isPrivate,
        @JsonProperty("password") String password
) {
} 