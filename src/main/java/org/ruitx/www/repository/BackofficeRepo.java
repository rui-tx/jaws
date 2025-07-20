package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.Cacheable;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserSession;

import java.util.List;
import java.util.Optional;

public class BackofficeRepo {

    private final Mimir db;

    public BackofficeRepo() {
        this.db = new Mimir();
    }

    /**
     * Retrieves all users from the database.
     *
     * @return List of User objects representing all users in the system.
     */
    @Cacheable(tables = {"USER"})
    public List<User> getAllUsers() {
        List<Row> rows = db.getRows("SELECT * FROM USER ORDER BY created_at DESC");
        return rows.stream()
                .map(User::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Retrieves all user sessions from the database.
     *
     * @return List of UserSession objects representing all user sessions in the system.
     */
    @Cacheable(tables = {"USER_SESSION"})
    public List<UserSession> getAllUserSessions() {
        List<Row> rows = db.getRows("SELECT * FROM USER_SESSION ORDER BY created_at DESC");
        return rows.stream()
                .map(UserSession::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }
}