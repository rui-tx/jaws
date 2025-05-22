package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.auth.LoginRequest;
import org.ruitx.www.dto.auth.LoginResponse;
import org.ruitx.www.dto.auth.LogoutRequest;
import org.ruitx.www.dto.auth.RefreshTokenRequest;
import org.ruitx.www.service.AuthService;

import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class AuthController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/auth/";
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

        APIResponse<LoginResponse> response = authService.loginUser(
                username,
                password,
                userAgent,
                ipAddress);

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

        APIResponse<LoginResponse> response = authService.refreshToken(
                refreshToken,
                userAgent,
                ipAddress);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, response.data());
    }

    @AccessControl(login = true)
    @Route(endpoint = API_ENDPOINT + "logout-all", method = POST, responseType = JSON)
    public void logoutAll() {
        String userId = getCurrentToken();
        if (userId.isEmpty()) {
            sendErrorResponse(BAD_REQUEST, "Could not get user id from cookie");
            return;
        }

        APIResponse<Void> response = authService.logoutAll(userId);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(OK, null);
    }
}