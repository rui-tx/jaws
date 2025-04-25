package org.ruitx.jaws.simulation.tyr;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TyrSimulatorTest {
    private TyrSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new TyrSimulator();
        simulator.initialize();
    }

    @Test
    void testTokenCreationAndValidation() {
        String userId = "user1";
        String token = simulator.createToken(userId);
        
        assertNotNull(token, "Token should not be null");
        assertTrue(simulator.isTokenValid(token), "Token should be valid");
        assertEquals(userId, simulator.getUserIdFromToken(token), "User ID should match");
    }

    @Test
    void testTokenExpiration() {
        String userId = "user2";
        String token = simulator.createToken(userId);
        
        // Simulate time passing by 2 hours (more than TOKEN_EXPIRATION_HOURS)
        long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
        simulator.update(futureTime);
        
        assertFalse(simulator.isTokenValid(token), "Token should be expired");
    }

    @Test
    void testInvalidPassword() {
        String userId = "user3";
        String token = simulator.createToken(userId, "wrongpassword");
        
        assertNull(token, "Token should be null for invalid password");
        assertEquals(1, simulator.getFailedLogins(), "Failed logins should increment");
    }

    @Test
    void testValidPassword() {
        String userId = "user4";
        String token = simulator.createToken(userId, "password123");
        
        assertNotNull(token, "Token should be created for valid password");
        assertTrue(simulator.isTokenValid(token), "Token should be valid");
        assertEquals(1, simulator.getSuccessfulLogins(), "Successful logins should increment");
    }

    @Test
    void testFaultInjection() {
        String userId = "user5";
        String token = simulator.createToken(userId);
        
        // Test INVALID_TOKENS fault
        simulator.injectFault("INVALID_TOKENS");
        assertFalse(simulator.isTokenValid(token), "Token should be invalid after fault injection");
        
        // Test EXPIRED_TOKENS fault
        simulator.injectFault("RESET"); // Reset first
        token = simulator.createToken(userId);
        simulator.injectFault("EXPIRED_TOKENS");
        assertFalse(simulator.isTokenValid(token), "Token should be expired after fault injection");
        
        // Test CORRUPTED_TOKENS fault - this fault type corrupts the user ID in the token
        simulator.injectFault("RESET"); // Reset first
        token = simulator.createToken(userId);
        simulator.injectFault("CORRUPTED_TOKENS");
        // The corrupted token should be invalid because the user ID is corrupted
        assertFalse(simulator.isTokenValid(token), "Token should be invalid after corruption");
    }

    @Test
    void testTokenStatistics() {
        // Create multiple tokens with different outcomes
        simulator.createToken("user1", "password123"); // Success
        simulator.createToken("user2", "wrongpassword"); // Fail
        simulator.createToken("user3", "password123"); // Success
        simulator.createToken("user4"); // Success (no password)
        
        // Let some tokens expire
        long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
        simulator.update(futureTime);
        
        // Note: Each successful token creation counts as a successful login
        // The update method creates random tokens, but they don't count towards successful logins
        // because they're created internally
        assertEquals(3, simulator.getSuccessfulLogins(), "Should have 3 successful logins (2 from password, 1 from no password)");
        assertEquals(1, simulator.getFailedLogins(), "Should have 1 failed login");
        assertTrue(simulator.getExpiredTokens() > 0, "Should have some expired tokens");
    }

    @Test
    void testTokenClaims() {
        String userId = "user1";
        String token = simulator.createToken(userId);
        
        // Verify token structure through validation
        assertTrue(simulator.isTokenValid(token), "Token should be valid");
        assertEquals(userId, simulator.getUserIdFromToken(token), "User ID should match");
    }

    @Test
    void testCleanup() {
        // Create some tokens
        simulator.createToken("user1");
        simulator.createToken("user2");
        
        simulator.cleanup();
        
        assertEquals(0, simulator.getValidTokensCount(), "All tokens should be cleared");
        assertEquals(0, simulator.getExpiredTokens(), "Expired tokens should be reset");
    }

    @Test
    void testVerifyState() {
        // Create a valid token
        simulator.createToken("user1");
        
        // Should not throw exception for valid state
        assertDoesNotThrow(() -> simulator.verifyState());
        
        // Create a token and force it to expire
        simulator.createToken("user2");
        simulator.injectFault("EXPIRED_TOKENS");
        
        // Should throw exception for invalid state
        assertThrows(IllegalStateException.class, () -> simulator.verifyState());
    }
} 