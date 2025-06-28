package org.ruitx.jaws.components.freyr;

import org.ruitx.jaws.interfaces.Job;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base implementation of Job interface providing common functionality.
 * This class encapsulates job properties such as ID, type, priority,
 * max retries, timeout, execution mode, and payload.
 * <p>
 * Concrete job classes can extend this to avoid boilerplate code.
 */
public abstract class BaseJob implements Job {

    private final String id;
    private final String type;
    private final int priority;
    private final int maxRetries;
    private final long timeoutMs;
    private final ExecutionMode executionMode;
    private final Map<String, Object> payload;

    /**
     * Constructor for creating a new job with execution mode
     *
     * @param type          the job type
     * @param executionMode the execution mode (e.g., PARALLEL, SEQUENTIAL)
     * @param priority      the job priority (lower number = higher priority)
     * @param maxRetries    the maximum number of retries for this job
     * @param timeoutMs     the timeout in milliseconds for this job
     * @param payload       the job payload containing additional data
     */
    protected BaseJob(String type,
                      ExecutionMode executionMode,
                      int priority,
                      int maxRetries,
                      long timeoutMs,
                      Map<String, Object> payload) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.DEFAULT;
        this.priority = priority;
        this.maxRetries = maxRetries;
        this.timeoutMs = timeoutMs;
        this.payload = new HashMap<>(payload != null ? payload : Map.of());

        // Add creation timestamp to payload
        this.payload.put("createdAt", Instant.now().toEpochMilli());
    }

    /**
     * Constructor for creating a new job, defaults to PARALLEL
     *
     * @param type       the job type
     * @param priority   the job priority (lower number = higher priority)
     * @param maxRetries the maximum number of retries for this job
     * @param timeoutMs  the timeout in milliseconds for this job
     * @param payload    the job payload containing additional data
     */
    protected BaseJob(String type, int priority, int maxRetries, long timeoutMs, Map<String, Object> payload) {
        this(type, ExecutionMode.DEFAULT, priority, maxRetries, timeoutMs, payload);
    }

    /**
     * Constructor for jobs with execution mode and default settings, defaults to PARALLEL
     * It defaults to priority 5, 3 retries, and 30 seconds timeout.
     *
     * @param type          the job type
     * @param executionMode the execution mode (e.g., PARALLEL, SEQUENTIAL)
     * @param payload       the job payload containing additional data
     */
    protected BaseJob(String type, ExecutionMode executionMode, Map<String, Object> payload) {
        this(type, executionMode, 5, 3, 30000L, payload); // Default: priority 5, 3 retries, 30s timeout
    }

    /**
     * Constructor for jobs with default settings, defaults to PARALLEL
     * It defaults to DEFAULT execution mode, priority 5, 3 retries, and 30 seconds timeout.
     *
     * @param type    the job type
     * @param payload the job payload containing additional data
     */
    protected BaseJob(String type, Map<String, Object> payload) {
        this(type, ExecutionMode.DEFAULT, 5, 3, 30000L, payload); // Default: priority 5, 3 retries, 30s timeout
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Helper method to get typed data from payload
     * This method checks if the value exists and is of the expected type.
     *
     * @param key  the key to look up in the payload
     * @param type the expected type of the value
     * @return the value cast to the expected type, or null if not found or type mismatch
     */
    @SuppressWarnings("unchecked")
    protected <T> T get(String key, Class<T> type) {
        Object value = payload.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Helper method to get string data from payload
     * This method retrieves the value associated with the key and casts it to String.
     *
     * @param key the key to look up in the payload
     * @return the value as a String, or null if not found or type mismatch
     */
    protected String getString(String key) {
        return get(key, String.class);
    }

    /**
     * Helper method to get integer data from payload
     * This method retrieves the value associated with the key and converts it to Integer.
     *
     * @param key the key to look up in the payload
     * @return the value as an Integer, or null if not found or type mismatch
     */
    protected Integer getInteger(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * Helper method to get long data from payload
     * This method retrieves the value associated with the key and converts it to Long.
     *
     * @param key the key to look up in the payload
     * @return the value as a Long, or null if not found or type mismatch
     */
    protected Long getLong(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * Helper method to get boolean data from payload
     * This method retrieves the value associated with the key and converts it to Boolean.
     *
     * @param key the key to look up in the payload
     * @return the value as a Boolean, or null if not found or type mismatch
     */
    protected Boolean getBoolean(String key) {
        return get(key, Boolean.class);
    }

    @Override
    public String toString() {
        return String.format("Job{id='%s', type='%s', priority=%d, mode=%s}", id, type, priority, executionMode);
    }
} 