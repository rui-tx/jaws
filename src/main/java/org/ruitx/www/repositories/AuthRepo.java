package org.ruitx.www.repositories;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;

public class AuthRepo {

    private final Mimir db;

    public AuthRepo() {
        this.db = new Mimir();
    }

    public boolean validateToken(String token) {
        return true;
    }

    public Row getUserByUsername(String username) {
        return db.getRow("SELECT * FROM USER WHERE user = ?", username);
    }

    public int createUser(String username, String hashedPassword) {
        return db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                username, hashedPassword);
    }
}