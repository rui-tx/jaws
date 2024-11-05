package org.ruitx.server.controllers;

import org.ruitx.server.components.Mimir;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.Row;

import java.io.IOException;
import java.util.List;

import static org.ruitx.server.strings.RequestType.*;
import static org.ruitx.server.strings.ResponseCode.*;

public class Todo {


    @Route(endpoint = "/todos", method = GET)
    public void getTodos(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();

        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append(generateTodoList(todos));

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todos", method = POST)
    public void createTodo(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null || !rh.getBodyParams().containsKey("todo")) {
            rh.sendHTMLResponse(BAD_REQUEST, "Missing todo parameter");
            return;
        }
        Mimir db = new Mimir();

        String todo = rh.getBodyParams().get("todo");

        int affectedRows = db.executeSql("INSERT INTO TODO (todo) VALUES (?)", todo);
        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error adding todo");
            return;
        }

        StringBuilder body = new StringBuilder();
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<div id=\"todo-list\">").append(generateTodoList(todos)).append("</div>");

        // Send the updated list as the response
        rh.sendHTMLResponse(CREATED, body.toString());
    }

    // TOD: Refactor this method
    @Route(endpoint = "/todos/:id", method = GET)
    public void getTodoById(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        String todoId = rh.getPathParams().get("id");

        StringBuilder body = new StringBuilder();
        List<Row> todos = db.getRows("SELECT * FROM TODO");

        for (Row r : todos) {
            if (r.get("id").toString().equals(todoId)) {
                body.append(r.get("todo"));
                break;
            }
        }

        if (body.toString().isEmpty()) {
            rh.sendHTMLResponse(NOT_FOUND, "Todo not found");
            return;
        }


        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todos/:id", method = DELETE)
    public void deleteTodo(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        String todoId = rh.getPathParams().get("id");

        int affectedRows = db.executeSql("DELETE FROM TODO WHERE id = ?", todoId);
        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error deleting todo");
            return;
        }

        StringBuilder body = new StringBuilder();
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<div id=\"todo-list\">").append(generateTodoList(todos)).append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todos/", method = DELETE)
    public void deleteAllTodos(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();

        boolean affectedRows = db.executeSql("DELETE FROM TODO");

        StringBuilder body = new StringBuilder();
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<div id=\"todo-list\">").append(generateTodoList(todos)).append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    private String generateTodoList(List<Row> todos) {
        StringBuilder ulBody = new StringBuilder();
        ulBody.append("<ul>");

        if (todos.isEmpty()) {
            ulBody.append("<li>No todos found</li>");
            ulBody.append("</ul>");
            return ulBody.toString();
        }

        for (Row r : todos) {
            ulBody.append(generateTodoItem(r));
        }

        ulBody.append("</ul>");
        return ulBody.toString();
    }

    private String generateTodoItem(Row todoRow) {
        String todoId = todoRow.get("id").toString();
        String todoText = todoRow.getString("todo");

        return "<li id=\"todo-" + todoId + "\">" +
                todoText +
                " <button hx-delete='/todos/" + todoId + "' " +
                "hx-target='#todo-list' hx-swap='outerHTML'>X</button>" +
                "</li>";
    }
}
