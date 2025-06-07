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
 * FileSystemCleanupJob - Example of a sequential job for file system operations
 * 
 * File system cleanup operations are ideal for sequential processing because:
 * - Multiple cleanup operations can interfere with each other
 * - They may compete for disk I/O resources
 * - One cleanup might delete files another cleanup is trying to process
 * - Sequential execution ensures consistent and predictable behavior
 * 
 * This job demonstrates another use case for ExecutionMode.SEQUENTIAL.
 */
public class FileSystemCleanupJob extends BaseJob {
    
    public static final String JOB_TYPE = "filesystem-cleanup";
    
    /**
     * Constructor - uses SEQUENTIAL execution mode
     */
    public FileSystemCleanupJob(Map<String, Object> payload) {
        // Using SEQUENTIAL execution mode with medium priority and moderate timeout
        super(JOB_TYPE, ExecutionMode.SEQUENTIAL, 4, 2, 90000L, payload); // priority 4, 2 retries, 90s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting file system cleanup job: {}", getId());
        
        try {
            // Get cleanup parameters from payload
            String targetDirectory = getString("targetDirectory");
            Integer maxAgeInDays = getInteger("maxAgeInDays");
            String filePattern = getString("filePattern");
            Boolean dryRun = getBoolean("dryRun");
            
            if (targetDirectory == null) {
                throw new IllegalArgumentException("Target directory is required");
            }
            
            // Default values
            if (maxAgeInDays == null) maxAgeInDays = 30;
            if (filePattern == null) filePattern = "*";
            if (dryRun == null) dryRun = false;
            
            Logger.info("Cleaning up directory: {} (pattern: {}, max age: {} days, dry run: {})", 
                       targetDirectory, filePattern, maxAgeInDays, dryRun);
            
            // Simulate directory scanning
            Logger.info("Scanning directory for files matching criteria...");
            Thread.sleep(1500); // 1.5 seconds for scanning
            
            // Simulate finding files to clean
            int foundFiles = simulateFileDiscovery(targetDirectory, filePattern, maxAgeInDays);
            
            // Simulate file deletion
            int deletedFiles = 0;
            long totalSpaceFreed = 0;
            
            if (!dryRun && foundFiles > 0) {
                Logger.info("Deleting {} files...", foundFiles);
                for (int i = 0; i < foundFiles; i++) {
                    Thread.sleep(100); // 100ms per file deletion
                    deletedFiles++;
                    totalSpaceFreed += (long) (Math.random() * 10 * 1024 * 1024); // Random size 0-10MB
                }
            } else if (dryRun) {
                Logger.info("Dry run mode - no files will be deleted");
            }
            
            // Simulate final cleanup verification
            Logger.info("Verifying cleanup completion...");
            Thread.sleep(500); // 500ms for verification
            
            // Create successful result
            Map<String, Object> result = new HashMap<>();
            result.put("cleanupCompleted", true);
            result.put("targetDirectory", targetDirectory);
            result.put("filePattern", filePattern);
            result.put("maxAgeInDays", maxAgeInDays);
            result.put("dryRun", dryRun);
            result.put("filesFound", foundFiles);
            result.put("filesDeleted", deletedFiles);
            result.put("spaceFreedBytes", totalSpaceFreed);
            result.put("spaceFreedMB", Math.round(totalSpaceFreed / 1024.0 / 1024.0 * 100.0) / 100.0);
            result.put("timestamp", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            result.put("executionMode", getExecutionMode().name());
            
            // Additional metadata
            if (getString("initiatedBy") != null) {
                result.put("initiatedBy", getString("initiatedBy"));
            }
            if (getString("cleanupReason") != null) {
                result.put("cleanupReason", getString("cleanupReason"));
            }
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("File system cleanup job completed successfully: {} (freed {} MB)", 
                       getId(), Math.round(totalSpaceFreed / 1024.0 / 1024.0 * 100.0) / 100.0);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("File system cleanup job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Cleanup was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("File system cleanup job failed: {}", e.getMessage(), e);
            
            // Store error result with details
            String errorMessage = String.format("Cleanup failed: %s", e.getMessage());
            JobResultStore.storeError(getId(), 500, errorMessage);
            throw e;
        }
    }
    
    /**
     * Simulate discovering files that match cleanup criteria
     * In a real implementation, this would scan the actual file system
     */
    private int simulateFileDiscovery(String directory, String pattern, int maxAgeInDays) throws Exception {
        Logger.info("Discovering files in {} matching pattern {} older than {} days", 
                   directory, pattern, maxAgeInDays);
        
        // Simulate discovery time based on directory complexity
        long discoveryTime = Math.min(1000 + directory.length() * 10, 5000); // 1-5 seconds
        Thread.sleep(discoveryTime);
        
        // Simulate finding random number of files (0-100)
        int foundFiles = (int) (Math.random() * 101);
        
        // Simulate potential access errors (2% chance)
        if (Math.random() < 0.02) {
            throw new RuntimeException("Access denied to directory: " + directory);
        }
        
        Logger.info("Found {} files matching cleanup criteria", foundFiles);
        return foundFiles;
    }
} 