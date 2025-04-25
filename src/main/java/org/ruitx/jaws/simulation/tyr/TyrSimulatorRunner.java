package org.ruitx.jaws.simulation.tyr;

import org.ruitx.jaws.simulation.SimulationManager;
import org.tinylog.Logger;

import java.util.concurrent.TimeUnit;

public class TyrSimulatorRunner {
    public static void main(String[] args) {
        // Create the simulation manager
        SimulationManager manager = new SimulationManager();
        
        // Register components
        TyrSimulator tyr = new TyrSimulator();
        manager.registerComponent("auth", tyr);
        
        // Set time acceleration (1 second = 1 minute)
        manager.setTimeAccelerationFactor(60.0);
        
        Logger.info("Starting simulation");
        manager.runSimulation(1, TimeUnit.DAYS);
        
        // Create some tokens after the simulation
        Logger.info("Creating test tokens...");
        for (int i = 0; i < 5; i++) {
            String userId = "user" + (i + 1);
            String token = tyr.createToken(userId, "password123");
            if (token != null) {
                Logger.debug("Created token for user {}", userId);
            }
        }
        
        // Print initial state
        Logger.info("Initial State:");
        Logger.info("Successful Logins: {}", tyr.getSuccessfulLogins());
        Logger.info("Failed Logins: {}", tyr.getFailedLogins());
        Logger.info("Expired Tokens: {}", tyr.getExpiredTokens());
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
        
        // Test invalid tokens
        Logger.info("Testing invalid tokens...");
        tyr.injectFault("INVALID_TOKENS");
        Logger.info("After INVALID_TOKENS fault injection:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());

        // Test expired tokens
        Logger.info("Testing expired tokens...");
        tyr.injectFault("EXPIRED_TOKENS");
        Logger.info("After EXPIRED_TOKENS fault injection:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());

        // Test corrupted tokens
        Logger.info("Testing corrupted tokens...");
        tyr.injectFault("CORRUPTED_TOKENS");
        Logger.info("After CORRUPTED_TOKENS fault injection:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());

        // Reset faults and recreate tokens
        Logger.info("Resetting and recreating tokens...");
        tyr.injectFault("RESET");
        for (int i = 0; i < 5; i++) {
            String userId = "user" + (i + 1);
            String token = tyr.createToken(userId, "password123");
            if (token != null) {
                Logger.debug("Created token for user {}", userId);
            }
        }
        Logger.info("After RESET and token recreation:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
    }
} 