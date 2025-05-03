package org.ruitx.www.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.types.User;
import org.ruitx.www.repositories.AuthRepo;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.models.auth.TokenResponse;

import static org.ruitx.jaws.strings.ResponseCode.*;

import java.util.List;
import java.util.Optional;

public class AuthService {

    private final AuthRepo authRepo;

    public AuthService() {
        this.authRepo = new AuthRepo();
    }

    public APIResponse<TokenResponse> loginUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(BAD_REQUEST, "User / password is missing");
        }

        Optional<User> user = authRepo.getUserByUsername(username.toLowerCase());
        if (user.isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        if (!BCrypt.verifyer()
                .verify(password.toCharArray(), user.get().passwordHash()).verified) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        String token = Tyr.createToken(user.get().user());
        if (token == null) {
            return APIResponse.error(UNAUTHORIZED, "Token creation failed");
        }

        return APIResponse.success(CREATED, new TokenResponse(token));
    }

    public APIResponse<TokenResponse> createUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(BAD_REQUEST, "User / password is missing");
        }

        Optional<User> user = authRepo.getUserByUsername(username.toLowerCase());
        if (user.isPresent()) {
            return APIResponse.error(CONFLICT, "User already exists");
        }

        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        Optional<Integer> result = authRepo.createUser(username, hashedPassword);
        if (result.isEmpty()) {
            return APIResponse.error(INTERNAL_SERVER_ERROR, "Cannot create user. Contact support");
        }

        String token = Tyr.createToken(username);
        if (token == null) {
            return APIResponse.error(UNAUTHORIZED, "Token creation failed");
        }

        return APIResponse.success(CREATED, new TokenResponse(token));
    }

    public APIResponse<List<User>> listUsers() {
        return APIResponse.success(OK,
                authRepo.getAllUsers().stream()
                        .map(User::defaultView)
                        .toList());
    }
}
