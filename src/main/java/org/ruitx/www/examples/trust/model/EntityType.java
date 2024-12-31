package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntityType(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("type")
        String type) {
}