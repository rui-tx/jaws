package org.ruitx.jaws.simulation;

import org.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates simulation components and manages the simulation lifecycle.
 */
public class SimulationManager {
    private final SimulationTimeController clock;
    private final Map<String, SimulationParticipant> components;
    private boolean isRunning = false;
    
    public SimulationManager() {
        this.clock = new SimulationTimeController();
        this.components = new HashMap<>();
    }
    
    public void registerComponent(String id, SimulationParticipant component) {
        if (isRunning) {
            throw new IllegalStateException("Cannot register components while simulation is running");
        }
        components.put(id, component);
        Logger.info("Registered simulation component: {}", id);
    }
    
    public void runSimulation(long duration, TimeUnit timeUnit) {
        if (isRunning) {
            throw new IllegalStateException("Simulation is already running");
        }
        
        isRunning = true;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeUnit.toMillis(duration);
        
        clock.setTimeRange(startTime, endTime);
        Logger.info("Starting simulation for {} {}", duration, timeUnit);
        
        try {
            // Initialize all components
            components.values().forEach(SimulationParticipant::initialize);
            
            // Run simulation until completion
            while (!clock.isSimulationComplete()) {
                // Advance time in smaller chunks (1 second at a time)
                clock.advance(TimeUnit.SECONDS.toMillis(1));
                
                // Update all components
                components.values().forEach(component -> 
                    component.update(clock.getCurrentTime()));
                
                // Verify state
                components.values().forEach(SimulationParticipant::verifyState);
            }
            
            Logger.info("Simulation completed successfully");
        } finally {
            // Cleanup all components
            components.values().forEach(SimulationParticipant::cleanup);
            isRunning = false;
        }
    }
    
    public void setTimeAccelerationFactor(double factor) {
        clock.setTimeAccelerationFactor(factor);
    }
    
    public SimulationParticipant getComponent(String id) {
        return components.get(id);
    }
    
    public boolean isRunning() {
        return isRunning;
    }
} 