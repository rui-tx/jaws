package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.User;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.repositories.AuthRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.OK;

public class BackofficeController extends Bragi {


    private static final String BASE_HTML_PATH = "backoffice/index.html";
    private static final String BODY_HTML_PATH = "backoffice/_body.html";
    private static final String UNAUTHORIZED_PAGE = "backoffice/partials/unauthorized.html";
    private static final String SETTINGS_PAGE = "backoffice/partials/settings.html";
    private static final String USER_PROFILE_PAGE = "backoffice/partials/profile.html";
    private static final Logger log = LoggerFactory.getLogger(BackofficeController.class);

    private final AuthRepo authRepo;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice", method = GET)
    public void renderIndex() {
        String token = getRequestHandler().getCurrentTokenValue();
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(token))).get();
        setTemplateVariable("userId", Tyr.getUserIdFromJWT(token));
        setTemplateVariable(
                "currentUser", token.isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/settings", method = GET)
    public void renderSettings() {
        String token = getRequestHandler().getCurrentTokenValue();
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(token))).get();
        setTemplateVariable("userId", Tyr.getUserIdFromJWT(token));
        setTemplateVariable(
                "currentUser", token.isEmpty() ? "-" : user.firstName() + " " + user.lastName());

        if (isHTMX()) {
            sendHTMLResponse(OK, renderTemplate(SETTINGS_PAGE));
            return;
        }

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, SETTINGS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/profile/:id", method = GET)
    public void renderUserProfile() {
        String userId = getPathParam("id");
        String token = getRequestHandler().getCurrentTokenValue();
        User currentUser = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(token))).get();
        User user = authRepo.getUserById(Long.parseLong(userId)).get();

        Map<String, String> context = new HashMap<>();
        context.put("currentUser", token.isEmpty() ? "-" : currentUser.firstName() + " " + currentUser.lastName());
        context.put("userId", userId);
        context.put("username", user.user());
        context.put("userEmail", user.email());
        context.put("userFirstName", user.firstName());
        context.put("userLastName", user.lastName());
        context.put("createdAt", JawsUtils.formatUnixTimestamp(user.createdAt()));
        setContext(context);
        if (isHTMX()) {
            sendHTMLResponse(OK, renderTemplate(USER_PROFILE_PAGE));
            return;
        }

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USER_PROFILE_PAGE));
    }
}
