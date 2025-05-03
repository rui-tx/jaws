package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.types.Todo;
import org.ruitx.jaws.types.UserView;
import org.ruitx.www.models.todo.CreateTodoRequest;
import org.ruitx.www.services.TodoService;

import java.util.List;

public class TodoController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/todo/";
    private final TodoService todoService;

    public TodoController() {
        this.todoService = new TodoService();
    }

    @Route(endpoint = API_ENDPOINT, method = RequestType.POST)
    public void createTodo(CreateTodoRequest request) {
        String username = request.user();
        String content = request.content();

        APIResponse<Todo> response = todoService.createTodo(username, content);
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = API_ENDPOINT, method = RequestType.GET)
    public void getTodos() {
        APIResponse<List<Todo>> response = todoService.getUserTodos(getPathParam("id"));
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = API_ENDPOINT + "user/id/:id", method = RequestType.GET)
    public void getTodoByUserId() {
        APIResponse<List<Todo>> response = todoService.getUserTodos(getPathParam("id"));
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = API_ENDPOINT + "user/name/:name", method = RequestType.GET)
    public void getTodoByUsername() {
        APIResponse<UserView> response = todoService.getUserWithTodos(getPathParam("name"));
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }
}