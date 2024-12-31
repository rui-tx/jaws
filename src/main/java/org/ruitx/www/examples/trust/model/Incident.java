package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Incident(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("created_at")
        String createdAt,  // You can handle this as a String or Date
        @JsonProperty("updated_at")
        String updatedAt,
        @JsonProperty("completed_at")
        String completedAt,
        @JsonProperty("completed")
        Boolean completed,
        @JsonProperty("incident_type_id")
        Integer incidentTypeId) {
}
