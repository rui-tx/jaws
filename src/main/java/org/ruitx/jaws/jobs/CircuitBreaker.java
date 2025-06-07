package org.ruitx.jaws.jobs;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.components.Odin;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;   
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NOT FINISHED
 * CircuitBreaker - Circuit breaker pattern for external dependencies
 * 
 * This class implements the circuit breaker pattern to protect against cascading failures
 * and provide fast-fail behavior for external services that are experiencing issues.
 */
public class CircuitBreaker {
    
    /**
     * Circuit breaker states
     */
    public enum State {
        CLOSED,    // Normal operation - requests pass through
        OPEN,      // Circuit is open - requests fail fast
        HALF_OPEN  // Testing recovery - limited requests pass through
    }
    
    /**
     * Circuit breaker configuration
     */
    public static class Configuration {
        public final int failureThreshold;           // Number of failures to open circuit
        public final double failureRateThreshold;    // Percentage of failures to open circuit
        public final long timeWindowMs;              // Time window for failure rate calculation
        public final long openTimeoutMs;             // How long to keep circuit open
        public final int halfOpenMaxCalls;           // Max calls allowed in half-open state
        public final long slowCallThresholdMs;       // Threshold for considering a call slow
        public final double slowCallRateThreshold;   // Percentage of slow calls to open circuit
        
        public Configuration(int failureThreshold, double failureRateThreshold, 
                           long timeWindowMs, long openTimeoutMs, int halfOpenMaxCalls,
                           long slowCallThresholdMs, double slowCallRateThreshold) {
            this.failureThreshold = failureThreshold;
            this.failureRateThreshold = failureRateThreshold;
            this.timeWindowMs = timeWindowMs;
            this.openTimeoutMs = openTimeoutMs;
            this.halfOpenMaxCalls = halfOpenMaxCalls;
            this.slowCallThresholdMs = slowCallThresholdMs;
            this.slowCallRateThreshold = slowCallRateThreshold;
        }
        
        // Default configuration for external APIs
        public static Configuration forExternalAPI() {
            return new Configuration(
                5,      // 5 failures
                50.0,   // 50% failure rate
                60000L, // 1 minute window
                30000L, // 30 seconds open timeout
                3,      // 3 calls in half-open
                5000L,  // 5 seconds slow call threshold
                80.0    // 80% slow call rate
            );
        }
        
        // Configuration for database operations
        public static Configuration forDatabase() {
            return new Configuration(
                3,      // 3 failures (more sensitive)
                30.0,   // 30% failure rate
                30000L, // 30 seconds window
                60000L, // 1 minute open timeout
                2,      // 2 calls in half-open
                3000L,  // 3 seconds slow call threshold
                70.0    // 70% slow call rate
            );
        }
        
        // Configuration for internal services
        public static Configuration forInternalService() {
            return new Configuration(
                10,     // 10 failures
                60.0,   // 60% failure rate
                120000L,// 2 minutes window
                15000L, // 15 seconds open timeout
                5,      // 5 calls in half-open
                2000L,  // 2 seconds slow call threshold
                90.0    // 90% slow call rate
            );
        }
    }
    
    /**
     * Circuit breaker call result
     */
    public static class CallResult {
        public final boolean success;
        public final long durationMs;
        public final Throwable exception;
        public final long timestamp;
        
        public CallResult(boolean success, long durationMs, Throwable exception) {
            this.success = success;
            this.durationMs = durationMs;
            this.exception = exception;
            this.timestamp = Instant.now().toEpochMilli();
        }
        
        public static CallResult success(long durationMs) {
            return new CallResult(true, durationMs, null);
        }
        
        public static CallResult failure(long durationMs, Throwable exception) {
            return new CallResult(false, durationMs, exception);
        }
        
        public boolean isSlow(long thresholdMs) {
            return durationMs > thresholdMs;
        }
    }
    
