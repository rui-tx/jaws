package org.ruitx.server.controllers;

import org.ruitx.server.components.Mimir;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.Row;

import java.io.IOException;
import java.util.List;

import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.RequestType.POST;
import static org.ruitx.server.strings.ResponseCode.*;

public class Todo {


    @Route(endpoint = "/todos", method = GET)
    public void getTodos(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();

        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<ul>");
        for (Row r : todos) {
            body.append("<li>").append(r.getString("todo")).append("</li>");
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
        Mimir db = new Mimir();

        String todo = rh.getBodyParams().get("todo");

        int affectedRows = db.executeSql("INSERT INTO TODO (todo) VALUES (?)", todo);
        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error adding todo");
            return;
        }

        StringBuilder body = new StringBuilder();

        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<ul>");
        for (Row r : todos) {
            body.append("<li>").append(r.getString("todo")).append("</li>");
        }
        body.append("</ul>");

        rh.sendHTMLResponse(CREATED, body.toString());
    }

//    @Route(endpoint = "/todo/:id", method = GET)
//    public void getTodo(Yggdrasill.RequestHandler rh) throws IOException {
//
//        rh.sendHTMLResponse(NOT_IMPLEMENTED, "Not implemented");
//    }


}
