package org.ruitx.jaws.simulation;

/**
 * Base interface for all simulation components.
 * Components must implement this interface to participate in the simulation.
 */
public interface SimulationParticipant {
    /**
     * Initialize the component for simulation.
     */
    void initialize();
    
    /**
     * Update the component's state based on the current simulation time.
     * @param currentTime The current simulation time in milliseconds since epoch.
     */
    void update(long currentTime);
    
    /**
     * Verify the component's state is consistent.
     * @throws IllegalStateException if the state is inconsistent.
     */
    void verifyState();
    
    /**
     * Clean up any resources used by the component.
     */
    void cleanup();
} 