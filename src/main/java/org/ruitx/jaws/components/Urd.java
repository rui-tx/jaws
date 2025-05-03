package org.ruitx.jaws.components;

import org.tinylog.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Urd is a deterministic simulator and model checker for Jaws components.
 * It allows running components in a controlled environment to verify their behavior
 * and perform stress testing.
 */
public class Urd {
    private static Urd instance;
    private final Map<String, List<SimulationStep>> simulations;
    private final Map<String, Object> simulationState;
    private long simulationTimeMillis;
    private long timeScale; // milliseconds of real time per millisecond of simulation time

    private Urd() {
        this.simulations = new ConcurrentHashMap<>();
        this.simulationState = new ConcurrentHashMap<>();
        this.simulationTimeMillis = 0;
        this.timeScale = 1; // Default: 1ms real time = 1ms simulation time
    }

    public static synchronized Urd getInstance() {
        if (instance == null) {
            instance = new Urd();
        }
        return instance;
    }

    /**
     * Sets the time scale for the simulation.
     *
     * @param timeScale Number of milliseconds of simulation time per millisecond of real time
     */
    public void setTimeScale(long timeScale) {
        this.timeScale = timeScale;
        Logger.info("Set simulation time scale to {}ms simulation time per 1ms real time", timeScale);
    }

    /**
     * Gets the current simulation time in milliseconds.
     *
     * @return Current simulation time
     */
    public long getSimulationTimeMillis() {
        return simulationTimeMillis;
    }

    /**
     * Advances the simulation time by the specified duration.
     *
     * @param durationMillis Duration to advance in milliseconds
     */
    public void advanceTime(long durationMillis) {
        long realTimeStart = System.currentTimeMillis();
        simulationTimeMillis += durationMillis;

        // If timeScale < 1, we need to wait to slow down simulation
        // If timeScale > 1, simulation runs faster than real time (no waiting)
        if (timeScale < 1 && timeScale > 0) {
            long realTimeToWait = (long) (durationMillis / (double) timeScale);
            try {
                Thread.sleep(realTimeToWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.error("Time advancement interrupted: {}", e.getMessage());
            }
        }

        Logger.info("Advanced simulation time by {}ms to {}ms", durationMillis, simulationTimeMillis);
    }

    /**
     * Registers a new simulation for a component.
     *
     * @param componentName The name of the component to simulate
     * @param steps         The simulation steps to execute
     */
    public void registerSimulation(String componentName, List<SimulationStep> steps) {
        simulations.put(componentName, steps);
        Logger.info("Registered simulation for component: {}", componentName);
    }

    /**
     * Runs a simulation for a specific component.
     *
     * @param componentName The name of the component to simulate
     */
    public void runSimulation(String componentName) {
        List<SimulationStep> steps = simulations.get(componentName);
        if (steps == null) {
            Logger.error("No simulation registered for component: {}", componentName);
            return;
        }

        Logger.info("Starting simulation for component: {} at time {}ms", componentName, simulationTimeMillis);
        for (SimulationStep step : steps) {
            try {
                step.execute(simulationState);
            } catch (Exception e) {
                Logger.error("Error in simulation step: {}", e.getMessage(), e);
            }
        }
        Logger.info("Completed simulation for component: {} at time {}ms", componentName, simulationTimeMillis);
    }

    /**
     * Gets the current simulation state.
     *
     * @return The simulation state map
     */
    public Map<String, Object> getSimulationState() {
        return simulationState;
    }

    /**
     * Clears the simulation state.
     */
    public void clearSimulationState() {
        simulationState.clear();
    }

    /**
     * Resets the simulation time to zero.
     */
    public void resetTime() {
        simulationTimeMillis = 0;
        Logger.info("Reset simulation time to 0ms");
    }

    /**
     * Represents a single step in a simulation.
     */
    public static class SimulationStep {
        private final String description;
        private final Function<Map<String, Object>, Void> action;

        public SimulationStep(String description, Function<Map<String, Object>, Void> action) {
            this.description = description;
            this.action = action;
        }

        public void execute(Map<String, Object> state) {
            Logger.info("Executing simulation step: {}", description);
            action.apply(state);
        }
    }
} 