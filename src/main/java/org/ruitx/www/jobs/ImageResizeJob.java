package org.ruitx.www.jobs;

import org.ruitx.jaws.components.freyr.BaseJob;
import org.ruitx.jaws.components.freyr.ExecutionMode;
import org.ruitx.www.model.Image;
import org.ruitx.www.repository.ImageRepo;
import org.tinylog.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * ImageResizeJob - Processes images to create different sized variants
 * 
 * This job takes an uploaded image and creates:
 * - Thumbnail (150x150)
 * - Medium (800x600) 
 * - Large (1200x900)
 * 
 * All variants maintain aspect ratio and use high quality scaling.
 */
public class ImageResizeJob extends BaseJob {
    
    public static final String JOB_TYPE = "IMAGE_RESIZE";
    
    // Target sizes for different variants
    private static final int THUMBNAIL_SIZE = 150;
    private static final int MEDIUM_WIDTH = 800;
    private static final int MEDIUM_HEIGHT = 600;
    private static final int LARGE_WIDTH = 1200;
    private static final int LARGE_HEIGHT = 900;
    
    private final ImageRepo imageRepo;

    public ImageResizeJob(Map<String, Object> payload) {
        super(JOB_TYPE, ExecutionMode.PARALLEL, 3, 3, 60000L, payload); // 1 minute timeout
        this.imageRepo = new ImageRepo();
    }

