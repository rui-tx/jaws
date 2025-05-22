package org.ruitx.jaws.components;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.auth.UserSession;
import org.tinylog.Logger;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

public class Tyr {
    private static final long ACCESS_TOKEN_EXPIRATION = 6 * 60 * 60L; // 6 hours in seconds
    private static final long REFRESH_TOKEN_EXPIRATION = 30 * 24 * 60 * 60L; // 30 days in seconds

    public static TokenPair createTokenPair(String userId, String userAgent, String ipAddress) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        long now = Instant.now().getEpochSecond();

        String accessToken = Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .issuedAt(Date.from(Instant.ofEpochSecond(now)))
                .expiration(Date.from(Instant.ofEpochSecond(now + ACCESS_TOKEN_EXPIRATION)))
                .signWith(key)
                .compact();

        String refreshToken = Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .issuedAt(Date.from(Instant.ofEpochSecond(now)))
                .expiration(Date.from(Instant.ofEpochSecond(now + REFRESH_TOKEN_EXPIRATION)))
                .signWith(key)
                .compact();

        Mimir db = new Mimir();
        db.executeSql("""
                        INSERT INTO USER_SESSION (
                            user_id, refresh_token, access_token, user_agent, ip_address, 
                            created_at, expires_at, last_used_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Integer.parseInt(userId),
                refreshToken,
                accessToken,
                userAgent,
                ipAddress,
                now,
                now + REFRESH_TOKEN_EXPIRATION,
                now
        );

        return new TokenPair(accessToken, refreshToken);
    }

    public static Optional<TokenPair> refreshToken(String refreshToken, String userAgent, String ipAddress) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

        try {
            // Verify refresh token
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();

            // Get session from database
            Mimir db = new Mimir();
            Row sessionRow = db.getRow(
                    "SELECT * FROM USER_SESSION WHERE refresh_token = ? AND is_active = 1",
                    refreshToken
            );

            Optional<UserSession> session = UserSession.fromRow(sessionRow);
            if (session.isEmpty()) {
                return Optional.empty();
            }

            // Verify user agent and IP if they match the original session
            UserSession s = session.get();
            if (!s.userAgent().equals(userAgent) || !s.ipAddress().equals(ipAddress)) {
                // Potential security breach - invalidate session
                db.executeSql(
                        "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
                        refreshToken
                );
                return Optional.empty();
            }

            // Create new token pair
            String userId = claims.getSubject();
            TokenPair newTokens = createTokenPair(userId, userAgent, ipAddress);

            // Invalidate old session
            db.executeSql(
                    "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
                    refreshToken
            );

            return Optional.of(newTokens);

        } catch (JwtException e) {
            Logger.error("Error validating refresh token: " + e);
            return Optional.empty();
        }
    }

    /**
     * Checks if the provided token is valid.
     *
     * @param token the token to check.
     * @return true if the token is valid, false otherwise.
     */
    public static boolean isTokenValid(String token) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        try {
            Jwts.parser()
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

    public static String getUserIdFromJWT(String token) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String userId;
        try {
            userId = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload().getSubject();


        } catch (JwtException e) {
            Logger.error("Error validating: " + e);
            return "";
        }

        return userId == null ? "" : userId;
    }

    public static String getUserRoleFromJWT(String token) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        Object userRole;
        try {
            userRole = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload().get("role");


        } catch (JwtException e) {
            Logger.error("Error validating: " + e);
            return "";
        }

        return userRole == null ? "" : userRole.toString();
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}


