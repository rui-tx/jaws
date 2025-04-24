package org.ruitx.www.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.utils.Row;
import org.ruitx.www.repositories.AuthRepo;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.www.models.auth.TokenResponse;

public class AuthService {

    private final AuthRepo authRepo;

    public AuthService() {
        this.authRepo = new AuthRepo();
    }

    public APIResponse<TokenResponse> loginUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(
                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                "User / password is missing"
            );
        }

        Row dbUser = authRepo.getUserByUsername(username);
        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            return APIResponse.error(
                ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                "Credentials are invalid"
            );
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(password.toCharArray(), storedPasswordHash)
                .verified) {
            return APIResponse.error(
                ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                "Credentials are invalid"
            );
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            return APIResponse.error(
                ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                "Token creation failed"
            );
        }

        return APIResponse.success(
            ResponseCode.CREATED.getCodeAndMessage(),
            new TokenResponse(token)
        );
    }

    public APIResponse<TokenResponse> createUser(String username, String password) {
        if (username == null || password == null) {
            return APIResponse.error(
                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                "User / password is missing"
            );
        }

        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, password.toCharArray());
        
        int affectedRows = authRepo.createUser(username, hashedPassword);
        if (affectedRows == 0) {
            return APIResponse.error(
                ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                "Cannot create user"
            );
        }

        String token = Tyr.createToken(username);
        if (token == null) {
            return APIResponse.error(
                ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                "Token creation failed"
            );
        }

        return APIResponse.success(
            ResponseCode.CREATED.getCodeAndMessage(),
            new TokenResponse(token)
        );
    }
}
