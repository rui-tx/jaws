package org.ruitx.jaws.components;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.www.model.auth.UserSession;

/**
 * Tyr is a utility class for handling JWT token creation, validation, and refresh operations. It
 * provides methods to create access and refresh tokens, validate them, and manage user sessions.
 */
public class Tyr {

  private static final long ACCESS_TOKEN_EXPIRATION = 6 * 60 * 60L; // 6 hours in seconds
  private static final long REFRESH_TOKEN_EXPIRATION = 30 * 24 * 60 * 60L; // 30 days in seconds

  /**
   * Creates a new access and refresh token pair for the given user.
   *
   * @param userId    the ID of the user.
   * @param userRoles the roles of the user.
   * @param userAgent the user agent string of the client.
   * @param ipAddress the IP address of the client.
   * @return a TokenPair containing the access and refresh tokens.
   */
  public static TokenPair createTokenPair(String userId, List<String> userRoles, String userAgent,
      String ipAddress) {
    Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    long now = Instant.now().getEpochSecond();

    String accessToken = Jwts.builder()
        .issuer(APPLICATION_NAME)
        .subject(userId)
        .claim("roles", userRoles)  // Include roles in JWT claims
        .issuedAt(Date.from(Instant.ofEpochSecond(now)))
        .expiration(Date.from(Instant.ofEpochSecond(now + ACCESS_TOKEN_EXPIRATION)))
        .signWith(key)
        .compact();

    String refreshToken = Jwts.builder()
        .issuer(APPLICATION_NAME)
        .subject(userId)
        .claim("roles", userRoles)  // Include roles in refresh token too
        .issuedAt(Date.from(Instant.ofEpochSecond(now)))
        .expiration(Date.from(Instant.ofEpochSecond(now + REFRESH_TOKEN_EXPIRATION)))
        .signWith(key)
        .compact();

    Mimir db = Odin.getMimir("db");
    db.execute("""
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

  /**
   * Refreshes the access token using the provided refresh token.
   *
   * @param refreshToken the refresh token to validate and use for generating a new access token.
   * @param userAgent    the user agent string of the client.
   * @param ipAddress    the IP address of the client.
   * @return an Optional containing a TokenPair with new access and refresh tokens, or empty if
   * validation fails.
   */
  public static Optional<TokenPair> refreshToken(String refreshToken, String userAgent,
      String ipAddress) {
    SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

    try {
      // Verify refresh token
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(refreshToken)
          .getPayload();

      // Get session from database
      Mimir db = Odin.getMimir("db");
      Row sessionRow = db.getRow(
          "SELECT * FROM USER_SESSION WHERE refresh_token = ? AND is_active = 1",
          refreshToken
      ).get();

      Optional<UserSession> session = UserSession.fromRow(sessionRow);
      if (session.isEmpty()) {
        return Optional.empty();
      }

      // Verify user agent and IP if they match the original session
      UserSession s = session.get();
      if (!s.userAgent().equals(userAgent) || !s.ipAddress().equals(ipAddress)) {
        // Potential security breach - invalidate session
        db.execute(
            "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
            refreshToken
        );
        return Optional.empty();
      }

      // Create new token pair
      String userId = claims.getSubject();
      List<String> userRoles = getUserRolesFromJWT(refreshToken);
      TokenPair newTokens = createTokenPair(userId, userRoles, userAgent, ipAddress);

      // Invalidate old session
      db.execute(
          "UPDATE USER_SESSION SET is_active = 0 WHERE refresh_token = ?",
          refreshToken
      );

      return Optional.of(newTokens);

    } catch (JwtException e) {
      JawsLogger.debug("Error validating refresh token: " + e);
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
      JawsLogger.debug("Error validating: " + e);
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

  /**
   * Extracts the user ID from the provided JWT token.
   *
   * @param token the JWT token.
   * @return the user ID, or an empty string if extraction fails.
   */
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
      JawsLogger.debug("Error validating: " + e);
      return "";
    }

    return userId == null ? "" : userId;
  }

  /**
   * Extracts user roles from the provided JWT token.
   *
   * @param token the JWT token.
   * @return a list of user roles, or an empty list if extraction fails.
   */
  public static List<String> getUserRolesFromJWT(String token) {
    SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    try {
      Object rolesObj = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload().get("roles");

      if (rolesObj instanceof List<?>) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) rolesObj;
        return roles != null ? roles : new ArrayList<>();
      }

      // Handle legacy single role claim or null
      return new ArrayList<>();

    } catch (JwtException e) {
      JawsLogger.error("Error validating: " + e);
      return new ArrayList<>();
    }
  }

  /**
   * Represents a pair of tokens: access token and refresh token.
   *
   * @param accessToken  the access token
   * @param refreshToken the refresh token
   */
  public record TokenPair(String accessToken, String refreshToken) {

  }
}
