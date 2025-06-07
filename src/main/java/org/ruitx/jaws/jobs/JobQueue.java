package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.types.Row;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JobQueue - The New Job Processing System
 * 
 * This replaces Frigg's complex route-based async system with a clean,
 * framework-agnostic job queue. No more route resolution, no more mocking!
 * 
 * Key improvements over Frigg:
 * - Jobs are self-contained (no route resolution needed)
 * - No web framework dependency in workers
 * - Clean separation between web layer and job layer
 * - Much simpler database schema
 * - Industry-standard job queue patterns
 * - Support for both parallel and sequential execution modes
 */
public class JobQueue implements Runnable {
    
    private static final int DEFAULT_WORKER_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5 minutes
    
    private static JobQueue instance;
    private static final Object instanceLock = new Object();
    
    private final Mimir mimir = new Mimir();
    private final JobRegistry jobRegistry;
    private final ExecutorService workerPool;
    private final PriorityBlockingQueue<JobInstance> jobQueue;
    private final SequentialJobQueue sequentialJobQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final ScheduledExecutorService cleanupScheduler;
    
    // Statistics
    private final AtomicInteger totalJobs = new AtomicInteger(0);   
    private final AtomicInteger completedJobs = new AtomicInteger(0);
    private final AtomicInteger failedJobs = new AtomicInteger(0);
    
