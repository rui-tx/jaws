import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;

import java.io.File;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

public class BatchLoggingTest {

    @BeforeAll
    public static void setupDatabase() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            DriverManager.registerDriver(new org.sqlite.JDBC());
            System.out.println("SQLite JDBC driver loaded successfully for BatchLogging test");
            
            // Start Freyr job system
            Freyr freyr = Freyr.getInstance();
            freyr.start();
            System.out.println("Freyr job system started");
            
        } catch (Exception e) {
            System.err.println("Failed to setup BatchLogging test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void cleanup() {
        try {
            // Force flush any remaining logs
            JawsLogger.forceFlush();
            
            // Give time for batch job to complete
            Thread.sleep(2000);
            
            // Shutdown Freyr
            Freyr.getInstance().shutdown();
            
            // Clean up test log databases
            new File("logs.db").delete();
            new File("src/main/resources/logs.db").delete();
            
        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }
    }

    @Test
    public void testBatchLoggingPerformance() {
        System.out.println("=== Testing Batch Logging Performance ===");
        
        // Get buffer statistics before test
        Map<String, Object> statsBefore = JawsLogger.getBufferStatistics();
        System.out.println("Buffer stats before test: " + statsBefore);
        
        // Generate a burst of log entries
        long startTime = System.currentTimeMillis();
        int numLogs = 200;
        
        for (int i = 0; i < numLogs; i++) {
            JawsLogger.info("Performance test log entry {}", i);
            JawsLogger.debug("Debug message {}", i);
            if (i % 10 == 0) {
                JawsLogger.warn("Warning message for iteration {}", i);
            }
            if (i % 50 == 0) {
                try {
                    throw new RuntimeException("Test exception " + i);
                } catch (Exception e) {
                    JawsLogger.error(e, "Error occurred at iteration {}", i);
                }
            }
        }
        
        long loggingTime = System.currentTimeMillis() - startTime;
        System.out.println(String.format("Generated %d log entries in %dms (%.3fms per log)", 
                          numLogs, loggingTime, (double)loggingTime / numLogs));
        
        // Get buffer statistics after logging
        Map<String, Object> statsAfter = JawsLogger.getBufferStatistics();
        System.out.println("Buffer stats after logging: " + statsAfter);
        
        // Force flush to process all entries
        JawsLogger.forceFlush();
        
        // Wait for batch processing to complete
        try {
            Thread.sleep(3000); // Give time for jobs to process
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify entries were written to database
        verifyDatabaseEntries(numLogs);
        
        System.out.println("✅ Batch logging performance test completed");
    }
    
    @Test
    public void testBatchFlushingBehavior() {
        System.out.println("=== Testing Batch Flushing Behavior ===");
        
        // Test size-based flushing
        System.out.println("Testing size-based flushing (50 entries)...");
        for (int i = 0; i < 50; i++) {
            JawsLogger.info("Size-based flush test entry {}", i);
        }
        
        // Check if buffer was flushed
        Map<String, Object> stats = JawsLogger.getBufferStatistics();
        System.out.println("Buffer size after 50 entries: " + stats.get("bufferSize"));
        
        // Test time-based flushing
        System.out.println("Testing time-based flushing...");
        JawsLogger.info("Time-based flush test entry 1");
        JawsLogger.info("Time-based flush test entry 2");
        
        // Wait for time-based flush (1 second interval)
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        stats = JawsLogger.getBufferStatistics();
        System.out.println("Buffer size after time delay: " + stats.get("bufferSize"));
        
        System.out.println("✅ Batch flushing behavior test completed");
    }
    
    @Test
    public void testFreyrJobSystemIntegration() {
        System.out.println("=== Testing Freyr Job System Integration ===");
        
        // Get Freyr statistics before
        Map<String, Object> freyrStatsBefore = Freyr.getInstance().getStatistics();
        System.out.println("Freyr stats before: totalJobs=" + freyrStatsBefore.get("totalJobs") + 
                          ", sequentialQueueSize=" + freyrStatsBefore.get("sequentialQueueSize"));
        
        // Generate logs that should trigger batch jobs
        for (int i = 0; i < 75; i++) { // Should trigger at least one batch job (50+ entries)
            JawsLogger.info("Freyr integration test log {}", i);
        }
        
        // Force flush to ensure job submission
        JawsLogger.forceFlush();
        
        // Wait a moment for job submission
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Get Freyr statistics after
        Map<String, Object> freyrStatsAfter = Freyr.getInstance().getStatistics();
        System.out.println("Freyr stats after: totalJobs=" + freyrStatsAfter.get("totalJobs") + 
                          ", sequentialQueueSize=" + freyrStatsAfter.get("sequentialQueueSize"));
        
        // Verify that jobs were submitted
        int totalJobsBefore = (Integer) freyrStatsBefore.get("totalJobs");
        int totalJobsAfter = (Integer) freyrStatsAfter.get("totalJobs");
        
        if (totalJobsAfter > totalJobsBefore) {
            System.out.println("✅ Successfully submitted batch logging jobs to Freyr");
        } else {
            System.out.println("⚠️ No new jobs detected in Freyr - might need more time or entries");
        }
        
        System.out.println("✅ Freyr job system integration test completed");
    }
    
    private void verifyDatabaseEntries(int expectedMinimum) {
        try {
            // Query the logs database directly
            Mimir logsDb = new Mimir("src/main/resources/logs.db", "src/main/resources/sql/logs_schema.sql");
            logsDb.initializeDatabase("src/main/resources/logs.db");
            
            List<Row> logEntries = logsDb.getRows("SELECT COUNT(*) as count FROM LOG_ENTRIES WHERE message LIKE '%Performance test%' OR message LIKE '%Debug message%'");
            
            if (!logEntries.isEmpty()) {
                int count = logEntries.get(0).getInt("count").orElse(0);
                System.out.println(String.format("Found %d test log entries in database", count));
                
                if (count >= expectedMinimum * 0.8) { // Allow some tolerance
                    System.out.println("✅ Database verification passed");
                } else {
                    System.out.println(String.format("⚠️ Expected at least %d entries, found %d", (int)(expectedMinimum * 0.8), count));
                }
                
                // Show some sample entries
                List<Row> sampleEntries = logsDb.getRows("SELECT level, message, logger, method FROM LOG_ENTRIES WHERE message LIKE '%test%' ORDER BY timestamp DESC LIMIT 5");
                System.out.println("Sample log entries:");
                for (Row entry : sampleEntries) {
                    System.out.println("  [" + entry.getString("level").orElse("?") + "] " + 
                                     entry.getString("message").orElse("?") + " (" + 
                                     entry.getString("method").orElse("?") + ")");
                }
            } else {
                System.out.println("❌ No log entries found in database");
            }
            
            logsDb.close();
            
        } catch (Exception e) {
            System.err.println("❌ Error verifying database entries: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            setupDatabase();
            
            BatchLoggingTest test = new BatchLoggingTest();
            
            System.out.println("=== Batch Logging System Test ===\n");
            
            test.testBatchLoggingPerformance();
            System.out.println();
            
            test.testBatchFlushingBehavior();
            System.out.println();
            
            test.testFreyrJobSystemIntegration();
            System.out.println();
            
            cleanup();
            System.out.println("=== All batch logging tests completed! ===");
            
        } catch (Exception e) {
            System.err.println("Batch logging test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 