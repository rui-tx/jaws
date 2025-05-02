package org.ruitx.jaws.utils.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserView(
    @JsonProperty("id") Integer id,
    @JsonProperty("user") String user,
    @JsonProperty("created_at") Long createdAt,
    @JsonProperty("todos") List<Todo> todos
) {
    /**
     * Creates a UserWithTodosView from a User and their todos
     * @param user The user
     * @param todos The user's todos
     * @return A new UserWithTodosView
     */
    public static UserView withTodos(User user, List<Todo> todos) {
        return new UserView(
            user.id(),
            user.user(),
            user.createdAt(),
            todos
        );
    }
} 