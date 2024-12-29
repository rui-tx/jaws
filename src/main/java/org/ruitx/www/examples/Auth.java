package org.ruitx.www.examples;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.Row;

import java.io.IOException;

import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.*;

public class Auth {

    @Route(endpoint = "/auth/token/create", method = POST)
    public void generateToken(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("user")
                || !rh.getBodyParams().containsKey("password")) {
            rh.sendJSONResponse(BAD_REQUEST, "{\"error\": \"User / password is missing\"}");
            return;
        }

        String user = rh.getBodyParams().get("user");
        String password = rh.getBodyParams().get("password");
        String token = Tyr.createToken(user, password);

        if (token == null) {
            rh.sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        rh.sendJSONResponse(OK, "{\"token\": " + token + "}");
    }

    @Route(endpoint = "/auth/token/verify", method = POST)
    public void validateToken(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("token")
                || rh.getBodyParams().get("token").isEmpty()) {
            rh.sendJSONResponse(BAD_REQUEST, "{\"error\": \"Token is missing or empty\"}");
            return;
        }

        boolean isTokenValid = Tyr.isTokenValid(rh.getBodyParams().get("token"));

        rh.sendJSONResponse(OK, "{\"valid\": " + isTokenValid + "}");
    }

    @Route(endpoint = "/auth/user/create", method = POST)
    public void createUser(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("user")
                || !rh.getBodyParams().containsKey("password")) {
            rh.sendJSONResponse(BAD_REQUEST, "{\"error\": \"User / password is missing\"}");
            return;
        }

        String user = rh.getBodyParams().get("user");
        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, rh.getBodyParams().get("password").toCharArray());
        Mimir db = new Mimir();
        int affectedRows = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                user, hashedPassword);

        if (affectedRows == 0) {
            rh.sendHTMLResponse(INTERNAL_SERVER_ERROR, "{\"error\": \"Cannot create user\"}");
            return;
        }

        String token = Tyr.createToken(user);
        if (token == null) {
            rh.sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        rh.sendJSONResponse(CREATED, "{\"token\": " + token + "}");
    }

    @Route(endpoint = "/auth/user/login", method = POST)
    public void loginUser(Yggdrasill.RequestHandler rh) throws IOException {
        if (rh.getBodyParams() == null
                || !rh.getBodyParams().containsKey("user")
                || !rh.getBodyParams().containsKey("password")) {
            rh.sendJSONResponse(BAD_REQUEST, "{\"error\": \"User / password is missing\"}");
            return;
        }

        Mimir db = new Mimir();
        Row dbUser = db.getRow("SELECT * FROM USER WHERE user = ?",
                rh.getBodyParams().get("user"));

        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            // user does not exist
            rh.sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(rh.getBodyParams().get("password").toCharArray(), storedPasswordHash)
                .verified) {
            // password does not match
            rh.sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Credentials are invalid\"}");
            return;
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            rh.sendJSONResponse(UNAUTHORIZED, "{\"error\": \"Token creation failed\"}");
            return;
        }

        rh.sendJSONResponse(CREATED, "{\"token\": \"" + token + "\"}");
    }
}
