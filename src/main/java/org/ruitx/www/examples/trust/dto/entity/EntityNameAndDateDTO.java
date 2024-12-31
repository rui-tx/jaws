package org.ruitx.www.examples.trust.dto.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntityNameAndDateDTO(
        @JsonProperty("name")
        String name,
        @JsonProperty("birth_date")
        String birthDate
) {
}
