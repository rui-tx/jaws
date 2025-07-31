package org.ruitx.jaws.components.freyr;

import static org.ruitx.jaws.configs.ApplicationConfig.CLEANUP_INTERVAL_MS;
import static org.ruitx.jaws.configs.ApplicationConfig.QUEUE_CAPACITY;
import static org.ruitx.jaws.configs.ApplicationConfig.WORKER_THREADS;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.interfaces.Job;
import org.ruitx.jaws.types.Row;
import org.tinylog.Logger;

/**
 * Freyr is the main entry point for the job queue system. It is responsible for submitting jobs,
 * processing them, and managing their status.
 */
public class Freyr implements Runnable {

  private static final int DEFAULT_WORKER_THREADS = WORKER_THREADS;
  private static final int DEFAULT_QUEUE_CAPACITY = QUEUE_CAPACITY;
  private static final Object instanceLock = new Object();
  private static Freyr instance;
  private final Mimir mimir = new Mimir();
  private final JobRegistry jobRegistry;
  private final ExecutorService workerPool;
  private final PriorityBlockingQueue<JobInstance> jobQueue;
  private final SequentialJobQueue sequentialJobQueue;
  private final JobRetryManager retryManager;
  private final DeadLetterQueue deadLetterQueue;
  private final JobRetryScheduler retryScheduler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicInteger activeWorkers = new AtomicInteger(0);
  private final ScheduledExecutorService cleanupScheduler;

  // Statistics
  private final AtomicInteger totalJobs = new AtomicInteger(0);
  private final AtomicInteger completedJobs = new AtomicInteger(0);
  private final AtomicInteger failedJobs = new AtomicInteger(0);
  private final AtomicInteger retriedJobs = new AtomicInteger(0);

