package org.ruitx.jaws.components.freyr;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.ruitx.jaws.configs.JobRegistryConfig;
import org.ruitx.jaws.interfaces.Job;
import org.tinylog.Logger;

/**
 * JobRegistry manages the mapping between job types and their corresponding job classes.
 */
public class JobRegistry {
    
  private static final Object lock = new Object();
  private static volatile JobRegistry instance;
  private final Map<String, Class<? extends Job>> jobTypes = new ConcurrentHashMap<>();
  private final Map<Class<? extends Job>, Constructor<? extends Job>> constructorCache = new ConcurrentHashMap<>();

  private JobRegistry() {
    for (Map.Entry<String, Class<? extends Job>> entry : JobRegistryConfig.JOBS.entrySet()) {
      String jobType = entry.getKey();
      Class<? extends Job> jobClass = entry.getValue();
      register(jobType, jobClass);
    }

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
          Logger.error("Job class {} must have a constructor that takes Map<String, Object>",
              clazz.getSimpleName());
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