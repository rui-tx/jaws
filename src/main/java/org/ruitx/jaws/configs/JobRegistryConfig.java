package org.ruitx.jaws.configs;

import java.util.Map;
import org.ruitx.jaws.interfaces.Job;
import org.ruitx.jaws.utils.logger.JawsLoggerJob;
import org.ruitx.www.base.jobs.ParallelPingJob;
import org.ruitx.www.base.jobs.SequentialPingJob;

/**
 * Configuration class for job registry. This class holds the mapping between job types and their
 * corresponding job classes. It is used to register jobs that can be processed by the Freyr job
 * queue system.
 */
public class JobRegistryConfig {

  /**
   * Map of job types to their corresponding job classes. Each entry in this map represents a job
   * type and the class that implements it. The key is the job type string, and the value is the
   * class that implements the Job interface.
   */
  public static final Map<String, Class<? extends Job>> JOBS = Map.of(
      SequentialPingJob.JOB_TYPE, SequentialPingJob.class,
      ParallelPingJob.JOB_TYPE, ParallelPingJob.class,
      JawsLoggerJob.JOB_TYPE, JawsLoggerJob.class
  );

  private JobRegistryConfig() {
  }
}
