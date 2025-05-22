package org.ruitx.www.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Post(
        @JsonProperty("userId") Integer userId,
        @JsonProperty("id") Integer id,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body) {
}
