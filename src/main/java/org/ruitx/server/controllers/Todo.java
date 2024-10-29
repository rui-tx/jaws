package org.ruitx.server.controllers;

import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.strings.ResponseCode;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Todo {

    private static final List<String> listOfTodos = Collections.synchronizedList(new ArrayList<>(List.of("Buy milk", "Buy eggs", "Buy bread")));

    @Route(endpoint = "/todos", method = "GET")
    public void getTodos(Yggdrasill.RequestHandler requestHandler) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("<ul>");
        for (String todo : listOfTodos) {
            body.append("<li>").append(todo).append("</li>");
        }
        body.append("</ul>");

        requestHandler.sendHTMLResponse(ResponseCode.OK, body.toString());
    }

    @Route(endpoint = "/new-todo", method = "GET")
    public void createTodo(Yggdrasill.RequestHandler requestHandler) throws IOException {
        Map<String, String> queryParams = requestHandler.getQueryParams();
        String todo = queryParams.get("todo");
        Logger.info("Adding todo: " + todo);

        synchronized (listOfTodos) {
            listOfTodos.add(todo);
        }

        StringBuilder body = new StringBuilder();
        body.append("<ul>");
        for (String item : listOfTodos) {
            body.append("<li>").append(item).append("</li>");
        }
        body.append("</ul>");

        requestHandler.sendHTMLResponse(ResponseCode.CREATED, body.toString());
    }
}
