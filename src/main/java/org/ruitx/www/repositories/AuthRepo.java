package org.ruitx.www.repositories;

import java.util.Optional;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;
import org.ruitx.jaws.utils.types.User;

public class AuthRepo {

    private final Mimir db;

    public AuthRepo() {
        this.db = new Mimir();
    }

    public Optional<User> getUserByUsername(String username) {
        Row user = db.getRow("SELECT * FROM USER WHERE user = ?", username);
        return user != null ? Optional.of(new User(
                user.getInt("id"),
                user.get("user").toString(),
                user.get("password_hash").toString(),
                user.get("created_at").toString()))
                : Optional.empty();
    }

    public Optional<Integer> createUser(String username, String hashedPassword) {
        int result = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                username, hashedPassword);
        return result > 0 ? Optional.of(result) : Optional.empty();
    }
}