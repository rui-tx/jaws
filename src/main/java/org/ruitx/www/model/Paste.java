package org.ruitx.www.model;

import org.ruitx.jaws.types.Row;

public record Paste(
        String id,
        String content,
        String title,
        String language,
        Long expiresAt,
        Integer viewCount,
        Boolean isPrivate,
        String passwordHash,
        Integer userId,
        String ipAddress,
        String userAgent,
        Long createdAt,
        Long updatedAt
) {
    public static Paste fromRow(Row row) {
        return new Paste(
                row.getString("id").orElse(null),
                row.getString("content").orElse(null),
                row.getString("title").orElse(null),
                row.getString("language").orElse(null),
                row.getLong("expires_at").orElse(null),
                row.getInt("view_count").orElse(0),
                row.getInt("is_private").orElse(0) == 1,
                row.getString("password_hash").orElse(null),
                row.getInt("user_id").orElse(null),
                row.getString("ip_address").orElse(null),
                row.getString("user_agent").orElse(null),
                row.getLong("created_at").orElse(null),
                row.getLong("updated_at").orElse(null)
        );
    }

    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() / 1000 > expiresAt;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
} 