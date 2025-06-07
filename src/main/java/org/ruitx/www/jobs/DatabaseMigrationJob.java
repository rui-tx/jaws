package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.jobs.BaseJob;
import org.ruitx.jaws.jobs.ExecutionMode;
import org.ruitx.jaws.jobs.JobResultStore;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DatabaseMigrationJob - Example of a sequential job that must run one at a time
 * 
 * Database migrations are perfect for sequential processing because:
 * - They modify database schema/data in ways that can conflict
 * - They must be executed in a specific order
 * - Running multiple migrations simultaneously can cause corruption
 * - They often have dependencies on previous migrations
 * 
 * This job demonstrates how to use ExecutionMode.SEQUENTIAL to ensure
 * only one migration runs at a time, even if multiple are submitted.
 */
public class DatabaseMigrationJob extends BaseJob {
    
    public static final String JOB_TYPE = "database-migration";
    
    /**
     * Constructor - uses SEQUENTIAL execution mode
     */
    public DatabaseMigrationJob(Map<String, Object> payload) {
        // Note: Using SEQUENTIAL execution mode with high priority (2) and longer timeout
        super(JOB_TYPE, ExecutionMode.SEQUENTIAL, 2, 1, 120000L, payload); // priority 2, 1 retry, 2min timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting database migration job: {}", getId());
        
        try {
            // Get migration details from payload
            String migrationName = getString("migrationName");
            String migrationScript = getString("migrationScript");
            Integer migrationVersion = getInteger("migrationVersion");
            
            if (migrationName == null || migrationScript == null) {
                throw new IllegalArgumentException("Migration name and script are required");
            }
            
            Logger.info("Executing migration: {} (version {})", migrationName, migrationVersion);
            
            // Simulate pre-migration checks
            Logger.info("Performing pre-migration validation...");
            Thread.sleep(2000); // 2 seconds for validation
            
            // Simulate running the migration script
            Logger.info("Executing migration script for: {}", migrationName);
            executeMigrationScript(migrationScript);
            
            // Simulate post-migration verification
            Logger.info("Performing post-migration verification...");
            Thread.sleep(1000); // 1 second for verification
            
            // Create successful result
            Map<String, Object> result = new HashMap<>();
            result.put("migrationCompleted", true);
            result.put("migrationName", migrationName);
            result.put("migrationVersion", migrationVersion);
            result.put("executionTime", "~5 seconds (simulated)");
            result.put("timestamp", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            result.put("executionMode", getExecutionMode().name());
            
            // Additional metadata
            if (getString("databaseName") != null) {
                result.put("databaseName", getString("databaseName"));
            }
            if (getString("executedBy") != null) {
                result.put("executedBy", getString("executedBy"));
            }
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("Database migration job completed successfully: {}", getId());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Database migration job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Migration was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("Database migration job failed: {}", e.getMessage(), e);
            
            // Store error result with details
            String errorMessage = String.format("Migration failed: %s", e.getMessage());
            JobResultStore.storeError(getId(), 500, errorMessage);
            throw e;
        }
    }
    
    /**
     * Simulate executing a migration script
     * In a real implementation, this would connect to the database and execute DDL/DML
     */
    private void executeMigrationScript(String script) throws Exception {
        Logger.info("Executing migration script: {}", script.substring(0, Math.min(script.length(), 50)) + "...");
        
        // Simulate script execution time based on script complexity
        int scriptLength = script.length();
        long executionTime = Math.min(2000 + (scriptLength / 10), 10000); // 2-10 seconds
        
        Thread.sleep(executionTime);
        
        // Simulate potential failure (5% chance)
        if (Math.random() < 0.05) {
            throw new RuntimeException("Simulated migration script execution failure");
        }
        
        Logger.info("Migration script executed successfully in {}ms", executionTime);
    }
} 