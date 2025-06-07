package org.ruitx.jaws.jobs;

import org.ruitx.www.jobs.ExternalApiJob;
import org.ruitx.www.jobs.RetryTestJob;
import org.ruitx.www.jobs.SequentialPingJob;
import org.tinylog.Logger;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobRegistry manages the mapping between job types and their corresponding job classes.
 */
public class JobRegistry {
    
    private static volatile JobRegistry instance;
    private static final Object lock = new Object();
    
    private final Map<String, Class<? extends Job>> jobTypes = new ConcurrentHashMap<>();
    private final Map<Class<? extends Job>, Constructor<? extends Job>> constructorCache = new ConcurrentHashMap<>();
    
    private JobRegistry() {

        // Sequential
        register("sequential-ping", SequentialPingJob.class);

        // Parallel
        register("retry-test", RetryTestJob.class);

        // External 
        register("external-api-call", ExternalApiJob.class);

        Logger.info("JobRegistry initialized with {} job types", jobTypes.size());
    }
    
    /**
     * Get the singleton instance of JobRegistry
     */
    public static JobRegistry getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new JobRegistry();
                }
            }
        }
        return instance;
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
    
} 