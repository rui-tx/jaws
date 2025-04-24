package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.models.auth.CreateUserRequest;
import org.ruitx.www.models.auth.LoginRequest;
import org.ruitx.www.models.auth.TokenResponse;
import org.ruitx.www.services.AuthService;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.strings.ResponseCode;

import static org.ruitx.jaws.strings.RequestType.POST;

public class AuthController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/auth/";
    private final AuthService authService;

    public AuthController() {
        this.authService = new AuthService();
    }

    @Route(endpoint = API_ENDPOINT + "login", method = POST)
    public void loginUser(LoginRequest request) {
        String username = request.user();
        String password = request.password();
        
        APIResponse<TokenResponse> response = authService.loginUser(username, password);
        if (!response.success()) {
            sendErrorResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendSucessfulResponse(ResponseCode.OK, response.data());
    }

    @Route(endpoint = API_ENDPOINT + "create", method = POST)
    public void createUser(CreateUserRequest request) {
        String username = request.user();
        String password = request.password();
        
        APIResponse<TokenResponse> response = authService.createUser(username, password);
        if (!response.success()) {
            sendErrorResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
            return;
        }
        
        sendSucessfulResponse(ResponseCode.CREATED, response.data());
    }
}
