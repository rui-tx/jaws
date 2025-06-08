package org.ruitx.www.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.Image;
import org.ruitx.www.model.ImageVariant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ImageRepo {

    private final Mimir db;

    public ImageRepo() {
        this.db = new Mimir();
    }

    /**
     * Create a new image record
     */
    public Optional<String> createImage(String id, String originalFilename, String filePath, 
                                      int fileSize, String mimeType, Integer width, Integer height,
                                      String userSession, Integer userId) {
        long now = Instant.now().toEpochMilli();
        
        int result = db.executeSql(
                """
                INSERT INTO IMAGES (
                    id, original_filename, file_path, file_size, mime_type, width, height,
                    status, upload_date, user_session, user_id, metadata_removed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, originalFilename, filePath, fileSize, mimeType, width, height,
                "pending", now, userSession, userId, 0
        );
        
        return result > 0 ? Optional.of(id) : Optional.empty();
    }

    /**
     * Get image by ID
     */
    public Optional<Image> getImageById(String id) {
        Row row = db.getRow("SELECT * FROM IMAGES WHERE id = ?", id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(Image.fromRow(row));
    }

    /**
     * Update image status
     */
    public boolean updateImageStatus(String id, String status) {
        long processedDate = "completed".equals(status) ? Instant.now().toEpochMilli() : 0;
        
        int result = db.executeSql(
                "UPDATE IMAGES SET status = ?, processed_date = ? WHERE id = ?",
                status, processedDate > 0 ? processedDate : null, id
        );
        
        return result > 0;
    }

    /**
     * Update image dimensions
     */
    public boolean updateImageDimensions(String id, int width, int height) {
        int result = db.executeSql(
                "UPDATE IMAGES SET width = ?, height = ? WHERE id = ?",
                width, height, id
        );
        
        return result > 0;
    }

    /**
     * Mark metadata as removed
     */
    public boolean markMetadataRemoved(String id) {
        int result = db.executeSql(
                "UPDATE IMAGES SET metadata_removed = 1 WHERE id = ?",
                id
        );
        
        return result > 0;
    }

    /**
     * Create image variant
     */
    public Optional<String> createImageVariant(String id, String imageId, String variantType,
                                             String filePath, int fileSize, int width, int height,
                                             String mimeType) {
        long now = Instant.now().toEpochMilli();
        
        int result = db.executeSql(
                """
                INSERT INTO IMAGE_VARIANTS (
                    id, image_id, variant_type, file_path, file_size, width, height,
                    mime_type, created_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, imageId, variantType, filePath, fileSize, width, height, mimeType, now
        );
        
        return result > 0 ? Optional.of(id) : Optional.empty();
    }

    /**
     * Get variants for an image
     */
    public List<ImageVariant> getImageVariants(String imageId) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGE_VARIANTS WHERE image_id = ? ORDER BY created_date",
                imageId
        );
        return rows.stream()
                .map(ImageVariant::fromRow)
                .toList();
    }

    /**
     * Get specific variant
     */
    public Optional<ImageVariant> getImageVariant(String imageId, String variantType) {
        Row row = db.getRow(
                "SELECT * FROM IMAGE_VARIANTS WHERE image_id = ? AND variant_type = ?",
                imageId, variantType
        );
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(ImageVariant.fromRow(row));
    }

    /**
     * Get recent images
     */
    public List<Image> getRecentImages(int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGES ORDER BY upload_date DESC LIMIT ?",
                limit
        );
        return rows.stream()
                .map(Image::fromRow)
                .toList();
    }

    /**
     * Get images by user session (for anonymous uploads)
     */
    public List<Image> getImagesByUserSession(String userSession, int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGES WHERE user_session = ? ORDER BY upload_date DESC LIMIT ?",
                userSession, limit
        );
        return rows.stream()
                .map(Image::fromRow)
                .toList();
    }

    /**
     * Get images by user ID
     */
    public List<Image> getImagesByUserId(Integer userId, int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGES WHERE user_id = ? ORDER BY upload_date DESC LIMIT ?",
                userId, limit
        );
        return rows.stream()
                .map(Image::fromRow)
                .toList();
    }

    /**
     * Delete image and all variants
     */
    public boolean deleteImage(String id) {
        // First delete variants
        db.executeSql("DELETE FROM IMAGE_VARIANTS WHERE image_id = ?", id);
        
        // Then delete the main image record
        int result = db.executeSql("DELETE FROM IMAGES WHERE id = ?", id);
        return result > 0;
    }

    /**
     * Get image count
     */
    public long getImageCount() {
        Row row = db.getRow("SELECT COUNT(*) as count FROM IMAGES");
        return row != null ? row.getLong("count").orElse(0L) : 0L;
    }

    /**
     * Get images by status
     */
    public List<Image> getImagesByStatus(String status, int limit) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGES WHERE status = ? ORDER BY upload_date DESC LIMIT ?",
                status, limit
        );
        return rows.stream()
                .map(Image::fromRow)
                .toList();
    }

    /**
     * Get images older than specified timestamp (for cleanup)
     */
    public List<Image> getImagesOlderThan(long cutoffTimestamp) {
        List<Row> rows = db.getRows(
                "SELECT * FROM IMAGES WHERE upload_date < ? ORDER BY upload_date ASC",
                cutoffTimestamp
        );
        return rows.stream()
                .map(Image::fromRow)
                .toList();
    }
} 