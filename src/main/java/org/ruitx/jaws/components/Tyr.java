package org.ruitx.jaws.components;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.ruitx.jaws.utils.Row;
import org.tinylog.Logger;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

public class Tyr {
    private static final long TOKEN_EXPIRATION_HOURS = 1;
    private static Key SIGNING_KEY = null;
    
    static {
        try {
            SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        } catch (Exception e) {
            Logger.error("Failed to initialize signing key: " + e.getMessage());
            // We'll handle this in the methods that use the key
        }
    }
    
    /**
     * Create a JWT token with claims (e.g., user ID, roles, etc.).
     * This method WILL check if the user exists in the database.
     *
     * @param userId   the user ID.
     * @param password the password.
     * @return the JWT token.
     */
    public static String createToken(String userId, String password) {
        Mimir db = new Mimir();
        Row user = db.getRow("SELECT * FROM USER WHERE user = ? AND password_hash = ?",
                userId, password);
        if (user == null || user.get("user").toString().isEmpty()) {
            return null;
        }

        return createTokenWithExpiration(userId);
    }

    /**
     * Create a JWT token with claims (e.g., user ID, roles, etc.).
     * This method WILL NOT check if the user exists in the database.
     * It is intended for use cases where the user is already known.
     *
     * @param userId the user ID.
     * @return the JWT token.
     */
    public static String createToken(String userId) {
        return createTokenWithExpiration(userId);
    }

    /**
     * Checks if the provided token is valid.
     *
     * @param token the token to check.
     * @return true if the token is valid, false otherwise.
     */
    public static boolean isTokenValid(String token) {
        if (SIGNING_KEY == null) {
            Logger.error("Signing key not initialized");
            return false;
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) SIGNING_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Check if token is expired
            return claims.getExpiration().after(Date.from(Instant.now()));
        } catch (JwtException e) {
            Logger.error("Error validating token: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a new secret key for JWT tokens.
     *
     * @return the new secret key (Base64 encoded).
     */
    public static String createSecreteKey() {
        // Create a secure random 256-bit key (32 bytes)
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32]; // 256 bits = 32 bytes
        random.nextBytes(key);

        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Gets the user ID from a JWT token.
     *
     * @param token the JWT token.
     * @return the user ID, or empty string if token is invalid.
     */
    public static String getUserIdFromJWT(String token) {
        if (SIGNING_KEY == null) {
            Logger.error("Signing key not initialized");
            return "";
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) SIGNING_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userId = claims.getSubject();
            return userId != null ? userId : "";
        } catch (JwtException e) {
            Logger.error("Error getting user ID from token: " + e.getMessage());
            return "";
        }
    }

    /**
     * Gets the user role from a JWT token.
     *
     * @param token the JWT token.
     * @return the user role, or empty string if token is invalid or role not found.
     */
    public static String getUserRoleFromJWT(String token) {
        if (SIGNING_KEY == null) {
            Logger.error("Signing key not initialized");
            return "";
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) SIGNING_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Object role = claims.get("role");
            return role != null ? role.toString() : "";
        } catch (JwtException e) {
            Logger.error("Error getting user role from token: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Creates a JWT token with expiration time.
     *
     * @param userId the user ID.
     * @return the JWT token.
     */
    private static String createTokenWithExpiration(String userId) {
        if (SIGNING_KEY == null) {
            Logger.error("Signing key not initialized");
            return null;
        }
        
        Instant now = Instant.now();
        Instant expiration = now.plus(TOKEN_EXPIRATION_HOURS, ChronoUnit.HOURS);
        
        return Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(SIGNING_KEY)
                .compact();
    }
}


