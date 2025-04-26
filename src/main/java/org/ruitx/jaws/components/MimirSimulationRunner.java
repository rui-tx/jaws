package org.ruitx.jaws.components;

import org.tinylog.Logger;
import org.ruitx.jaws.utils.Row;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

/**
 * MimirSimulationRunner provides and runs simulations for the Mimir component.
 */
public class MimirSimulationRunner {
    private final Urd urd;

    public MimirSimulationRunner() {
        this.urd = Urd.getInstance();
    }

    /**
     * Creates a basic simulation for the Mimir component.
     * @return A list of simulation steps for Mimir
     */
    private List<Urd.SimulationStep> createBasicSimulation() {
        List<Urd.SimulationStep> steps = new ArrayList<>();
        
        // Step 1: Initialize Mimir
        steps.add(new Urd.SimulationStep("Initialize Mimir", state -> {
            Mimir mimir = new Mimir();
            state.put("mimir", mimir);
            return null;
        }));

        // Step 2: Execute a simple query
        steps.add(new Urd.SimulationStep("Execute simple query", state -> {
            Mimir mimir = (Mimir) state.get("mimir");
            Row result = mimir.getRow("SELECT 1");
            Logger.info("Query result: {}", result);
            return null;
        }));

        return steps;
    }

    /**
     * Creates a simulation that tests transaction handling.
     * @return A list of simulation steps for Mimir transaction testing
     */
    private List<Urd.SimulationStep> createTransactionSimulation() {
        List<Urd.SimulationStep> steps = new ArrayList<>();
        
        // Step 1: Initialize Mimir
        steps.add(new Urd.SimulationStep("Initialize Mimir", state -> {
            Mimir mimir = new Mimir();
            state.put("mimir", mimir);
            return null;
        }));

        // Step 2: Begin transaction
        steps.add(new Urd.SimulationStep("Begin transaction", state -> {
            Mimir mimir = (Mimir) state.get("mimir");
            try {
                mimir.beginTransaction();
            } catch (SQLException e) {
                Logger.error("Failed to begin transaction: {}", e.getMessage());
                throw new RuntimeException("Transaction failed", e);
            }
            return null;
        }));

        // Step 3: Execute query within transaction
        steps.add(new Urd.SimulationStep("Execute query in transaction", state -> {
            Mimir mimir = (Mimir) state.get("mimir");
            Row result = mimir.getRow("SELECT 1");
            Logger.info("Transaction query result: {}", result);
            return null;
        }));

        // Step 4: Commit transaction
        steps.add(new Urd.SimulationStep("Commit transaction", state -> {
            Mimir mimir = (Mimir) state.get("mimir");
            try {
                mimir.commitTransaction();
            } catch (SQLException e) {
                Logger.error("Failed to commit transaction: {}", e.getMessage());
                throw new RuntimeException("Transaction commit failed", e);
            }
            return null;
        }));

        return steps;
    }

    /**
     * Runs all Mimir simulations.
     */
    public void runAllSimulations() {
        Logger.info("Starting Mimir simulation runner");
        
        // Register the basic simulation
        urd.registerSimulation("MimirBasic", createBasicSimulation());
        
        // Register the transaction simulation
        urd.registerSimulation("MimirTransaction", createTransactionSimulation());
        
        // Run the basic simulation
        Logger.info("Running basic Mimir simulation");
        urd.runSimulation("MimirBasic");
        
        // Clear state between simulations
        urd.clearSimulationState();
        
        // Run the transaction simulation
        Logger.info("Running transaction Mimir simulation");
        urd.runSimulation("MimirTransaction");
        
        Logger.info("All Mimir simulations completed");
    }

    public static void main(String[] args) {
        new MimirSimulationRunner().runAllSimulations();
    }
} 