  private Freyr(Map<String, Object> config) {
    // Get singleton JobRegistry instance
    this.jobRegistry = JobRegistry.getInstance();

    this.workerPool = Executors.newFixedThreadPool(DEFAULT_WORKER_THREADS,
        r -> new Thread(r, "job-worker-" + Thread.currentThread().getName()));
    this.jobQueue = new PriorityBlockingQueue<>(DEFAULT_QUEUE_CAPACITY,
        Comparator.comparingInt((JobInstance ji) -> ji.job.getPriority())
            .thenComparingLong(ji -> ji.createdAt));
    this.retryManager = new JobRetryManager();
    this.deadLetterQueue = new DeadLetterQueue();
    this.sequentialJobQueue = new SequentialJobQueue(this.deadLetterQueue);
    this.retryScheduler = new JobRetryScheduler(this.deadLetterQueue);
    this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "job-cleanup"));

    loadPendingJobs();

    Logger.info("JobQueue initialized with {} worker threads, queue capacity of {}, and shared DLQ",
        DEFAULT_WORKER_THREADS, DEFAULT_QUEUE_CAPACITY);
  }


  /**
   * Returns the singleton instance of Freyr. This method ensures that only one instance of Freyr is
   * created and shared across the application.
   *
   * @return the Freyr instance
   */
  public static Freyr getInstance() {
    if (instance == null) {
      synchronized (instanceLock) {
        if (instance == null) {
          instance = new Freyr(new HashMap<>());
        }
      }
    }
    return instance;
  }


  /**
   * Submit a job for processing.
   *
   * <p>This method creates a new JobInstance and adds it to either the sequential or parallel job
   * queue depending on the job's execution mode. It also increments the total number of jobs ever
   * submitted.
   *
   * @param job The job to submit
   * @return The ID of the job
   * @throws RuntimeException if the job could not be submitted
   */
  public String submit(Job job) {
    try {
      persistJob(job);

      if (job.getExecutionMode() == ExecutionMode.SEQUENTIAL) {
        boolean queued = sequentialJobQueue.submit(job);
        if (!queued) {
          throw new RuntimeException("Sequential queue is full");
        }
        Logger.trace("Job submitted to sequential queue: {}", job);
      } else {
        jobQueue.offer(new JobInstance(job));
        Logger.trace("Job submitted to parallel queue: {}", job);
      }

      totalJobs.incrementAndGet();

      return job.getId();

    } catch (Exception e) {
      Logger.error("Failed to submit job: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to submit job", e);
    }
  }


  /**
   * Retrieve the status of a job by its ID.
   *
   * @param jobId The unique identifier of the job.
   * @return The current status of the job as a JobStatus enum, or null if the job does not exist or
   * an error occurs.
   */
  public JobStatus getJobStatus(String jobId) {
    try {
      Row row = mimir.getRow("SELECT status FROM JOBS WHERE id = ?", jobId);
      if (row != null) {
        return row.getString("status")
            .map(JobStatus::valueOf)
            .orElse(null);
      }
      return null;
    } catch (Exception e) {
      Logger.error("Failed to get job status for {}: {}", jobId, e.getMessage());
      return null;
    }
  }


  /**
   * Retrieve the result of a job by its ID.
   *
   * @param jobId The unique identifier of the job.
   * @return A JobResult object containing the job's result details such as status code, headers,
   * body, content type, and expiration time. Returns null if the job result does not exist, has
   * expired, or an error occurs during retrieval.
   */
  public JobResult getJobResult(String jobId) {
    try {
      Row row = mimir.getRow(
          "SELECT * FROM JOB_RESULTS WHERE job_id = ? AND expires_at > ?",
          jobId, Instant.now().toEpochMilli());

      if (row != null) {
        Map<String, String> headers = parseHeaders(
            row.getString("headers").orElse(null));
        return new JobResult(
            row.getString("job_id").orElse(jobId),
            row.getInt("status_code").orElse(500),
            headers,
            row.getString("body").orElse(""),
            row.getString("content_type").orElse("application/json"),
            row.getLong("expires_at").orElse(0L)
        );
      }
      return null;
    } catch (Exception e) {
      Logger.error("Failed to get job result for {}: {}", jobId, e.getMessage());
      return null;
    }
  }


  /**
   * Starts the JobQueue processing system.
   *
   * <p>This method will start the following components:
   * <ul>
   *   <li>Parallel worker threads for processing jobs in parallel.</li>
   *   <li>Sequential job processing.</li>
   *   <li>Job retry scheduler.</li>
   *   <li>Cleanup scheduler.</li>
   * </ul>
   *
   * <p>This method will not block and will return immediately after starting the components.
   * If the JobQueue is already running, this method will be a no-op.
   */
  public void start() {
    if (running.compareAndSet(false, true)) {
      Logger.info("Starting JobQueue processing system...");

      // Start parallel worker threads
      for (int i = 0; i < DEFAULT_WORKER_THREADS; i++) {
        workerPool.execute(new JobWorker());
      }

      // Start sequential processing
      sequentialJobQueue.start();

      // Start retry scheduler
      retryScheduler.start();

      // Start cleanup scheduler
      cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredData,
          CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS,
          TimeUnit.MILLISECONDS);

      Logger.info(
          "JobQueue started with {} parallel workers, 1 sequential worker, and retry scheduler",
          DEFAULT_WORKER_THREADS);
    }
  }

  /**
   * Shuts down the JobQueue processing system.
   *
   * <p>This method will gracefully shut down the following components:
   * <ul>
   *   <li>Parallel worker threads.</li>
   *   <li>Sequential job processing.</li>
   *   <li>Job retry scheduler.</li>
   *   <li>Cleanup scheduler.</li>
   * </ul>
   *
   * <p>This method will wait for up to 30 seconds for the parallel worker threads to
   * complete and up to 5 seconds for the cleanup scheduler to complete. If either
   * of them does not complete within the given time, it will be force terminated.
   *
   * <p>If the JobQueue is already shut down, this method will be a no-op.
   */
  public void shutdown() {
    if (running.compareAndSet(true, false)) {
      Logger.info("Shutting down JobQueue processing system...");

      workerPool.shutdown();
      sequentialJobQueue.shutdown();
      retryScheduler.stop();
      cleanupScheduler.shutdown();

      try {
        if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
          workerPool.shutdownNow();
        }
        if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          cleanupScheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        workerPool.shutdownNow();
        cleanupScheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }

      Logger.info("JobQueue shutdown complete");
    }
  }

  /**
   * Get system statistics
   */
  public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("totalJobs", totalJobs.get());
    stats.put("completedJobs", completedJobs.get());
    stats.put("failedJobs", failedJobs.get());
    stats.put("retriedJobs", retriedJobs.get());
    stats.put("parallelQueueSize", jobQueue.size());
    stats.put("sequentialQueueSize", sequentialJobQueue.getQueueSize());
    stats.put("activeParallelWorkers", activeWorkers.get());
    stats.put("sequentialProcessing", sequentialJobQueue.isProcessingJob());
    stats.put("running", running.get());

    // Add sequential queue statistics
    Map<String, Object> sequentialStats = sequentialJobQueue.getStatistics();
    stats.put("sequential", sequentialStats);

    // Add retry statistics
    try {
      JobRetryManager.RetryStatistics retryStats = retryManager.getRetryStatistics();
      Map<String, Object> retryStatsMap = new HashMap<>();
      retryStatsMap.put("retryScheduledJobs", retryStats.getRetryScheduledJobs());
      retryStatsMap.put("permanentlyFailedJobs", retryStats.getPermanentlyFailedJobs());
      retryStatsMap.put("totalRetryAttempts", retryStats.getTotalRetryAttempts());
      stats.put("retry", retryStatsMap);
    } catch (Exception e) {
      Logger.warn("Failed to get retry statistics: {}", e.getMessage());
      Map<String, Object> fallbackRetryStats = new HashMap<>();
      fallbackRetryStats.put("retryScheduledJobs", 0);
      fallbackRetryStats.put("permanentlyFailedJobs", 0);
      fallbackRetryStats.put("totalRetryAttempts", 0);
      stats.put("retry", fallbackRetryStats);
    }

    // Add Dead Letter Queue statistics
    try {
      DeadLetterQueue.DLQStatistics dlqStats = deadLetterQueue.getStatistics();
      Map<String, Object> dlqStatsMap = new HashMap<>();
      dlqStatsMap.put("totalEntries", dlqStats.getTotalEntries());
      dlqStatsMap.put("retryableEntries", dlqStats.getRetryableEntries());
      dlqStatsMap.put("entriesByType",
          dlqStats.getEntriesByType() != null ? dlqStats.getEntriesByType() : new HashMap<>());
      // Handle potential null value for oldestEntryTimestamp
      dlqStatsMap.put("oldestEntryTimestamp", dlqStats.getOldestEntryTimestamp());
      stats.put("deadLetterQueue", dlqStatsMap);
    } catch (Exception e) {
      Logger.warn("Failed to get DLQ statistics: {}", e.getMessage());
      Map<String, Object> fallbackDlqStats = new HashMap<>();
      fallbackDlqStats.put("totalEntries", 0);
      fallbackDlqStats.put("retryableEntries", 0);
      fallbackDlqStats.put("entriesByType", new HashMap<>());
      fallbackDlqStats.put("oldestEntryTimestamp", null);
      stats.put("deadLetterQueue", fallbackDlqStats);
    }

    // Add Retry Scheduler statistics
    try {
      JobRetryScheduler.RetrySchedulerStatistics schedulerStats = retryScheduler.getStatistics();
      Map<String, Object> schedulerStatsMap = new HashMap<>();
      schedulerStatsMap.put("totalRetriesProcessed", schedulerStats.getTotalRetriesProcessed());
      schedulerStatsMap.put("successfulRetries", schedulerStats.getSuccessfulRetries());
      schedulerStatsMap.put("failedRetries", schedulerStats.getFailedRetries());
      schedulerStatsMap.put("movedToDeadLetter", schedulerStats.getMovedToDeadLetter());
      schedulerStatsMap.put("running", schedulerStats.isRunning());
      stats.put("retryScheduler", schedulerStatsMap);
    } catch (Exception e) {
      Logger.warn("Failed to get retry scheduler statistics: {}", e.getMessage());
      Map<String, Object> fallbackSchedulerStats = new HashMap<>();
      fallbackSchedulerStats.put("totalRetriesProcessed", 0);
      fallbackSchedulerStats.put("successfulRetries", 0);
      fallbackSchedulerStats.put("failedRetries", 0);
      fallbackSchedulerStats.put("movedToDeadLetter", 0);
      fallbackSchedulerStats.put("running", false);
      stats.put("retryScheduler", fallbackSchedulerStats);
    }

    return stats;
  }

  // Private helper methods

  /**
   * Persists a job in the database
   *
   * @param job the job to persist
   */
  private void persistJob(Job job) {
    try {
      String payloadJson = Odin.getMapper().writeValueAsString(job.getPayload());

      mimir.executeSql(
          "INSERT INTO JOBS (id, type, payload, priority, max_retries, current_retries, timeout_ms, execution_mode, status, created_at, client_id, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          job.getId(),
          job.getType(),
          payloadJson,
          job.getPriority(),
          job.getMaxRetries(),
          0, // current_retries starts at 0
          job.getTimeoutMs(),
          job.getExecutionMode().name(),
          JobStatus.PENDING.name(),
          Instant.now().toEpochMilli(),
          job.getClientId(),
          job.getUserId()
      );
    } catch (Exception e) {
      throw new RuntimeException("Failed to persist job", e);
    }
  }

  /**
   * Loads pending and processing jobs from the database and submits them to the appropriate
   * queues.
   * <p>
   * This method retrieves jobs with status 'PENDING' or 'PROCESSING' from the JOBS table, ordered
   * by priority and creation time. Each job is instantiated using the JobRegistry and routed to
   * either the sequential or parallel job queue based on its execution mode. The method logs the
   * count of jobs loaded into each queue.
   */
  private void loadPendingJobs() {
    try {
      List<Row> rows = mimir.getRows(
          "SELECT * FROM JOBS WHERE status IN ('PENDING', 'PROCESSING') ORDER BY priority, created_at");
      int parallelLoaded = 0;
      int sequentialLoaded = 0;

      for (Row row : rows) {
        try {
          String jobType = row.getString("type").orElse("");
          String payloadJson = row.getString("payload").orElse("{}");
          Map<String, Object> payload = Odin.getMapper().readValue(payloadJson, Map.class);

          // Create job instance using registry
          Job job = jobRegistry.createJob(jobType, payload);
          if (job != null) {
            // Route to appropriate queue based on execution mode
            if (job.getExecutionMode() == ExecutionMode.SEQUENTIAL) {
              sequentialJobQueue.submit(job);
              sequentialLoaded++;
            } else {
              jobQueue.offer(new JobInstance(job));
              parallelLoaded++;
            }
          }
        } catch (Exception e) {
          Logger.error("Failed to load pending job from row: {}", e.getMessage());
        }
      }

      Logger.info("Loaded {} parallel jobs and {} sequential jobs from database",
          parallelLoaded, sequentialLoaded);
    } catch (Exception e) {
      Logger.error("Failed to load pending jobs: {}", e.getMessage());
    }
  }

  /**
   * Parses a JSON string representing HTTP headers into a map.
   *
   * @param headersJson the JSON string containing headers.
   * @return a map of header names to their corresponding values. Returns an empty map if the input
   * is null, empty, or parsing fails.
   */
  private Map<String, String> parseHeaders(String headersJson) {
    if (headersJson == null || headersJson.trim().isEmpty()) {
      return new HashMap<>();
    }
    try {
      return Odin.getMapper().readValue(headersJson, Map.class);
    } catch (Exception e) {
      Logger.error("Failed to parse headers JSON: {}", e.getMessage());
      return new HashMap<>();
    }
  }


  /**
   * Periodically cleans up expired job results and old completed/failed jobs from the database.
   *
   * <p>
   * This method is intended to be called periodically by the {@link Freyr} instance to remove
   * outdated job results and old jobs that are no longer relevant. It removes job results that have
   * exceeded their expiration time and jobs that have been completed or failed more than 24 hours
   * ago. The method logs the number of records removed from each table.
   */
  private void cleanupExpiredData() {
    try {
      long now = Instant.now().toEpochMilli();

      // Cleanup expired results
      int expiredResults = mimir.executeSql(
          "DELETE FROM JOB_RESULTS WHERE expires_at < ?", now);

      // Cleanup old completed/failed jobs (older than 24 hours)
      long dayAgo = now - 86400000;
      int oldJobs = mimir.executeSql(
          "DELETE FROM JOBS WHERE status IN ('COMPLETED', 'FAILED') AND completed_at < ?", dayAgo);

      if (expiredResults > 0 || oldJobs > 0) {
        Logger.info("Cleanup: removed {} expired results and {} old jobs",
            expiredResults, oldJobs);
      }
    } catch (Exception e) {
      Logger.error("Failed to cleanup expired data: {}", e.getMessage());
    }
  }

  /**
   * Start the Freyr instance.
   * <p>
   * This method is intended to be called once to start the background tasks of the Freyr instance.
   * Currently, no background tasks are implemented, but this method can be overridden in
   * sub-classes to add custom background tasks.
   */
  @Override
  public void run() {
    // Background tasks can be added here if needed
  }

  /**
   * Get access to the Dead Letter Queue for admin operations
   */
  public DeadLetterQueue getDeadLetterQueue() {
    return deadLetterQueue;
  }

  /**
   * Get access to the Retry Scheduler for admin operations
   */
  public JobRetryScheduler getRetryScheduler() {
    return retryScheduler;
  }

  /**
   * Job status enum
   */
  public enum JobStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, TIMEOUT, RETRY_SCHEDULED, DEAD_LETTER
  }

  /**
   * Job instance class
   */
  private static class JobInstance {

    final Job job;
    final long createdAt;

    JobInstance(Job job) {
      this.job = job;
      this.createdAt = Instant.now().toEpochMilli();
    }
  }

  /**
   * Worker thread that processes jobs
   */
  private class JobWorker implements Runnable {


    /**
     * Worker thread's main execution loop.
     *
     * <p>This method continuously polls the job queue for new jobs to process.
     * If a job is available, it invokes the processJob method to execute it. The thread will run as
     * long as the 'running' flag is true.
     *
     * <p>It handles interruptions by setting the interrupt flag and breaking the loop,
     * and logs any unexpected exceptions that occur during job processing.
     */
    @Override
    public void run() {
      Thread.currentThread().setName("job-worker-" + Thread.currentThread().getName());

      while (running.get()) {
        try {
          JobInstance jobInstance = jobQueue.poll(1, TimeUnit.SECONDS);
          if (jobInstance != null) {
            processJob(jobInstance.job);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          Logger.error("Job worker thread error: {}", e.getMessage(), e);
        }
      }
    }

    /**
     * Process a job in parallel using the job queue.
     * <p>
     * This method executes the job in the context of the current thread and updates the job status
     * in the database. If the job fails, it will apply the retry logic using the configured retry
     * manager. If the job fails permanently or retries are exceeded, it will move the job to the
     * dead letter queue.
     *
     * @param job The job to process
     */
    private void processJob(Job job) {
      activeWorkers.incrementAndGet();

      try {
        Logger.trace("Processing parallel job: {}", job);

        updateJobStatus(job.getId(), JobStatus.PROCESSING, null,
            Instant.now().toEpochMilli(), null);
        job.execute();
        updateJobStatus(job.getId(), JobStatus.COMPLETED, null, null,
            Instant.now().toEpochMilli());

        completedJobs.incrementAndGet();
        Logger.trace("Completed parallel job: {}", job.getId());

      } catch (Exception e) {
        Logger.error("Failed to process parallel job {}: {}", job.getId(), e.getMessage(), e);

        int currentRetries = retryManager.getCurrentRetryCount(job.getId());
        JobRetryManager.RetryDecision decision = retryManager.shouldRetry(
            job.getId(), job.getType(), e, currentRetries, job.getMaxRetries());

        // Schedule for retry
        if (decision.shouldRetry()) {
          retryManager.scheduleRetry(job.getId(), decision.getRetryDelayMs(), e);
          retriedJobs.incrementAndGet();
          Logger.info("Parallel job {} scheduled for retry: {}", job.getId(), decision.getReason());
        } else {

          // Mark as permanently failed and move to DLQ
          retryManager.markAsPermanentlyFailed(job.getId(), e, decision.getReason());
          deadLetterQueue.moveToDeadLetterQueue(job.getId(), decision.getReason());
          failedJobs.incrementAndGet();
          Logger.warn("Parallel job {} permanently failed and moved to DLQ: {}", job.getId(),
              decision.getReason());
        }

      } finally {
        activeWorkers.decrementAndGet();
      }
    }

    /**
     * Updates the status of a job in the database, along with additional information: error
     * message, start time, and completion time.
     *
     * @param jobId        The ID of the job to update.
     * @param status       The new status of the job.
     * @param errorMessage An error message if the job failed; otherwise, null.
     * @param startedAt    The start time of the job; otherwise, null.
     * @param completedAt  The completion time of the job; otherwise, null.
     */
    private void updateJobStatus(String jobId, JobStatus status, String errorMessage,
        Long startedAt, Long completedAt) {
      try {
        mimir.executeSql(
            "UPDATE JOBS SET status = ?, error_message = ?, started_at = ?, completed_at = ? WHERE id = ?",
            status.name(), errorMessage, startedAt, completedAt, jobId);
      } catch (Exception e) {
        Logger.error("Failed to update job status for {}: {}", jobId, e.getMessage());
      }
    }
  }
} 