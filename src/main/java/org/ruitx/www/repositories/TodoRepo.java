package org.ruitx.www.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.Transactional;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.types.Todo;
import org.tinylog.Logger;

public class TodoRepo {
    private final Mimir db;

    public TodoRepo() {
        this.db = new Mimir();
    }

    public Optional<Todo> getTodoById(Integer id) {
        Row row = db.getRow("SELECT * FROM TODO WHERE id = ?", id);
        if (row == null) {
            return Optional.empty();
        }
        return Todo.fromRow(row);
    }

    public List<Todo> getTodosByUserId(Integer userId) {
        List<Row> rows = db.getRows(
                "SELECT * FROM TODO WHERE user_id = ? ORDER BY created_at DESC",
                userId
        );
        return rows.stream()
                .map(Todo::fromRow)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional
    public Optional<Todo> createTodo(Integer userId, String content) {
        try {
            long createdAt = Instant.now().getEpochSecond();
            List<Row> inserted = db.executeInsert(
                    "INSERT INTO TODO (user_id, content, created_at) VALUES (?, ?, ?)",
                    userId, content, createdAt
            );
            return inserted.isEmpty() ? Optional.empty() : Todo.fromRow(inserted.get(0));
        } catch (Exception e) {
            Logger.error("Failed to create todo: {}", e.getMessage());
            throw new RuntimeException("Failed to create todo", e);
        }
    }


    public boolean updateTodo(Integer id, String content) {
        return db.executeSql(
                "UPDATE TODO SET content = ? WHERE id = ?",
                content, id
        ) > 0;
    }

    public boolean deleteTodo(Integer id) {
        return db.executeSql("DELETE FROM TODO WHERE id = ?", id) > 0;
    }

    public boolean deleteAllUserTodos(Integer userId) {
        return db.executeSql("DELETE FROM TODO WHERE user_id = ?", userId) > 0;
    }
} 