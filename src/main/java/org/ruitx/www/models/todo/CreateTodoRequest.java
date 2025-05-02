package org.ruitx.www.models.todo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTodoRequest(
        @JsonProperty("user") String user,
        @JsonProperty("content") String content
) {}
