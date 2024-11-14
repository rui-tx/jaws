package unit;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Mimir;
import org.ruitx.server.components.Tyr;
import org.tinylog.Logger;

import java.security.Key;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.ruitx.server.configs.ApplicationConfig.*;

public class TyrUnitTests {

    private Mimir db;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Mimir();
        db.initializeDatabase(DEFAULT_DATABASE_TESTS_PATH);
        String username = "testuser123";
        String password = "testpassword";
        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        db.executeSql("INSERT INTO USER (user, password_hash) VALUES (?, ?)", username, hashedPassword);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.executeSql("DROP TABLE IF EXISTS USER");
        db.deleteDatabase();
    }

    // The way Tyr is made right now, I cant really test it with integration tests
    // because it's a static class, but I'm not sure if that's the best way to do it
    @Disabled
    @Test
    public void givenValidUserIdAndPassword_whenCreateToken_thenReturnsValidToken() {
        String userId = "testuser123";
        String password = "testpassword";

        String token = Tyr.createToken(userId, password);
        Logger.info("Token: " + token);

        assertNotEquals(token, null, "The method should return a valid token for valid credentials.");
    }

    @Test
    public void givenValidToken_whenGetUserIdFromJWT_thenReturnsCorrectUserId() {
        // Given
        String userId = "testuser123";
        String role = "admin";
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String token = Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .claim("role", role)
                .signWith(key)
                .compact();

        // When
        String extractedUserId = Tyr.getUserIdFromJWT(token);  // Call the method

        // Then
        assertEquals(userId, extractedUserId, "The extracted user ID should match the one in the token.");
    }

    @Test
    public void givenInvalidToken_whenGetUserIdFromJWT_thenReturnsEmpty() {
        String invalidToken = "invalid.token";
        String extractedUserId = Tyr.getUserIdFromJWT(invalidToken);  // Call the method
        assertEquals("", extractedUserId, "The method should return an empty string for invalid tokens.");
    }

    @Test
    public void givenValidToken_whenGetUserRoleFromJWT_thenReturnsCorrectUserRole() {
        String userId = "testuser123";
        String role = "admin";
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String token = Jwts.builder()
                .issuer(APPLICATION_NAME)
                .subject(userId)
                .claim("role", role)
                .signWith(key)
                .compact();
        String extractedRole = Tyr.getUserRoleFromJWT(token);  // Call the method
        assertEquals(role, extractedRole, "The extracted role should match the one in the token.");
    }

    @Test
    public void givenInvalidToken_whenGetUserRoleFromJWT_thenReturnsEmpty() {
        String invalidToken = "invalid.token";
        String extractedRole = Tyr.getUserRoleFromJWT(invalidToken);  // Call the method
        assertEquals("", extractedRole, "The method should return an empty string for invalid tokens.");
    }
}
