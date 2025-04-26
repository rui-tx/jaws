package org.ruitx.jaws.components;

import org.tinylog.Logger;

import java.util.ArrayList;
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

    private Urd() {
        this.simulations = new ConcurrentHashMap<>();
        this.simulationState = new ConcurrentHashMap<>();
    }

    public static synchronized Urd getInstance() {
        if (instance == null) {
            instance = new Urd();
        }
        return instance;
    }

    /**
     * Registers a new simulation for a component.
     * @param componentName The name of the component to simulate
     * @param steps The simulation steps to execute
     */
    public void registerSimulation(String componentName, List<SimulationStep> steps) {
        simulations.put(componentName, steps);
        Logger.info("Registered simulation for component: {}", componentName);
    }

    /**
     * Runs a simulation for a specific component.
     * @param componentName The name of the component to simulate
     */
    public void runSimulation(String componentName) {
        List<SimulationStep> steps = simulations.get(componentName);
        if (steps == null) {
            Logger.error("No simulation registered for component: {}", componentName);
            return;
        }

        Logger.info("Starting simulation for component: {}", componentName);
        for (SimulationStep step : steps) {
            try {
                step.execute(simulationState);
            } catch (Exception e) {
                Logger.error("Error in simulation step: {}", e.getMessage(), e);
            }
        }
        Logger.info("Completed simulation for component: {}", componentName);
    }

    /**
     * Gets the current simulation state.
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