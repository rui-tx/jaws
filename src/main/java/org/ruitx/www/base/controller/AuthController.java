package org.ruitx.www.base.controller;

import static org.ruitx.jaws.enums.RequestType.POST;
import static org.ruitx.jaws.enums.ResponseType.JSON;

import org.ruitx.jaws.components.njord.Route;
import org.ruitx.jaws.components.yggdrasill.AccessControl;
import org.ruitx.jaws.components.yggdrasill.Bragi;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.base.dto.auth.LoginRequest;
import org.ruitx.www.base.dto.auth.LoginResponse;
import org.ruitx.www.base.dto.auth.LogoutRequest;
import org.ruitx.www.base.dto.auth.RefreshTokenRequest;
import org.ruitx.www.base.service.AuthService;

public class AuthController extends Bragi {

  private static final String API_ENDPOINT = "/api/v1/auth/";
  private final AuthService authService;

  public AuthController() {
    this.authService = new AuthService();
  }

  @Route(endpoint = API_ENDPOINT + "login", method = POST, responseType = JSON)
  public void loginUser(LoginRequest request) {
    APIResponse<LoginResponse> response = authService.loginUser(
        request.user(),
        request.password(),
        getHeaders().get("User-Agent"),
        getClientIpAddress());

    sendSuccess(response.code(), response.data());
  }

  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT + "logout", method = POST, responseType = JSON)
  public void logout(LogoutRequest request) {
    APIResponse<Void> response = authService.logout(request.refreshToken());

    sendSuccess(response.code(), response.data());
  }

  @Route(endpoint = API_ENDPOINT + "refresh", method = POST, responseType = JSON)
  public void refreshToken(RefreshTokenRequest request) {
    APIResponse<LoginResponse> response = authService.refreshToken(
        request.refreshToken(),
        getHeaders().get("User-Agent"),
        getClientIpAddress());

    sendSuccess(response.code(), response.data());
  }

}