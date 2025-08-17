package org.ruitx.jaws.components;

import io.jsonwebtoken.Clock;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.tinylog.Logger;

/**
 * Urd is a deterministic simulator and model checker for Jaws components. It allows running
 * components in a controlled environment to verify their behavior and perform stress testing.
 */
public class Urd {

  private static Urd instance;
  private final Map<String, List<SimulationStep>> simulations;
  private final Map<String, Object> simulationState;
  private long simulationTimeMillis;

  // Simulation time is purely logical; advancing time does not sleep.

  private Urd() {
    this.simulations = new ConcurrentHashMap<>();
    this.simulationState = new ConcurrentHashMap<>();
    this.simulationTimeMillis = 0;
    // No real-time coupling
  }

  public static synchronized Urd getInstance() {
    if (instance == null) {
      instance = new Urd();
    }
    return instance;
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
   * Provides a JWT Clock backed by Urd's logical time.
   */
  public Clock jwtClock() {
    return () -> new Date(simulationTimeMillis);
  }

  /**
   * Advances the simulation time by the specified duration.
   *
   * @param durationMillis Duration to advance in milliseconds
   */
  public void advanceTime(long durationMillis) {
    simulationTimeMillis += durationMillis;
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

    Logger.info("Starting simulation for component: {} at time {}ms", componentName,
        simulationTimeMillis);
    for (SimulationStep step : steps) {
      try {
        step.execute(simulationState);
      } catch (Exception e) {
        Logger.error("Error in simulation step: {}", e.getMessage(), e);
      }
    }
    Logger.info("Completed simulation for component: {} at time {}ms", componentName,
        simulationTimeMillis);
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
    private final Consumer<Map<String, Object>> action;

    public SimulationStep(String description, Consumer<Map<String, Object>> action) {
      this.description = description;
      this.action = action;
    }

    public void execute(Map<String, Object> state) {
      Logger.info("Executing simulation step: {}", description);
      action.accept(state);
    }
  }
}