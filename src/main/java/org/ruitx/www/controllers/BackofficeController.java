package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.types.User;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.repositories.AuthRepo;
import org.ruitx.www.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.METHOD_NOT_ALLOWED;
import static org.ruitx.jaws.strings.ResponseCode.OK;

public class BackofficeController extends Bragi {


    private static final String BASE_HTML_PATH = "backoffice/index.html";
    private static final String BODY_HTML_PATH = "backoffice/_body.html";
    private static final String SETTINGS_PAGE = "backoffice/partials/settings.html";
    private static final String USERS_PAGE = "backoffice/partials/users.html";
    private static final String USER_PROFILE_PAGE = "backoffice/partials/profile.html";
    private static final Logger log = LoggerFactory.getLogger(BackofficeController.class);

    private final AuthRepo authRepo;
    private final AuthService authService;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
        this.authService = new AuthService();
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice", method = GET)
    public void renderIndex() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/settings", method = GET)
    public void renderSettings() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, SETTINGS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/users", method = GET)
    public void renderUsers() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USERS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/profile/:id", method = GET)
    public void renderUserProfile() {
        String userId = getPathParam("id");
        User currentUser = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();
        User user = authRepo.getUserById(Long.parseLong(userId)).get();

        Map<String, String> context = new HashMap<>();
        context.put("currentUser", getCurrentToken().isEmpty() ?
                "-" : currentUser.firstName() + " " + currentUser.lastName());
        context.put("userId", userId);
        context.put("username", user.user());
        context.put("userEmail", user.email());
        context.put("userFirstName", user.firstName());
        context.put("userLastName", user.lastName());
        context.put("createdAt", JawsUtils.formatUnixTimestamp(user.createdAt()));
        context.put("lastLogin", JawsUtils.formatUnixTimestamp(user.lastLogin(), "yyyy-MM-dd HH:mm:ss"));
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USER_PROFILE_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/users", method = GET)
    public void listUsersHTMX() {
        if (!isHTMX()) {
            sendHTMLResponse(METHOD_NOT_ALLOWED, "This endpoint is only accessible via HTMX");
            return;
        }

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
                    .append("<a href=\"/backoffice/profile/").append(user.id()).append("\" ")
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
