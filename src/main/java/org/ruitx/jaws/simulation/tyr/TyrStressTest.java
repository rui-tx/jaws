package org.ruitx.jaws.simulation.tyr;

import org.ruitx.jaws.simulation.SimulationManager;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TyrStressTest {
    private static final int THREAD_COUNT = 50;
    private static final int OPERATIONS_PER_THREAD = 1000;
    private static final int TOKEN_EXPIRATION_MINUTES = 5; // Short expiration for stress testing
    private static final double SUCCESS_RATE = 0.8;

    public static void main(String[] args) throws InterruptedException {
        TyrSimulator tyr = new TyrSimulator();
        tyr.initialize();

        Logger.info("Starting Tyr Stress Test");
        Logger.info("Threads: {}, Operations per thread: {}", THREAD_COUNT, OPERATIONS_PER_THREAD);

        // Test 1: High Concurrent Token Creation
        testConcurrentTokenCreation(tyr);

        // Test 2: Rapid Token Expiration
        testTokenExpiration(tyr);

        // Test 3: Mixed Success/Failure Rates
        testMixedSuccessRates(tyr);

        // Test 4: Fault Injection Under Load
        testFaultInjectionUnderLoad(tyr);

        // Test 5: Memory Usage
        testMemoryUsage(tyr);
    }

    private static void testConcurrentTokenCreation(TyrSimulator tyr) throws InterruptedException {
        Logger.info("\n=== Test 1: High Concurrent Token Creation ===");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger successfulLogins = new AtomicInteger(0);
        AtomicInteger failedLogins = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    String userId = "user_" + threadId + "_" + j;
                    String token = tyr.createToken(userId, "password123");
                    if (token != null) {
                        successfulLogins.incrementAndGet();
                    } else {
                        failedLogins.incrementAndGet();
                    }
                }
            }));
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Logger.error("Thread execution failed: {}", e.getMessage());
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        Logger.info("Concurrent Token Creation Results:");
        Logger.info("Successful Logins: {}", successfulLogins.get());
        Logger.info("Failed Logins: {}", failedLogins.get());
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
    }

    private static void testTokenExpiration(TyrSimulator tyr) {
        Logger.info("\n=== Test 2: Rapid Token Expiration ===");
        // Create tokens that will expire quickly
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String token = tyr.createToken("expiring_user_" + i, "password123");
            if (token != null) {
                tokens.add(token);
            }
        }

        // Wait for tokens to expire
        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(TOKEN_EXPIRATION_MINUTES) + 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check how many tokens are still valid
        int validTokens = 0;
        for (String token : tokens) {
            if (tyr.isTokenValid(token)) {
                validTokens++;
            }
        }

        Logger.info("Token Expiration Results:");
        Logger.info("Initial Tokens: {}", tokens.size());
        Logger.info("Valid Tokens After Expiration: {}", validTokens);
        Logger.info("Expired Tokens: {}", tyr.getExpiredTokens());
    }

    private static void testMixedSuccessRates(TyrSimulator tyr) {
        Logger.info("\n=== Test 3: Mixed Success/Failure Rates ===");
        int totalAttempts = 1000;
        int successfulAttempts = 0;
        int failedAttempts = 0;

        for (int i = 0; i < totalAttempts; i++) {
            String userId = "mixed_user_" + i;
            if (Math.random() < SUCCESS_RATE) {
                String token = tyr.createToken(userId, "password123");
                if (token != null) {
                    successfulAttempts++;
                } else {
                    failedAttempts++;
                }
            } else {
                String token = tyr.createToken(userId, "wrongpassword");
                if (token == null) {
                    failedAttempts++;
                }
            }
        }

        Logger.info("Mixed Success Rates Results:");
        Logger.info("Total Attempts: {}", totalAttempts);
        Logger.info("Successful Attempts: {}", successfulAttempts);
        Logger.info("Failed Attempts: {}", failedAttempts);
        Logger.info("Actual Success Rate: {}%", (successfulAttempts * 100.0) / totalAttempts);
    }

    private static void testFaultInjectionUnderLoad(TyrSimulator tyr) throws InterruptedException {
        Logger.info("\n=== Test 4: Fault Injection Under Load ===");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();

        // Start token creation threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    String userId = "fault_user_" + threadId + "_" + j;
                    tyr.createToken(userId, "password123");
                }
            }));
        }

        // Inject various faults while under load
        Thread.sleep(1000); // Let some tokens be created
        tyr.injectFault("INVALID_TOKENS");
        Logger.info("Injected INVALID_TOKENS fault");
        Thread.sleep(1000);

        tyr.injectFault("RESET");
        Logger.info("Reset system");
        Thread.sleep(1000);

        tyr.injectFault("EXPIRED_TOKENS");
        Logger.info("Injected EXPIRED_TOKENS fault");
        Thread.sleep(1000);

        tyr.injectFault("RESET");
        Logger.info("Reset system");
        Thread.sleep(1000);

        tyr.injectFault("CORRUPTED_TOKENS");
        Logger.info("Injected CORRUPTED_TOKENS fault");
        Thread.sleep(1000);

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Logger.error("Thread execution failed: {}", e.getMessage());
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        Logger.info("Fault Injection Results:");
        Logger.info("Successful Logins: {}", tyr.getSuccessfulLogins());
        Logger.info("Failed Logins: {}", tyr.getFailedLogins());
        Logger.info("Expired Tokens: {}", tyr.getExpiredTokens());
        Logger.info("Remaining Valid Tokens: {}", tyr.getValidTokensCount());
    }

    private static void testMemoryUsage(TyrSimulator tyr) {
        Logger.info("\n=== Test 5: Memory Usage ===");
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Create a large number of tokens
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            String token = tyr.createToken("memory_user_" + i, "password123");
            if (token != null) {
                tokens.add(token);
            }
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;

        Logger.info("Memory Usage Results:");
        Logger.info("Initial Memory: {} bytes", initialMemory);
        Logger.info("Final Memory: {} bytes", finalMemory);
        Logger.info("Memory Used: {} bytes", memoryUsed);
        Logger.info("Memory Used per Token: {} bytes", memoryUsed / tokens.size());
    }
} 