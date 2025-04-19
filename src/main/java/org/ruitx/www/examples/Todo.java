package org.ruitx.www.examples;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.components.Hermes;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.Row;

import java.io.IOException;
import java.util.List;

import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;

public class Todo extends BaseController {

    private static final String BASE_HTML_PATH = "examples/todo/index.html";
    private static final String BODY_HTML_PATH = "examples/todo/partials/_body.html";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 5;

    private int page;
    private int pageSize;

    public Todo() {
        Hermes.setBodyPath(BODY_HTML_PATH);
        page = DEFAULT_PAGE;
        pageSize = DEFAULT_PAGE_SIZE;
    }

    @Route(endpoint = "/todo", method = GET)
    public void renderIndex() throws IOException {
        sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, BODY_HTML_PATH));
    }

    @Route(endpoint = "/todos", method = GET)
    public void getTodos() throws IOException {
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();

        if (Yggdrasill.RequestHandler.getCurrentToken() == null ||
                !Tyr.isTokenValid(Yggdrasill.RequestHandler.getCurrentToken())) {
            body.append("<div id=\"todo-list\">")
                    .append("<li>No todos found</li>")
                    .append("</div>");
            sendHTMLResponse(OK, body.toString());
            return;
        }

        extractPaginationParams();

        int offset = (page - 1) * pageSize;
        List<Row> todos = db.getRows("SELECT * FROM TODO ORDER BY id DESC LIMIT ? OFFSET ?", pageSize, offset);

        // Make pagination controls
        int totalTodos = db.getRow("SELECT COUNT(*) FROM TODO").getInt("COUNT(*)");
        int totalPages = (int) Math.ceil(totalTodos / (double) pageSize);

        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append(generateTodoListPagination(page, pageSize, totalPages))
                .append("</div>");

        sendHTMLResponse(OK, body.toString());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/todos", method = POST)
    public void createTodo() throws IOException {
        if (getBodyParam("todo") == null) {
            sendHTMLResponse(BAD_REQUEST, "Missing todo parameter");
            return;
        }
        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();
        String todo = getBodyParam("todo");

        String tokenUserId = Tyr.getUserIdFromJWT(Yggdrasill.RequestHandler.getCurrentToken());
        int dbUserId = db.getRow("SELECT id FROM USER WHERE user = ?", tokenUserId).getInt("id");

        int affectedRows = db.executeSql("INSERT INTO TODO (todo, user_id) VALUES (?, ?)", todo, dbUserId);
        if (affectedRows == 0) {
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error adding todo");
            return;
        }

        extractPaginationParams();
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

        sendHTMLResponse(OK, body.toString());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/todos/:id", method = GET)
    public void getTodoById() throws IOException {
        Mimir db = new Mimir();
        String todoId = getPathParam("id");

        StringBuilder body = new StringBuilder();
        List<Row> todos = db.getRows("SELECT * FROM TODO");

        for (Row r : todos) {
            if (r.get("id").toString().equals(todoId)) {
                body.append(r.get("todo"));
                break;
            }
        }

        if (body.toString().isEmpty()) {
            sendHTMLResponse(NOT_FOUND, "Todo not found");
            return;
        }

        sendHTMLResponse(OK, body.toString());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/todos/:id", method = DELETE)
    public void deleteTodo() throws IOException {
        Mimir db = new Mimir();
        String todoId = getPathParam("id");
        StringBuilder body = new StringBuilder();

        String tokenUserId = Tyr.getUserIdFromJWT(Yggdrasill.RequestHandler.getCurrentToken());
        int dbUserId = db.getRow("SELECT id FROM USER WHERE user = ?", tokenUserId).getInt("id");

        int affectedRows = db.executeSql("DELETE FROM TODO WHERE id = ? AND user_id = ?", todoId, dbUserId);
        if (affectedRows == 0) {
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<p> You are not authorized to perform this action!</p>");
            return;
        }

        extractPaginationParams();
        int offset = (page - 1) * pageSize;
        List<Row> todos = db.getRows("SELECT * FROM TODO ORDER BY id DESC LIMIT ? OFFSET ?", pageSize, offset);

        int totalTodos = db.getRow("SELECT COUNT(*) FROM TODO").getInt("COUNT(*)");
        int totalPages = (int) Math.ceil(totalTodos / (double) pageSize);

        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append(generateTodoListPagination(page, pageSize, totalPages))
                .append("</div>");

        sendHTMLResponse(OK, body.toString());
    }

    @AccessControl(login = true, role = "ADMIN")
    @Route(endpoint = "/todos/", method = DELETE)
    public void deleteAllTodos() throws IOException {
        if (Yggdrasill.RequestHandler.getCurrentToken() == null ||
                !Tyr.isTokenValid(Yggdrasill.RequestHandler.getCurrentToken()) ||
                !Tyr.getUserRoleFromJWT(Yggdrasill.RequestHandler.getCurrentToken()).equals("ADMIN")) {
            sendHTMLResponse(OK, "<p> You are not authorized to perform this action!</p>");
            return;
        }

        Mimir db = new Mimir();
        StringBuilder body = new StringBuilder();

        boolean affectedRows = db.executeSql("DELETE FROM TODO");
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        body.append("<div id=\"todo-list\">")
                .append(generateTodoList(todos))
                .append("</div>");

        sendHTMLResponse(OK, body.toString());
    }

    @Route(endpoint = "/todo/login-page", method = GET)
    public void loginPage() throws IOException {
        String partialPath = "examples/todo/partials/login.html";
        if (isHTMX()) {
            sendHTMLResponse(OK, Hermes.makePartialPage(partialPath));
            return;
        }

        sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, partialPath));
    }

    @Route(endpoint = "/todo/login", method = POST)
    public void loginUser() throws IOException {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        Mimir db = new Mimir();
        Row dbUser = db.getRow("SELECT * FROM USER WHERE user = ?",
                getBodyParam("user"));

        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div> ");
            return;
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(getBodyParam("password").toCharArray(), storedPasswordHash)
                .verified) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div>");
            return;
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/todo/");
        sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Login successful! Redirecting...</div>");
    }

    @Route(endpoint = "/todo/logout", method = GET)
    public void logoutUser() throws IOException {
        addCustomHeader("Set-Cookie", "token=; Max-Age=0; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/todo/");
        sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Logout successful! Redirecting...</div>");
    }

    @Route(endpoint = "/todo/create-account-page", method = GET)
    public void createAccountPage() throws IOException {
        String partialPath = "examples/todo/partials/create-account.html";
        if (isHTMX()) {
            sendHTMLResponse(OK, Hermes.makePartialPage(partialPath));
            return;
        }

        sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, partialPath));
    }

    @Route(endpoint = "/todo/create-account", method = POST)
    public void createUser() throws IOException {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        String user = getBodyParam("user");
        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, getBodyParam("password").toCharArray());
        Mimir db = new Mimir();
        int affectedRows = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                user, hashedPassword);

        if (affectedRows == 0) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User already exists</div>");
            return;
        }

        String token = Tyr.createToken(user);
        if (token == null) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/todo/");
        sendHTMLResponse(OK, "<div id=\"register-message\" class=\"success\">User created successfully! You will be redirected...</div>");
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

        Mimir db = new Mimir();
        String tokenUserId = Tyr.getUserIdFromJWT(Yggdrasill.RequestHandler.getCurrentToken());

        Row userRow = db.getRow("SELECT id FROM USER WHERE user = ?", tokenUserId);
        if (userRow == null) {
            return "";
        }

        int dbUserId = userRow.getInt("id");
        if (todoRow.getInt("user_id") == null) {
            return "";
        }

        if (todoRow.getInt("user_id") == dbUserId) {
            return "<li id=\"todo-" + todoId + "\">" +
                    todoText +
                    " <button hx-delete='/todos/" + todoId + "' " +
                    "hx-target='#todo-list' hx-swap='outerHTML'>X</button>" +
                    "</li>";
        }

        return "";
    }

    private void extractPaginationParams() {
        if (getQueryParam("page") != null) {
            page = Integer.parseInt(getQueryParam("page"));
        }
        if (getQueryParam("pageSize") != null) {
            pageSize = Integer.parseInt(getQueryParam("pageSize"));
        }
    }

    private StringBuilder generateTodoListPagination(int page, int pageSize, int totalPages) {
        StringBuilder body = new StringBuilder();
        body.append("<div class='pagination'>");

        if (page > 1) {
            body.append("<a href='#' hx-get='/todos?page=")
                    .append(page - 1)
                    .append("&pageSize=")
                    .append(pageSize)
                    .append("' hx-target='#todo-list' class='prev'><</a>");
        } else {
            body.append("<span class='disabled'><</span>");
        }

        body.append("<span class='page-info'>Page ").append(page).append(" of ").append(totalPages).append("</span>");

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
