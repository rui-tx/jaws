package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.types.LogoutRequest;
import org.ruitx.jaws.types.RefreshTokenRequest;
import org.ruitx.jaws.types.User;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.models.auth.LoginRequest;
import org.ruitx.www.models.auth.TokenResponse;
import org.ruitx.www.services.AuthService;

import java.util.List;
import java.util.Optional;

import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
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

        String userAgent = getHeaders().get("User-Agent");
        String ipAddress = getClientIpAddress();

        APIResponse<TokenResponse> response = authService.loginUser(
                username,
                password,
                userAgent,
                ipAddress
        );

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, response.data());
    }

    @AccessControl(login = true)
    @Route(endpoint = API_ENDPOINT + "logout", method = POST, responseType = JSON)
    public void logout(LogoutRequest request) {
        APIResponse<Void> response = authService.logout(request.refreshToken());
        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, null);
    }

    @Route(endpoint = API_ENDPOINT + "refresh", method = POST, responseType = JSON)
    public void refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        String userAgent = getHeaders().get("User-Agent");
        String ipAddress = getClientIpAddress();

        APIResponse<TokenResponse> response = authService.refreshToken(
                refreshToken,
                userAgent,
                ipAddress
        );

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, response.data());
    }

    @AccessControl(login = true)
    @Route(endpoint = API_ENDPOINT + "logout-all", method = POST, responseType = JSON)
    public void logoutAll() {
        Optional<String> userId = getCookieToken();
        if (userId.isEmpty()) {
            sendErrorResponse(BAD_REQUEST, "Could not get user id from cookie");
            return;
        }

        APIResponse<Void> response = authService.logoutAll(userId.get());

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, null);
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
                    .append("<img class=\"is-rounded\" src=\"https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F")
                    //.append(user.user().toLowerCase().replace(" ", "-")) // disable for now
                    .append(".svg\">")
                    .append("</div>")
                    .append("</td>")
                    .append("<td data-label=\"Name\">")
                    .append("<p>").append(user.user()).append("</p>")
                    .append("<p class=\"has-text-grey is-size-7\">")
                    .append(user.email() != null ? user.email() : "No email")
                    .append("</p>")
                    .append("</td>")
                    .append("<td data-label=\"Full Name\">")
                    .append(user.firstName() != null ? user.firstName() : "")
                    .append(" ")
                    .append(user.lastName() != null ? user.lastName() : "")
                    .append("</td>")
                    .append("<td data-label=\"Created\">")
                    .append("<small class=\"has-text-grey is-abbr-like\" title=\"")
                    .append(user.createdAt() != null ? user.createdAt() : "N/A")
                    .append("\">").append(user.createdAt() != null ?
                            JawsUtils.formatUnixTimestamp(user.createdAt()) : "N/A").append("</small>")
                    .append("</td>")
                    .append("<td class=\"is-actions-cell\">")
                    .append("<div class=\"buttons is-right\">")
                    //.append("<a href=\"/backoffice/profile/").append(user.id()).append("\" ")
                    .append("<a  hx-get=\"/backoffice/profile/").append(user.id()).append("\" ")
                    .append("hx-trigger=\"click\" hx-target=\"#bodytemplate\" hx-target=\"#bodytemplate\" hx-push-url=\"true\" hx-swap=\"outerHTML swap:175ms settle:175ms\"")

                    .append("class=\"button is-small is-primary\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-eye\"></i></span>")
                    .append("</a>")
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
