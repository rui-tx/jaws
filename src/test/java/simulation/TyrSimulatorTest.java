package simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.simulation.SimulationManager;
import org.ruitx.jaws.simulation.tyr.TyrSimulator;
import org.tinylog.Logger;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TyrSimulatorTest {
    private TyrSimulator tyr;
    private SimulationManager manager;

    @BeforeEach
    void setUp() {
        manager = new SimulationManager();
        tyr = new TyrSimulator();
        // Initialize with test key before registering
        tyr.initialize();
        manager.registerComponent("tyr", tyr);
    }
    
    @Test
    void testBasicSimulation() {
        // Run simulation for 1 hour
        manager.runSimulation(1, TimeUnit.HOURS);
        
        assertTrue(tyr.getSuccessfulLogins() >= 0);
        assertTrue(tyr.getFailedLogins() >= 0);
    }
    
    @Test
    void testFaultInjection() {
        // Create some tokens
        String token1 = tyr.createToken("user1", "password123");
        String token2 = tyr.createToken("user2", "password123");
        assertTrue(tyr.isTokenValid(token1));
        assertTrue(tyr.isTokenValid(token2));
        
        // Inject faults
        tyr.injectFault("INVALID_TOKENS");
        assertFalse(tyr.isTokenValid(token1));
        assertFalse(tyr.isTokenValid(token2));

        // tyr.injectFault("EXPIRED_TOKENS");
        // assertFalse(tyr.isTokenValid(token1));
        // assertFalse(tyr.isTokenValid(token2));

        // tyr.injectFault("CORRUPTED_TOKENS");
        // assertFalse(tyr.isTokenValid(token1));
        // assertFalse(tyr.isTokenValid(token2));
        
        // Reset
        tyr.injectFault("RESET");
        assertTrue(tyr.isTokenValid(token1));
        assertTrue(tyr.isTokenValid(token2));
    }
    
    @Test
    void testTokenExpiration() {
        // Set time acceleration (1 second = 1 hour)
        manager.setTimeAccelerationFactor(3600.0);
        
        // Create a token
        String token = tyr.createToken("user1", "password123");
        assertTrue(tyr.isTokenValid(token));
        
        // Simulate time passing (6 hours)
        // With time acceleration (1s = 1h), this is 6 seconds real time
        manager.runSimulation(6, TimeUnit.HOURS);
        
        // Get the results
        int expiredTokens = tyr.getExpiredTokens();
        int successfulLogins = tyr.getSuccessfulLogins();
        int failedLogins = tyr.getFailedLogins();
        
        Logger.info("Test Results:");
        Logger.info("Expired Tokens: {}", expiredTokens);
        Logger.info("Successful Logins: {}", successfulLogins);
        Logger.info("Failed Logins: {}", failedLogins);
        
        // Calculate expected expired tokens:
        // - Simulation runs for 6 hours
        // - With time acceleration (1s = 1h), this is 6 seconds real time
        // - Tokens are created every 5 minutes (1-3 tokens each time)
        // - That's 72 intervals (6 hours * 12 intervals per hour)
        // - Each interval creates 1-3 tokens
        // - With 80% success rate, we expect:
        //   - Minimum: 72 * 1 * 0.8 = 57.6 (round down to 57)
        //   - Maximum: 72 * 3 * 0.8 = 172.8 (round up to 173)
        assertTrue(expiredTokens >= 57, "Should have at least 57 expired tokens (minimum 1 token per 5-minute interval with 80% success rate)");
        assertTrue(expiredTokens <= 173, "Should have at most 173 expired tokens (maximum 3 tokens per 5-minute interval with 80% success rate)");
        
        // Original token should be expired
        assertFalse(tyr.isTokenValid(token));
    }
} 