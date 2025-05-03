package org.ruitx.www.repositories;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.types.User;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class AuthRepo {

    private final Mimir db;

    public AuthRepo() {
        this.db = new Mimir();
    }

    public Optional<User> getUserByUsername(String username) {
        Row row = db.getRow("SELECT * FROM USER WHERE user = ?", username);
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

    public Optional<Integer> createUser(String username, String hashedPassword) {
        int result = db.executeSql("INSERT INTO USER (user, password_hash, created_at) VALUES (?, ?, ?)",
                username, hashedPassword, Date.from(Instant.now()));
        return result > 0 ? Optional.of(result) : Optional.empty();
    }
}