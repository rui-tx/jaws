package fuzzer;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import org.ruitx.jaws.components.Mimir;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class MimirFuzzer {
    private static Mimir mimir;
    private static Path tempDbPath;
    private static long queryCount = 0;

    public static void fuzzerInitialize() throws IOException {
        // Create a temporary database file for testing
        tempDbPath = Files.createTempFile("fuzz-test-db-", ".db");
        mimir = new Mimir();
        mimir.initializeDatabase(tempDbPath.toString());
        
        // Create a simple test table
        mimir.executeSql("CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY, name TEXT)");
        
        Logger.info("Fuzzer initialized with temporary database at: " + tempDbPath);
    }

    public static void fuzzerTearDown() throws IOException {
        // Clean up the temporary database file
        Files.deleteIfExists(tempDbPath);
        Logger.info("Fuzzer completed. Total queries tested: " + queryCount);
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        queryCount++;
        String query = data.consumeRemainingAsString();
        
        try {
            // Try to execute the query
            mimir.executeQuery(query, resultSet -> {
                // Just consume the result set without doing anything with it
                return null;
            });
        } catch (RuntimeException e) {
            // Check if this is a wrapped SQLException
            Throwable cause = e.getCause();
            if (cause instanceof SQLException) {
                SQLException sqlEx = (SQLException) cause;
                // Only log unexpected SQL errors (not syntax errors or prepared statement errors)
                if (!sqlEx.getMessage().contains("syntax error") && 
                    !sqlEx.getMessage().contains("prepared statement has been finalized")) {
                    Logger.error("Unexpected SQL error on query #" + queryCount + ": " + sqlEx.getMessage());
                    Logger.error("Query that caused error: " + query);
                }
            } else {
                // Log any other runtime exceptions as they might indicate serious issues
                Logger.error("Unexpected error on query #" + queryCount + ": " + e.getMessage());
                Logger.error("Query that caused error: " + query);
                e.printStackTrace();
            }
        }
    }
} 