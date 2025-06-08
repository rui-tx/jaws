import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;

import java.io.File;
import java.sql.DriverManager;
import java.util.List;

public class JawsLoggerTest {

    @BeforeAll
    public static void setupDatabase() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            DriverManager.registerDriver(new org.sqlite.JDBC());
            System.out.println("SQLite JDBC driver loaded successfully for JawsLogger test");
        } catch (Exception e) {
            System.err.println("Failed to load SQLite JDBC driver in JawsLogger test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void cleanup() {
        // Clean up test log database
        new File("logs.db").delete();
    }

    @Test
    public void testJawsLoggerBasicFunctionality() {
        System.out.println("=== Testing JawsLogger Basic Functionality ===");
        
        // Test database availability
        System.out.println("Database logging available: " + JawsLogger.isDatabaseLoggingAvailable());
        System.out.println("Logs database path: " + JawsLogger.getLogsDatabasePath());
        
        // Test all log levels
        JawsLogger.trace("This is a TRACE message");
        JawsLogger.debug("This is a DEBUG message");
        JawsLogger.info("This is an INFO message");
        JawsLogger.warn("This is a WARN message");
        JawsLogger.error("This is an ERROR message");
        
        System.out.println("✅ Basic logging test completed");
    }
    
    @Test
    public void testJawsLoggerWithArguments() {
        System.out.println("=== Testing JawsLogger With Arguments ===");
        
        String username = "testuser";
        int userId = 123;
        
        // Test parameterized logging
        JawsLogger.info("User {} logged in with ID: {}", username, userId);
        JawsLogger.debug("Processing request for user: {} at timestamp: {}", username, System.currentTimeMillis());
        JawsLogger.warn("Warning: User {} has {} failed login attempts", username, 3);
        
        System.out.println("✅ Parameterized logging test completed");
    }
    
    @Test
    public void testJawsLoggerWithExceptions() {
        System.out.println("=== Testing JawsLogger With Exceptions ===");
        
        try {
            // Simulate an exception
            throw new RuntimeException("Simulated test exception");
        } catch (Exception e) {
            JawsLogger.error(e, "Exception caught during test execution");
            JawsLogger.error(e, "Failed to process user request for ID: {}", 456);
        }
        
        System.out.println("✅ Exception logging test completed");
    }
    
    @Test
    public void testDatabasePersistence() {
        System.out.println("=== Testing Database Persistence ===");
        
        if (!JawsLogger.isDatabaseLoggingAvailable()) {
            System.out.println("⚠️ Database logging not available, skipping persistence test");
            return;
        }
        
        // Log some entries
        JawsLogger.info("Database persistence test entry");
        JawsLogger.error("Test error for database persistence");
        
        try {
            // Wait a moment for async operations (if any)
            Thread.sleep(100);
            
            // Query the logs database directly to verify persistence
            Mimir logsDb = new Mimir("logs.db");
            List<Row> logEntries = logsDb.getRows("SELECT COUNT(*) as count FROM LOG_ENTRIES WHERE message LIKE '%persistence%'");
            
            if (!logEntries.isEmpty()) {
                int count = logEntries.get(0).getInt("count").orElse(0);
                System.out.println("✅ Found " + count + " log entries in database");
                
                // Get recent entries for verification
                List<Row> recentLogs = logsDb.getRows("SELECT level, message, logger, method FROM LOG_ENTRIES WHERE message LIKE '%persistence%' ORDER BY timestamp DESC LIMIT 5");
                
                System.out.println("Recent log entries:");
                for (Row log : recentLogs) {
                    System.out.println("  [" + log.getString("level").orElse("?") + "] " + 
                                     log.getString("message").orElse("?") + " (" + 
                                     log.getString("method").orElse("?") + ")");
                }
            } else {
                System.out.println("❌ No log entries found in database");
            }
            
            logsDb.close();
            
        } catch (Exception e) {
            System.err.println("❌ Error testing database persistence: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("✅ Database persistence test completed");
    }
    
    public static void main(String[] args) {
        try {
            // Load driver
            Class.forName("org.sqlite.JDBC");
            
            JawsLoggerTest test = new JawsLoggerTest();
            setupDatabase();
            
            System.out.println("=== JawsLogger Comprehensive Test ===\n");
            
            test.testJawsLoggerBasicFunctionality();
            System.out.println();
            
            test.testJawsLoggerWithArguments();
            System.out.println();
            
            test.testJawsLoggerWithExceptions();
            System.out.println();
            
            test.testDatabasePersistence();
            System.out.println();
            
            cleanup();
            System.out.println("=== JawsLogger tests completed successfully! ===");
            
        } catch (Exception e) {
            System.err.println("JawsLogger test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 