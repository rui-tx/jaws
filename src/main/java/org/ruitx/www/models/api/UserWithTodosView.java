package org.ruitx.www.models.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ruitx.jaws.utils.types.Todo;
import org.ruitx.jaws.utils.types.User;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserWithTodosView(
    @JsonProperty("user_id") Integer userId,
    @JsonProperty("username") String username,
    @JsonProperty("created_at") Long createdAt,
    @JsonProperty("todos") List<Todo> todos
) {
    /**
     * Creates a UserWithTodosView from a User and their todos
     * @param user The user
     * @param todos The user's todos
     * @return A new UserWithTodosView
     */
    public static UserWithTodosView from(User user, List<Todo> todos) {
        return new UserWithTodosView(
            user.id(),
            user.user(),
            user.createdAt(),
            todos
        );
    }
} 