    /**
     * Circuit breaker exception thrown when circuit is open
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String serviceName) {
            super("Circuit breaker is OPEN for service: " + serviceName);
        }
    }
    
    /**
     * Interface for operations that can be protected by circuit breaker
     */
    @FunctionalInterface
    public interface ProtectedOperation<T> {
        T execute() throws Exception;
    }
    
    // Instance fields
    private final String serviceName;
    private final Configuration config;
    private final Mimir mimir = new Mimir();
    
    // State management
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger halfOpenCallCount = new AtomicInteger(0);
    
    // Metrics tracking
    private final CircularBuffer callResults;
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successfulCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);
    private final AtomicInteger slowCalls = new AtomicInteger(0);
    
    // Global circuit breaker registry
    private static final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    /**
     * Private constructor - use factory methods
     */
    private CircuitBreaker(String serviceName, Configuration config) {
        this.serviceName = serviceName;
        this.config = config;
        this.callResults = new CircularBuffer(100); // Keep last 100 call results
        
        Logger.info("Circuit breaker created for service: {} with config: failures={}, rate={}%, window={}ms", 
                   serviceName, config.failureThreshold, config.failureRateThreshold, config.timeWindowMs);
    }
    
    /**
     * Get or create a circuit breaker for a service
     */
    public static CircuitBreaker forService(String serviceName, Configuration config) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> new CircuitBreaker(name, config));
    }
    
    /**
     * Get circuit breaker for external API with default config
     */
    public static CircuitBreaker forExternalAPI(String serviceName) {
        return forService(serviceName, Configuration.forExternalAPI());
    }
    
    /**
     * Get circuit breaker for database with default config
     */
    public static CircuitBreaker forDatabase(String serviceName) {
        return forService(serviceName, Configuration.forDatabase());
    }
    
    /**
     * Execute an operation protected by the circuit breaker
     */
    public <T> T execute(ProtectedOperation<T> operation) throws Exception {
        // Check if circuit breaker allows the call
        if (!allowCall()) {
            throw new CircuitBreakerOpenException(serviceName);
        }
        
        long startTime = System.currentTimeMillis();
        boolean success = false;
        Throwable exception = null;
        
        try {
            T result = operation.execute();
            success = true;
            return result;
            
        } catch (Exception e) {
            exception = e;
            throw e;
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordCallResult(new CallResult(success, duration, exception));
        }
    }
    
    /**
     * Check if a call should be allowed through the circuit breaker
     */
    private boolean allowCall() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Check if it's time to transition to half-open
                if (shouldTransitionToHalfOpen()) {
                    transitionToHalfOpen();
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited calls in half-open state
                return halfOpenCallCount.get() < config.halfOpenMaxCalls;
                
            default:
                return false;
        }
    }
    
    /**
     * Record the result of a call and update circuit breaker state
     */
    private void recordCallResult(CallResult result) {
        // Update metrics
        totalCalls.incrementAndGet();
        if (result.success) {
            successfulCalls.incrementAndGet();
        } else {
            failedCalls.incrementAndGet();
        }
        
        if (result.isSlow(config.slowCallThresholdMs)) {
            slowCalls.incrementAndGet();
        }
        
        // Add to circular buffer for analysis
        callResults.add(result);
        
        State currentState = state.get();
        
        // Handle state transitions based on call result
        switch (currentState) {
            case CLOSED:
                if (shouldOpenCircuit()) {
                    transitionToOpen();
                }
                break;
                
            case HALF_OPEN:
                halfOpenCallCount.incrementAndGet();
                
                if (result.success) {
                    // If we've had enough successful calls, close the circuit
                    if (halfOpenCallCount.get() >= config.halfOpenMaxCalls) {
                        transitionToClosed();
                    }
                } else {
                    // Any failure in half-open state reopens the circuit
                    transitionToOpen();
                }
                break;
                
            case OPEN:
                // No action needed, circuit remains open
                break;
        }
        
        // Record metrics to database for monitoring
        recordMetrics(result);
    }
    
    /**
     * Check if circuit should be opened based on failure rate
     */
    private boolean shouldOpenCircuit() {
        long windowStart = System.currentTimeMillis() - config.timeWindowMs;
        
        CallResult[] recentCalls = callResults.getCallsSince(windowStart);
        
        if (recentCalls.length < config.failureThreshold) {
            return false; // Not enough calls to make a decision
        }
        
        int failures = 0;
        int slowCalls = 0;
        
        for (CallResult call : recentCalls) {
            if (!call.success) {
                failures++;
            }
            if (call.isSlow(config.slowCallThresholdMs)) {
                slowCalls++;
            }
        }
        
        double failureRate = (double) failures / recentCalls.length * 100.0;
        double slowCallRate = (double) slowCalls / recentCalls.length * 100.0;
        
        boolean shouldOpen = failures >= config.failureThreshold || 
                           failureRate >= config.failureRateThreshold ||
                           slowCallRate >= config.slowCallRateThreshold;
        
        if (shouldOpen) {
            Logger.warn("Circuit breaker opening for service {}: failures={}/{}, failure_rate={:.1f}%, slow_rate={:.1f}%", 
                       serviceName, failures, recentCalls.length, failureRate, slowCallRate);
        }
        
        return shouldOpen;
    }
    
    /**
     * Check if circuit should transition from open to half-open
     */
    private boolean shouldTransitionToHalfOpen() {
        long openTime = System.currentTimeMillis() - lastStateChangeTime.get();
        return openTime >= config.openTimeoutMs;
    }
    
    /**
     * Transition to OPEN state
     */
    private void transitionToOpen() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) || 
            state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            
            lastStateChangeTime.set(System.currentTimeMillis());
            halfOpenCallCount.set(0);
            
            Logger.warn("Circuit breaker OPENED for service: {}", serviceName);
            recordStateChange(State.OPEN);
        }
    }
    
    /**
     * Transition to HALF_OPEN state
     */
    private void transitionToHalfOpen() {
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            lastStateChangeTime.set(System.currentTimeMillis());
            halfOpenCallCount.set(0);
            
            Logger.info("Circuit breaker transitioning to HALF_OPEN for service: {}", serviceName);
            recordStateChange(State.HALF_OPEN);
        }
    }
    
    /**
     * Transition to CLOSED state
     */
    private void transitionToClosed() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            lastStateChangeTime.set(System.currentTimeMillis());
            halfOpenCallCount.set(0);
            
            Logger.info("Circuit breaker CLOSED for service: {}", serviceName);
            recordStateChange(State.CLOSED);
        }
    }
    
    /**
     * Record state change to database for monitoring
     */
    private void recordStateChange(State newState) {
        try {
            long timestamp = Instant.now().toEpochMilli();
            
            // This could be stored in a dedicated circuit_breaker_events table
            String eventData = Odin.getMapper().writeValueAsString(Map.of(
                "service", serviceName,
                "state", newState.name(),
                "timestamp", timestamp,
                "totalCalls", totalCalls.get(),
                "failedCalls", failedCalls.get(),
                "successRate", getSuccessRate()
            ));
            
            Logger.info("Circuit breaker state change recorded: {}", eventData);
            
        } catch (Exception e) {
            Logger.warn("Failed to record circuit breaker state change: {}", e.getMessage());
        }
    }
    
    /**
     * Record call metrics to database
     */
    private void recordMetrics(CallResult result) {
        // This is called for every operation, so we sample to avoid too much DB load
        if (totalCalls.get() % 10 == 0) { // Sample every 10th call
            try {
                String metricsData = Odin.getMapper().writeValueAsString(Map.of(
                    "service", serviceName,
                    "state", state.get().name(),
                    "totalCalls", totalCalls.get(),
                    "successfulCalls", successfulCalls.get(),
                    "failedCalls", failedCalls.get(),
                    "slowCalls", slowCalls.get(),
                    "lastCallSuccess", result.success,
                    "lastCallDuration", result.durationMs,
                    "timestamp", result.timestamp
                ));
                
                Logger.debug("Circuit breaker metrics recorded: {}", metricsData);
                
            } catch (Exception e) {
                Logger.warn("Failed to record circuit breaker metrics: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get current circuit breaker statistics
     */
    public CircuitBreakerStats getStatistics() {
        long windowStart = System.currentTimeMillis() - config.timeWindowMs;
        CallResult[] recentCalls = callResults.getCallsSince(windowStart);
        
        int recentFailures = 0;
        int recentSlowCalls = 0;
        
        for (CallResult call : recentCalls) {
            if (!call.success) recentFailures++;
            if (call.isSlow(config.slowCallThresholdMs)) recentSlowCalls++;
        }
        
        double recentFailureRate = recentCalls.length > 0 ? 
            (double) recentFailures / recentCalls.length * 100.0 : 0.0;
        double recentSlowCallRate = recentCalls.length > 0 ? 
            (double) recentSlowCalls / recentCalls.length * 100.0 : 0.0;
        
        return new CircuitBreakerStats(
            serviceName, state.get(), lastStateChangeTime.get(),
            totalCalls.get(), successfulCalls.get(), failedCalls.get(), slowCalls.get(),
            getSuccessRate(), recentCalls.length, recentFailures, recentFailureRate, recentSlowCallRate
        );
    }
    
    /**
     * Get overall success rate
     */
    private double getSuccessRate() {
        int total = totalCalls.get();
        return total > 0 ? (double) successfulCalls.get() / total * 100.0 : 100.0;
    }
    
    /**
     * Reset circuit breaker (for testing/admin use)
     */
    public void reset() {
        state.set(State.CLOSED);
        lastStateChangeTime.set(System.currentTimeMillis());
        halfOpenCallCount.set(0);
        totalCalls.set(0);
        successfulCalls.set(0);
        failedCalls.set(0);
        slowCalls.set(0);
        callResults.clear();
        
        Logger.info("Circuit breaker reset for service: {}", serviceName);
    }
    
    /**
     * Get all circuit breaker instances
     */
    public static Map<String, CircuitBreaker> getAllCircuitBreakers() {
        return Map.copyOf(circuitBreakers);
    }
    
    /**
     * Circuit breaker statistics
     */
    public static class CircuitBreakerStats {
        public final String serviceName;
        public final State state;
        public final long lastStateChangeTime;
        public final int totalCalls;
        public final int successfulCalls;
        public final int failedCalls;
        public final int slowCalls;
        public final double successRate;
        public final int recentCallCount;
        public final int recentFailures;
        public final double recentFailureRate;
        public final double recentSlowCallRate;
        
        public CircuitBreakerStats(String serviceName, State state, long lastStateChangeTime,
                                 int totalCalls, int successfulCalls, int failedCalls, int slowCalls,
                                 double successRate, int recentCallCount, int recentFailures,
                                 double recentFailureRate, double recentSlowCallRate) {
            this.serviceName = serviceName;
            this.state = state;
            this.lastStateChangeTime = lastStateChangeTime;
            this.totalCalls = totalCalls;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.slowCalls = slowCalls;
            this.successRate = successRate;
            this.recentCallCount = recentCallCount;
            this.recentFailures = recentFailures;
            this.recentFailureRate = recentFailureRate;
            this.recentSlowCallRate = recentSlowCallRate;
        }
    }
    
    /**
     * Circular buffer for storing recent call results
     */
    private static class CircularBuffer {
        private final CallResult[] buffer;
        private final int capacity;
        private volatile int position = 0;
        private volatile int size = 0;
        
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new CallResult[capacity];
        }
        
        public synchronized void add(CallResult result) {
            buffer[position] = result;
            position = (position + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        }
        
        public synchronized CallResult[] getCallsSince(long timestamp) {
            return java.util.Arrays.stream(buffer, 0, size)
                .filter(call -> call != null && call.timestamp >= timestamp)
                .toArray(CallResult[]::new);
        }
        
        public synchronized void clear() {
            java.util.Arrays.fill(buffer, null);
            position = 0;
            size = 0;
        }
    }
} 