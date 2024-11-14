package org.ruitx.server.controllers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.server.components.Hermes;
import org.ruitx.server.components.Mimir;
import org.ruitx.server.components.Tyr;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.Row;

import java.io.IOException;
import java.util.List;

import static org.ruitx.server.strings.RequestType.*;
import static org.ruitx.server.strings.ResponseCode.*;

public class Todo {

    private static final String BASE_HTML_PATH = "todo/index.html";
    private static final String DEFAULT_BODY_HTML_PATH = "todo/partials/_body.html";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 5;

    private int page;
    private int pageSize;

    public Todo() {
        Hermes.setBodyPath(DEFAULT_BODY_HTML_PATH);
        page = DEFAULT_PAGE;
        pageSize = DEFAULT_PAGE_SIZE;
    }

    @Route(endpoint = "/todos", method = GET)
    public void getTodos(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();
        extractPaginationParams(rh);

        int offset = (page - 1) * pageSize;
        List<Row> todos = db.getRows("SELECT * FROM TODO ORDER BY id DESC LIMIT ? OFFSET ?", pageSize, offset);

        // Make pagination controls
        int totalTodos = db.getRow("SELECT COUNT(*) FROM TODO").getInt("COUNT(*)");
        int totalPages = (int) Math.ceil(totalTodos / (double) pageSize);

        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append(generateTodoListPagination(page, pageSize, totalPages))
                .append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todos", method = POST)
    public void createTodo(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null || !rh.getBodyParams().containsKey("todo")) {
            rh.sendHTMLResponse(BAD_REQUEST, "Missing todo parameter");
            return;
        }
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();
        String todo = rh.getBodyParams().get("todo");

        int affectedRows = db.executeSql("INSERT INTO TODO (todo) VALUES (?)", todo);
        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error adding todo");
            return;
        }

        extractPaginationParams(rh);
        int totalTodos = db.getRow("SELECT COUNT(*) FROM TODO").getInt("COUNT(*)");
        int totalPages = (int) Math.ceil((double) totalTodos / pageSize);
        if (page > totalPages) {
            page = totalPages;
        }
        int offset = (page - 1) * pageSize;
        List<Row> todos = db.getRows("SELECT * FROM TODO ORDER BY id DESC LIMIT ? OFFSET ?", pageSize, offset);

        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append(generateTodoListPagination(page, pageSize, totalPages))
                .append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
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
        StringBuilder body = new StringBuilder();

        int affectedRows = db.executeSql("DELETE FROM TODO WHERE id = ?", todoId);
        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error deleting todo");
            return;
        }

        extractPaginationParams(rh);
        int offset = (page - 1) * pageSize;
        List<Row> todos = db.getRows("SELECT * FROM TODO ORDER BY id DESC LIMIT ? OFFSET ?", pageSize, offset);

        // Make pagination controls
        int totalTodos = db.getRow("SELECT COUNT(*) FROM TODO").getInt("COUNT(*)");
        int totalPages = (int) Math.ceil(totalTodos / (double) pageSize);

        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append(generateTodoListPagination(page, pageSize, totalPages))
                .append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todos/", method = DELETE)
    public void deleteAllTodos(Yggdrasill.RequestHandler rh) throws IOException {
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();

        boolean affectedRows = db.executeSql("DELETE FROM TODO");
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append("</div>");

        rh.sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todo/login-page", method = GET)
    public void loginPage(Yggdrasill.RequestHandler rh) throws IOException {
        String partialPath = "todo/partials/login.html";
        if (rh.isHTMX()) {
            rh.sendHTMLResponse(OK, Hermes.makePartialPage(partialPath));
            return;
        }

        rh.sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, partialPath));
    }

    @Route(endpoint = "/todo/login", method = POST)
    public void loginUser(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("user")
                || !rh.getBodyParams().containsKey("password")) {
            rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        Mimir db = new Mimir();
        Row dbUser = db.getRow("SELECT * FROM USER WHERE user = ?",
                rh.getBodyParams().get("user"));

        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            // user does not exist
            rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div> ");
            return;
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(rh.getBodyParams().get("password").toCharArray(), storedPasswordHash)
                .verified) {
            // password does not match
            rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div>");
            return;
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        // send token to client via cookie or authorization header
        rh.addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        //rh.addCustomHeader("Authorization", "Bearer " + token);
        rh.addCustomHeader("HX-Location", "/todo/");

        // Return a success message in HTML format
        rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Login successful! Redirecting...</div>");
    }

    @Route(endpoint = "/todo/logout", method = GET)
    public void logoutUser(Yggdrasill.RequestHandler rh) throws IOException {
        rh.addCustomHeader("Set-Cookie", "token=; Max-Age=0; Path=/; HttpOnly; Secure");
        rh.addCustomHeader("HX-Location", "/todo/");
        rh.sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Logout successful! Redirecting...</div>");
    }

    @Route(endpoint = "/todo/create-account-page", method = GET)
    public void createAccountPage(Yggdrasill.RequestHandler rh) throws IOException {
        String partialPath = "todo/partials/create-account.html";
        if (rh.isHTMX()) {
            rh.sendHTMLResponse(OK, Hermes.makePartialPage(partialPath));
            return;
        }

        rh.sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, partialPath));
    }

    @Route(endpoint = "/todo/create-account", method = POST)
    public void createUser(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("user")
                || !rh.getBodyParams().containsKey("password")) {
            rh.sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        String user = rh.getBodyParams().get("user");
        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, rh.getBodyParams().get("password").toCharArray());
        Mimir db = new Mimir();
        int affectedRows = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                user, hashedPassword);

        if (affectedRows == 0) {
            rh.sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User already exists</div>");
            return;
        }

        String token = Tyr.createToken(user);
        if (token == null) {
            rh.sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        // send token to client via cookie or authorization header
        rh.addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        //rh.addCustomHeader("Authorization", "Bearer " + token);
        rh.addCustomHeader("HX-Location", "/todo/");

        // Return a success message in HTML format
        rh.sendHTMLResponse(OK, "<div id=\"register-message\" class=\"success\">User created successfully! You will be redirected...</div>");
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

    private void extractPaginationParams(Yggdrasill.RequestHandler rh) {
        if (rh.getQueryParams() != null) {
            if (rh.getQueryParams().containsKey("page")) {
                page = Integer.parseInt(rh.getQueryParams().get("page"));
            }
            if (rh.getQueryParams().containsKey("pageSize")) {
                pageSize = Integer.parseInt(rh.getQueryParams().get("pageSize"));
            }
        }
    }

    private StringBuilder generateTodoListPagination(int page, int pageSize, int totalPages) {
        StringBuilder body = new StringBuilder();
        // Generate pagination controls
        body.append("<div class='pagination'>");

        // Previous page button
        if (page > 1) {
            body.append("<a href='#' hx-get='/todos?page=")
                    .append(page - 1)
                    .append("&pageSize=")
                    .append(pageSize)
                    .append("' hx-target='#todo-list' class='prev'><</a>");
        } else {
            body.append("<span class='disabled'><</span>");
        }

        // Page info (e.g., "Page 2 of 5")
        body.append("<span class='page-info'>Page ").append(page).append(" of ").append(totalPages).append("</span>");

        // Next page button
        if (page < totalPages) {
            body.append("<a href='#' hx-get='/todos?page=")
                    .append(page + 1)
                    .append("&pageSize=")
                    .append(pageSize)
                    .append("' hx-target='#todo-list' class='next'>></a>");
        } else {
            body.append("<span class='disabled'>></span>");
        }

        body.append("</div>");

        return body;
    }
}
