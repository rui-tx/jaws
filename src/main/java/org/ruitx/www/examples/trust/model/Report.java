package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Report(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("created_at")
        String createdAt,  // You can handle this as a String or Date
        @JsonProperty("updated_at")
        String updatedAt,
        @JsonProperty("description")
        String description,
        @JsonProperty("report_type_id")
        Integer reportTypeId,
        @JsonProperty("incident_id")
        Integer incidentId) {
}
