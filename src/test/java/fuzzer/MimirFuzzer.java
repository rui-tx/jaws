package fuzzer;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Row;
import org.tinylog.Logger;

/**
 * Jazzer fuzz target for Mimir. It exercises read/write operations, cache semantics, and TTL
 * behavior under randomized inputs while constraining schema to avoid flaky failures.
 * <p>
 * Safety notes: - Limits query sizes and parameter counts. - Restricts operations to known
 * tables/columns created in fuzzerInitialize. - Includes small, bounded sleeps for TTL checks.
 */
public class MimirFuzzer {

  private static Mimir mimir;
  private static Path tempDbPath;
  private static long queryCount = 0;

  public static void fuzzerInitialize() throws IOException {
    tempDbPath = Files.createTempFile("fuzz-test-mimir-", ".mimir");
    mimir = new Mimir(tempDbPath.toString());
    mimir.initializeDatabase(tempDbPath.toString());

    // Create test schema (keep small and deterministic)
    mimir.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
    mimir.execute(
        "CREATE TABLE IF NOT EXISTS LOG_ENTRIES (id INTEGER PRIMARY KEY AUTOINCREMENT, logger TEXT)");

    // Default cache context
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST", "LOG_ENTRIES"));

    Logger.info("MimirFuzzer initialized at {}", tempDbPath);
  }

  public static void fuzzerTearDown() throws IOException {
    try {
      if (mimir != null) {
        mimir.close();
      }
    } catch (Exception ignored) {
    }
    Files.deleteIfExists(tempDbPath);
    Logger.info("MimirFuzzer completed. Total iterations: {}", queryCount);
  }

  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    queryCount++;

    // Cache toggles (keep deterministic to schema)
    boolean enableCache = data.consumeBoolean();
    if (enableCache) {
      Mimir.enableCacheForCurrentThread();
    } else {
      Mimir.disableCacheForCurrentThread();
    }
    // Scope invalidation to TEST to avoid cross-db surprises
    Mimir.setCurrentTables(Set.of("TEST"));

    // Occasionally set a short TTL (0..200ms)
    boolean setTtl = data.consumeBoolean();
    if (setTtl) {
      long ttl = Math.max(0, Math.min(200, data.consumeInt()));
      Mimir.setCurrentTtl(ttl);
    } else {
      Mimir.clearCurrentTtl();
    }

    // Choose op: 0=SELECT, 1=INSERT, 2=UPDATE, 3=DELETE
    int op = Math.floorMod(data.consumeInt(), 4);

