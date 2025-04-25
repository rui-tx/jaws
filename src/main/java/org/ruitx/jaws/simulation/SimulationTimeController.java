package org.ruitx.jaws.simulation;

import org.tinylog.Logger;

/**
 * Manages simulated time for the simulation framework.
 * Allows for deterministic time advancement and control.
 */
public class SimulationTimeController {
    private long currentTime;
    private long startTime;
    private long endTime;
    private double timeAccelerationFactor = 1.0;
    
    public SimulationTimeController() {
        this.currentTime = System.currentTimeMillis();
        this.startTime = currentTime;
        this.endTime = Long.MAX_VALUE;
    }
    
    public void setTimeRange(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentTime = startTime;
        Logger.info("Simulation time range set: {} to {}", startTime, endTime);
    }
    
    public void advance(long duration) {
        long advanceBy = (long) (duration * timeAccelerationFactor);
        currentTime += advanceBy;
        Logger.debug("Advanced simulation time by {}ms to {}", advanceBy, currentTime);
    }
    
    public long getCurrentTime() {
        return currentTime;
    }
    
    public void setTimeAccelerationFactor(double factor) {
        this.timeAccelerationFactor = factor;
        Logger.info("Time acceleration factor set to {}", factor);
    }
    
    public boolean isSimulationComplete() {
        return currentTime >= endTime;
    }
} 