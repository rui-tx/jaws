package org.ruitx.server.components;

import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.tinylog.Logger;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;

import static org.ruitx.server.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.server.configs.ApplicationConfig.JWT_SECRET;

public class Tyr {

    /**
     * Create a JWT token with claims (e.g., user ID, roles, etc.).
     *
     * @param userId   the user ID.
     * @param password the password.
     * @return the JWT token.
     */
    public static String createToken(String userId, String password) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

        // TODO: check if credentials are valid
        
        return Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .signWith(key)
                .compact();
    }

    /**
     * Checks if the provided token is valid.
     *
     * @param token the token to check.
     * @return true if the token is valid, false otherwise.
     */
    public static boolean isTokenValid(String token) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        Jwt<?, ?> jwt;
        try {
            jwt = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parse(token);

        } catch (JwtException e) {
            Logger.error("Error validating: " + e);
            return false;
        }
        return true;
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
}


