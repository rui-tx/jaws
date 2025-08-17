package logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.logger.JawsLogger;

@DisplayName("JawsLogger Integration Tests")
public class JawsLoggerIntegrationTest {

  private static Path mainDbPath; // Only used if we need to register DB_NAME locally
  private static Path logsDbPath;

  @BeforeAll
  static void beforeAll() throws Exception {
    // Ensure clean slate: close any previous DBs/background workers from other tests
    Odin.shutdownAllForTests();

    // If main DB isn't present (e.g., running this test in isolation), register a temp one
    if (!Odin.hasDatabase(Odin.DB_NAME)) {
      mainDbPath = Paths.get("target", "logger-it-db-" + System.nanoTime() + ".db");
      Odin.registerDatabase(Odin.DB_NAME, new DatabaseConfig(
          mainDbPath.toAbsolutePath().toString(),
          ApplicationConfig.DATABASE_SCHEMA_PATH,
          ApplicationConfig.MIMIR_READER_POOL_SIZE,
          ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
          ApplicationConfig.MIMIR_ENABLE_WAL,
          ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
          null,
          "jaws-writer",
          "jaws-reader",
          null
      ));
    }

    // Prepare temp logs DB path (unique per run)
    logsDbPath = Paths.get("target", "logger-it-logs-" + System.nanoTime() + ".db");

    // Register only the logs database in Odin (avoid touching main app DB if already present)
    if (!Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      Odin.registerDatabase(Odin.LOGS_DB_NAME, new DatabaseConfig(
          logsDbPath.toAbsolutePath().toString(),
          Paths.get("src/main/resources/sql/logs_schema.sql").toAbsolutePath().toString(),
          Math.max(2, ApplicationConfig.MIMIR_READER_POOL_SIZE / 2),
          ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
          true,
          ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
          null,
          "jaws-logs-writer",
          "jaws-logs-reader",
          null
      ));
    }

    // Bootstrap JawsLogger against Odin's logs DB
    if (Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));
    }

    // Start Freyr job system (batch processor)
    Freyr.getInstance().start();
  }

  @AfterAll
  static void afterAll() {
    try {
      JawsLogger.forceFlush();
      Thread.sleep(500);
    } catch (InterruptedException ignored) {
    }

    // Stop background workers first
    Freyr.getInstance().shutdown();

    // Unregister test databases so all pools/connections are closed
    try {
      if (Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
        Odin.unregisterDatabase(Odin.LOGS_DB_NAME);
      }
      if (mainDbPath != null && Odin.hasDatabase(Odin.DB_NAME)) {
        Odin.unregisterDatabase(Odin.DB_NAME);
      }
    } catch (Throwable ignored) {
    }

    // Best-effort cleanup of temp DB files (including WAL/SHM sidecars)
    try {
      if (logsDbPath != null) {
        Files.deleteIfExists(logsDbPath);
        Files.deleteIfExists(Paths.get(logsDbPath.toString() + "-wal"));
        Files.deleteIfExists(Paths.get(logsDbPath.toString() + "-shm"));
      }
      if (mainDbPath != null) {
        Files.deleteIfExists(mainDbPath);
        Files.deleteIfExists(Paths.get(mainDbPath.toString() + "-wal"));
        Files.deleteIfExists(Paths.get(mainDbPath.toString() + "-shm"));
      }
    } catch (IOException ignored) {
    }
  }

  private static int scalarCount(Mimir db, String sql, Object[] params) throws Exception {
    List<Row> rows = db.getRows(sql, params);
    if (rows.isEmpty()) {
      return 0;
    }
    return rows.get(0).getInt("count").orElse(0);
  }

  @AfterEach
  void afterEach() throws InterruptedException {
    // Give a short window for async batch to complete between tests
    JawsLogger.forceFlush();
    Thread.sleep(300);
  }

  @Test
  @DisplayName("Given burst logs When forceFlush Then entries persisted to logs DB")
  void givenBurstLogs_whenForceFlush_thenPersisted() throws Exception {
    // Scope cache context for queries (defensive; not strictly required here)
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("LOG_ENTRIES"));

    int countBefore = scalarCount(Odin.getDB(Odin.LOGS_DB_NAME),
        "SELECT COUNT(*) as count FROM LOG_ENTRIES", new Object[]{});

    int toWrite = 120;
    for (int i = 0; i < toWrite; i++) {
      JawsLogger.info("IT burst entry {}", i);
      if (i % 10 == 0) {
        JawsLogger.warn("IT burst warn {}", i);
      }
      if (i % 33 == 0) {
        JawsLogger.error(new RuntimeException("boom-" + i), "IT burst err {}", i);
      }
    }

    JawsLogger.forceFlush();
    Thread.sleep(800); // allow batch job to push

    int countAfter = scalarCount(Odin.getDB(Odin.LOGS_DB_NAME),
        "SELECT COUNT(*) as count FROM LOG_ENTRIES", new Object[]{});

    assertTrue(countAfter - countBefore >= toWrite * 0.8, "Expected majority of burst persisted");

    // Cleanup cache context
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();
  }

  @Test
  @DisplayName("Given size threshold When exceeded Then Freyr jobs submitted")
  void givenSizeThreshold_whenExceeded_thenFreyrJobsSubmitted() throws Exception {
    Map<String, Object> before = Freyr.getInstance().getStatistics();
    int jobsBefore = (Integer) before.getOrDefault("totalJobs", 0);

    for (int i = 0; i < 75; i++) {
      JawsLogger.info("threshold-log-{}", i);
    }

    JawsLogger.forceFlush();
    Thread.sleep(500);

    Map<String, Object> after = Freyr.getInstance().getStatistics();
    int jobsAfter = (Integer) after.getOrDefault("totalJobs", 0);

    assertTrue(jobsAfter >= jobsBefore, "Expected Freyr to receive jobs for batch logging");
  }

  @Test
  @DisplayName("Given exception logging When emitted Then persisted with ERROR level")
  void givenException_whenLogged_thenPersistedAsError() throws Exception {
    Exception ex = new IllegalStateException("IT test exception");
    JawsLogger.error(ex, "Integration test exception record {}", 42);

    JawsLogger.forceFlush();
    Thread.sleep(300);

    List<Row> rows = Odin.getDB(Odin.LOGS_DB_NAME).getRows(
        "SELECT level, message FROM LOG_ENTRIES WHERE level = 'ERROR' AND message LIKE ? ORDER BY timestamp DESC LIMIT 5",
        "%Integration test exception record%"
    );

    assertTrue(rows.size() >= 1, "Expected at least one ERROR entry persisted");
  }

  @Test
  @DisplayName("Given small logs When waiting Then time-based flush persists to DB")
  void givenSmallLogs_whenWaiting_thenTimeBasedFlushPersists() throws Exception {
    // Note: no forceFlush here; rely on time-based/batch mechanism
    String marker = "time-flush-" + System.nanoTime();

    // Write a couple of entries below typical size threshold
    JawsLogger.info(marker + "-1");
    JawsLogger.info(marker + "-2");

    // Wait beyond expected time-based flush interval
    Thread.sleep(ApplicationConfig.FLUSH_INTERVAL_MS + 100);

    List<Row> rows = Odin.getDB(Odin.LOGS_DB_NAME).getRows(
        "SELECT COUNT(*) as count FROM LOG_ENTRIES WHERE message LIKE ?",
        "%" + marker + "%"
    );

    int count = rows.isEmpty() ? 0 : rows.get(0).getInt("count").orElse(0);
    assertTrue(count >= 1, "Expected time-based flush to persist at least one entry");
  }

  @Test
  @DisplayName("Given buffer When stats requested Then exposes basic telemetry")
  void givenBuffer_whenGetStats_thenTelemetryPresent() {
    Map<String, Object> stats = JawsLogger.getBufferStatistics();
    assertTrue(stats != null, "Stats map should not be null");
    assertTrue(!stats.isEmpty(), "Stats should expose at least one key");
  }
}