    @Override
    public void execute() throws Exception {
        String imageId = getString("imageId");
        if (imageId == null) {
            throw new IllegalArgumentException("Missing imageId in payload");
        }

        Logger.info("Starting image resize job for image: {}", imageId);
        
        // Get image from database
        Image image = imageRepo.getImageById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        
        // Update status to processing
        imageRepo.updateImageStatus(imageId, "processing");
        
        try {
            // Load the original image
            BufferedImage originalImage = loadImage(image.filePath());
            
            // Update original image dimensions if not set
            if (image.width() == null || image.height() == null) {
                imageRepo.updateImageDimensions(imageId, originalImage.getWidth(), originalImage.getHeight());
            }
            
            // Create the uploads directory structure
            createDirectories();
            
            // Generate variants
            createThumbnail(imageId, originalImage, image.originalFilename());
            createMedium(imageId, originalImage, image.originalFilename());
            createLarge(imageId, originalImage, image.originalFilename());
            
            // Update status to completed
            imageRepo.updateImageStatus(imageId, "completed");
            
            Logger.info("Successfully completed image resize job for image: {}", imageId);
            
        } catch (Exception e) {
            // Update status to failed
            imageRepo.updateImageStatus(imageId, "failed");
            Logger.error("Failed to process image {}: {}", imageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Load image from file path
     */
    private BufferedImage loadImage(String filePath) throws IOException {
        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            throw new IOException("Image file not found: " + filePath);
        }
        
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Could not read image: " + filePath);
        }
        
        return image;
    }

    /**
     * Create directory structure for variants
     */
    private void createDirectories() throws IOException {
        Files.createDirectories(Paths.get("uploads/thumbnails"));
        Files.createDirectories(Paths.get("uploads/medium"));
        Files.createDirectories(Paths.get("uploads/large"));
    }

    /**
     * Create thumbnail variant (150x150, maintains aspect ratio)
     */
    private void createThumbnail(String imageId, BufferedImage original, String originalFilename) throws IOException {
        BufferedImage thumbnail = resizeImage(original, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true);
        String filename = generateVariantFilename(originalFilename, "thumbnail");
        String filePath = "uploads/thumbnails/" + filename;
        
        saveImage(thumbnail, filePath, getImageFormat(originalFilename));
        
        // Save variant to database
        imageRepo.createImageVariant(
                UUID.randomUUID().toString(),
                imageId,
                "thumbnail",
                filePath,
                (int) new File(filePath).length(),
                thumbnail.getWidth(),
                thumbnail.getHeight(),
                "image/" + getImageFormat(originalFilename)
        );
        
        Logger.info("Created thumbnail: {}", filePath);
    }

    /**
     * Create medium variant (800x600, maintains aspect ratio)
     */
    private void createMedium(String imageId, BufferedImage original, String originalFilename) throws IOException {
        BufferedImage medium = resizeImage(original, MEDIUM_WIDTH, MEDIUM_HEIGHT, false);
        String filename = generateVariantFilename(originalFilename, "medium");
        String filePath = "uploads/medium/" + filename;
        
        saveImage(medium, filePath, getImageFormat(originalFilename));
        
        // Save variant to database
        imageRepo.createImageVariant(
                UUID.randomUUID().toString(),
                imageId,
                "medium",
                filePath,
                (int) new File(filePath).length(),
                medium.getWidth(),
                medium.getHeight(),
                "image/" + getImageFormat(originalFilename)
        );
        
        Logger.info("Created medium: {}", filePath);
    }

    /**
     * Create large variant (1200x900, maintains aspect ratio)
     */
    private void createLarge(String imageId, BufferedImage original, String originalFilename) throws IOException {
        BufferedImage large = resizeImage(original, LARGE_WIDTH, LARGE_HEIGHT, false);
        String filename = generateVariantFilename(originalFilename, "large");
        String filePath = "uploads/large/" + filename;
        
        saveImage(large, filePath, getImageFormat(originalFilename));
        
        // Save variant to database
        imageRepo.createImageVariant(
                UUID.randomUUID().toString(),
                imageId,
                "large",
                filePath,
                (int) new File(filePath).length(),
                large.getWidth(),
                large.getHeight(),
                "image/" + getImageFormat(originalFilename)
        );
        
        Logger.info("Created large: {}", filePath);
    }

    /**
     * Resize image with high quality scaling
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight, boolean square) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        // Calculate new dimensions maintaining aspect ratio
        int newWidth, newHeight;
        
        if (square) {
            // For thumbnails, create a square crop
            int size = Math.min(originalWidth, originalHeight);
            newWidth = newHeight = THUMBNAIL_SIZE;
        } else {
            // Maintain aspect ratio
            double aspectRatio = (double) originalWidth / originalHeight;
            
            if (originalWidth > originalHeight) {
                newWidth = Math.min(targetWidth, originalWidth);
                newHeight = (int) (newWidth / aspectRatio);
                
                if (newHeight > targetHeight) {
                    newHeight = targetHeight;
                    newWidth = (int) (newHeight * aspectRatio);
                }
            } else {
                newHeight = Math.min(targetHeight, originalHeight);
                newWidth = (int) (newHeight * aspectRatio);
                
                if (newWidth > targetWidth) {
                    newWidth = targetWidth;
                    newHeight = (int) (newWidth / aspectRatio);
                }
            }
        }
        
        // Create high-quality scaled image
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (square) {
            // Center crop for square thumbnails
            int cropX = (originalWidth - Math.min(originalWidth, originalHeight)) / 2;
            int cropY = (originalHeight - Math.min(originalWidth, originalHeight)) / 2;
            int cropSize = Math.min(originalWidth, originalHeight);
            
            g2d.drawImage(original.getSubimage(cropX, cropY, cropSize, cropSize), 
                         0, 0, newWidth, newHeight, null);
        } else {
            g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        }
        
        g2d.dispose();
        return resized;
    }

    /**
     * Save image to file
     */
    private void saveImage(BufferedImage image, String filePath, String format) throws IOException {
        File outputFile = new File(filePath);
        outputFile.getParentFile().mkdirs();
        
        ImageIO.write(image, format, outputFile);
    }

    /**
     * Generate filename for variant
     */
    private String generateVariantFilename(String originalFilename, String variant) {
        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        return baseName + "_" + variant + extension;
    }

    /**
     * Get image format from filename
     */
    private String getImageFormat(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "jpg";
        };
    }
} 