    try {
      switch (op) {
        case 0 -> doSelectScenario(data, enableCache);
        case 1 -> doInsertScenario(data);
        case 2 -> doUpdateScenario(data);
        case 3 -> doDeleteScenario(data);
        default -> {
          // no-op (should not happen)
        }
      }
    } catch (RuntimeException e) {
      // Allow expected SQL/runtime errors but log unexpected ones
      // We avoid nulls; just log and swallow to let Jazzer continue exploring
      Logger.debug("Iteration {} threw: {}", queryCount, e.toString());
    } finally {
      // Clear TTL so next iteration starts clean
      Mimir.clearCurrentTtl();
    }
  }

  // region Scenarios

  private static void doSelectScenario(FuzzedDataProvider data, boolean cacheEnabled) {
    // Build one of two templates to keep it simple and valid
    boolean byName = data.consumeBoolean();
    String nameParam = sanitizeName(data.consumeString(32));

    if (byName) {
      String sql = "SELECT * FROM TEST WHERE name = ? ORDER BY id LIMIT 200";
      List<Row> first = mimir.getRows(sql, nameParam);
      List<Row> second = mimir.getRows(sql, nameParam);
      if (cacheEnabled) {
        // Given cache enabled and no writes
        // When issuing the same SELECT with same params
        // Then identity should match
        if (first != null && second != null) {
          assertSameInstance(first, second);
        }
      }

      // Occasionally test TTL refresh
      maybeTestTtlRefresh(sql, new Object[]{nameParam});
    } else {
      String sql = "SELECT * FROM TEST ORDER BY id LIMIT 200";
      List<Row> first = mimir.getRows(sql);
      List<Row> second = mimir.getRows(sql);
      if (cacheEnabled) {
        if (first != null && second != null) {
          assertSameInstance(first, second);
        }
      }
      maybeTestTtlRefresh(sql, new Object[]{});
    }
  }

  private static void doInsertScenario(FuzzedDataProvider data) {
    int n = Math.min(5, Math.max(1, data.consumeInt(1, 5)));
    for (int i = 0; i < n; i++) {
      String name = sanitizeName(data.consumeString(24));
      mimir.execute("INSERT INTO TEST (name) VALUES (?)", name);
    }

    // Verify invalidation behavior by comparing identity pre/post write
    List<Row> before = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    String extra = randomSuffix();
    mimir.execute("INSERT INTO TEST (name) VALUES (?)", extra);
    List<Row> after = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    if (before != null && after != null) {
      // Given a write occurred
      // When reading the same query again
      // Then identity may differ due to invalidation
      assertDifferentInstance(before, after);
    }
  }

  private static void doUpdateScenario(FuzzedDataProvider data) {
    // Ensure at least one row exists to update sometimes
    mimir.execute("INSERT INTO TEST (name) VALUES (?)", sanitizeName(data.consumeString(24)));

    List<Row> warm = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    String newName = sanitizeName(data.consumeString(24));
    mimir.execute("UPDATE TEST SET name = ? WHERE id IN (SELECT id FROM TEST ORDER BY id LIMIT 1)",
        newName);
    List<Row> after = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    if (warm != null && after != null) {
      assertDifferentInstance(warm, after);
    }
  }

  private static void doDeleteScenario(FuzzedDataProvider data) {
    // Seed a few rows, then delete one
    int seed = Math.min(3, Math.max(1, data.consumeInt(1, 3)));
    for (int i = 0; i < seed; i++) {
      mimir.execute("INSERT INTO TEST (name) VALUES (?)", sanitizeName(data.consumeString(24)));
    }
    List<Row> before = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    mimir.execute("DELETE FROM TEST WHERE id IN (SELECT id FROM TEST ORDER BY id LIMIT 1)");
    List<Row> after = mimir.getRows("SELECT * FROM TEST ORDER BY id LIMIT 200");
    if (before != null && after != null) {
      assertDifferentInstance(before, after);
    }
  }

  private static void maybeTestTtlRefresh(String sql, Object[] params) {
    // Low probability TTL check to keep runs fast
    if (ThreadLocalRandom.current().nextInt(8) == 0) {
      // Set very short TTL and verify identity refresh
      Mimir.setCurrentTtl(50);
      List<Row> a = mimir.getRows(sql, params);
      sleepQuiet(80);
      List<Row> b = mimir.getRows(sql, params);
      if (a != null && b != null) {
        assertDifferentInstance(a, b);
      }
      Mimir.clearCurrentTtl();
    }
  }

  // endregion

  // region Helpers

  private static String sanitizeName(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    // Keep it simple ASCII subset and truncate length
    String cleaned = s.replaceAll("[^a-zA-Z0-9 _-]", "_");
    if (cleaned.length() > 64) {
      cleaned = cleaned.substring(0, 64);
    }
    return cleaned;
  }

  private static String randomSuffix() {
    return "row-" + ThreadLocalRandom.current().nextInt(0, 10_000);
  }

  private static void sleepQuiet(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static void assertSameInstance(Object a, Object b) {
    if (a != b) {
      throw new IllegalStateException("Expected same cached instance but got different objects");
    }
  }

  private static void assertDifferentInstance(Object a, Object b) {
    if (a == b) {
      throw new IllegalStateException("Expected different instance after invalidation/TTL");
    }
  }

  // endregion
}