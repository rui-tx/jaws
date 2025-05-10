package org.ruitx.www.repositories;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.types.User;
import org.ruitx.www.models.auth.UserSession;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class AuthRepo {

    private final Mimir db;

    public AuthRepo() {
        this.db = new Mimir();
    }

    public Optional<Integer> createUser(String username, String hashedPassword) {
        int result = db.executeSql("INSERT INTO USER (user, password_hash, created_at) VALUES (?, ?, ?)",
                username, hashedPassword, Date.from(Instant.now()));
        return result > 0 ? Optional.of(result) : Optional.empty();
    }

    public Optional<UserSession> findActiveSessionByRefreshToken(String refreshToken) {
        Row result = db.getRow(
                "SELECT * FROM USER_SESSION WHERE refresh_token = ? AND is_active = 1",
                refreshToken
        );
        return UserSession.fromRow(result);
    }

    public void deactivateSession(String refreshToken) {
        db.executeSql(
                "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
                refreshToken
        );
    }

    public void deactivateAllUserSessions(Integer userId) {
        db.executeSql(
                "UPDATE USER_SESSION SET is_active = 0 WHERE user_id = ?",
                userId
        );
    }

    public Optional<User> getUserByUsername(String username) {
        Row row = db.getRow("SELECT * FROM USER WHERE user = ?", username);
        if (row == null) {
            return Optional.empty();
        }
        return User.fromRow(row);
    }

    public Optional<User> getUserById(Long id) {
        Row row = db.getRow("SELECT * FROM USER WHERE id = ?", id);
        if (row == null) {
            return Optional.empty();
        }
        return User.fromRow(row);
    }

    public List<User> getAllUsers() {
        List<Row> rows = db.getRows("SELECT * FROM USER ORDER BY created_at DESC");
        return rows.stream()
                .map(User::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }

    // schedule method
    public void cleanOldSessions() {
        db.executeSql("DELETE FROM USER_SESSION WHERE expires_at < ?", Date.from(Instant.now()));
    }
}