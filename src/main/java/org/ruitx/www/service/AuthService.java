package org.ruitx.www.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.auth.LoginResponse;
import org.ruitx.www.dto.auth.UserCreateRequest;
import org.ruitx.www.dto.auth.UserUpdateRequest;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.repository.AuthRepo;

import java.util.List;
import java.util.Optional;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class AuthService {

    private final AuthRepo authRepo;

    public AuthService() {
        this.authRepo = new AuthRepo();
    }

    public APIResponse<String> createUser(UserCreateRequest request) {
        Optional<User> user = authRepo.getUserByUsername(request.username().toLowerCase());
        if (user.isPresent()) {
            return APIResponse.error(CONFLICT, "User already exists");
        }

        Optional<Integer> result = authRepo.createUser(
                request.username(),
                BCrypt.withDefaults().hashToString(12, request.password().toCharArray()),
                request.firstName(),
                request.lastName());

        if (result.isEmpty()) {
            return APIResponse.error(INTERNAL_SERVER_ERROR, "Cannot create user. Check the logs for more details");
        }

        return APIResponse.success(CREATED, "User created successfully!");
    }

    public APIResponse<String> updateUser(Integer userId, UserUpdateRequest updateRequest) {
        Optional<User> userOpt = authRepo.getUserById(userId);
        if (userOpt.isEmpty()) {
            return APIResponse.error(NOT_FOUND, "User not found");
        }
        User currentUser = userOpt.get();

        // Hash password if present
        String passwordHash = updateRequest.password() != null
                ? BCrypt.withDefaults().hashToString(12, updateRequest.password().toCharArray())
                : currentUser.passwordHash();

        User updatedUser = User.builder()
                .id(currentUser.id())
                .user(currentUser.user())
                .passwordHash(passwordHash)
                .email(updateRequest.email() != null ? updateRequest.email() : currentUser.email())
                .firstName(updateRequest.firstName() != null ? updateRequest.firstName() : currentUser.firstName())
                .lastName(updateRequest.lastName() != null ? updateRequest.lastName() : currentUser.lastName())
                .birthdate(updateRequest.birthdate() != null ? updateRequest.birthdate() : currentUser.birthdate())
                .gender(updateRequest.gender() != null ? updateRequest.gender() : currentUser.gender())
                .phoneNumber(
                        updateRequest.phoneNumber() != null ? updateRequest.phoneNumber() : currentUser.phoneNumber())
                .profilePicture(updateRequest.profilePicture() != null ? updateRequest.profilePicture()
                        : currentUser.profilePicture())
                .bio(updateRequest.bio() != null ? updateRequest.bio() : currentUser.bio())
                .location(updateRequest.location() != null ? updateRequest.location() : currentUser.location())
                .website(updateRequest.website() != null ? updateRequest.website() : currentUser.website())
                .isActive(updateRequest.isActive() != null ? updateRequest.isActive() : currentUser.isActive())
                .lockoutUntil(updateRequest.lockoutUntil() != null ? updateRequest.lockoutUntil()
                        : currentUser.lockoutUntil())
                .createdAt(currentUser.createdAt())
                .updatedAt(System.currentTimeMillis())
                .build();

        Optional<Integer> result = authRepo.updateUser(updatedUser);
        return result.isEmpty()
                ? APIResponse.error(INTERNAL_SERVER_ERROR, "Error updating the user")
                : APIResponse.success(NO_CONTENT, "User updated sucessfully");

    }

    public APIResponse<LoginResponse> loginUser(String username, String password, String userAgent, String ipAddress) {
        Optional<User> user = authRepo.getUserByUsername(username.toLowerCase());
        if (user.isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        if (!BCrypt.verifyer()
                .verify(password.toCharArray(), user.get().passwordHash()).verified) {
            return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
        }

        Tyr.TokenPair tokenPair = Tyr.createTokenPair(
                user.get().id().toString(),
                userAgent,
                ipAddress);

        authRepo.updateLastLogin(user.get().id());
        return APIResponse.success(OK, LoginResponse.fromTokenPair(tokenPair));
    }

    public APIResponse<LoginResponse> refreshToken(String refreshToken, String userAgent, String ipAddress) {
        Optional<Tyr.TokenPair> newTokens = Tyr.refreshToken(refreshToken, userAgent, ipAddress);
        if (newTokens.isEmpty()) {
            return APIResponse.error(UNAUTHORIZED, "Invalid or expired refresh token");
        }

        return APIResponse.success(OK, LoginResponse.fromTokenPair(newTokens.get()));
    }

    public APIResponse<Void> logout(String refreshToken) {
        authRepo.deactivateSession(refreshToken);
        return APIResponse.success(OK, null);
    }

    public APIResponse<Void> logoutAll(String userId) {
        authRepo.deactivateAllUserSessions(Integer.parseInt(userId));
        return APIResponse.success(OK, null);
    }

    public APIResponse<List<User>> listUsers() {
        return APIResponse.success(OK,
                authRepo.getAllUsers().stream()
                        .map(User::defaultView)
                        .toList());
    }

    // schedule method
    public void cleanOldSessions() {
        authRepo.cleanOldSessions();
    }
}
