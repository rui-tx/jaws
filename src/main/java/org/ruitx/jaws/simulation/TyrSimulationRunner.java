package org.ruitx.jaws.simulation;

import org.ruitx.jaws.components.Urd;
import org.ruitx.jaws.components.Urd.SimulationStep;
import org.tinylog.Logger;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

/**
 * TyrSimulationRunner provides and runs simulations for the Tyr component.
 */
public class TyrSimulationRunner {
    private final Urd urd;

    public TyrSimulationRunner() {
        this.urd = Urd.getInstance();
    }

    /**
     * Creates a simulation-aware Clock for JWT operations
     */
    private Clock createSimulationClock() {
        return () -> new Date(urd.getSimulationTimeMillis());
    }

    /**
     * Creates a simulation that tests token expiration.
     * @return A list of simulation steps for Tyr token expiration testing
     */
    private List<Urd.SimulationStep> createTokenExpirationSimulation() {
        List<Urd.SimulationStep> steps = new ArrayList<>();
        
        // Step 1: Create a token with a short expiration time (10 seconds)
        steps.add(new Urd.SimulationStep("Create token with expiration", state -> {
            final String userId = "testUser";
            final Clock simulationClock = createSimulationClock();
            
            // Create a token with expiration based on simulation time
            String token = Jwts.builder()
                    .issuer("JAWS")
                    .subject(userId)
                    .issuedAt(simulationClock.now())
                    .expiration(new Date(urd.getSimulationTimeMillis() + 10000)) // 10 seconds from simulation now
                    .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                    .compact();
            
            state.put("token", token);
            state.put("simulationClock", simulationClock);
            Logger.info("Created token with 10 second expiration at simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 2: Verify token is valid immediately after creation
        steps.add(new Urd.SimulationStep("Verify token is valid", state -> {
            String token = (String) state.get("token");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            // Custom validation using simulation clock
            boolean isValid = verifyTokenWithSimulationClock(token, simulationClock);
            
            Logger.info("Token valid at simulation time {}ms: {}", urd.getSimulationTimeMillis(), isValid);
            if (!isValid) {
                Logger.error("Token should be valid immediately after creation!");
            }
            state.put("initialValidation", isValid);
            return null;
        }));

        // Step 3: Extract data from token
        steps.add(new Urd.SimulationStep("Extract data from token", state -> {
            String token = (String) state.get("token");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            // Use a custom method to extract user ID with simulation clock
            String userId = extractUserIdWithSimulationClock(token, simulationClock);
            
            Logger.info("Extracted user ID from token: {}", userId);
            return null;
        }));

        // Step 4: Wait for 5 seconds (token still valid)
        steps.add(new Urd.SimulationStep("Wait 5 seconds", state -> {
            urd.advanceTime(5000); // Advance simulation time by 5 seconds
            Logger.info("Waited 5 seconds, current simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 5: Verify token is still valid after 5 seconds
        steps.add(new Urd.SimulationStep("Verify token is still valid", state -> {
            String token = (String) state.get("token");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            boolean isValid = verifyTokenWithSimulationClock(token, simulationClock);
            
            Logger.info("Token valid after 5 seconds at simulation time {}ms: {}", urd.getSimulationTimeMillis(), isValid);
            if (!isValid) {
                Logger.error("Token should still be valid after 5 seconds!");
            }
            return null;
        }));

        // Step 6: Wait for 6 more seconds (token should expire)
        steps.add(new Urd.SimulationStep("Wait 6 more seconds", state -> {
            urd.advanceTime(6000); // Advance simulation time by 6 seconds
            Logger.info("Waited 6 more seconds, current simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 7: Verify token is invalid after expiration
        steps.add(new Urd.SimulationStep("Verify token is invalid after expiration", state -> {
            String token = (String) state.get("token");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            boolean isValid = verifyTokenWithSimulationClock(token, simulationClock);
            
            Logger.info("Token valid after expiration at simulation time {}ms: {}", urd.getSimulationTimeMillis(), isValid);
            if (isValid) {
                Logger.error("Token should be invalid after expiration!");
            }
            return null;
        }));

        return steps;
    }

    /**
     * Verify a token using a simulation clock instead of system time
     */
    private boolean verifyTokenWithSimulationClock(String token, Clock clock) {
        try {
            Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .clock(clock) // Use simulation time
                .build()
                .parse(token);
            return true;
        } catch (Exception e) {
            Logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract user ID from token using simulation clock
     */
    private String extractUserIdWithSimulationClock(String token, Clock clock) {
        try {
            return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .clock(clock) // Use simulation time
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (Exception e) {
            Logger.debug("Failed to extract user ID: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Creates a simulation that tests token creation and validation over a long time period.
     * @return A list of simulation steps for long-term token testing
     */
    private List<Urd.SimulationStep> createLongTermTokenSimulation() {
        List<Urd.SimulationStep> steps = new ArrayList<>();
        
        // Step 1: Create a token with a long expiration time (1 year)
        steps.add(new Urd.SimulationStep("Create token with 1 year expiration", state -> {
            final String userId = "longTermUser";
            final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;
            final Clock simulationClock = createSimulationClock();
            
            // Create a token with long expiration based on simulation time
            String token = Jwts.builder()
                    .issuer("JAWS")
                    .subject(userId)
                    .issuedAt(simulationClock.now())
                    .expiration(new Date(urd.getSimulationTimeMillis() + ONE_YEAR_MS))
                    .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                    .compact();
            
            state.put("longToken", token);
            state.put("simulationClock", simulationClock);
            Logger.info("Created token with 1 year expiration at simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 2: Advance time by 6 months
        steps.add(new Urd.SimulationStep("Advance time by 6 months", state -> {
            final long SIX_MONTHS_MS = 182L * 24 * 60 * 60 * 1000;
            urd.advanceTime(SIX_MONTHS_MS);
            Logger.info("Advanced time by 6 months, current simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 3: Verify token is still valid after 6 months
        steps.add(new Urd.SimulationStep("Verify token after 6 months", state -> {
            String token = (String) state.get("longToken");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            boolean isValid = verifyTokenWithSimulationClock(token, simulationClock);
            
            Logger.info("Token valid after 6 months at simulation time {}ms: {}", urd.getSimulationTimeMillis(), isValid);
            if (!isValid) {
                Logger.error("Long-term token should still be valid after 6 months!");
            }
            return null;
        }));

        // Step 4: Advance time by another 7 months (token should expire)
        steps.add(new Urd.SimulationStep("Advance time by 7 more months", state -> {
            final long SEVEN_MONTHS_MS = 213L * 24 * 60 * 60 * 1000;
            urd.advanceTime(SEVEN_MONTHS_MS);
            Logger.info("Advanced time by 7 more months, current simulation time: {}ms", urd.getSimulationTimeMillis());
            return null;
        }));

        // Step 5: Verify token is invalid after 13 months
        steps.add(new Urd.SimulationStep("Verify token after 13 months", state -> {
            String token = (String) state.get("longToken");
            Clock simulationClock = (Clock) state.get("simulationClock");
            
            boolean isValid = verifyTokenWithSimulationClock(token, simulationClock);
            
            Logger.info("Token valid after 13 months at simulation time {}ms: {}", urd.getSimulationTimeMillis(), isValid);
            if (isValid) {
                Logger.error("Long-term token should be invalid after 13 months!");
            }
            return null;
        }));

        return steps;
    }

    /**
     * Runs all Tyr simulations with different time scales.
     */
    public void runAllSimulations() {
        Logger.info("Starting Tyr simulation runner");
        
        // Run token expiration simulation at 1000x speed
        urd.resetTime();
        urd.setTimeScale(1000); // 1000ms simulation time = 1ms real time
        urd.registerSimulation("TyrTokenExpiration", createTokenExpirationSimulation());
        Logger.info("Running token expiration simulation at 1000x speed");
        urd.runSimulation("TyrTokenExpiration");
        
        // Clear state between simulations
        urd.clearSimulationState();
        
        // Run long-term token simulation at 10,000,000x speed
        // This allows simulating 1 year in just a few seconds
        urd.resetTime();
        urd.setTimeScale(10_000_000); // 10M ms simulation time = 1ms real time
        urd.registerSimulation("TyrLongTermToken", createLongTermTokenSimulation());
        Logger.info("Running long-term token simulation at 10,000,000x speed");
        urd.runSimulation("TyrLongTermToken");
        
        Logger.info("All Tyr simulations completed");
    }

    public static void main(String[] args) {
        new TyrSimulationRunner().runAllSimulations();
    }
} 