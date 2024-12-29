package org.ruitx.www.examples.gallery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Image(
        @JsonProperty("id")
        String id,
        @JsonProperty("url")
        String url,
        @JsonProperty("description")
        String description) {
}
