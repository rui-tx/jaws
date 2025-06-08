package org.ruitx.www.model;

import org.ruitx.jaws.types.Row;

public record Image(
        String id,
        String originalFilename,
        String filePath,
        Integer fileSize,
        String mimeType,
        Integer width,
        Integer height,
        String status,
        Long uploadDate,
        Long processedDate,
        String userSession,
        Integer userId,
        Boolean metadataRemoved
) {
    public static Image fromRow(Row row) {
        return new Image(
                row.getString("id").orElse(null),
                row.getString("original_filename").orElse(null),
                row.getString("file_path").orElse(null),
                row.getInt("file_size").orElse(null),
                row.getString("mime_type").orElse(null),
                row.getInt("width").orElse(null),
                row.getInt("height").orElse(null),
                row.getString("status").orElse("pending"),
                row.getLong("upload_date").orElse(null),
                row.getLong("processed_date").orElse(null),
                row.getString("user_session").orElse(null),
                row.getInt("user_id").orElse(null),
                row.getInt("metadata_removed").orElse(0) == 1
        );
    }

    public boolean isProcessing() {
        return "processing".equals(status);
    }

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }
} 