package components.mimir;

import static components.mimir.MimirTestUtils.closeAll;
import static components.mimir.MimirTestUtils.create;
import static components.mimir.MimirTestUtils.deleteAllDbs;
import static components.mimir.MimirTestUtils.init;
import static components.mimir.MimirTestUtils.newTempDb;
import static components.mimir.MimirTestUtils.scalarCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Row;

public class MimirMultiDatabaseTest {

  private static final String LOGS_SCHEMA_CP = "sql/logs_schema_v1.sql";

  @BeforeAll
  public static void setupDatabase() {
    // Ensure a clean slate before this test suite
    deleteAllDbs();
  }

  @AfterAll
  public static void cleanup() {
    // Close and remove all DBs created via helpers
    closeAll();
    deleteAllDbs();
  }

  @Test
  @DisplayName("Default constructor: Mimir can be created and closed (backward-compat)")
  public void testDefaultMimirConstructor() {
    Mimir defaultMimir = new Mimir();
    assertNotNull(defaultMimir.getDatabasePath());
    defaultMimir.close();
  }

  @Test
  @DisplayName("Custom DB without schema: initializes empty DB, target table absent")
  public void testCustomDatabaseWithoutSchema() {
    Path logsDbPath = newTempDb("logs-");

    // Create a logs database without schema
    Mimir logsDb = create(logsDbPath.toString());

    // Initialize the database (should create empty database)
    init(logsDb, logsDbPath.toString());

    // LOG_ENTRIES table should NOT exist without schema
    assertTrue(MimirTestUtils.tableNotExists(logsDb, "LOG_ENTRIES"));

    logsDb.close();
  }

  @Test
  @DisplayName("Custom DB with schema: table exists, can insert and query rows")
  public void testCustomDatabaseWithSchema() {
    Path logsDbPath = newTempDb("logs-");

    // Create a logs database with schema loaded from classpath
    Mimir logsDb = MimirTestUtils.createWithSchemaResource(logsDbPath.toString(), LOGS_SCHEMA_CP);

    // Initialize the database (should create database with logs schema)
    init(logsDb, logsDbPath.toString());

    // Table should exist after schema init
    assertTrue(MimirTestUtils.tableExists(logsDb, "LOG_ENTRIES"));

    // Insert a log entry
    int insertCount = logsDb.execute(
        "INSERT INTO LOG_ENTRIES (timestamp, level, logger, thread, message, method, line) VALUES (?, ?, ?, ?, ?, ?, ?)",
        System.currentTimeMillis(),
        "INFO",
        "components.mimir.MimirMultiDatabaseTest",
        Thread.currentThread().getName(),
        "Test log message",
        "testCustomDatabaseWithSchema",
        42
    );
    assertEquals(1, insertCount);

    // Query the inserted row by logger
    List<Row> logEntries = logsDb.getRows("SELECT * FROM LOG_ENTRIES WHERE logger = ?",
        "components.mimir.MimirMultiDatabaseTest");
    assertEquals(1, logEntries.size());

    logsDb.close();
  }

  @Test
  @DisplayName("Multiple DB instances: isolation between main and logs DBs")
  public void testMultipleDatabaseInstances() {
    Path mainDbPath = newTempDb("main-");
    Path logsDbPath = newTempDb("logs-");

    // Create main database (no schema)
    Mimir mainDb = create(mainDbPath.toString());
    init(mainDb, mainDbPath.toString());

    // Create logs database with logs schema
    Mimir logsDb = MimirTestUtils.createWithSchemaResource(logsDbPath.toString(), LOGS_SCHEMA_CP);
    init(logsDb, logsDbPath.toString());

    // Isolation checks
    assertTrue(MimirTestUtils.tableExists(logsDb, "LOG_ENTRIES"));
    assertTrue(MimirTestUtils.tableNotExists(mainDb, "LOG_ENTRIES"));

    // Insert into logs and verify count
    int logInsertCount = logsDb.execute(
        "INSERT INTO LOG_ENTRIES (timestamp, level, logger, message) VALUES (?, ?, ?, ?)",
        System.currentTimeMillis(),
        "DEBUG",
        "MultiDBTest",
        "Testing multiple databases"
    );
    assertEquals(1, logInsertCount);

    int count = scalarCount(logsDb, "SELECT COUNT(*) as count FROM LOG_ENTRIES WHERE logger = ?",
        "MultiDBTest");
    assertEquals(1, count);

    mainDb.close();
    logsDb.close();
  }
}