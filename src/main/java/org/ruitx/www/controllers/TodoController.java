package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.utils.types.Todo;
import org.ruitx.www.models.auth.LoginRequest;
import org.ruitx.www.models.auth.TokenResponse;
import org.ruitx.www.models.todo.CreateTodoRequest;
import org.ruitx.www.services.APIService;
import org.ruitx.www.services.TodoService;

import static org.ruitx.jaws.strings.ResponseCode.OK;

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

        sendSucessfulResponse(ResponseCode.valueOf(response.code()), response.data());
    }
}