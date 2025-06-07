package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.jobs.BaseJob;
import org.ruitx.jaws.jobs.JobResultStore;
import org.tinylog.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ImageProcessingJob - Example of async image processing
 * 
 * This job demonstrates your exact use case:
 * 1. Receive image data
 * 2. Apply filter/processing  
 * 3. Save processed image
 * 4. Return download link
 * 
 * Instead of:
 * @Route(endpoint = "/api/process-image", method = POST)
 * @Async(timeout = 60000)
 * public void processImageAsync() { ... }
 */
public class ImageProcessingJob extends BaseJob {
    
    public static final String JOB_TYPE = "image-processing";
    
    /**
     * Constructor - jobs must have a constructor that takes Map<String, Object>
     */
    public ImageProcessingJob(Map<String, Object> payload) {
        super(JOB_TYPE, 6, 2, 60000L, payload); // priority 6, 2 retries, 60s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting image processing job: {}", getId());
        
        try {
            // Step 1: Get image data from payload
            String imageBase64 = getString("imageData");
            String filterType = getString("filterType");
            String originalFileName = getString("originalFileName");
            
            if (imageBase64 == null || imageBase64.isEmpty()) {
                throw new IllegalArgumentException("No image data provided");
            }
            
            Logger.info("Processing image: {} with filter: {}", originalFileName, filterType);
            
            // Step 2: Decode and save original image
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            String imageId = UUID.randomUUID().toString();
            String fileExtension = getFileExtension(originalFileName);
            
            // Create upload directory if it doesn't exist
            Path uploadDir = Paths.get("uploads/processed");
            Files.createDirectories(uploadDir);
            
            // Step 3: Apply filter (simulate image processing)
            byte[] processedImageBytes = applyImageFilter(imageBytes, filterType);
            
            // Step 4: Save processed image
            String processedFileName = imageId + "_" + filterType + "." + fileExtension;
            Path processedImagePath = uploadDir.resolve(processedFileName);
            
            try (FileOutputStream fos = new FileOutputStream(processedImagePath.toFile())) {
                fos.write(processedImageBytes);
            }
            
            // Step 5: Generate download link
            String downloadUrl = "/api/download/" + processedFileName;
            
            // Step 6: Create result with download link
            Map<String, Object> result = new HashMap<>();
            result.put("processingCompleted", true);
            result.put("originalFileName", originalFileName);
            result.put("processedFileName", processedFileName);
            result.put("filterApplied", filterType);
            result.put("downloadUrl", downloadUrl);
            result.put("imageId", imageId);
            result.put("fileSize", processedImageBytes.length);
            result.put("processedAt", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("expiresAt", Instant.now().toEpochMilli() + 86400000); // 24 hours
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("Image processing job completed: {} -> {}", originalFileName, downloadUrl);
            
        } catch (Exception e) {
            Logger.error("Image processing job failed: {}", e.getMessage(), e);
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Image processing failed: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Simulate image filter processing
     * In real implementation, you'd use image processing libraries like ImageIO, BufferedImage, etc.
     */
    private byte[] applyImageFilter(byte[] originalBytes, String filterType) throws InterruptedException {
        Logger.info("Applying {} filter to image ({} bytes)...", filterType, originalBytes.length);
        
        // Simulate processing time based on filter complexity
        long processingTime = switch (filterType != null ? filterType.toLowerCase() : "default") {
            case "sepia" -> 2000L;
            case "blur" -> 3000L;
            case "sharpen" -> 1500L;
            case "vintage" -> 4000L;
            default -> 1000L;
        };
        
        Thread.sleep(processingTime);
        
        // In real implementation, you would:
        // BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalBytes));
        // Apply actual filters using Graphics2D, ConvolveOp, etc.
        // ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ImageIO.write(processedImage, "jpg", baos);
        // return baos.toByteArray();
        
        // For demo purposes, just return the original bytes
        // (in reality, this would be the processed image)
        return originalBytes;
    }
    
    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg"; // default
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
} 