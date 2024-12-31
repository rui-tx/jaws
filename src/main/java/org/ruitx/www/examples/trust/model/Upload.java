package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Upload(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("created_at")
        String createdAt,  // You can handle this as a String or Date
        @JsonProperty("updated_at")
        String updatedAt,
        @JsonProperty("url")
        String url,
        @JsonProperty("report_id")
        Integer reportId) {
}
