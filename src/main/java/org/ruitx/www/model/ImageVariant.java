package org.ruitx.www.model;

import org.ruitx.jaws.types.Row;

public record ImageVariant(
        String id,
        String imageId,
        String variantType,
        String filePath,
        Integer fileSize,
        Integer width,
        Integer height,
        String mimeType,
        Long createdDate
) {
    public static ImageVariant fromRow(Row row) {
        return new ImageVariant(
                row.getString("id").orElse(null),
                row.getString("image_id").orElse(null),
                row.getString("variant_type").orElse(null),
                row.getString("file_path").orElse(null),
                row.getInt("file_size").orElse(null),
                row.getInt("width").orElse(null),
                row.getInt("height").orElse(null),
                row.getString("mime_type").orElse(null),
                row.getLong("created_date").orElse(null)
        );
    }

    public boolean isThumbnail() {
        return "thumbnail".equals(variantType);
    }

    public boolean isMedium() {
        return "medium".equals(variantType);
    }

    public boolean isLarge() {
        return "large".equals(variantType);
    }

    public boolean isWebP() {
        return "webp".equals(variantType);
    }
} 