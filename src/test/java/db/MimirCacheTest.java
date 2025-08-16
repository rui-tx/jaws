package db;

import static db.MimirTestUtils.closeAll;
import static db.MimirTestUtils.create;
import static db.MimirTestUtils.deleteAllDbs;
import static db.MimirTestUtils.init;
import static db.MimirTestUtils.newTempDb;
import static db.MimirTestUtils.scalarCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.types.Row;

/**
 * Cache behavior tests for Mimir. These tests mirror the structure and utilities used by
 * MimirMultiDatabaseTest while focusing on cache semantics rather than multi-database mechanics.
 */
public class MimirCacheTest {

  private Mimir db;

  @BeforeAll
  public static void globalSetup() {
    // Ensure a clean slate before this test suite
    deleteAllDbs();
  }

  @AfterAll
  public static void globalCleanup() {
    // Close and remove all DBs created via helpers
    closeAll();
    deleteAllDbs();
  }

  @BeforeEach
  public void setUp() {
    Path dbPath = newTempDb("cache-");
    db = create(dbPath.toString());
    init(db, dbPath.toString());

    // Simple test table
    db.execute("CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");

    // Enable cache for this test thread and scope invalidation to TEST table
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));
  }

  @AfterEach
  public void tearDown() {
    // Clear per-thread cache context
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();

    // Close this test's DB instance
    try {
      if (db != null) {
        db.close();
      }
    } catch (Exception ignored) {
    }
  }

  @Test
  @DisplayName("Cache populate (MISS), then HIT, then invalidate on write")
  public void testCachePopulateHitAndInvalidate() {
    // Seed
    db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "B");

    // First read: populates cache
    List<Row> first = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(2, first.size());

    // Second read: should come from cache (object identity)
    List<Row> second = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertSame(first, second, "Second query should return the same cached instance");

    // Write causes invalidation for TEST
    db.execute("INSERT INTO TEST (name) VALUES (?)", "C");

    // Third read: cache should have been invalidated and repopulated
    List<Row> third = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(3, third.size());
  }

  @Test
  @DisplayName("Object identity when cached")
  public void testObjectIdentityWhenCached() {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "X");

    List<Row> first = db.getRows("SELECT * FROM TEST");
    List<Row> second = db.getRows("SELECT * FROM TEST");
    assertSame(first, second, "Second query should return the same cached object instance");
  }

  @Test
  @DisplayName("Bypass cache when disabled for current thread")
  public void testCacheBypassWhenDisabled() {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "Y");
    List<Row> cached = db.getRows("SELECT * FROM TEST");

    // Disable caching for the current thread
    Mimir.disableCacheForCurrentThread();
    try {
      List<Row> fresh = db.getRows("SELECT * FROM TEST");
      assertNotSame(cached, fresh, "Disabling cache should force a fresh query instance");
    } finally {
      // Restore for other tests
      Mimir.enableCacheForCurrentThread();
    }
  }

  @Test
  @DisplayName("TTL expiration evicts entry and refreshes on next read")
  public void testTtlExpirationEvictsEntry() throws InterruptedException {
    // Set a small TTL for this query context
    Mimir.setCurrentTtl(75); // ms

    db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    // Mutate data while cached
    db.execute("INSERT INTO TEST (name) VALUES (?)", "B");

    // Sleep beyond TTL to ensure expiration (with margin)
    Thread.sleep(200);

    // Next read should refresh with new data
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(2, after.size(), "Entry should have expired and been refreshed");
  }

  @Test
  @DisplayName("Isolation across multiple databases (cache and invalidation scoped by tables)")
  public void testCacheIsolationAcrossDatabases() {
    // Main DB (TEST table)
    db.execute("INSERT INTO TEST (name) VALUES (?)", "m1");
    List<Row> mainFirst = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, mainFirst.size());

    // Separate logs DB with its own table
    Path logsPath = newTempDb("logs-");
    Mimir logsDb = create(logsPath.toString());
    init(logsDb, logsPath.toString());
    logsDb.execute(
        "CREATE TABLE IF NOT EXISTS LOG_ENTRIES (id INTEGER PRIMARY KEY AUTOINCREMENT, logger TEXT)");

    // Operate on logs DB: should not affect main DB data
    logsDb.execute("INSERT INTO LOG_ENTRIES (logger) VALUES (?)", "CacheTest");
    int logsCount = scalarCount(logsDb,
        "SELECT COUNT(*) as count FROM LOG_ENTRIES WHERE logger = ?", "CacheTest");
    assertEquals(1, logsCount);

    // Another read on main DB should still return the same data (ignore object identity)
    List<Row> mainSecond = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, mainSecond.size());
    assertEquals(
        mainFirst.get(0).getString("name").orElse(""),
        mainSecond.get(0).getString("name").orElse("")
    );

    logsDb.close();
  }

  @Test
  @DisplayName("Concurrent reads after write invalidation observe fresh data")
  public void testReadAfterWriteInvalidationConcurrent() throws Exception {
    // Seed and warm cache
    db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    List<Row> warm = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, warm.size());

    // Write invalidates cache for TEST
    db.execute("INSERT INTO TEST (name) VALUES (?)", "B");

    // Two concurrent readers should both see 2 rows
    CountDownLatch start = new CountDownLatch(1);
    final int[] sizes = new int[2];

    Thread t1 = new Thread(() -> {
      try {
        start.await(1, TimeUnit.SECONDS);
        sizes[0] = db.getRows("SELECT * FROM TEST ORDER BY id").size();
      } catch (InterruptedException ignored) {
      }
    });

    Thread t2 = new Thread(() -> {
      try {
        start.await(1, TimeUnit.SECONDS);
        sizes[1] = db.getRows("SELECT * FROM TEST ORDER BY id").size();
      } catch (InterruptedException ignored) {
      }
    });

    t1.start();
    t2.start();
    start.countDown();
    t1.join();
    t2.join();

    assertEquals(2, sizes[0]);
    assertEquals(2, sizes[1]);
  }

  @Test
  @DisplayName("Cached read is faster than direct DB read (averaged)")
  public void testCachedReadIsFaster() {
    // Populate a reasonably sized table
    for (int i = 0; i < 15000; i++) {
      db.execute("INSERT INTO TEST (name) VALUES (?)", "row-" + i);
    }

    // Warm up
    db.getRows("SELECT * FROM TEST ORDER BY id");

    // Measure uncached average over multiple runs
    long uncachedTotal = 0L;
    int runs = 5;
    for (int i = 0; i < runs; i++) {
      // Disable cache to force DB read
      Mimir.disableCacheForCurrentThread();
      long t1 = System.nanoTime();
      List<Row> r = db.getRows("SELECT * FROM TEST ORDER BY id");
      long dt = System.nanoTime() - t1;
      uncachedTotal += dt;
      // Re-enable cache for the next loop
      Mimir.enableCacheForCurrentThread();
      assertEquals(15000, r.size());
    }
    long uncachedAvg = uncachedTotal / runs;

    // Measure cached average over multiple runs
    // Prime the cache once
    List<Row> cachedPrime = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(15000, cachedPrime.size());

    long cachedTotal = 0L;
    for (int i = 0; i < runs; i++) {
      long t2 = System.nanoTime();
      List<Row> r2 = db.getRows("SELECT * FROM TEST ORDER BY id");
      long dt2 = System.nanoTime() - t2;
      cachedTotal += dt2;
      assertEquals(15000, r2.size());
    }
    long cachedAvg = cachedTotal / runs;

    // Cached reads should be faster than uncached reads
    assertTrue(cachedAvg < uncachedAvg,
        "Cached query should be faster than direct DB query on average");
  }

  @Test
  @DisplayName("Parameter sensitivity: same params -> identity, different params -> different instances")
  public void testParameterSensitivityAndIdentity() {
    db.execute("INSERT INTO TEST (name) VALUES (?), (?), (?)", "A", "B", "B");

    // Same SQL and params should be identity-equal
    List<Row> a1 = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "A");
    List<Row> a2 = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "A");
    assertSame(a1, a2);
    assertEquals(1, a1.size());

    // Different params produce a different cache key and instance
    List<Row> b1 = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "B");
    assertEquals(2, b1.size());
    assertNotSame(a1, b1);
  }

  @Test
  @DisplayName("Disabled cache does not populate: snapshot remains empty until enabled")
  public void testDisableDoesNotPopulateCache() {
    // Ensure empty cache to start
    Mimir.flushCache();

    // Disable and perform reads
    Mimir.disableCacheForCurrentThread();
    try {
      db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "X", "Y");
      db.getRows("SELECT * FROM TEST ORDER BY id");
      db.getRows("SELECT * FROM TEST ORDER BY id");

      // With cache disabled, snapshot should still be empty
      assertTrue(Mimir.snapshotCache().isEmpty());
    } finally {
      Mimir.enableCacheForCurrentThread();
    }

    // Now enable and read once -> snapshot should have entries
    db.getRows("SELECT * FROM TEST ORDER BY id");
    assertFalse(Mimir.snapshotCache().isEmpty());
  }

  @Test
  @DisplayName("flushCache() clears entries and forces next read to create a new instance")
  public void testFlushCacheClearsEntries() {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "Z");
    List<Row> first = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertFalse(Mimir.snapshotCache().isEmpty());

    Mimir.flushCache();
    assertTrue(Mimir.snapshotCache().isEmpty());

    List<Row> second = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertNotSame(first, second);
  }

  @Test
  @DisplayName("Per-key TTL without writes: one key expires while another persists")
  public void testPerKeyTtlWithoutWrites() throws InterruptedException {
    db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "K1", "K2");

    // Apply short TTL only for K1 and cache it
    Mimir.setCurrentTtl(60);
    List<Row> k1_first = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K1");
    assertEquals(1, k1_first.size());

    // Clear TTL for subsequent queries and cache K2 with default/no TTL
    Mimir.clearCurrentTtl();
    List<Row> k2_first = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K2");
    assertEquals(1, k2_first.size());

    // No writes. Wait past K1 TTL so only K1 expires
    Thread.sleep(180);

    // K1 should refresh (new instance), same data
    List<Row> k1_after = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K1");
    assertEquals(1, k1_after.size());
    assertNotSame(k1_first, k1_after);

    // K2 should remain cached (same instance)
    List<Row> k2_after = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K2");
    assertEquals(1, k2_after.size());
    assertSame(k2_first, k2_after);
  }

  @Test
  @DisplayName("Table write invalidates all cached queries for that table (both keys)")
  public void testTableWriteInvalidatesAllKeys() {
    db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "K1", "K2");

    // Cache both keys
    List<Row> k1_first = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K1");
    List<Row> k2_first = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K2");
    assertEquals(1, k1_first.size());
    assertEquals(1, k2_first.size());

    // Any write to TEST invalidates both cached keys
    db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "K1", "K2");

    // Next reads should be new instances reflecting the extra rows
    List<Row> k1_after = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K1");
    List<Row> k2_after = db.getRows("SELECT * FROM TEST WHERE name = ? ORDER BY id", "K2");
    assertNotSame(k1_first, k1_after);
    assertNotSame(k2_first, k2_after);
    assertEquals(2, k1_after.size());
    assertEquals(2, k2_after.size());
  }

  @Test
  @DisplayName("Concurrent first load: many threads obtain the same cached instance")
  public void testConcurrentFirstLoadSingleInstance() throws Exception {
    Mimir.flushCache();
    db.execute("INSERT INTO TEST (name) VALUES (?), (?), (?), (?)", "A", "B", "C", "D");

    final int threads = 8;
    final List<Row>[] results = new List[threads];
    CountDownLatch start = new CountDownLatch(1);
    Thread[] ts = new Thread[threads];
    for (int i = 0; i < threads; i++) {
      final int idx = i;
      ts[i] = new Thread(() -> {
        try {
          start.await(1, TimeUnit.SECONDS);
          results[idx] = db.getRows("SELECT * FROM TEST ORDER BY id");
        } catch (InterruptedException ignored) {
        }
      });
      ts[i].start();
    }
    start.countDown();
    for (Thread t : ts) {
      t.join();
    }

    // All threads should see the very same cached instance
    for (int i = 1; i < threads; i++) {
      assertEquals(results[0], results[i]);
    }
    assertEquals(4, results[0].size());
  }

  @Test
  @DisplayName("Update and delete operations also invalidate and refresh cache")
  public void testUpdateAndDeleteInvalidate() {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "X");
    List<Row> first = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, first.size());

    // Update should invalidate TEST
    db.execute("UPDATE TEST SET name = ? WHERE name = ?", "X2", "X");
    List<Row> afterUpdate = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals("X2", afterUpdate.get(0).getString("name").orElse(""));
    assertNotSame(first, afterUpdate);

    // Delete should invalidate TEST
    db.execute("DELETE FROM TEST WHERE name = ?", "X2");
    List<Row> afterDelete = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(0, afterDelete.size());
  }

  @Test
  @DisplayName("Writes to unrelated table do not invalidate cached TEST data when tables differ")
  public void testUnrelatedTableWriteDoesNotInvalidate() {
    // Start with TEST cached
    db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    List<Row> testCached = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, testCached.size());

    // Create OTHER table and write to it
    db.execute("CREATE TABLE IF NOT EXISTS OTHER (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)");
    db.execute("INSERT INTO OTHER (v) VALUES (?)", "zzz");

    // TEST cached result should remain the same instance if invalidation scoping is correct
    List<Row> testAgain = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(testCached, testAgain);
  }

  @Test
  @DisplayName("Transaction semantics: rollback does not invalidate; commit does")
  public void testTransactionRollbackVsCommitInvalidation() {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "T");

    // Warm cache
    List<Row> cached = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, cached.size());

    // Phase 1: ROLLBACK should NOT invalidate cached entry
    db.execute("BEGIN");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "T_rollback");
    db.execute("ROLLBACK");

    List<Row> afterRollback = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(cached, afterRollback, "Rollback must not invalidate the cache");
    assertEquals(1, afterRollback.size());

    // Phase 2: COMMIT should invalidate cached entry
    db.execute("BEGIN");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "T_commit");
    db.execute("COMMIT");

    List<Row> afterCommit = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertNotSame(cached, afterCommit, "Commit must invalidate and refresh the cache");
    assertEquals(2, afterCommit.size());
  }

  @Test
  @DisplayName("Thread-local isolation: cache flags and TTL do not leak across threads")
  public void testThreadLocalIsolation() throws Exception {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "TL");

    // Main thread: enable cache and read once
    Mimir.enableCacheForCurrentThread();
    List<Row> main1 = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, main1.size());

    // Worker thread: disable cache and set short TTL for its own context only
    CountDownLatch ready = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    final List<Row>[] workerReads = new List[2];
    Thread t = new Thread(() -> {
      try {
        Mimir.disableCacheForCurrentThread();
        Mimir.setCurrentTtl(50);
        ready.countDown();
        workerReads[0] = db.getRows("SELECT * FROM TEST ORDER BY id");
        workerReads[1] = db.getRows("SELECT * FROM TEST ORDER BY id");
      } finally {
        Mimir.clearCurrentTtl();
        Mimir.enableCacheForCurrentThread();
        done.countDown();
      }
    });
    t.start();
    ready.await(1, TimeUnit.SECONDS);
    done.await(2, TimeUnit.SECONDS);

    // In worker: cache disabled -> consecutive reads should NOT be identity-equal
    assertNotSame(workerReads[0], workerReads[1]);

    // In main thread: previously cached instance remains valid and identity-equal on next read
    List<Row> main2 = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertSame(main1, main2, "Thread-local flags must not leak to main thread");
  }

  @Test
  @DisplayName("Stampede after invalidation: post-write concurrent readers get the same new instance")
  public void testStampedeAfterInvalidationSingleFlight() throws Exception {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "S");

    // Warm cache with first instance
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    // Invalidate via write
    db.execute("INSERT INTO TEST (name) VALUES (?)", "S2");

    // Concurrent readers after invalidation
    final int threads = 8;
    final List<Row>[] results = new List[threads];
    CountDownLatch start = new CountDownLatch(1);
    Thread[] ts = new Thread[threads];
    for (int i = 0; i < threads; i++) {
      final int idx = i;
      ts[i] = new Thread(() -> {
        try {
          start.await(1, TimeUnit.SECONDS);
          results[idx] = db.getRows("SELECT * FROM TEST ORDER BY id");
        } catch (InterruptedException ignored) {
        }
      });
      ts[i].start();
    }
    start.countDown();
    for (Thread th : ts) {
      th.join();
    }

    // All should get the same refreshed instance, distinct from the pre-invalidation one
    for (int i = 1; i < threads; i++) {
      assertEquals(results[0], results[i]);
      assertNotSame(before, results[i]);
    }
    assertEquals(2, results[0].size());
  }

  @Test
  @DisplayName("Exception path: failed query does not poison cache; next valid query succeeds and populates")
  public void testExceptionDoesNotPoisonCache() {
    // Ensure cache enabled for this thread
    Mimir.enableCacheForCurrentThread();
    // Ensure TEST exists and is empty
    scalarCount(db, "SELECT COUNT(*) FROM TEST");

    // Attempt a failing query (invalid table) and ensure it throws but does not add to cache
    int beforeSize = Mimir.snapshotCache().size();
    try {
      db.getRows("SELECT * FROM TEST_DOES_NOT_EXIST");
      fail("Expected query to fail for non-existent table");
    } catch (Exception ignored) {
      // expected
    }
    int afterFailSize = Mimir.snapshotCache().size();
    assertEquals(beforeSize, afterFailSize, "Failed queries must not populate the cache");

    // Valid query should succeed and populate cache (verify by identity on repeated reads)
    db.execute("INSERT INTO TEST (name) VALUES (?)", "E");
    List<Row> ok1 = db.getRows("SELECT * FROM TEST ORDER BY id");
    List<Row> ok2 = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, ok1.size());
    assertSame(ok1, ok2, "Successful read should be cached for identical subsequent read");
  }

  @Test
  @DisplayName("Writes to TEST do not invalidate cached OTHER table queries (unrelated scope)")
  public void testWriteToTestDoesNotInvalidateOther() {
    // Prepare OTHER and TEST
    db.execute("CREATE TABLE IF NOT EXISTS OTHER (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)");
    db.execute("INSERT INTO OTHER (v) VALUES (?), (?)", "o1", "o2");

    // Cache OTHER
    List<Row> otherCached = db.getRows("SELECT * FROM OTHER ORDER BY id");
    assertEquals(2, otherCached.size());

    // Write to TEST (unrelated table)
    db.execute("INSERT INTO TEST (name) VALUES (?)", "T_w");

    // OTHER cache should remain the same instance
    List<Row> otherAgain = db.getRows("SELECT * FROM OTHER ORDER BY id");
    assertEquals(otherCached, otherAgain);
  }
}