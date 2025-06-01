package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.Paste;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PasteRepo {

    private final Mimir db;

    public PasteRepo() {
        this.db = new Mimir();
    }

    public Optional<String> createPaste(String id, String content, String title, String language, 
                                      Long expiresAt, Boolean isPrivate, String passwordHash,
                                      Integer userId, String ipAddress, String userAgent) {
        long now = Instant.now().getEpochSecond();
        
        int result = db.executeSql(
                """
                INSERT INTO PASTE (
                    id, content, title, language, expires_at, is_private, password_hash,
                    user_id, ip_address, user_agent, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, content, title, language, expiresAt, 
                isPrivate != null && isPrivate ? 1 : 0, passwordHash,
                userId, ipAddress, userAgent, now
        );
        
        return result > 0 ? Optional.of(id) : Optional.empty();
    }

    public Optional<Paste> getPasteById(String id) {
        Row row = db.getRow("SELECT * FROM PASTE WHERE id = ?", id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(Paste.fromRow(row));
    }

    public void incrementViewCount(String id) {
        db.executeSql("UPDATE PASTE SET view_count = view_count + 1 WHERE id = ?", id);
    }

    public List<Paste> getRecentPastes(int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM PASTE WHERE is_private = 0 AND (expires_at IS NULL OR expires_at > ?) ORDER BY created_at DESC LIMIT ?",
                Instant.now().getEpochSecond(), limit
        );
        return rows.stream()
                .map(Paste::fromRow)
                .toList();
    }

    public List<Paste> getUserPastes(Integer userId, int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM PASTE WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                userId, limit
        );
        return rows.stream()
                .map(Paste::fromRow)
                .toList();
    }

    public boolean deletePaste(String id) {
        int result = db.executeSql("DELETE FROM PASTE WHERE id = ?", id);
        return result > 0;
    }

    public void cleanExpiredPastes() {
        long now = Instant.now().getEpochSecond();
        db.executeSql("DELETE FROM PASTE WHERE expires_at IS NOT NULL AND expires_at < ?", now);
    }

    public long getPasteCount() {
        Row row = db.getRow("SELECT COUNT(*) as count FROM PASTE");
        return row != null ? row.getLong("count").orElse(0L) : 0L;
    }
} 