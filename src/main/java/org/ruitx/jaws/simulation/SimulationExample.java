package org.ruitx.jaws.simulation;

import org.tinylog.Logger;

import java.util.concurrent.TimeUnit;

public class SimulationExample {
    public static void main(String[] args) {
        // Create the simulation manager
        SimulationManager manager = new SimulationManager();
        
        // Register components
        TyrSimulator tyr = new TyrSimulator();
        manager.registerComponent("auth", tyr);
        
        // Set time acceleration (1 second = 1 hour)
        manager.setTimeAccelerationFactor(3600.0);
        
        // Run simulation for 24 hours
        Logger.info("Starting simulation for 24 hours...");
        manager.runSimulation(24, TimeUnit.HOURS);
        
        // Print results
        Logger.info("Simulation Results:");
        Logger.info("Successful Logins: {}", tyr.getSuccessfulLogins());
        Logger.info("Failed Logins: {}", tyr.getFailedLogins());
        Logger.info("Expired Tokens: {}", tyr.getExpiredTokens());
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
        
        // Test fault injection
        Logger.info("Testing fault injection...");
        tyr.injectFault("INVALID_TOKENS");
        Logger.info("After INVALID_TOKENS fault injection:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
        
        // Reset faults
        tyr.injectFault("RESET");
        Logger.info("After RESET:");
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
    }
} 