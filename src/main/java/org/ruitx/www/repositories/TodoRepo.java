package org.ruitx.www.repositories;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;
import org.ruitx.jaws.utils.types.Todo;

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

    /**
     * Creates a new todo for a user
     * @param userId The ID of the user who owns this todo
     * @param content The content of the todo
     * @return Optional containing the created Todo if successful, empty if failed
     */
    public Optional<Todo> createTodo(Integer userId, String content) {
        try {
            db.beginTransaction();
            
            long createdAt = Instant.now().getEpochSecond();
            int result = db.executeSql(
                "INSERT INTO TODO (user_id, content, created_at) VALUES (?, ?, ?)",
                userId, content, createdAt
            );
            
            if (result <= 0) {
                db.rollbackTransaction();
                return Optional.empty();
            }
            
            // Get the last inserted ID and fetch the created todo
            Row row = db.getRow("SELECT * FROM TODO WHERE id = last_insert_rowid()");
            if (row == null) {
                db.rollbackTransaction();
                return Optional.empty();
            }
            
            Optional<Todo> todo = Todo.fromRow(row);
            if (todo.isEmpty()) {
                db.rollbackTransaction();
                return Optional.empty();
            }
            
            db.commitTransaction();
            return todo;
            
        } catch (Exception e) {
            try {
                db.rollbackTransaction();
            } catch (Exception ignored) {}
            System.out.println(Arrays.toString(e.getStackTrace()));
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