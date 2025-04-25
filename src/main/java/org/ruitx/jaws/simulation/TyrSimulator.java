package org.ruitx.jaws.simulation;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.ruitx.jaws.components.Tyr;
import org.tinylog.Logger;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

/**
 * Simulator for the Tyr component.
 * This version allows for deterministic behavior and fault injection.
 */
public class TyrSimulator implements SimulationParticipant {
    private static final long TOKEN_EXPIRATION_HOURS = 1;
    private Key signingKey = null;
    private static final Random RANDOM = new Random();
    private static final String[] USERS = {"user1", "user2", "user3", "user4", "user5"};
    private static final String VALID_PASSWORD = "password123";
    private static final String DEFAULT_SECRET = "test-secret-key-for-simulation-1234567890";
    
    // Simulation state
    private final Map<String, String> validTokens = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenExpirations = new ConcurrentHashMap<>();
    private int successfulLogins = 0;
    private int failedLogins = 0;
    private int expiredTokens = 0;
    private boolean isFaulty = false;
    private long lastTokenCreationTime = 0;
    private static final long TOKEN_CREATION_INTERVAL = TimeUnit.MINUTES.toMillis(5); // Create tokens every 5 minutes
    private static final double SUCCESS_RATE = 0.8;
    
    @Override
    public void initialize() {
        try {
            String secret = JWT_SECRET != null ? JWT_SECRET : DEFAULT_SECRET;
            signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        } catch (Exception e) {
            Logger.error("Failed to initialize signing key: " + e.getMessage());
            throw new RuntimeException("Failed to initialize signing key", e);
        }
        
        validTokens.clear();
        tokenExpirations.clear();
        successfulLogins = 0;
        failedLogins = 0;
        expiredTokens = 0;
        isFaulty = false;
        lastTokenCreationTime = 0;
        Logger.info("Initialized TyrSimulator");
    }
    
    @Override
    public void update(long currentTimeMillis) {
        Logger.debug("Checking for expired tokens at time {}", currentTimeMillis);
        
        // Check for expired tokens
        int expiredCount = 0;
        for (Map.Entry<String, Long> entry : new HashMap<>(tokenExpirations).entrySet()) {
            if (entry.getValue() <= currentTimeMillis) {
                String token = entry.getKey();
                validTokens.remove(token);
                tokenExpirations.remove(token);
                expiredCount++;
                Logger.debug("Token {} expired at time {} (expiration was {})", 
                    token, currentTimeMillis, entry.getValue());
            }
        }
        if (expiredCount > 0) {
            expiredTokens += expiredCount;
            Logger.debug("Expired {} tokens at time {}, total expired: {}", 
                expiredCount, currentTimeMillis, expiredTokens);
        }
        
        // Create new tokens periodically
        if (currentTimeMillis - lastTokenCreationTime >= TOKEN_CREATION_INTERVAL) {
            createRandomTokens();
            lastTokenCreationTime = currentTimeMillis;
        }
    }
    
    private void createRandomTokens() {
        // Create 1-3 random tokens
        int numTokens = RANDOM.nextInt(3) + 1;
        for (int i = 0; i < numTokens; i++) {
            String userId = USERS[RANDOM.nextInt(USERS.length)];
            // 90% chance of successful login
            if (RANDOM.nextDouble() < SUCCESS_RATE) {
                createToken(userId, VALID_PASSWORD);
            } else {
                createToken(userId, "wrongpassword");
            }
        }
    }
    
    @Override
    public void verifyState() {
        // Verify that all valid tokens have future expiration times
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : tokenExpirations.entrySet()) {
            if (entry.getValue() <= currentTime) {
                throw new IllegalStateException(
                    "Token " + entry.getKey() + " is expired but still marked as valid");
            }
        }
    }
    
    @Override
    public void cleanup() {
        validTokens.clear();
        tokenExpirations.clear();
        Logger.info("Cleaned up TyrSimulator");
    }
    
    public String createToken(String userId) {
        return createToken(userId, null);
    }
    
    public String createToken(String userId, String password) {
        if (signingKey == null || isFaulty) {
            Logger.error("Signing key not initialized or system is faulty");
            return null;
        }
        
        // If password is provided, simulate authentication
        if (password != null && !password.equals(VALID_PASSWORD)) {
            failedLogins++;
            return null;
        }
        
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        Instant expiration = now.plus(TOKEN_EXPIRATION_HOURS, ChronoUnit.HOURS);
        Logger.debug("Creating token for user {} with expiration {}", userId, expiration);
        
        String token = Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
        
        validTokens.put(token, userId);
        tokenExpirations.put(token, expiration.toEpochMilli());
        successfulLogins++;
        Logger.debug("Created token {} with expiration {}", token, expiration);
        
        return token;
    }
    
    public boolean isTokenValid(String token) {
        if (signingKey == null || isFaulty) {
            Logger.error("Signing key not initialized or system is faulty");
            return false;
        }
        
        // First check if the token is in our valid tokens map
        if (!validTokens.containsKey(token)) {
            return false;
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Check if token is expired
            boolean isValid = claims.getExpiration().after(Date.from(Instant.now()));
            if (!isValid) {
                // If token is expired, remove it from our maps and increment counter
                validTokens.remove(token);
                tokenExpirations.remove(token);
                expiredTokens++;
            }
            return isValid;
        } catch (JwtException e) {
            Logger.error("Error validating token: " + e.getMessage());
            failedLogins++;
            return false;
        }
    }
    
    public String getUserIdFromToken(String token) {
        if (signingKey == null) {
            Logger.error("Signing key not initialized");
            return "";
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) signingKey)
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
    
    public void injectFault(String faultType) {
        switch (faultType) {
            case "INVALID_TOKENS":
                isFaulty = true;
                break;
            case "EXPIRED_TOKENS":
                // Set all tokens to expire immediately
                tokenExpirations.replaceAll((k, v) -> System.currentTimeMillis());
                // Remove all tokens and count them as expired
                int expiredCount = validTokens.size();
                validTokens.clear();
                tokenExpirations.clear();
                expiredTokens += expiredCount;
                break;
            case "CORRUPTED_TOKENS":
                validTokens.replaceAll((k, v) -> k + "_corrupted");
                break;
            case "RESET":
                isFaulty = false;
                break;
        }
    }
    
    public int getSuccessfulLogins() {
        return successfulLogins;
    }
    
    public int getFailedLogins() {
        return failedLogins;
    }
    
    public int getExpiredTokens() {
        return expiredTokens;
    }
    
    public int getValidTokensCount() {
        return validTokens.size();
    }
} 