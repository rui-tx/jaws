package org.ruitx.www.examples.upload.controller;

import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.components.Hermes;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.Row;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.tinylog.Logger;

import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class UploadController extends BaseController {
    private static final String BASE_HTML_PATH = "examples/upload/index.html";
    private static final String BODY_HTML_PATH = "examples/upload/partials/_body.html";
    private static final String UPLOAD_DIR = ApplicationConfig.UPLOAD_DIR;
    private static final String API_ENDPOINT = "/api/v1/upload/";

    public UploadController() {
        Hermes.setBodyPath(BODY_HTML_PATH);
        // Create upload directory if it doesn't exist
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                System.out.println("Created upload directory at: " + uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error creating upload directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===========================================
    // Frontend Methods (HTML/UI)
    // ===========================================

    @Route(endpoint = "/upload", method = GET)
    public void renderIndex() throws IOException {
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
    }

    @Route(endpoint = "/upload/login-page", method = GET)
    public void loginPage() throws IOException {
        if (isHTMX()) {
            sendHTMLResponse(OK, renderTemplate("examples/upload/partials/login.html"));
            return;
        }
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, "examples/upload/partials/login.html"));
    }

    @Route(endpoint = "/upload/create-account-page", method = GET)
    public void createAccountPage() throws IOException {
        if (isHTMX()) {
            sendHTMLResponse(OK, renderTemplate("examples/upload/partials/create-account.html"));
            return;
        }
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, "examples/upload/partials/create-account.html"));
    }

    @Route(endpoint = "/upload/download/:id", method = GET)
    public void downloadFile() throws IOException {
        String id = getPathParam("id");
        Mimir db = new Mimir();
        Row upload = db.getRow("SELECT * FROM UPLOADS WHERE id = ?", id);

        if (upload == null) {
            if (isHTMX()) {
                sendHTMLResponse(OK, renderTemplate("examples/upload/partials/not-found.html"));
            } else {
                sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, "examples/upload/partials/not-found.html"));
            }
            return;
        }

        String fileName = upload.getString("file_name");
        String originalName = upload.getString("original_name");
        long fileSize = upload.get("file_size") instanceof Integer ? 
            ((Integer) upload.get("file_size")).longValue() : 
            (Long) upload.get("file_size");
        long expiryTime = upload.get("expiry_time") instanceof Integer ? 
            ((Integer) upload.get("expiry_time")).longValue() : 
            (Long) upload.get("expiry_time");
        Date expiryDate = new Date(expiryTime);

        if (expiryDate.getTime() < System.currentTimeMillis()) {
            if (isHTMX()) {
                sendHTMLResponse(OK, renderTemplate("examples/upload/partials/expired.html"));
            } else {
                sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, "examples/upload/partials/expired.html"));
            }
            return;
        }

        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        if (!Files.exists(filePath)) {
            if (isHTMX()) {
                sendHTMLResponse(OK, renderTemplate("examples/upload/partials/not-found.html"));
            } else {
                sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, "examples/upload/partials/not-found.html"));
            }
            return;
        }

        // Show download page with file info
        Map<String, String> params = new HashMap<>();
        params.put("id", id);
        params.put("filename", originalName);
        params.put("filesize", formatFileSize(fileSize));
        params.put("expires", String.valueOf((expiryDate.getTime() - System.currentTimeMillis()) / (60 * 1000)));

        if (isHTMX()) {
            sendHTMLResponse(OK, renderTemplate("examples/upload/partials/download-page.html", params));
            return;
        }

        sendHTMLResponse(OK, assemblePageWithContent(BASE_HTML_PATH, renderTemplate("examples/upload/partials/download-page.html", params)));
    }

    // ===========================================
    // Backend Methods (API)
    // ===========================================

    @Route(endpoint = API_ENDPOINT + "upload", method = POST)
    public void handleUpload() {
        String base64File = getBodyParam("file");
        String fileName = getBodyParam("filename");
        String expiryMinutes = getBodyParam("expiry");
        
        if (base64File == null || fileName == null || expiryMinutes == null) {
            sendHTMLResponse(BAD_REQUEST, "<div class='error'>Missing required fields</div>");
            return;
        }

        // Check file size (10MB limit)
        byte[] fileBytes = Base64.getDecoder().decode(base64File);
        if (fileBytes.length > 10 * 1024 * 1024) { // 10MB in bytes
            sendHTMLResponse(PAYLOAD_TOO_LARGE, "<div class='error'>File size exceeds 10MB limit</div>");
            return;
        }

        // Generate unique filename
        String uniqueId = UUID.randomUUID().toString();
        String extension = fileName.substring(fileName.lastIndexOf("."));
        String newFileName = uniqueId + extension;
        
        // Save file
        Path filePath = Paths.get(UPLOAD_DIR, newFileName);
        System.out.println("Saving file to: " + filePath.toAbsolutePath());
        try {
            Files.write(filePath, fileBytes);
        } catch (Exception e) {
            Logger.error("Failed to save file: {}", e.getMessage());
            throw new SendRespondException("Failed to save file", e);
        }

        // Calculate expiry time - ensure it's at least 1 minute in the future
        long minutes = Long.parseLong(expiryMinutes);
        long expiryTime = System.currentTimeMillis() + (minutes * 60 * 1000);
        long fileSize = fileBytes.length;

        // Save to database
        Mimir db = new Mimir();
        Integer userId = null;
        if (getBodyParam("user_id") != null) {
            userId = Integer.parseInt(getBodyParam("user_id"));
        }

        int affectedRows = db.executeSql(
            "INSERT INTO UPLOADS (file_name, original_name, file_size, expiry_time, user_id) VALUES (?, ?, ?, ?, ?)",
            newFileName, fileName, fileSize, expiryTime, userId
        );

        if (affectedRows == 0) {
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<div class='error'>Failed to save upload information</div>");
            return;
        }

        sendHTMLResponse(OK, "<div class='success'>File uploaded successfully</div>");
    }

    @AccessControl(login = true)
    @Route(endpoint = API_ENDPOINT + "list", method = GET)
    public void listUploads() throws IOException {
        try {
            Mimir db = new Mimir();
            String tokenUserId = Tyr.getUserIdFromJWT(Yggdrasill.RequestHandler.getCurrentToken());
            int dbUserId = db.getRow("SELECT id FROM USER WHERE user = ?", tokenUserId).getInt("id");
            
            // Get filter from query parameter, default to active
            String filter = getQueryParam("filter");
            if (filter == null) {
                filter = "active";
            }
            
            StringBuilder html = new StringBuilder("""
                <table>
                    <tr>
                        <th>File Name</th>
                        <th>Size</th>
                        <th>Expires In</th>
                        <th>Actions</th>
                    </tr>
            """);
            
            String query = "SELECT * FROM UPLOADS WHERE user_id = ? ";
            if (filter.equals("active")) {
                query += "AND expiry_time > ? ";
            } else if (filter.equals("expired")) {
                query += "AND expiry_time <= ? ";
            }
            query += "ORDER BY created_at DESC";
            
            List<Row> uploads = db.getRows(query, dbUserId, new Date(System.currentTimeMillis()));
            
            for (Row upload : uploads) {
                try {
                    String id = upload.getString("id");
                    String originalName = upload.getString("original_name");
                    long fileSize = upload.get("file_size") instanceof Integer ? 
                        ((Integer) upload.get("file_size")).longValue() : 
                        (Long) upload.get("file_size");
                    long expiryTime = upload.get("expiry_time") instanceof Integer ? 
                        ((Integer) upload.get("expiry_time")).longValue() : 
                        (Long) upload.get("expiry_time");
                    Date expiryDate = new Date(expiryTime);
                    long minutesLeft = (expiryDate.getTime() - System.currentTimeMillis()) / (60 * 1000);
                    boolean isExpired = minutesLeft <= 0;
                    
                    html.append("""
                        <tr class="%s">
                            <td>%s</td>
                            <td>%s</td>
                            <td>%s</td>
                            <td>
                                %s
                                <a href="#" hx-get="/upload/download/%s" hx-target="#main" class="action-button download %s">Download</a>
                                <button hx-delete='/api/v1/upload/%s' hx-target='#uploads-list' class="action-button delete">Delete</button>
                            </td>
                        </tr>
                    """.formatted(
                        isExpired ? "expired" : "",
                        originalName,
                        formatFileSize(fileSize),
                        isExpired ? "Expired" : minutesLeft + " minutes",
                        isExpired ? "" : "<a href='/upload/download/" + id + "' target='_blank' class='action-button share'>Share</a>",
                        id,
                        isExpired ? "disabled" : "",
                        id
                    ));
                } catch (Exception e) {
                    System.err.println("Error processing upload row: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            html.append("</table>");
            sendHTMLResponse(OK, html.toString());
        } catch (Exception e) {
            System.err.println("Error in listUploads: " + e.getMessage());
            e.printStackTrace();
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<div class='error'>Error loading uploads list</div>");
        }
    }

    @AccessControl(login = true)
    @Route(endpoint = API_ENDPOINT + ":id", method = DELETE)
    public void deleteUpload() throws IOException {
        String id = getPathParam("id");
        Mimir db = new Mimir();
        String tokenUserId = Tyr.getUserIdFromJWT(Yggdrasill.RequestHandler.getCurrentToken());
        int dbUserId = db.getRow("SELECT id FROM USER WHERE user = ?", tokenUserId).getInt("id");

        Row upload = db.getRow("SELECT * FROM UPLOADS WHERE id = ? AND user_id = ?", id, dbUserId);
        if (upload == null) {
            sendHTMLResponse(NOT_FOUND, "<div class='error'>Upload not found or not authorized</div>");
            return;
        }

        // Delete file
        String fileName = upload.getString("file_name");
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        Files.deleteIfExists(filePath);

        // Delete from database
        db.executeSql("DELETE FROM UPLOADS WHERE id = ? AND user_id = ?", id, dbUserId);

        // Return updated list
        listUploads();
    }

    @Route(endpoint = API_ENDPOINT + "download/:id", method = GET)
    public void handleFileDownload() {
        String id = getPathParam("id");
        Mimir db = new Mimir();
        Row upload = db.getRow("SELECT * FROM UPLOADS WHERE id = ?", id);

        if (upload == null) {
            sendHTMLResponse(NOT_FOUND, "File not found");
            return;
        }

        String fileName = upload.getString("file_name");
        String originalName = upload.getString("original_name");
        long fileSize = upload.get("file_size") instanceof Integer ? 
            ((Integer) upload.get("file_size")).longValue() : 
            (Long) upload.get("file_size");
        long expiryTime = upload.get("expiry_time") instanceof Integer ? 
            ((Integer) upload.get("expiry_time")).longValue() : 
            (Long) upload.get("expiry_time");
        Date expiryDate = new Date(expiryTime);

        if (expiryDate.getTime() < System.currentTimeMillis()) {
            sendHTMLResponse(GONE, "File has expired");
            return;
        }

        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        if (!Files.exists(filePath)) {
            sendHTMLResponse(NOT_FOUND, "File not found");
            return;
        }

        // Set proper download headers
        addCustomHeader("Content-Type", "application/octet-stream");
        addCustomHeader("Content-Disposition", "attachment; filename=\"" + originalName + "\"");
        addCustomHeader("Content-Length", String.valueOf(fileSize));
        
        // Send the file
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            sendHTMLResponse(OK, new String(fileBytes, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            Logger.error("Failed to read file: {}", e.getMessage());
            throw new SendRespondException("Failed to read file", e);
        }
    }

    @Route(endpoint = "/upload/login", method = POST)
    public void loginUser() throws IOException {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        Mimir db = new Mimir();
        Row dbUser = db.getRow("SELECT * FROM USER WHERE user = ?",
                getBodyParam("user"));

        if (dbUser == null || dbUser.get("user").toString().isEmpty()) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div> ");
            return;
        }

        String storedPasswordHash = dbUser.get("password_hash").toString();
        if (!BCrypt.verifyer()
                .verify(getBodyParam("password").toCharArray(), storedPasswordHash)
                .verified) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Credentials are invalid</div>");
            return;
        }

        String token = Tyr.createToken(dbUser.get("user").toString());
        if (token == null) {
            sendHTMLResponse(OK, "<div id=\"login-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/upload/");
        sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Login successful! Redirecting...</div>");
    }

    @Route(endpoint = "/upload/logout", method = GET)
    public void logoutUser() throws IOException {
        addCustomHeader("Set-Cookie", "token=; Max-Age=0; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/upload/");
        sendHTMLResponse(OK, "<div id=\"login-message\" class=\"success\">Logout successful! Redirecting...</div>");
    }

    @Route(endpoint = "/upload/create-account", method = POST)
    public void createUser() throws IOException {
        if (getBodyParam("user") == null || getBodyParam("password") == null) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User / password is missing</div>");
            return;
        }

        String user = getBodyParam("user");
        String hashedPassword = BCrypt.withDefaults()
                .hashToString(12, getBodyParam("password").toCharArray());
        Mimir db = new Mimir();
        int affectedRows = db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)",
                user, hashedPassword);

        if (affectedRows == 0) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">User already exists</div>");
            return;
        }

        String token = Tyr.createToken(user);
        if (token == null) {
            sendHTMLResponse(OK, "<div id=\"register-message\" class=\"error\">Token creation failed</div>");
            return;
        }

        addCustomHeader("Set-Cookie", "token=" + token + "; Max-Age=3600; Path=/; HttpOnly; Secure");
        addCustomHeader("HX-Location", "/upload/");
        sendHTMLResponse(OK, "<div id=\"register-message\" class=\"success\">User created successfully! You will be redirected...</div>");
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
} 