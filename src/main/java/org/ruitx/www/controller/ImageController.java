package org.ruitx.www.controller;

import jakarta.servlet.http.Part;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.model.Image;
import org.ruitx.www.model.ImageVariant;
import org.ruitx.www.service.ImageService;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;
import static org.ruitx.jaws.strings.ResponseType.JSON;

/**
 * ImageController - Handles image upload and management endpoints
 */
public class ImageController extends Bragi {

    private static final String BASE_HTML_PATH = "images/index.html";
    private static final String BODY_HTML_PATH = "images/_body.html";
    private static final String GALLERY_HTML_PATH = "images/gallery.html";
    
    private final ImageService imageService;

    public ImageController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.imageService = new ImageService();
    }

    // ========================================
    // HTML Pages
    // ========================================


    // /**
    //  * Main image upload page
    //  */
    // @Route(endpoint = "/images", method = GET)
    // public void renderIndex() {
    //     sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
    // }

    /**
     * Image gallery page
     */
    @Route(endpoint = "/images", method = GET)
    public void renderIndex() {
        String userSession = getSessionId();
        
        Map<String, String> context = new HashMap<>();
        
        // Get recent images for this user session
        APIResponse<List<Image>> response = imageService.getImagesByUserSession(userSession, 20);
        if (response.success()) {
            List<Image> images = response.data();
            
            StringBuilder galleryHtml = new StringBuilder();
            galleryHtml.append("<div class=\"image-gallery\">");
            
            if (images.isEmpty()) {
                galleryHtml.append("<div class=\"empty-gallery\">");
                // galleryHtml.append("<h3>No images yet</h3>");
                // galleryHtml.append("<p>Upload some images to see them here!</p>");
                // galleryHtml.append("<a href=\"/images\" class=\"btn-primary\">Upload Images</a>");
                galleryHtml.append("</div>");
            } else {
                galleryHtml.append("<div class=\"gallery-grid\">");
                
                for (Image image : images) {
                    galleryHtml.append(String.format("""
                        <div class="image-card" data-image-id="%s">
                            <div class="image-preview">
                                <img src="/api/images/%s/serve" alt="%s" loading="lazy">
                                <div class="image-overlay">
                                    <span class="status status-%s">%s</span>
                                </div>
                            </div>
                            <div class="image-info">
                                <h4>%s</h4>
                                <div class="image-meta">
                                    <span>%s</span>
                                    <span>%s KB</span>
                                    %s
                                </div>
                                <div class="image-actions">
                                    <button onclick="viewImage('%s')" class="btn-secondary">View</button>
                                    <button onclick="deleteImage('%s')" class="btn-danger">Delete</button>
                                </div>
                            </div>
                        </div>
                        """,
                        image.id(),
                        image.id(),
                        escapeHtml(image.originalFilename()),
                        image.status().toLowerCase(),
                        capitalize(image.status()),
                        escapeHtml(image.originalFilename()),
                        image.mimeType(),
                        image.fileSize() / 1024,
                        image.width() != null && image.height() != null ? 
                            String.format("<span>%dx%d</span>", image.width(), image.height()) : "",
                        image.id(),
                        image.id()
                    ));
                }
                
                galleryHtml.append("</div>");
            }
            
            galleryHtml.append("</div>");
            context.put("galleryContent", galleryHtml.toString());
        } else {
            context.put("galleryContent", "<div class=\"error\">Failed to load images: " + response.info() + "</div>");
        }
        
        setContext(context);
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, GALLERY_HTML_PATH));
    }

    // ========================================
    // API Endpoints
    // ========================================

    /**
     * Upload image endpoint
     */
    @Route(endpoint = "/api/images/upload", method = POST, responseType = JSON)
    public void uploadImage() {
        try {
            // Check if this is a multipart request
            if (!isMultipartRequest()) {
                sendErrorResponse(BAD_REQUEST, "This endpoint requires multipart/form-data content type");
                return;
            }

            // Get the uploaded file
            Part filePart = getMultipartFile("image");
            if (filePart == null) {
                sendErrorResponse(BAD_REQUEST, "No image file provided. Please include an 'image' field in your form data.");
                return;
            }

            // Validate file
            String filename = filePart.getSubmittedFileName();
            if (filename == null || filename.trim().isEmpty()) {
                sendErrorResponse(BAD_REQUEST, "Uploaded file has no filename");
                return;
            }

            String contentType = filePart.getContentType();
            if (contentType == null) {
                sendErrorResponse(BAD_REQUEST, "Unable to determine file content type");
                return;
            }

            // Read file data
            byte[] fileData;
            try {
                fileData = filePart.getInputStream().readAllBytes();
            } catch (IOException e) {
                JawsLogger.error("Failed to read uploaded file: {}", e.getMessage());
                sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to read uploaded file");
                return;
            }

            if (fileData.length == 0) {
                sendErrorResponse(BAD_REQUEST, "Uploaded file is empty");
                return;
            }

            // Get user information
            String userSession = getSessionId();
            Integer userId = getCurrentUserId();

            // Process the upload using ImageService
            APIResponse<String> response = imageService.uploadImage(fileData, filename, contentType, userSession, userId);

            if (response.success()) {
                sendSucessfulResponse(response.code(), response.data());
            } else {
                sendErrorResponse(response.code(), response.info());
            }

        } catch (Exception e) {
            JawsLogger.error("Failed to upload image: {}", e.getMessage(), e);
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Get image metadata
     */
    @Route(endpoint = "/api/images/:id", method = GET, responseType = JSON)
    public void getImage() {
        String imageId = getPathParam("id");
        
        APIResponse<Image> response = imageService.getImage(imageId);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    /**
     * Get image variants
     */
    @Route(endpoint = "/api/images/:id/variants", method = GET, responseType = JSON)
    public void getImageVariants() {
        String imageId = getPathParam("id");
        
        APIResponse<List<ImageVariant>> response = imageService.getImageVariants(imageId);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    /**
     * Serve image file (original or variant)
     */
    @Route(endpoint = "/api/images/:id/serve", method = GET)
    public void serveImageFile() {
        String imageId = getPathParam("id");
        JawsLogger.info("serveImageFile called with imageId: {}", imageId);
        String variant = getQueryParam("variant"); // optional: thumbnail, medium, large
        
        try {
            APIResponse<Image> imageResponse = imageService.getImage(imageId);
            if (!imageResponse.success()) {
                sendErrorResponse(imageResponse.code(), imageResponse.info());
                return;
            }

            Image image = imageResponse.data();
            String filePath;
            String mimeType = image.mimeType();

            if (variant != null && !variant.isEmpty()) {
                // Serve variant
                APIResponse<List<ImageVariant>> variantsResponse = imageService.getImageVariants(imageId);
                if (!variantsResponse.success()) {
                    sendErrorResponse(variantsResponse.code(), variantsResponse.info());
                    return;
                }

                ImageVariant requestedVariant = variantsResponse.data().stream()
                    .filter(v -> variant.equals(v.variantType()))
                    .findFirst()
                    .orElse(null);

                if (requestedVariant == null) {
                    sendErrorResponse(NOT_FOUND, "Variant not found: " + variant);
                    return;
                }

                filePath = requestedVariant.filePath();
                mimeType = requestedVariant.mimeType();
            } else {
                // Serve original
                filePath = image.filePath();
            }

            // Read file and serve as binary response
            try {
                byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
                sendBinaryResponse(OK, mimeType, fileContent);
            } catch (IOException e) {
                JawsLogger.error("Failed to read image file {}: {}", filePath, e.getMessage());
                sendErrorResponse(NOT_FOUND, "Image file not found");
            }

        } catch (Exception e) {
            JawsLogger.error("Failed to serve image file {}: {}", imageId, e.getMessage());
            sendErrorResponse(INTERNAL_SERVER_ERROR, "Failed to serve image file");
        }
    }

    /**
     * Delete image
     */
    @Route(endpoint = "/api/images/:id", method = DELETE, responseType = JSON)
    public void deleteImage() {
        String imageId = getPathParam("id");
        
        APIResponse<Boolean> response = imageService.deleteImage(imageId);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), Map.of(
                "deleted", response.data(),
                "message", response.info()
            ));
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    /**
     * Get recent images
     */
    @Route(endpoint = "/api/images", method = GET, responseType = JSON)
    public void getRecentImages() {
        int limit = 20;
        String limitParam = getQueryParam("limit");
        if (limitParam != null) {
            try {
                limit = Math.min(Integer.parseInt(limitParam), 100); // Max 100
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        APIResponse<List<Image>> response = imageService.getRecentImages(limit);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    /**
     * Get image statistics
     */
    @Route(endpoint = "/api/images/stats", method = GET, responseType = JSON)
    public void getImageStatistics() {
        APIResponse<Map<String, Object>> response = imageService.getImageStatistics();
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    /**
     * Test route for debugging
     */
    @Route(endpoint = "/api/images/test", method = GET, responseType = JSON)
    public void testRoute() {
        sendSucessfulResponse("200 OK", "Test route works!");
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Get current user ID from JWT token
     */
    private Integer getCurrentUserId() {
        String token = getCurrentToken();
        if (token != null && !token.isEmpty()) {
            try {
                // This would depend on your JWT implementation
                // For now, return null for anonymous uploads
                return null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get session ID for anonymous users
     */
    private String getSessionId() {
        // You might want to generate this from IP + User-Agent or use a cookie
        String ipAddress = getClientIpAddress();
        String userAgent = getHeaders().get("User-Agent");
        return Integer.toString((ipAddress + userAgent).hashCode());
    }

    /**
     * Simple HTML escaping
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    /**
     * Capitalize first letter
     */
    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
} 