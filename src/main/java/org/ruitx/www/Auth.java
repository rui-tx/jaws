package org.ruitx.www;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.Row;

import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.*;

public class Auth extends BaseController {

    private static final String API_ENDPOINT = "/api/v1/";

    public Auth() {
    }

    @Route(endpoint = API_ENDPOINT + "auth/token/create", method = POST)
    public void generateToken() {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendJSONResponse(BAD_REQUEST, "User / password is missing");
            return;
        }

        String user = getBodyParam("user");
        String password = getBodyParam("password");
        String token = Tyr.createToken(user, password);

        if (token == null) {
            sendJSONResponse(UNAUTHORIZED, "Credentials are invalid");
            return;
        }

        sendJSONResponse(OK, "{\"token\": \"" + token + "\"}");
    }

    @Route(endpoint = "/auth/token/verify", method = POST)
    public void validateToken() {
        String token = getBodyParam("token");
        if (token == null || token.isEmpty()) {
            sendJSONResponse(BAD_REQUEST, "{\"error\": \"Token is missing or empty\"}");
            return;
        }

        boolean isTokenValid = Tyr.isTokenValid(token);
        sendJSONResponse(OK, "{\"valid\": " + isTokenValid + "}");
    }

    @Route(endpoint = "/auth/user/create", method = POST)
    public void createUser() {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendJSONResponse(BAD_REQUEST, "{\"error\": \"User / password is missing\"}");
            return;
        }

        String user = getBodyParam("user");
        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, getBodyParam("password").toCharArray());
        Mimir db = new Mimir();
        int affectedRows = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                user, hashedPassword);

        if (affectedRows == 0) {
            sendJSONResponse(INTERNAL_SERVER_ERROR, "{\"error\": \"Cannot create user\"}");
            return;
        }

        String token = Tyr.createToken(user);
        if (token == null) {
            sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        sendJSONResponse(CREATED, "{\"token\": \"" + token + "\"}");
    }

    @Route(endpoint = "/auth/user/login", method = POST)
    public void loginUser() {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendJSONResponse(BAD_REQUEST, "{\"error\": \"User / password is missing\"}");
            return;
        }

        Mimir db = new Mimir();
        Row dbUser = db.getRow("SELECT * FROM USER WHERE user = ?",
                getBodyParam("user"));

        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            // user does not exist
            sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(getBodyParam("password").toCharArray(), storedPasswordHash)
                .verified) {
            // password does not match
            sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Token creation failed\"}");
            return;
        }

        sendJSONResponse(CREATED, "{\"token\": \"" + token + "\"}");
    }
}
