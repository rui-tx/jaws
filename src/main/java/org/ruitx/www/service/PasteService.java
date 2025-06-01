package org.ruitx.www.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.PasteCreateRequest;
import org.ruitx.www.model.Paste;
import org.ruitx.www.repository.PasteRepo;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

public class PasteService {

    private final PasteRepo pasteRepo;
    private final SecureRandom random;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int DEFAULT_ID_LENGTH = 8;

    public PasteService() {
        this.pasteRepo = new PasteRepo();
        this.random = new SecureRandom();
    }

    public APIResponse<String> createPaste(PasteCreateRequest request, Integer userId, String ipAddress, String userAgent) {
        // Note: Basic validation is now handled by Jakarta Bean Validation annotations on the DTO
        // Only business-specific validation remains here
        
        // Generate unique ID
        String pasteId = generateUniqueId();
        
        // Calculate expiration
        Long expiresAt = null;
        if (request.expiresInHours() != null && request.expiresInHours() > 0) {
            long hoursInSeconds = request.expiresInHours() * 3600L;
            expiresAt = Instant.now().getEpochSecond() + hoursInSeconds;
        }

        // Hash password if provided
        String passwordHash = null;
        if (request.password() != null && !request.password().trim().isEmpty()) {
            passwordHash = BCrypt.withDefaults().hashToString(12, request.password().toCharArray());
        }

        try {
            Optional<String> result = pasteRepo.createPaste(
                    pasteId,
                    request.content().trim(),
                    request.title() != null ? request.title().trim() : null,
                    request.language() != null ? request.language().trim() : null,
                    expiresAt,
                    request.isPrivate(),
                    passwordHash,
                    userId,
                    ipAddress,
                    userAgent
            );

            if (result.isPresent()) {
                return APIResponse.success(ResponseCode.CREATED.getCodeAndMessage(), "Paste created successfully", result.get());
            } else {
                return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Failed to create paste");
            }
        } catch (Exception e) {
            return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Database error: " + e.getMessage());
        }
    }

    public APIResponse<Paste> getPaste(String id, String password) {
        if (id == null || id.trim().isEmpty()) {
            return APIResponse.error(ResponseCode.BAD_REQUEST.getCodeAndMessage(), "Invalid paste ID");
        }

        try {
            Optional<Paste> pasteOpt = pasteRepo.getPasteById(id.trim());
            
            if (pasteOpt.isEmpty()) {
                return APIResponse.error(ResponseCode.NOT_FOUND.getCodeAndMessage(), "Paste not found");
            }

            Paste paste = pasteOpt.get();

            // Check if expired
            if (paste.isExpired()) {
                return APIResponse.error(ResponseCode.GONE.getCodeAndMessage(), "Paste has expired");
            }

            // Check password protection
            if (paste.hasPassword()) {
                if (password == null || password.trim().isEmpty()) {
                    return APIResponse.error(ResponseCode.UNAUTHORIZED.getCodeAndMessage(), "Password required");
                }
                
                boolean passwordValid = BCrypt.verifyer()
                        .verify(password.toCharArray(), paste.passwordHash())
                        .verified;
                
                if (!passwordValid) {
                    return APIResponse.error(ResponseCode.UNAUTHORIZED.getCodeAndMessage(), "Invalid password");
                }
            }

            // Increment view count
            pasteRepo.incrementViewCount(id);

            return APIResponse.success(ResponseCode.OK.getCodeAndMessage(), "Paste retrieved successfully", paste);
        } catch (Exception e) {
            return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Database error: " + e.getMessage());
        }
    }

    private String generateUniqueId() {
        String id;
        int attempts = 0;
        final int maxAttempts = 10;
        
        do {
            id = generateRandomId(DEFAULT_ID_LENGTH);
            attempts++;
        } while (pasteRepo.getPasteById(id).isPresent() && attempts < maxAttempts);
        
        if (attempts >= maxAttempts) {
            // Fallback to longer ID if we can't find a unique short one
            id = generateRandomId(DEFAULT_ID_LENGTH + 2);
        }
        
        return id;
    }

    private String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }

    public void cleanExpiredPastes() {
        pasteRepo.cleanExpiredPastes();
    }
} 