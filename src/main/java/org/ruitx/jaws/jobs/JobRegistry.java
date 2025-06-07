package org.ruitx.jaws.jobs;

import org.tinylog.Logger;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobRegistry manages the mapping between job types and their corresponding job classes.
 * 
 * This is much simpler than route resolution - we just map strings to classes!
 * No annotations, no reflection magic, just a simple registry pattern.
 */
public class JobRegistry {
    
    private final Map<String, Class<? extends Job>> jobTypes = new ConcurrentHashMap<>();
    private final Map<Class<? extends Job>, Constructor<? extends Job>> constructorCache = new ConcurrentHashMap<>();
    
    public JobRegistry() {
        registerDefaultJobs();
    }
    
    /**
     * Register a job type with its corresponding job class
     */
    public void register(String jobType, Class<? extends Job> jobClass) {
        jobTypes.put(jobType, jobClass);
        Logger.info("Registered job type: {} -> {}", jobType, jobClass.getSimpleName());
    }
    
    /**
     * Create a job instance from type and payload
     */
    public Job createJob(String jobType, Map<String, Object> payload) {
        Class<? extends Job> jobClass = jobTypes.get(jobType);
        if (jobClass == null) {
            Logger.error("Unknown job type: {}", jobType);
            return null;
        }
        
        try {
            // Get or cache constructor
            Constructor<? extends Job> constructor = constructorCache.computeIfAbsent(jobClass, clazz -> {
                try {
                    // Look for constructor that takes Map<String, Object>
                    return clazz.getConstructor(Map.class);
                } catch (NoSuchMethodException e) {
                    Logger.error("Job class {} must have a constructor that takes Map<String, Object>", clazz.getSimpleName());
                    return null;
                }
            });
            
            if (constructor == null) {
                return null;
            }
            
            return constructor.newInstance(payload);
            
        } catch (Exception e) {
            Logger.error("Failed to create job of type {}: {}", jobType, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get all registered job types
     */
    public Map<String, Class<? extends Job>> getRegisteredTypes() {
        return new HashMap<>(jobTypes);
    }
    
    /**
     * Check if a job type is registered
     */
    public boolean isRegistered(String jobType) {
        return jobTypes.containsKey(jobType);
    }
    
    /**
     * Register default job types
     * These will be implemented later as we migrate from the current async system
     */
    private void registerDefaultJobs() {
        // Parallel processing jobs (existing)
        register("heavy-computation", org.ruitx.www.jobs.HeavyComputationJob.class);
        register("external-api-call", org.ruitx.www.jobs.ExternalApiJob.class);
        register("image-processing", org.ruitx.www.jobs.ImageProcessingJob.class);
        
        // Sequential processing jobs
        register("database-migration", org.ruitx.www.jobs.DatabaseMigrationJob.class);
        register("filesystem-cleanup", org.ruitx.www.jobs.FileSystemCleanupJob.class);
        register("sequential-ping", org.ruitx.www.jobs.SequentialPingJob.class);
        
        // Test jobs
        register("retry-test", org.ruitx.www.jobs.RetryTestJob.class);
        
        // These will be implemented next:
        // register("urgent-task", UrgentTaskJob.class);
        // register("user-processing", UserProcessingJob.class);
        
        Logger.info("JobRegistry initialized with {} job types", jobTypes.size());
    }
} 