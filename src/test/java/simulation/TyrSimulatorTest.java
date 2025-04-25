package simulation;

import org.junit.jupiter.api.Test;
import org.ruitx.jaws.simulation.SimulationManager;
import org.ruitx.jaws.simulation.TyrSimulator;
import org.tinylog.Logger;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TyrSimulatorTest {
    @Test
    void testBasicSimulation() {
        SimulationManager manager = new SimulationManager();
        manager.registerComponent("tyr", new TyrSimulator());
        
        // Run simulation for 1 hour
        manager.runSimulation(1, TimeUnit.HOURS);
        
        TyrSimulator tyr = (TyrSimulator) manager.getComponent("tyr");
        assertTrue(tyr.getSuccessfulLogins() >= 0);
        assertTrue(tyr.getFailedLogins() >= 0);
    }
    
    @Test
    void testFaultInjection() {
        SimulationManager manager = new SimulationManager();
        TyrSimulator tyr = new TyrSimulator();
        manager.registerComponent("tyr", tyr);
        
        // Create some tokens
        String token1 = tyr.createToken("user1", "password123");
        String token2 = tyr.createToken("user2", "password123");
        assertTrue(tyr.isTokenValid(token1));
        assertTrue(tyr.isTokenValid(token2));
        
        // Inject faults
        tyr.injectFault("INVALID_TOKENS");
        assertFalse(tyr.isTokenValid(token1));
        assertFalse(tyr.isTokenValid(token2));
        
        // Reset
        tyr.injectFault("RESET");
        assertTrue(tyr.isTokenValid(token1));
        assertTrue(tyr.isTokenValid(token2));
    }
    
    @Test
    void testTokenExpiration() {
        SimulationManager manager = new SimulationManager();
        TyrSimulator tyr = new TyrSimulator();
        manager.registerComponent("tyr", tyr);
        
        // Create a token
        String token = tyr.createToken("user1", "password123");
        assertTrue(tyr.isTokenValid(token));
        
        // Simulate time passing (1.5 hours, which is more than the 1 hour expiration)
        manager.runSimulation(90, TimeUnit.MINUTES);
        
        // Token should be expired
        assertFalse(tyr.isTokenValid(token));
        assertEquals(1, tyr.getExpiredTokens());
    }
} 