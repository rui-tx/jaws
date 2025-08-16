package db;

import static db.MimirTestUtils.closeAll;
import static db.MimirTestUtils.create;
import static db.MimirTestUtils.deleteAllDbs;
import static db.MimirTestUtils.init;
import static db.MimirTestUtils.newTempDb;
import static db.MimirTestUtils.scalarCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
 * Transaction behavior tests for Mimir focusing on correctness, isolation, and cache interaction.
 */
public class MimirTransactionTest {

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

  @BeforeEach
  public void setUp() {
    Path dbPath = newTempDb("tx-");
    db = create(dbPath.toString());
    init(db, dbPath.toString());

    // Simple table for tests
    db.execute("CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");

    // Enable cache and scope invalidation to TEST
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));
  }

  @AfterEach
  public void tearDown() {
    // Clear per-thread cache context
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();

    try {
      if (db != null) {
        db.close();
      }
    } catch (Exception ignored) {
    }
  }

  @Test
  @DisplayName("Given no transaction, when commit is called, then it throws")
  public void testGivenNoTransaction_WhenCommit_ThenThrows() {
    assertThrows(SQLException.class, () -> db.commitTransaction());
  }

  @Test
  @DisplayName("Given no transaction, when rollback is called, then it throws")
  public void testGivenNoTransaction_WhenRollback_ThenThrows() {
    assertThrows(SQLException.class, () -> db.rollbackTransaction());
  }

  @Test
  @DisplayName("Given transaction started, when begin is called again, then it throws")
  public void testGivenTransactionStarted_WhenBeginAgain_ThenThrows() throws Exception {
    db.beginTransaction();
    try {
      assertThrows(SQLException.class, () -> db.beginTransaction());
    } finally {
      db.rollbackTransaction();
    }
  }

  @Test
  @DisplayName("Given cached read, when commit after writes, then data visible and cache invalidated")
  public void testGivenCachedRead_WhenCommitAfterWrites_ThenDataVisibleAndCacheInvalidated()
      throws Exception {
    // Seed none, prime cache with empty result
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(0, before.size());

    CountDownLatch readyToReadBeforeCommit = new CountDownLatch(1);
    CountDownLatch commitNow = new CountDownLatch(1);

    final int[] observedBeforeCommit = new int[1];
    Thread reader = new Thread(() -> {
      try {
        readyToReadBeforeCommit.await(1, TimeUnit.SECONDS);
        // Outside transaction: should not see uncommitted rows
        observedBeforeCommit[0] = db.getRows("SELECT * FROM TEST ORDER BY id").size();
        // Wait for commit, then read again to see committed rows
        commitNow.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
      }
    });

    db.beginTransaction();
    try {
      // Write inside transaction and read inside transaction (should see rows)
      db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "A", "B");
      List<Row> inTx = db.getRows("SELECT * FROM TEST ORDER BY id");
      assertEquals(2, inTx.size());

      // Spawn outside reader and let it read before commit
      reader.start();
      readyToReadBeforeCommit.countDown();

      // Give reader some time to perform its first read
      Thread.sleep(100);

      // Outside should not see uncommitted rows (still 0)
      assertEquals(0, observedBeforeCommit[0]);

      // Commit the transaction; this should invalidate cached TEST queries
      db.commitTransaction();
      commitNow.countDown();
      reader.join();
    } finally {
      // Ensure no open transaction leaks on failure
      try {
        db.rollbackTransaction();
      } catch (Exception ignored) {
      }
    }

    // After commit, a new read should reflect the 2 rows and not be the same cached instance
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(2, after.size());
    assertNotSame(before, after, "Cache should have been invalidated on commit");
  }

  @Test
  @DisplayName("Given cached read, when rollback after writes, then data unchanged but cache invalidated")
  public void testGivenCachedRead_WhenRollbackAfterWrites_ThenDataUnchangedButCacheInvalidated()
      throws Exception {
    // Seed base row and warm cache with non-empty result
    db.execute("INSERT INTO TEST (name) VALUES (?)", "BASE");
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    db.beginTransaction();
    try {
      db.execute("INSERT INTO TEST (name) VALUES (?)", "X");
      // Inside tx we see the rows
      assertEquals(2, db.getRows("SELECT * FROM TEST ORDER BY id").size());
      // Force an error path by rolling back
      db.rollbackTransaction();
    } finally {
      // Ensure state cleared
      try {
        db.rollbackTransaction();
      } catch (Exception ignored) {
      }
    }

    // After rollback: data unchanged (1 row) but cache invalidated so instance differs
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
    assertNotSame(before, after, "Rollback should invalidate cached entries for modified tables");
  }

  @Test
  @DisplayName("Given warm cache, when read inside transaction, then cache bypassed for in-tx read")
  public void testGivenWarmCache_WhenReadInsideTransaction_ThenCacheBypassed() throws Exception {
    db.execute("INSERT INTO TEST (name) VALUES (?)", "WARM");
    List<Row> cached = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, cached.size());

    db.beginTransaction();
    try {
      // In transaction: read should bypass cache -> not same instance
      List<Row> inTx = db.getRows("SELECT * FROM TEST ORDER BY id");
      assertEquals(1, inTx.size());
      assertNotSame(cached, inTx, "Reads inside an active transaction bypass cache");

      // No writes; commit should not change data but will not invalidate since no modified tables
      db.commitTransaction();
    } finally {
      try {
        db.rollbackTransaction();
      } catch (Exception ignored) {
      }
    }

    // After commit, subsequent reads resume normal caching semantics
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
  }

  @Test
  @DisplayName("Given uncommitted writes, when other thread reads, then isolation maintained until commit")
  public void testGivenUncommittedWrites_WhenOtherThreadReads_ThenIsolationMaintainedUntilCommit()
      throws Exception {
    CountDownLatch writesDone = new CountDownLatch(1);
    CountDownLatch commitNow = new CountDownLatch(1);

    final int[] counts = new int[2];

    Thread t = new Thread(() -> {
      try {
        // First read before any writes
        counts[0] = db.getRows("SELECT * FROM TEST ORDER BY id").size();
        // Wait until writes are performed but not committed
        writesDone.await(1, TimeUnit.SECONDS);
        // Read again before commit: still should be 0
        counts[1] = db.getRows("SELECT * FROM TEST ORDER BY id").size();
        // Wait for commit so test can proceed
        commitNow.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
      }
    });

    db.beginTransaction();
    try {
      db.execute("INSERT INTO TEST (name) VALUES (?), (?)", "A", "B");
      // Inside tx we see the rows
      assertEquals(2, db.getRows("SELECT * FROM TEST ORDER BY id").size());
      t.start();
      // Allow reader to progress and then signal writes are done
      Thread.sleep(50);
      writesDone.countDown();
      // Give the reader a chance to read before commit
      Thread.sleep(150);
      // Before commit, outside thread should see 0 both times
      assertEquals(0, counts[0]);
      assertEquals(0, counts[1]);

      db.commitTransaction();
      commitNow.countDown();
      t.join();
    } finally {
      try {
        db.rollbackTransaction();
      } catch (Exception ignored) {
      }
    }

    // After commit, outside reads should see committed rows
    assertEquals(2, db.getRows("SELECT * FROM TEST ORDER BY id").size());
  }

  @Test
  @DisplayName("Given multiple ops in transaction, when any fails, then rollback and no partial writes")
  public void testGivenMultipleOpsInTransaction_WhenAnyFails_ThenRollbackAndNoPartialWrites()
      throws Exception {
    // Start a transaction and perform a few statements, introducing a failure
    db.beginTransaction();
    boolean rolledBack = false;
    try {
      db.execute("INSERT INTO TEST (name) VALUES (?)", "OK1");
      db.execute("INSERT INTO TEST (name) VALUES (?)", "OK2");

      // Introduce a failing statement (invalid SQL)
      try {
        db.execute("INSRT INTO TEST (name) VALUES (?)", "FAIL");
      } catch (RuntimeException re) {
        // On error we perform an explicit rollback
        db.rollbackTransaction();
        rolledBack = true;
      }
    } finally {
      // Ensure no open tx remains
      if (!rolledBack) {
        try {
          db.rollbackTransaction();
        } catch (Exception ignored) {
        }
      }
    }

    // After rollback, table should be unchanged
    int count = scalarCount(db, "SELECT COUNT(*) as count FROM TEST", new Object[]{});
    assertEquals(0, count, "No partial writes should persist after rollback");
  }

  @Test
  @DisplayName("Given transaction cycle, when repeated, then no state leak across cycles")
  public void testGivenTransactionCycle_WhenRepeated_ThenNoStateLeak() throws Exception {
    // Cycle 1: commit one row
    db.beginTransaction();
    db.execute("INSERT INTO TEST (name) VALUES (?)", "C1");
    db.commitTransaction();

    assertEquals(1, scalarCount(db, "SELECT COUNT(*) as count FROM TEST", new Object[]{}));

    // Cycle 2: insert then rollback -> no extra row
    db.beginTransaction();
    db.execute("INSERT INTO TEST (name) VALUES (?)", "C2");
    // In-tx sees 2
    assertEquals(2, db.getRows("SELECT * FROM TEST ORDER BY id").size());
    db.rollbackTransaction();

    // After rollback still 1
    assertEquals(1, scalarCount(db, "SELECT COUNT(*) as count FROM TEST", new Object[]{}));

    // Cycle 3: commit another row -> total 2
    db.beginTransaction();
    db.execute("INSERT INTO TEST (name) VALUES (?)", "C3");
    db.commitTransaction();

    assertEquals(2, scalarCount(db, "SELECT COUNT(*) as count FROM TEST", new Object[]{}));
  }

  @Test
  @DisplayName("Given commit fails, when committing, then exception propagates and state cleaned")
  public void testGivenCommitFails_WhenCommitting_ThenExceptionPropagatesAndStateCleaned()
      throws Exception {
    // Fresh DB via connector that throws on commit
    Path dbPath = MimirTestUtils.newTempDb("tx-commit-fail-");
    ThrowingConnector connector = new ThrowingConnector(dbPath.toString(), true, false);
    Mimir failingDb = new Mimir(connector);
    init(failingDb, dbPath.toString());

    // Scope cache to TEST for invalidation semantics (not the focus here)
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    // Create table
    failingDb.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");

    // Begin, write, then commit -> expect SQLException
    failingDb.beginTransaction();
    failingDb.execute("INSERT INTO TEST (name) VALUES (?)", "X");
    assertEquals(1, failingDb.getRows("SELECT * FROM TEST").size()); // in-tx visibility

    assertThrows(SQLException.class, failingDb::commitTransaction);

    // After failed commit, state should be cleaned (no tx open), and changes should not persist
    // A fresh read should observe 0 rows (SQLite closes connection -> implicit rollback)
    List<Row> rowsAfter = failingDb.getRows("SELECT * FROM TEST");
    assertEquals(0, rowsAfter.size());

    // Can start a new transaction successfully (no lingering state)
    failingDb.beginTransaction();
    failingDb.rollbackTransaction();

    // Cleanup cache context for this test-created instance
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();

    failingDb.close();
  }

  @Test
  @DisplayName("Given rollback fails, when rolling back, then exception propagates and state cleaned")
  public void testGivenRollbackFails_WhenRollingBack_ThenExceptionPropagatesAndStateCleaned()
      throws Exception {
    // Fresh DB via connector that throws on rollback
    Path dbPath = MimirTestUtils.newTempDb("tx-rollback-fail-");
    ThrowingConnector connector = new ThrowingConnector(dbPath.toString(), false, true);
    Mimir failingDb = new Mimir(connector);
    init(failingDb, dbPath.toString());

    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    failingDb.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");

    failingDb.beginTransaction();
    failingDb.execute("INSERT INTO TEST (name) VALUES (?)", "Y");
    assertEquals(1, failingDb.getRows("SELECT * FROM TEST").size()); // in-tx visibility

    // Expect rollback to fail with SQLException; consume it explicitly to avoid test error
    boolean threw = false;
    try {
      failingDb.rollbackTransaction();
    } catch (SQLException e) {
      threw = true; // expected path
    }
    assertTrue(threw, "Rollback should throw a SQLException");

    // Verify no persistence using a fresh standard Mimir instance (no throwing connector)
    failingDb.close();
    Mimir verifyDb = new Mimir(dbPath.toString());
    init(verifyDb, dbPath.toString());
    List<Row> rowsAfter = verifyDb.getRows("SELECT * FROM TEST");
    assertEquals(0, rowsAfter.size());

    // No lingering tx state on new instance; can open+rollback trivially
    verifyDb.beginTransaction();
    verifyDb.rollbackTransaction();

    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();

    verifyDb.close();
  }

  @Test
  @DisplayName("Given no writes, when committing, then no cache invalidation occurs")
  public void testGivenNoWrites_WhenCommitting_ThenNoCacheInvalidationOccurs() throws Exception {
    // Scope cache to TEST
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    // Ensure table and seed a base row for non-empty cache identity checks
    db.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)");
    db.execute("DELETE FROM TEST");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "BASE");

    // Warm cache
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    // Begin and commit without any writes
    db.beginTransaction();
    db.commitTransaction();

    // No invalidation expected; should be the exact same cached instance
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
    assertEquals(before, after, "Commit without writes should not invalidate cache");

    // Cleanup
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();
  }

  @Test
  @DisplayName("Given no writes, when rolling back, then no cache invalidation occurs")
  public void testGivenNoWrites_WhenRollingBack_ThenNoCacheInvalidationOccurs() throws Exception {
    // Scope cache to TEST
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    // Ensure table and seed base row
    db.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)");
    db.execute("DELETE FROM TEST");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "BASE");

    // Warm cache
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    // Begin and rollback without writes
    db.beginTransaction();
    db.rollbackTransaction();

    // No invalidation expected; same cached instance
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
    assertEquals(before, after, "Rollback without writes should not invalidate cache");

    // Cleanup
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();
  }

  @Test
  @DisplayName("Given failed write inside transaction, when rolling back, then no persistence and cache invalidated")
  public void testGivenFailedWriteInsideTx_WhenRollingBack_ThenNoPersistenceAndCacheInvalidated()
      throws Exception {
    // Scope cache to TEST
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    // Recreate table with UNIQUE constraint to ensure violation occurs
    db.execute("DROP TABLE IF EXISTS TEST");
    db.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)");
    db.execute("DELETE FROM TEST");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "BASE");

    // Warm cache with non-empty result
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    db.beginTransaction();
    // First write succeeds
    db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    // Second write violates UNIQUE(name)
    boolean threw = false;
    Throwable cause = null;
    try {
      db.execute("INSERT INTO TEST (name) VALUES (?)", "A");
    } catch (RuntimeException e) { // Mimir.execute wraps SQLException
      threw = true; // expected
      cause = e.getCause();
    }
    assertTrue(threw, "Second insert should violate UNIQUE constraint");
    if (cause != null) {
      assertTrue(cause instanceof SQLException, "Cause should be SQLException");
    }

    // Roll back to discard all tx changes
    db.rollbackTransaction();

    // Data should be unchanged (only BASE), but cache should be invalidated due to tx writes
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
    assertNotSame(before, after, "Rollback after attempted writes should invalidate cache");

    // Cleanup
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();
  }

  @Test
  @DisplayName("Given INSERT OR REPLACE, when committing, then heuristic extraction invalidates cache")
  public void testGivenInsertOrReplace_WhenCommitting_ThenHeuristicInvalidatesCache()
      throws Exception {
    // Scope cache to TEST
    Mimir.enableCacheForCurrentThread();
    Mimir.setCurrentTables(Set.of("TEST"));

    // Recreate table with UNIQUE(name) to make INSERT OR REPLACE meaningful
    db.execute("DROP TABLE IF EXISTS TEST");
    db.execute(
        "CREATE TABLE IF NOT EXISTS TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)");
    db.execute("DELETE FROM TEST");
    db.execute("INSERT INTO TEST (name) VALUES (?)", "BASE");

    // Warm cache
    List<Row> before = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, before.size());

    // Use SQLite-specific syntax likely to trigger heuristic path in parser
    db.beginTransaction();
    db.execute("INSERT OR REPLACE INTO TEST (name) VALUES (?)", "BASE");
    db.commitTransaction();

    // Cache should be invalidated on commit because TEST table was modified
    List<Row> after = db.getRows("SELECT * FROM TEST ORDER BY id");
    assertEquals(1, after.size());
    assertNotSame(before, after,
        "Commit with INSERT OR REPLACE should invalidate cache via heuristic");

    // Cleanup
    Mimir.clearCurrentTables();
    Mimir.clearCurrentTtl();
    Mimir.disableCacheForCurrentThread();
  }

  // --- Helpers ---

  /**
   * Simple DbConnector that returns a writer connection which throws on commit and/or rollback,
   * using a dynamic proxy around a real SQLite connection. Reader connection is a standard RO
   * conn.
   */
  static class ThrowingConnector implements org.ruitx.jaws.components.mimir.DbConnector {

    private final String dbPath;
    private final boolean throwOnCommit;
    private final boolean throwOnRollback;

    ThrowingConnector(String dbPath, boolean throwOnCommit, boolean throwOnRollback) {
      this.dbPath = dbPath;
      this.throwOnCommit = throwOnCommit;
      this.throwOnRollback = throwOnRollback;
    }

    @Override
    public void initialize() throws Exception {
      // Ensure file exists
      java.nio.file.Files.createFile(java.nio.file.Path.of(dbPath));
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public Connection getReaderConnection() throws SQLException {
      return DriverManager.getConnection("jdbc:sqlite:file:" + dbPath + "?mode=ro&uri=true");
    }

    @Override
    public Connection getWriterConnection() throws SQLException {
      final Connection real = DriverManager.getConnection(
          "jdbc:sqlite:file:" + dbPath + "?uri=true");
      // Ensure FK and busy timeout similar to Mimir
      try (java.sql.Statement st = real.createStatement()) {
        st.execute("PRAGMA foreign_keys=ON");
        st.execute("PRAGMA busy_timeout=5000");
      } catch (SQLException ignored) {
      }

      InvocationHandler h = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          String name = method.getName();
          if ("commit".equals(name) && throwOnCommit) {
            throw new SQLException("Simulated commit failure");
          }
          if ("rollback".equals(name) && throwOnRollback) {
            throw new SQLException("Simulated rollback failure");
          }
          try {
            return method.invoke(real, args);
          } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getTargetException();
          }
        }
      };
      return (Connection) Proxy.newProxyInstance(
          Connection.class.getClassLoader(), new Class[]{Connection.class}, h);
    }

    @Override
    public <T> T withTransaction(java.util.function.Function<Connection, T> work) throws Exception {
      try (Connection c = getWriterConnection()) {
        c.setAutoCommit(false);
        try {
          T res = work.apply(c);
          c.commit();
          return res;
        } catch (Exception e) {
          c.rollback();
          throw e;
        }
      }
    }

    @Override
    public void close() {
      // No pooled resources
    }
  }
}
