package org.ruitx.www.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.utils.Row;
import org.ruitx.www.repositories.AuthRepo;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.models.auth.TokenResponse;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class AuthService {

    private final AuthRepo authRepo;

    public AuthService() {
        this.authRepo = new AuthRepo();
    }

    public APIResponse<TokenResponse> loginUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(BAD_REQUEST, "User / password is missing");
        }

        Row dbUser = authRepo.getUserByUsername(username);
        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(password.toCharArray(), storedPasswordHash).verified) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            return APIResponse.error(UNAUTHORIZED, "Token creation failed");
        }

        return APIResponse.success(CREATED, new TokenResponse(token));
    }

    public APIResponse<TokenResponse> createUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(BAD_REQUEST, "User / password is missing");
        }

        if (authRepo.getUserByUsername(username) != null) {
            return APIResponse.error(CONFLICT, "User already exists");
        }

        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, password.toCharArray());

        int affectedRows = authRepo.createUser(username, hashedPassword);
        if (affectedRows == 0) {
            return APIResponse.error(INTERNAL_SERVER_ERROR, "Cannot create user");
        }

        String token = Tyr.createToken(username);
        if (token == null) {
            return APIResponse.error(UNAUTHORIZED, "Token creation failed");
        }

        return APIResponse.success(CREATED, new TokenResponse(token));
    }
}
