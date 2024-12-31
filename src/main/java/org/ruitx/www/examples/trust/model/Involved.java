package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Involved(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("entity_id")
        Integer entityId,
        @JsonProperty("incident_id")
        Integer incidentId) {
}
