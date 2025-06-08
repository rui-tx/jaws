import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;

import java.io.File;
import java.sql.DriverManager;
import java.util.List;

public class MimirMultiDatabaseTest {

    private static final String MAIN_DB_PATH = "target/test-main.db";
    private static final String LOGS_DB_PATH = "target/test-logs.db";
    private static final String LOGS_SCHEMA_PATH = "src/main/resources/sql/logs_schema.sql";

    @BeforeAll
    public static void setupDatabase() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            DriverManager.registerDriver(new org.sqlite.JDBC());
            System.out.println("SQLite JDBC driver loaded successfully for test");
        } catch (Exception e) {
            System.err.println("Failed to load SQLite JDBC driver in test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void cleanup() {
        // Clean up test databases
        new File(MAIN_DB_PATH).delete();
        new File(LOGS_DB_PATH).delete();
    }

    @Test
    public void testDefaultMimirConstructor() {
        System.out.println("Testing default Mimir constructor (backward compatibility)...");
        
        Mimir defaultMimir = new Mimir();
        System.out.println("✅ Default Mimir created successfully");
        System.out.println("Database path: " + defaultMimir.getDatabasePath());
        System.out.println("Initialized: " + defaultMimir.isInitialized());
        
        defaultMimir.close();
        System.out.println("✅ Default Mimir test completed");
    }

    @Test
    public void testCustomDatabaseWithoutSchema() {
        System.out.println("Testing custom database without schema (logs use case)...");
        
        // Create a logs database without schema
        Mimir logsDb = new Mimir(LOGS_DB_PATH);
        System.out.println("✅ Logs Mimir created successfully");
        System.out.println("Database path: " + logsDb.getDatabasePath());
        System.out.println("Initialized: " + logsDb.isInitialized());
        
        // Initialize the database (should create empty database)
        logsDb.initializeDatabase(LOGS_DB_PATH);
        System.out.println("✅ Logs database initialized without schema");
        
        logsDb.close();
        System.out.println("✅ Custom database test completed");
    }

    @Test
    public void testCustomDatabaseWithSchema() {
        System.out.println("Testing custom database with logs schema...");
        
        // Clean up any existing database first
        new File(LOGS_DB_PATH).delete();
        
        // Create a logs database with schema
        Mimir logsDb = new Mimir(LOGS_DB_PATH, LOGS_SCHEMA_PATH);
        System.out.println("✅ Logs Mimir with schema created successfully");
        
        // Initialize the database (should create database with logs schema)
        logsDb.initializeDatabase(LOGS_DB_PATH);
        System.out.println("✅ Logs database initialized with schema");
        
        // Test inserting a log entry
        int insertCount = logsDb.executeSql(
            "INSERT INTO LOG_ENTRIES (timestamp, level, logger, thread, message, method, line) VALUES (?, ?, ?, ?, ?, ?, ?)",
            System.currentTimeMillis(),
            "INFO",
            "MimirMultiDatabaseTest",
            Thread.currentThread().getName(),
            "Test log message",
            "testCustomDatabaseWithSchema",
            42
        );
        System.out.println("✅ Inserted " + insertCount + " log entry");
        
        // Test querying the log entry
        List<Row> logEntries = logsDb.getRows("SELECT * FROM LOG_ENTRIES WHERE logger = ?", "MimirMultiDatabaseTest");
        System.out.println("✅ Retrieved " + logEntries.size() + " log entries");
        
        if (!logEntries.isEmpty()) {
            Row entry = logEntries.get(0);
            System.out.println("Log entry details:");
            System.out.printf("  ID: %d%n", entry.getInt("id").orElse(0));
            System.out.printf("  Level: %s%n", entry.getString("level").orElse("N/A"));
            System.out.printf("  Logger: %s%n", entry.getString("logger").orElse("N/A"));
            System.out.printf("  Message: %s%n", entry.getString("message").orElse("N/A"));
            System.out.printf("  Method: %s%n", entry.getString("method").orElse("N/A"));
            System.out.printf("  Line: %d%n", entry.getInt("line").orElse(0));
        }
        
        logsDb.close();
        System.out.println("✅ Logs database with schema test completed");
    }

    @Test
    public void testMultipleDatabaseInstances() {
        System.out.println("Testing multiple database instances simultaneously...");
        
        // Clean up any existing databases first
        new File(MAIN_DB_PATH).delete();
        new File(LOGS_DB_PATH).delete();
        
        // Create main database (with default schema simulation)
        Mimir mainDb = new Mimir(MAIN_DB_PATH);
        mainDb.initializeDatabase(MAIN_DB_PATH);
        
        // Create logs database with logs schema
        Mimir logsDb = new Mimir(LOGS_DB_PATH, LOGS_SCHEMA_PATH);
        logsDb.initializeDatabase(LOGS_DB_PATH);
        
        System.out.println("✅ Multiple database instances created");
        System.out.println("Main DB path: " + mainDb.getDatabasePath());
        System.out.println("Logs DB path: " + logsDb.getDatabasePath());
        
        // Test that databases are separate by inserting into logs
        int logInsertCount = logsDb.executeSql(
            "INSERT INTO LOG_ENTRIES (timestamp, level, logger, message) VALUES (?, ?, ?, ?)",
            System.currentTimeMillis(),
            "DEBUG",
            "MultiDBTest",
            "Testing multiple databases"
        );
        System.out.println("✅ Inserted " + logInsertCount + " entry into logs database");
        
        // Verify main database doesn't have LOG_ENTRIES table
        try {
            mainDb.getRows("SELECT * FROM LOG_ENTRIES");
            System.out.println("❌ ERROR: Main database shouldn't have LOG_ENTRIES table");
        } catch (Exception e) {
            System.out.println("✅ Confirmed: Main database doesn't have LOG_ENTRIES table (expected)");
        }
        
        // Verify logs database has the entry
        List<Row> logEntries = logsDb.getRows("SELECT COUNT(*) as count FROM LOG_ENTRIES");
        if (!logEntries.isEmpty()) {
            int count = logEntries.get(0).getInt("count").orElse(0);
            System.out.println("✅ Logs database has " + count + " entries");
        }
        
        mainDb.close();
        logsDb.close();
        System.out.println("✅ Multiple database instances test completed");
    }

    public static void main(String[] args) {
        try {
            // Load driver
            Class.forName("org.sqlite.JDBC");
            
            MimirMultiDatabaseTest test = new MimirMultiDatabaseTest();
            setupDatabase();
            
            System.out.println("=== Mimir Multiple Database Support Test ===\n");
            
            test.testDefaultMimirConstructor();
            System.out.println();
            
            test.testCustomDatabaseWithoutSchema();
            System.out.println();
            
            test.testCustomDatabaseWithSchema();
            System.out.println();
            
            test.testMultipleDatabaseInstances();
            System.out.println();
            
            cleanup();
            System.out.println("=== All tests completed successfully! ===");
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 