    private JobQueue(int workerThreads, int queueCapacity) {
        this.jobRegistry = new JobRegistry();
        this.workerPool = Executors.newFixedThreadPool(workerThreads, 
            r -> new Thread(r, "job-worker-" + Thread.currentThread().threadId()));
        this.jobQueue = new PriorityBlockingQueue<>(queueCapacity, 
            Comparator.comparingInt((JobInstance ji) -> ji.job.getPriority())
                     .thenComparingLong(ji -> ji.createdAt));
        this.sequentialJobQueue = new SequentialJobQueue();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "job-cleanup"));
        
        initializeDatabase();
        loadPendingJobs();
        
        Logger.info("JobQueue initialized with {} worker threads and queue capacity of {}", 
                   workerThreads, queueCapacity);
    }
    
    /**
     * Get the singleton instance
     */
    public static JobQueue getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new JobQueue(DEFAULT_WORKER_THREADS, DEFAULT_QUEUE_CAPACITY);
                }
            }
        }
        return instance;
    }
    
    /**
     * Submit a job for processing
     * Routes to appropriate queue based on execution mode
     */
    public String submit(Job job) {
        try {
            // Persist to database first
            persistJob(job);
            
            // Route to appropriate queue based on execution mode
            if (job.getExecutionMode() == ExecutionMode.SEQUENTIAL) {
                boolean queued = sequentialJobQueue.submit(job);
                if (!queued) {
                    throw new RuntimeException("Sequential queue is full");
                }
                Logger.info("Job submitted to sequential queue: {}", job);
            } else {
                // Add to parallel processing queue
                jobQueue.offer(new JobInstance(job));
                Logger.info("Job submitted to parallel queue: {}", job);
            }
            
            totalJobs.incrementAndGet();
            
            return job.getId();
            
        } catch (Exception e) {
            Logger.error("Failed to submit job: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit job", e);
        }
    }
    
    /**
     * Get job status
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
     * Get job result
     */
    public JobResult getJobResult(String jobId) {
        try {
            Row row = mimir.getRow(
                "SELECT * FROM JOB_RESULTS WHERE job_id = ? AND expires_at > ?", 
                jobId, Instant.now().toEpochMilli());
            
            if (row != null) {
                Map<String, String> headers = parseHeaders(row.getString("headers").orElse(null));
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
     * Start the job processing system
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
            
            // Start cleanup scheduler
            cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredData, 
                                               CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, 
                                               TimeUnit.MILLISECONDS);
            
            Logger.info("JobQueue started with {} parallel workers and 1 sequential worker", DEFAULT_WORKER_THREADS);
        }
    }
    
    /**
     * Shutdown the job processing system
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            Logger.info("Shutting down JobQueue processing system...");
            
            workerPool.shutdown();
            sequentialJobQueue.shutdown();
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
        stats.put("parallelQueueSize", jobQueue.size());
        stats.put("sequentialQueueSize", sequentialJobQueue.getQueueSize());
        stats.put("activeParallelWorkers", activeWorkers.get());
        stats.put("sequentialProcessing", sequentialJobQueue.isProcessingJob());
        stats.put("running", running.get());
        
        // Add sequential queue statistics
        Map<String, Object> sequentialStats = sequentialJobQueue.getStatistics();
        stats.put("sequential", sequentialStats);
        
        return stats;
    }
    
    // Private helper methods
    
    private void initializeDatabase() {
        try {
            // Create JOBS table if it doesn't exist
            mimir.executeSql("""
                CREATE TABLE IF NOT EXISTS JOBS (
                    id VARCHAR(36) PRIMARY KEY,
                    type VARCHAR(100) NOT NULL,
                    payload TEXT,
                    priority INTEGER DEFAULT 5,
                    max_retries INTEGER DEFAULT 3,
                    current_retries INTEGER DEFAULT 0,
                    timeout_ms BIGINT DEFAULT 30000,
                    execution_mode VARCHAR(20) DEFAULT 'PARALLEL',
                    status VARCHAR(20) DEFAULT 'PENDING',
                    created_at BIGINT NOT NULL,
                    started_at BIGINT,
                    completed_at BIGINT,
                    error_message TEXT,
                    client_id VARCHAR(100),
                    user_id INTEGER
                )
            """);
            
            // Create JOB_RESULTS table if it doesn't exist
            mimir.executeSql("""
                CREATE TABLE IF NOT EXISTS JOB_RESULTS (
                    id VARCHAR(36) PRIMARY KEY,
                    job_id VARCHAR(36) NOT NULL,
                    status_code INTEGER DEFAULT 200,
                    headers TEXT,
                    body TEXT,
                    content_type VARCHAR(100),
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
            """);
            
            Logger.info("Job database tables initialized");
        } catch (Exception e) {
            Logger.error("Failed to initialize job database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize job database", e);
        }
    }
    
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
    
    private void loadPendingJobs() {
        try {
            List<Row> rows = mimir.getRows("SELECT * FROM JOBS WHERE status IN ('PENDING', 'PROCESSING') ORDER BY priority, created_at");
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
    
    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.trim().isEmpty()) return new HashMap<>();
        try {
            return Odin.getMapper().readValue(headersJson, Map.class);
        } catch (Exception e) {
            Logger.error("Failed to parse headers JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
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
    
    @Override
    public void run() {
        // Background tasks can be added here if needed
    }
    
    /**
     * Job status enum
     */
    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, TIMEOUT
    }
    
    /**
     * Internal job instance wrapper
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
        @Override
        public void run() {
            Thread.currentThread().setName("job-worker-" + Thread.currentThread().threadId());
            
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
        
        private void processJob(Job job) {
            activeWorkers.incrementAndGet();
            
            try {
                Logger.info("Processing parallel job: {}", job);
                
                // Update status to processing
                updateJobStatus(job.getId(), JobStatus.PROCESSING, null, Instant.now().toEpochMilli(), null);
                
                // Execute the job
                job.execute();
                
                // Mark as completed
                updateJobStatus(job.getId(), JobStatus.COMPLETED, null, null, Instant.now().toEpochMilli());
                
                completedJobs.incrementAndGet();
                Logger.info("Completed parallel job: {}", job.getId());
                
            } catch (Exception e) {
                Logger.error("Failed to process parallel job {}: {}", job.getId(), e.getMessage(), e);
                
                // Mark as failed
                updateJobStatus(job.getId(), JobStatus.FAILED, e.getMessage(), null, Instant.now().toEpochMilli());
                failedJobs.incrementAndGet();
                
            } finally {
                activeWorkers.decrementAndGet();
            }
        }
        
        private void updateJobStatus(String jobId, JobStatus status, String errorMessage, Long startedAt, Long completedAt) {
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