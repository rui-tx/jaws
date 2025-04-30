package org.ruitx.www.services;

import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.utils.types.Todo;
import org.ruitx.www.repositories.TodoRepo;

import java.util.List;
import java.util.Optional;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class TodoService {
    private final TodoRepo todoRepo;

    public TodoService() {
        this.todoRepo = new TodoRepo();
    }

    public APIResponse<Todo> createTodo(Integer userId, String content) {
        if (userId == null) {
            return APIResponse.error(BAD_REQUEST, "User ID is required");
        }
        if (content == null || content.isBlank()) {
            return APIResponse.error(BAD_REQUEST, "Todo content is required");
        }

        Optional<Todo> todo = todoRepo.createTodo(userId, content);
        return todo.map(t -> APIResponse.success(CREATED, t))
                .orElseGet(() -> APIResponse.error(INTERNAL_SERVER_ERROR, "Failed to create todo"));
    }

    public APIResponse<List<Todo>> getUserTodos(Integer userId) {
        if (userId == null) {
            return APIResponse.error(BAD_REQUEST, "User ID is required");
        }

        List<Todo> todos = todoRepo.getTodosByUserId(userId);
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