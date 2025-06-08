package org.ruitx.www.service;

import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.jobs.ImageResizeJob;
import org.ruitx.www.model.Image;
import org.ruitx.www.model.ImageVariant;
import org.ruitx.www.repository.ImageRepo;
import org.tinylog.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ImageService - Business logic for image upload and processing
 */
public class ImageService {

    private final ImageRepo imageRepo;
    private final Freyr jobQueue;
    
    // Supported image types
    private static final String[] SUPPORTED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/bmp"};
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public ImageService() {
        this.imageRepo = new ImageRepo();
        this.jobQueue = Freyr.getInstance();
    }

    /**
     * Upload and process an image
     */
    public APIResponse<String> uploadImage(byte[] imageData, String originalFilename, 
                                         String mimeType, String userSession, Integer userId) {
        try {
            // Validate input
            APIResponse<String> validationResult = validateUpload(imageData, originalFilename, mimeType);
            if (!validationResult.success()) {
                return validationResult;
            }

            // Generate unique ID and file path
            String imageId = UUID.randomUUID().toString();
            String sanitizedFilename = sanitizeFilename(originalFilename);
            String filePath = "uploads/originals/" + imageId + "_" + sanitizedFilename;

            // Create directory structure
            Files.createDirectories(Paths.get("uploads/originals"));

            // Save original file
            Path outputPath = Paths.get(filePath);
            Files.write(outputPath, imageData);

            // Get image dimensions
            Integer width = null;
            Integer height = null;
            try {
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                if (bufferedImage != null) {
                    width = bufferedImage.getWidth();
                    height = bufferedImage.getHeight();
                }
            } catch (IOException e) {
                Logger.warn("Could not read image dimensions for {}: {}", imageId, e.getMessage());
            }

            // Save to database
            Optional<String> result = imageRepo.createImage(
                    imageId, originalFilename, filePath, imageData.length, 
                    mimeType, width, height, userSession, userId
            );

            if (result.isEmpty()) {
                // Clean up file if database save failed
                try {
                    Files.deleteIfExists(outputPath);
                } catch (IOException e) {
                    Logger.warn("Could not clean up file after database failure: {}", e.getMessage());
                }
                return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to save image to database");
            }

            // Queue image processing job
            Map<String, Object> jobPayload = Map.of("imageId", imageId);
            String jobId = jobQueue.submit(new ImageResizeJob(jobPayload));

            Logger.info("Image uploaded successfully: {} -> job: {}", imageId, jobId);

            return APIResponse.success("201 CREATED", "Image uploaded successfully", imageId);

        } catch (Exception e) {
            Logger.error("Failed to upload image: {}", e.getMessage(), e);
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Get image by ID
     */
    public APIResponse<Image> getImage(String imageId) {
        try {
            Optional<Image> imageOpt = imageRepo.getImageById(imageId);
            if (imageOpt.isEmpty()) {
                return APIResponse.error("404 NOT FOUND", "Image not found");
            }

            return APIResponse.success("200 OK", "Image retrieved successfully", imageOpt.get());
        } catch (Exception e) {
            Logger.error("Failed to get image {}: {}", imageId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to retrieve image");
        }
    }

    /**
     * Get image variants
     */
    public APIResponse<List<ImageVariant>> getImageVariants(String imageId) {
        try {
            // Check if image exists
            if (imageRepo.getImageById(imageId).isEmpty()) {
                return APIResponse.error("404 NOT FOUND", "Image not found");
            }

            List<ImageVariant> variants = imageRepo.getImageVariants(imageId);
            return APIResponse.success("200 OK", "Image variants retrieved successfully", variants);
        } catch (Exception e) {
            Logger.error("Failed to get image variants for {}: {}", imageId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to retrieve image variants");
        }
    }

    /**
     * Get recent images
     */
    public APIResponse<List<Image>> getRecentImages(int limit) {
        try {
            List<Image> images = imageRepo.getRecentImages(limit);
            return APIResponse.success("200 OK", "Recent images retrieved successfully", images);
        } catch (Exception e) {
            Logger.error("Failed to get recent images: {}", e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to retrieve recent images");
        }
    }

    /**
     * Get images by user session
     */
    public APIResponse<List<Image>> getImagesByUserSession(String userSession, int limit) {
        try {
            List<Image> images = imageRepo.getImagesByUserSession(userSession, limit);
            return APIResponse.success("200 OK", "User images retrieved successfully", images);
        } catch (Exception e) {
            Logger.error("Failed to get images for user session {}: {}", userSession, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to retrieve user images");
        }
    }

    /**
     * Delete image
     */
    public APIResponse<Boolean> deleteImage(String imageId) {
        try {
            Optional<Image> imageOpt = imageRepo.getImageById(imageId);
            if (imageOpt.isEmpty()) {
                return APIResponse.error("404 NOT FOUND", "Image not found");
            }

            Image image = imageOpt.get();

            // Delete variants first
            List<ImageVariant> variants = imageRepo.getImageVariants(imageId);
            for (ImageVariant variant : variants) {
                try {
                    Files.deleteIfExists(Paths.get(variant.filePath()));
                } catch (IOException e) {
                    Logger.warn("Could not delete variant file {}: {}", variant.filePath(), e.getMessage());
                }
            }

            // Delete original file
            try {
                Files.deleteIfExists(Paths.get(image.filePath()));
            } catch (IOException e) {
                Logger.warn("Could not delete original file {}: {}", image.filePath(), e.getMessage());
            }

            // Delete from database
            boolean deleted = imageRepo.deleteImage(imageId);
            if (!deleted) {
                return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to delete image from database");
            }

            return APIResponse.success("200 OK", "Image deleted successfully", true);
        } catch (Exception e) {
            Logger.error("Failed to delete image {}: {}", imageId, e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to delete image");
        }
    }

    /**
     * Validate upload data
     */
    private APIResponse<String> validateUpload(byte[] imageData, String originalFilename, String mimeType) {
        // Check file size
        if (imageData.length > MAX_FILE_SIZE) {
            return APIResponse.error("413 PAYLOAD TOO LARGE", 
                "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        // Check filename
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return APIResponse.error("400 BAD REQUEST", "Original filename is required");
        }

        // Check mime type
        if (mimeType == null || !isValidImageType(mimeType)) {
            return APIResponse.error("400 BAD REQUEST", 
                "Invalid image type. Supported types: " + String.join(", ", SUPPORTED_TYPES));
        }

        // Try to read the image to validate it's actually an image
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (bufferedImage == null) {
                return APIResponse.error("400 BAD REQUEST", "Invalid image data");
            }
        } catch (IOException e) {
            return APIResponse.error("400 BAD REQUEST", "Could not read image data");
        }

        return APIResponse.success("200 OK", "Validation passed", null);
    }

    /**
     * Check if mime type is supported
     */
    private boolean isValidImageType(String mimeType) {
        for (String supportedType : SUPPORTED_TYPES) {
            if (supportedType.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitize filename for safe storage
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown.jpg";
        
        // Remove path separators and dangerous characters
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure it has an extension
        if (!sanitized.contains(".")) {
            sanitized += ".jpg";
        }
        
        return sanitized;
    }

    /**
     * Get image statistics
     */
    public APIResponse<Map<String, Object>> getImageStatistics() {
        try {
            long totalImages = imageRepo.getImageCount();
            List<Image> pendingImages = imageRepo.getImagesByStatus("pending", 100);
            List<Image> processingImages = imageRepo.getImagesByStatus("processing", 100);
            List<Image> failedImages = imageRepo.getImagesByStatus("failed", 100);

            Map<String, Object> stats = Map.of(
                "totalImages", totalImages,
                "pendingImages", pendingImages.size(),
                "processingImages", processingImages.size(),
                "failedImages", failedImages.size(),
                "completedImages", totalImages - pendingImages.size() - processingImages.size() - failedImages.size()
            );

            return APIResponse.success("200 OK", "Image statistics retrieved successfully", stats);
        } catch (Exception e) {
            Logger.error("Failed to get image statistics: {}", e.getMessage());
            return APIResponse.error("500 INTERNAL SERVER ERROR", "Failed to retrieve image statistics");
        }
    }

    /**
     * Clean up images older than 24 hours
     */
    public void cleanOldImages() {
        try {
            long cutoffTime = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli(); // 24 hours ago
            
            // Get images older than 24 hours
            List<Image> oldImages = imageRepo.getImagesOlderThan(cutoffTime);
            
            if (oldImages.isEmpty()) {
                Logger.info("No old images found to clean up");
                return;
            }
            
            Logger.info("Found {} images older than 24 hours, starting cleanup", oldImages.size());
            
            int deletedCount = 0;
            int errorCount = 0;
            
            for (Image image : oldImages) {
                try {
                    // Delete variants first
                    List<ImageVariant> variants = imageRepo.getImageVariants(image.id());
                    for (ImageVariant variant : variants) {
                        try {
                            Files.deleteIfExists(Paths.get(variant.filePath()));
                        } catch (IOException e) {
                            Logger.warn("Could not delete variant file {}: {}", variant.filePath(), e.getMessage());
                        }
                    }
                    
                    // Delete original file
                    try {
                        Files.deleteIfExists(Paths.get(image.filePath()));
                    } catch (IOException e) {
                        Logger.warn("Could not delete original file {}: {}", image.filePath(), e.getMessage());
                    }
                    
                    // Delete from database
                    boolean deleted = imageRepo.deleteImage(image.id());
                    if (deleted) {
                        deletedCount++;
                        Logger.debug("Deleted old image: {} ({})", image.originalFilename(), image.id());
                    } else {
                        errorCount++;
                        Logger.warn("Failed to delete image from database: {}", image.id());
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    Logger.error("Error deleting old image {}: {}", image.id(), e.getMessage(), e);
                }
            }
            
            Logger.info("Image cleanup completed: {} deleted, {} errors", deletedCount, errorCount);
            
        } catch (Exception e) {
            Logger.error("Failed to clean up old images: {}", e.getMessage(), e);
        }
    }
} 