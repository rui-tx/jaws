package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Entity(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("name")
        String name,
        @JsonProperty("birth_date")
        String birthDate, // Use String or Date depending on how you handle dates
        @JsonProperty("type_id")
        Integer typeId,
        @JsonProperty("insurance_id")
        Integer insuranceId) {
}
