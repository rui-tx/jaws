package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.models.responses.TokenResponse;
import org.ruitx.www.models.responses.ValidationResponse;
import org.ruitx.www.services.AuthService;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.strings.ResponseCode;

import static org.ruitx.jaws.strings.RequestType.POST;

public class AuthController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final AuthService authService;

    public AuthController() {
        this.authService = new AuthService();
    }

    @Route(endpoint = API_ENDPOINT + "auth/token/create", method = POST)
    public void generateToken() {
        String username = getBodyParam("user");
        String password = getBodyParam("password");
        
        APIResponse<TokenResponse> response = authService.generateToken(username, password);
        if (!response.success()) {
            sendJSONResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendJSONResponse(ResponseCode.OK, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "auth/token/verify", method = POST)
    public void validateToken() {
        String token = getBodyParam("token");
        
        APIResponse<ValidationResponse> response = authService.validateToken(token);
        if (!response.success()) {
            sendJSONResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendJSONResponse(ResponseCode.OK, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "auth/user/create", method = POST)
    public void createUser() {
        String username = getBodyParam("user");
        String password = getBodyParam("password");
        
        APIResponse<TokenResponse> response = authService.createUser(username, password);
        if (!response.success()) {
            sendJSONResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendJSONResponse(ResponseCode.CREATED, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "auth/user/login", method = POST)
    public void loginUser() {
        String username = getBodyParam("user");
        String password = getBodyParam("password");
        
        APIResponse<TokenResponse> response = authService.loginUser(username, password);
        if (!response.success()) {
            sendJSONResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendJSONResponse(ResponseCode.OK, response.data());
    }
}
