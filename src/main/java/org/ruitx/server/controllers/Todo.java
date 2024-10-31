package org.ruitx.server.controllers;

import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.RequestType.POST;
import static org.ruitx.server.strings.ResponseCode.*;

public class Todo {

    private static final List<String> listOfTodos = Collections.synchronizedList(new ArrayList<>(List.of("Buy milk", "Buy eggs", "Buy bread")));

    @Route(endpoint = "/todos", method = GET)
    public void getTodos(Yggdrasill.RequestHandler rh) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("<ul>");
        for (String todo : listOfTodos) {
            body.append("<li>").append(todo).append("</li>");
        }
        body.append("</ul>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/new-todo", method = POST)
    public void createTodo(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null || !rh.getBodyParams().containsKey("todo")) {
            rh.sendHTMLResponse(BAD_REQUEST, "Missing todo parameter");
            return;
        }
        String todo = rh.getBodyParams().get("todo");

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

        rh.sendHTMLResponse(CREATED, body.toString());
    }
}
