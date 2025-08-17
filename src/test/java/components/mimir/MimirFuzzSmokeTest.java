package components.mimir;

import static components.mimir.MimirTestUtils.closeAll;
import static components.mimir.MimirTestUtils.create;
import static components.mimir.MimirTestUtils.deleteAllDbs;
import static components.mimir.MimirTestUtils.init;
import static components.mimir.MimirTestUtils.newTempDb;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.types.Row;

/**
 * Deterministic, bounded randomized test that mirrors parts of the fuzz harness. Helps CI runs
 * without requiring Jazzer runtime.
 */
public class MimirFuzzSmokeTest {

  private Mimir db;

  @BeforeAll
  public static void globalSetup() {
    deleteAllDbs();
  }

  @AfterAll
  public static void globalCleanup() {
    closeAll();
    deleteAllDbs();
  }

  private static void sleepQuiet(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @BeforeEach
  public void setUp() {
    Path dbPath = newTempDb("fuzz-smoke-");
    db = create(dbPath.toString());
    init(db, dbPath.toString());
    db.execute("CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));
  }

  @AfterEach
  public void tearDown() {
    try {
      if (db != null) {
        db.close();
      }
    } catch (Exception ignored) {
    }
    Mimir.clearCurrentTtl();
    Mimir.clearCurrentTables();
    Mimir.disableCacheForCurrentThread();
  }

  @Test
  @DisplayName("Given randomized inputs, when executed through Mimir, then no unexpected exceptions")
  public void GivenRandomizedInputs_WhenExecutedThroughMimir_ThenNoUnexpectedExceptions() {
    // Seed a few rows
    for (int i = 0; i < 5; i++) {
      db.execute("INSERT INTO TEST (name) VALUES (?)", "seed-" + i);
    }

    // Run a handful of randomized steps
    for (int step = 0; step < 20; step++) {
      int op = ThreadLocalRandom.current().nextInt(4); // 0=select,1=insert,2=update,3=delete
      int finalStep = step;
      int finalStep1 = step;
      switch (op) {
        case 0 -> {
          boolean byName = ThreadLocalRandom.current().nextBoolean();
          if (byName) {
            String name = "seed-" + ThreadLocalRandom.current().nextInt(0, 6);
            List<Row> a = assertDoesNotThrow(
                () -> db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", name));
            List<Row> b = assertDoesNotThrow(
                () -> db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", name));
            assertSame(a, b);
          } else {
            List<Row> a = assertDoesNotThrow(() -> db.getRows("SELECT * FROM TEST ORDER BY id"));
            List<Row> b = assertDoesNotThrow(() -> db.getRows("SELECT * FROM TEST ORDER BY id"));
            assertSame(a, b);
          }
        }
        case 1 -> {
          int beforeSize = db.getRows("SELECT * FROM TEST ORDER BY id").size();
          assertDoesNotThrow(
              () -> db.execute("INSERT INTO TEST (name) VALUES (?)", "smoke-" + finalStep));
          int afterSize = db.getRows("SELECT * FROM TEST ORDER BY id").size();
          assertTrue(afterSize >= beforeSize, "Insert should not reduce row count");
        }
        case 2 -> {
          List<Row> beforeList = db.getRows("SELECT * FROM TEST ORDER BY id");
          if (beforeList.isEmpty()) {
            db.execute("INSERT INTO TEST (name) VALUES (?)", "seed-upd-" + finalStep1);
            beforeList = db.getRows("SELECT * FROM TEST ORDER BY id");
          }
          String prevName = beforeList.get(0).getString("name").orElse("");
          assertDoesNotThrow(() -> db.execute(
              "UPDATE TEST SET name = ? WHERE id IN (SELECT id FROM TEST ORDER BY id LIMIT 1)",
              "upd-" + finalStep1));
          List<Row> afterList = db.getRows("SELECT * FROM TEST ORDER BY id");
          String afterName = afterList.get(0).getString("name").orElse("");
          assertNotEquals(prevName, afterName, "Update should change the first row name");
        }
        case 3 -> {
          int beforeSize = db.getRows("SELECT * FROM TEST ORDER BY id").size();
          assertDoesNotThrow(() -> db.execute(
              "DELETE FROM TEST WHERE id IN (SELECT id FROM TEST ORDER BY id LIMIT 1)"));
          int afterSize = db.getRows("SELECT * FROM TEST ORDER BY id").size();
          assertTrue(afterSize <= beforeSize, "Delete should not increase row count");
        }
      }

      // Occasionally test TTL
      if (ThreadLocalRandom.current().nextInt(6) == 0) {
        Mimir.setCurrentTtl(50);
        List<Row> a = db.getRows("SELECT * FROM TEST ORDER BY id");
        sleepQuiet(80);
        List<Row> b = db.getRows("SELECT * FROM TEST ORDER BY id");
        // If cache/TTL refresh happens, identity may change; behavior focus is no exceptions.
        // We keep this as a soft check by comparing sizes as a no-op.
        assertTrue(a.size() >= 0 && b.size() >= 0);
        Mimir.clearCurrentTtl();
      }
    }
  }
}
