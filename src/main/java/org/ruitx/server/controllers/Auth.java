package org.ruitx.server.controllers;

import org.ruitx.server.components.Tyr;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;

import java.io.IOException;

import static org.ruitx.server.strings.RequestType.POST;
import static org.ruitx.server.strings.ResponseCode.*;

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
}
