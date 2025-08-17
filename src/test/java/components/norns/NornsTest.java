package components.norns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Norns;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.utils.logger.JawsLogger;

@DisplayName("Norns Tests")
public class NornsTest {

  private static Path mainDbPath;
  private static Path logsDbPath;

  @BeforeAll
  static void beforeAll() throws Exception {
    // Prepare temp DB paths
    mainDbPath = Paths.get("target", "norns-it-db-" + System.nanoTime() + ".db");
    logsDbPath = Paths.get("target", "norns-it-logs-" + System.nanoTime() + ".db");

    // Register databases in Odin (idempotent)
    if (!Odin.hasDatabase(Odin.DB_NAME)) {
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

    // Bootstrap logger once
    if (Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));
    }
  }

  @AfterAll
  static void afterAll() {
    try { if (logsDbPath != null) Files.deleteIfExists(logsDbPath); } catch (IOException ignored) {}
    try { if (mainDbPath != null) Files.deleteIfExists(mainDbPath); } catch (IOException ignored) {}
  }

  @AfterEach
  void afterEach() throws Exception {
    // Ensure any running Norns thread is stopped and singleton reset for isolation
    try {
      Norns.getInstance().stop();
      // Give some time for any running loop to observe stop
      Thread.sleep(50);
    } catch (Throwable ignored) {}

    // Reset singleton via reflection so next test gets a fresh running=true instance
    try {
      java.lang.reflect.Field f = Norns.class.getDeclaredField("instance");
      f.setAccessible(true);
      f.set(null, null);
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
  }

  @Test
  @DisplayName("Given getInstance called twice When compared Then same singleton instance")
  void givenGetInstanceTwice_whenCompare_thenSameInstance() {
    Norns a = Norns.getInstance();
    Norns b = Norns.getInstance();
    assertTrue(a == b);
  }

  @Test
  @DisplayName("Given registered task When scheduler runs Then it executes periodically")
  void givenTaskRegistered_whenRuns_thenExecutesPeriodically() throws Exception {
    Norns norns = Norns.getInstance();
    AtomicInteger counter = new AtomicInteger();
    CountDownLatch atLeastTwo = new CountDownLatch(2);

    norns.registerTask("tick", () -> {
      counter.incrementAndGet();
      atLeastTwo.countDown();
    }, 200, TimeUnit.MILLISECONDS); // interval smaller than loop check; effectively ~1s cadence

    Thread t = new Thread(norns, "norns-test-runner-1");
    t.start();

    // Expect at least 2 executions within ~3 seconds (loop ticks every 1s)
    boolean ok = atLeastTwo.await(3500, TimeUnit.MILLISECONDS);

    norns.stop();
    t.join(1000);

    assertTrue(ok, "Expected task to execute at least twice");
    assertTrue(counter.get() >= 2);
  }

  @Test
  @DisplayName("Given running task When unregistered Then further executions stop")
  void givenRunningTask_whenUnregister_thenStopsExecuting() throws Exception {
    Norns norns = Norns.getInstance();
    AtomicInteger counter = new AtomicInteger();

    norns.registerTask("toRemove", counter::incrementAndGet, 100, TimeUnit.MILLISECONDS);

    Thread t = new Thread(norns, "norns-test-runner-2");
    t.start();

    // Allow at least one execution
    Thread.sleep(1500);
    int before = counter.get();

    norns.unregisterTask("toRemove");

    // Wait longer than one scheduler tick; count should stay the same
    Thread.sleep(1500);
    int after = counter.get();

    norns.stop();
    t.join(1000);

    assertTrue(before >= 1, "Expected at least one execution before unregister");
    assertEquals(before, after, "Counter should not increase after unregister");
  }

  @Test
  @DisplayName("Given scheduler running When stop called Then thread terminates cleanly")
  void givenSchedulerRunning_whenStop_thenTerminates() throws Exception {
    Norns norns = Norns.getInstance();
    Thread t = new Thread(norns, "norns-test-runner-3");
    t.start();

    // Let it enter the loop
    Thread.sleep(100);
    norns.stop();
    t.join(1500);

    assertTrue(!t.isAlive(), "Thread should terminate after stop");
  }

  @Test
  @DisplayName("Given task throws When executing Then other tasks continue running")
  void givenTaskThrows_whenExecuting_thenOtherTasksContinue() throws Exception {
    Norns norns = Norns.getInstance();
    AtomicInteger okCounter = new AtomicInteger();

    norns.registerTask("bad", () -> { throw new RuntimeException("boom"); }, 100, TimeUnit.MILLISECONDS);
    norns.registerTask("good", okCounter::incrementAndGet, 100, TimeUnit.MILLISECONDS);

    Thread t = new Thread(norns, "norns-test-runner-4");
    t.start();

    Thread.sleep(2000);

    norns.stop();
    t.join(1000);

    assertTrue(okCounter.get() >= 1, "Good task should continue executing even if another task throws");
  }
}
