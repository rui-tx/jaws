package org.ruitx.www.services;

import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.utils.types.Todo;
import org.ruitx.jaws.utils.types.User;
import org.ruitx.jaws.utils.types.UserView;
import org.ruitx.www.repositories.AuthRepo;
import org.ruitx.www.repositories.TodoRepo;

import java.util.List;
import java.util.Optional;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class TodoService {
    private final TodoRepo todoRepo;
    private final AuthRepo authRepo;

    public TodoService() {
        this.todoRepo = new TodoRepo();
        this.authRepo = new AuthRepo();
    }

    /**
     * Gets a user and their todos combined in a single view
     * @param username The username to look up
     * @return APIResponse containing the combined view if successful
     */
    public APIResponse<UserView> getUserWithTodos(String username) {
        Optional<User> userOpt = authRepo.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            return APIResponse.error(NOT_FOUND, "User not found");
        }

        User user = userOpt.get();
        List<Todo> todos = todoRepo.getTodosByUserId(user.id());
        
        return APIResponse.success(OK, UserView.withTodos(user, todos));
    }

    public APIResponse<Todo> createTodo(String username, String content) {
        if (username == null) {
            return APIResponse.error(BAD_REQUEST, "User ID is required");
        }
        if (content == null || content.isBlank()) {
            return APIResponse.error(BAD_REQUEST, "Todo content is required");
        }

        Optional<User> userOpt = authRepo.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            return APIResponse.error(BAD_REQUEST, "User not found");
        }

        Optional<Todo> todo = todoRepo.createTodo(userOpt.get().id(), content);
        return todo.map(t -> APIResponse.success(CREATED, t))
                .orElseGet(() -> APIResponse.error(INTERNAL_SERVER_ERROR, "Failed to create todo"));
    }

    public APIResponse<List<Todo>> getUserTodos(String userId) {
        if (userId == null) {
            return APIResponse.error(BAD_REQUEST, "User ID is required");
        }

        int userIdInt;
        try {
            userIdInt = Integer.parseInt(userId);
        } catch (NumberFormatException e) {
            throw new APIParsingException("Invalid User ID format.", e);
        }

        List<Todo> todos = todoRepo.getTodosByUserId(userIdInt);
        return APIResponse.success(OK, todos);
    }

    public APIResponse<Todo> updateTodo(Integer todoId, String content) {
        if (todoId == null) {
            return APIResponse.error(BAD_REQUEST, "Todo ID is required");
        }
        if (content == null || content.isBlank()) {
            return APIResponse.error(BAD_REQUEST, "Todo content is required");
        }

        Optional<Todo> existingTodo = todoRepo.getTodoById(todoId);
        if (existingTodo.isEmpty()) {
            return APIResponse.error(NOT_FOUND, "Todo not found");
        }

        boolean updated = todoRepo.updateTodo(todoId, content);
        if (!updated) {
            return APIResponse.error(INTERNAL_SERVER_ERROR, "Failed to update todo");
        }

        return todoRepo.getTodoById(todoId)
                .map(todo -> APIResponse.success(OK, todo))
                .orElseGet(() -> APIResponse.error(INTERNAL_SERVER_ERROR, "Failed to fetch updated todo"));
    }

    public APIResponse<Void> deleteTodo(Integer todoId) {
        if (todoId == null) {
            return APIResponse.error(BAD_REQUEST, "Todo ID is required");
        }

        Optional<Todo> existingTodo = todoRepo.getTodoById(todoId);
        if (existingTodo.isEmpty()) {
            return APIResponse.error(NOT_FOUND, "Todo not found");
        }

        boolean deleted = todoRepo.deleteTodo(todoId);
        return deleted ? 
            APIResponse.success(NO_CONTENT, null) : 
            APIResponse.error(INTERNAL_SERVER_ERROR, "Failed to delete todo");
    }
} 