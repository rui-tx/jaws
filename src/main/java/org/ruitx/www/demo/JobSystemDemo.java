package org.ruitx.www.demo;

import org.ruitx.jaws.jobs.JobQueue;
import org.ruitx.jaws.jobs.JobResult;
import org.ruitx.www.jobs.*;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * JobSystemDemo - Demonstrates both parallel and sequential job processing
 * 
 * This class shows how to:
 * 1. Submit parallel jobs (they run concurrently)
 * 2. Submit sequential jobs (they run one at a time)
 * 3. Check job status and retrieve results
 * 4. Monitor system statistics
 */
public class JobSystemDemo {
    
    private final JobQueue jobQueue = JobQueue.getInstance();
    
    public static void main(String[] args) {
        JobSystemDemo demo = new JobSystemDemo();
        demo.runDemo();
    }
    
    public void runDemo() {
        Logger.info("=== JAWS Job System Demo ===");
        
        // Start the job processing system
        jobQueue.start();
        
        try {
            // Demo 1: Submit multiple parallel jobs (they will run concurrently)
            demonstrateParallelJobs();
            
            // Demo 2: Submit multiple sequential jobs (they will run one at a time)
            demonstrateSequentialJobs();
            
            // Demo 3: Monitor system statistics
            demonstrateSystemMonitoring();
            
            // Wait a bit for jobs to complete
            Thread.sleep(20000); // 20 seconds
            
        } catch (Exception e) {
            Logger.error("Demo failed: {}", e.getMessage(), e);
        } finally {
            // Shutdown the system
            jobQueue.shutdown();
        }
        
        Logger.info("=== Demo Complete ===");
    }
    
    private void demonstrateParallelJobs() {
        Logger.info("\n--- Demonstrating Parallel Jobs ---");
        
        // Submit multiple heavy computation jobs - these will run in parallel
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskName", "Parallel Task " + i);
            payload.put("computationTimeMs", 5000L); // 5 seconds each
            payload.put("clientId", "demo-client");
            
            HeavyComputationJob job = new HeavyComputationJob(payload);
            String jobId = jobQueue.submit(job);
            
            Logger.info("Submitted parallel job {}: {}", i, jobId);
        }
        
        // Submit an image processing job - also parallel
        Map<String, Object> imagePayload = new HashMap<>();
        imagePayload.put("imageData", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="); // 1x1 pixel PNG
        imagePayload.put("filterType", "blur");
        imagePayload.put("originalFileName", "demo.png");
        
        ImageProcessingJob imageJob = new ImageProcessingJob(imagePayload);
        String imageJobId = jobQueue.submit(imageJob);
        
        Logger.info("Submitted parallel image job: {}", imageJobId);
        Logger.info("All parallel jobs submitted - they will run concurrently!");
    }
    
    private void demonstrateSequentialJobs() {
        Logger.info("\n--- Demonstrating Sequential Jobs ---");
        
        // Submit multiple database migration jobs - these will run one at a time
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("migrationName", "Migration_" + String.format("%03d", i) + "_add_user_table");
            payload.put("migrationScript", "CREATE TABLE user_data_" + i + " (id INT PRIMARY KEY, name VARCHAR(100));");
            payload.put("migrationVersion", i);
            payload.put("databaseName", "demo_db");
            payload.put("executedBy", "demo-system");
            
            DatabaseMigrationJob job = new DatabaseMigrationJob(payload);
            String jobId = jobQueue.submit(job);
            
            Logger.info("Submitted sequential migration job {}: {}", i, jobId);
        }
        
        // Submit file system cleanup jobs - also sequential
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetDirectory", "/tmp/cleanup_test_" + i);
            payload.put("maxAgeInDays", 30);
            payload.put("filePattern", "*.tmp");
            payload.put("dryRun", true); // Safe for demo
            payload.put("initiatedBy", "demo-system");
            payload.put("cleanupReason", "Scheduled maintenance");
            
            FileSystemCleanupJob job = new FileSystemCleanupJob(payload);
            String jobId = jobQueue.submit(job);
            
            Logger.info("Submitted sequential cleanup job {}: {}", i, jobId);
        }
        
        Logger.info("All sequential jobs submitted - they will run one at a time in order!");
    }
    
    private void demonstrateSystemMonitoring() {
        Logger.info("\n--- System Statistics ---");
        
        // Show initial statistics
        Map<String, Object> stats = jobQueue.getStatistics();
        Logger.info("Total jobs submitted: {}", stats.get("totalJobs"));
        Logger.info("Parallel queue size: {}", stats.get("parallelQueueSize"));
        Logger.info("Sequential queue size: {}", stats.get("sequentialQueueSize"));
        Logger.info("Active parallel workers: {}", stats.get("activeParallelWorkers"));
        Logger.info("Sequential processing: {}", stats.get("sequentialProcessing"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sequentialStats = (Map<String, Object>) stats.get("sequential");
        if (sequentialStats != null) {
            Logger.info("Sequential stats - Total: {}, Completed: {}, Failed: {}", 
                       sequentialStats.get("totalJobs"),
                       sequentialStats.get("completedJobs"),
                       sequentialStats.get("failedJobs"));
        }
        
        Logger.info("System is running: {}", stats.get("running"));
    }
} 