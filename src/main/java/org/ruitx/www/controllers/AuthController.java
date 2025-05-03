package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.types.User;
import org.ruitx.www.models.auth.CreateUserRequest;
import org.ruitx.www.models.auth.LoginRequest;
import org.ruitx.www.models.auth.TokenResponse;
import org.ruitx.www.services.AuthService;

import java.util.List;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.CREATED;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.HTML;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class AuthController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/auth/";
    private static final String HTML_ENDPOINT = "/api/v1/auth/html/";
    private final AuthService authService;

    public AuthController() {
        this.authService = new AuthService();
    }

    @Route(endpoint = API_ENDPOINT + "login", method = POST, responseType = JSON)
    public void loginUser(LoginRequest request) {
        String username = request.user();
        String password = request.password();

        APIResponse<TokenResponse> response = authService.loginUser(username, password);
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "create", method = POST, responseType = JSON)
    public void createUser(CreateUserRequest request) {
        String username = request.user();
        String password = request.password();

        APIResponse<TokenResponse> response = authService.createUser(username, password);
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(CREATED, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "list", method = GET, responseType = JSON)
    public void listUsers() {
        APIResponse<List<User>> response = authService.listUsers();
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, response.data());
    }

    @AccessControl(login = true)
    @Route(endpoint = HTML_ENDPOINT + "list", responseType = HTML)
    public void listUsersHTMX() {
        APIResponse<List<User>> response = authService.listUsers();
        if (!response.success()) {
            sendHTMLResponse(response.code(), "Error loading users");
            return;
        }

        StringBuilder html = new StringBuilder();
        for (User user : response.data()) {
            html.append("<tr>")
                    .append("<td class=\"is-image-cell\">")
                    .append("<div class=\"image\">")
                    .append("<img class=\"is-rounded\" src=\"https://avatars.dicebear.com/v2/initials/")
                    .append(user.user().toLowerCase().replace(" ", "-"))
                    .append(".svg\">")
                    .append("</div>")
                    .append("</td>")
                    .append("<td data-label=\"Name\">").append(user.user()).append("</td>")
                    .append("<td data-label=\"Created\">")
                    .append("<small class=\"has-text-grey is-abbr-like\" title=\"")
                    .append(user.createdAt() != null ? user.createdAt() : "N/A")
                    .append("\">").append(user.createdAt() != null ? user.createdAt() : "N/A").append("</small>")
                    .append("</td>")
                    .append("<td class=\"is-actions-cell\">")
                    .append("<div class=\"buttons is-right\">")
                    .append("<button class=\"button is-small is-primary\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-eye\"></i></span>")
                    .append("</button>")
                    .append("<button class=\"button is-small is-danger jb-modal\" data-target=\"sample-modal\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-trash-can\"></i></span>")
                    .append("</button>")
                    .append("</div>")
                    .append("</td>")
                    .append("</tr>");
        }

        sendHTMLResponse(OK, html.toString());
    }
}
