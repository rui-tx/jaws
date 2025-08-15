//import java.io.File;
//import java.sql.DriverManager;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import org.junit.jupiter.api.AfterAll;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.ruitx.jaws.components.mimir.Mimir;
//import org.ruitx.jaws.types.Row;
//
//public class MimirWriteQueueTest {
//
//  private static final String TEST_DB_PATH = "target/test-write-queue.mimir";
//  private Mimir db;
//
//  @BeforeAll
//  public static void setupDatabase() {
//    try {
//      // Load the SQLite JDBC driver
//      Class.forName("org.sqlite.JDBC");
//      DriverManager.registerDriver(new org.sqlite.JDBC());
//      System.out.println("SQLite JDBC driver loaded successfully for write queue test");
//    } catch (Exception e) {
//      System.err.println("Failed to load SQLite JDBC driver in test: " + e.getMessage());
//      e.printStackTrace();
//    }
//  }
//
//  @AfterAll
//  public static void cleanup() {
//    // Clean up test database
//    new File(TEST_DB_PATH).delete();
//  }
//
//  public static void main(String[] args) {
//    try {
//      setupDatabase();
//
//      MimirWriteQueueTest test = new MimirWriteQueueTest();
//
//      System.out.println("=== Mimir Simplified Write Queue Test ===\n");
//
//      test.setup();
//      test.testBasicWriteQueueOperations();
//      test.tearDown();
//      System.out.println();
//
//      test.setup();
//      test.testConcurrentWrites();
//      test.tearDown();
//      System.out.println();
//
//      test.setup();
//      test.testTransactionSupport();
//      test.tearDown();
//      System.out.println();
//
//      test.setup();
//      test.testWriteQueuePerformance();
//      test.tearDown();
//      System.out.println();
//
//      cleanup();
//      System.out.println("=== All write queue tests completed successfully! ===");
//
//    } catch (Exception e) {
//      System.err.println("Write queue test failed: " + e.getMessage());
//      e.printStackTrace();
//    }
//  }
//
//  @BeforeEach
//  public void setup() {
//    // Clean up any existing test database
//    new File(TEST_DB_PATH).delete();
//
//    // Create fresh database instance
//    db = new Mimir(TEST_DB_PATH);
//    db.initializeDatabase(TEST_DB_PATH);
//
//    // Create test table
//    db.execute(
//        "CREATE TABLE test_users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT)");
//    System.out.println("✅ Test database and table created");
//  }
//
//  @AfterEach
//  public void tearDown() {
//    if (db != null) {
//      db.close();
//    }
//  }
//
/// /  @Test /  public void testBasicWriteQueueOperations() { /    System.out.println("=== Testing
/// Basic Write Queue Operations ==="); / /    // Test executeSql with parameters /    int affected
/// = db.execute("INSERT INTO test_users (name, email) VALUES (?, ?)", /        "John Doe",
/// "john@example.com"); /    System.out.println("✅ INSERT affected " + affected + " rows"); / /
/// // Test executeInsert /    List<Row> inserted = db.insertAndFetch("INSERT INTO test_users (name,
/// email) VALUES (?, ?)", /        "Jane Smith", "jane@example.com"); /    System.out.println("✅
/// executeInsert returned " + inserted.size() + " rows"); /    if (!inserted.isEmpty()) { /
/// Row insertedRow = inserted.get(0); /      System.out.println("  Inserted ID: " +
/// insertedRow.getLong("id").orElse(-1L)); /      System.out.println("  Inserted Name: " +
/// insertedRow.getString("name").orElse("N/A")); /    } / /    // Verify data was written correctly
/// /    List<Row> users = db.getRows("SELECT * FROM test_users ORDER BY id"); /
/// System.out.println("✅ Found " + users.size() + " users in database"); / /    for (Row user :
/// users) { /      System.out.println("  User: " + user.getString("name").orElse("N/A") + /
///  " (" + user.getString("email").orElse("N/A") + ")"); /    } / /    // Test UPDATE /    int
/// updated = db.executeSql("UPDATE test_users SET email = ? WHERE name = ?", /
/// "john.doe@newdomain.com", "John Doe"); /    System.out.println("✅ UPDATE affected " + updated +
/// " rows"); / /    // Test DELETE /    int deleted = db.executeSql("DELETE FROM test_users WHERE
/// name = ?", "Jane Smith"); /    System.out.println("✅ DELETE affected " + deleted + " rows"); / /
///    // Final verification /    List<Row> finalUsers = db.getRows("SELECT * FROM test_users"); /
///  System.out.println("✅ Final user count: " + finalUsers.size()); /  }
//
//  @Test
//  public void testConcurrentWrites() {
//    System.out.println("=== Testing Concurrent Writes (SQLite Lock Mitigation) ===");
//
//    ExecutorService executor = Executors.newFixedThreadPool(5);
//    CompletableFuture<Void>[] futures = new CompletableFuture[10];
//
//    // Launch 10 concurrent write operations
//    for (int i = 0; i < 10; i++) {
//      final int userId = i;
//      futures[i] = CompletableFuture.runAsync(() -> {
//        try {
//          // Each thread performs multiple write operations
//          db.executeSql("INSERT INTO test_users (name, email) VALUES (?, ?)",
//              "User" + userId, "user" + userId + "@test.com");
//
//          db.executeSql("UPDATE test_users SET email = ? WHERE name = ?",
//              "updated" + userId + "@test.com", "User" + userId);
//
//          System.out.println("✅ Thread " + userId + " completed writes");
//        } catch (Exception e) {
//          System.err.println("❌ Thread " + userId + " failed: " + e.getMessage());
//          throw new RuntimeException(e);
//        }
//      }, executor);
//    }
//
//    // Wait for all operations to complete
//    try {
//      CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
//      System.out.println("✅ All concurrent writes completed successfully");
//    } catch (Exception e) {
//      System.err.println("❌ Concurrent writes failed: " + e.getMessage());
//      throw new RuntimeException(e);
//    } finally {
//      executor.shutdown();
//    }
//
//    // Verify all data was written correctly
//    List<Row> users = db.getRows("SELECT COUNT(*) as count FROM test_users");
//    int userCount = users.get(0).getInt("count").orElse(0);
//    System.out.println("✅ Final user count after concurrent writes: " + userCount);
//
//    if (userCount == 10) {
//      System.out.println("✅ All concurrent writes were successful - no SQLite lock errors!");
//    } else {
//      System.err.println("❌ Expected 10 users, but found " + userCount);
//    }
//  }
//
//  @Test
//  public void testTransactionSupport() {
//    System.out.println("=== Testing Transaction Support ===");
//
//    try {
//      // Test successful transaction
//      db.beginTransaction();
//      System.out.println("✅ Transaction started");
//
//      db.executeSql("INSERT INTO test_users (name, email) VALUES (?, ?)",
//          "Transaction User 1", "tx1@test.com");
//      db.executeSql("INSERT INTO test_users (name, email) VALUES (?, ?)",
//          "Transaction User 2", "tx2@test.com");
//
//      // Verify data is not visible outside transaction yet
//      List<Row> beforeCommit = db.getRows("SELECT COUNT(*) as count FROM test_users");
//      int countBeforeCommit = beforeCommit.get(0).getInt("count").orElse(0);
//      System.out.println("✅ Users in transaction (before commit): " + countBeforeCommit);
//
//      db.commitTransaction();
//      System.out.println("✅ Transaction committed");
//
//      // Verify data is now visible
//      List<Row> afterCommit = db.getRows("SELECT COUNT(*) as count FROM test_users");
//      int countAfterCommit = afterCommit.get(0).getInt("count").orElse(0);
//      System.out.println("✅ Users after commit: " + countAfterCommit);
//
//    } catch (Exception e) {
//      System.err.println("❌ Transaction test failed: " + e.getMessage());
//      try {
//        db.rollbackTransaction();
//      } catch (Exception rollbackError) {
//        System.err.println("❌ Rollback also failed: " + rollbackError.getMessage());
//      }
//      throw new RuntimeException(e);
//    }
//
//    try {
//      // Test transaction rollback
//      db.beginTransaction();
//      System.out.println("✅ Rollback test transaction started");
//
//      db.executeSql("INSERT INTO test_users (name, email) VALUES (?, ?)",
//          "Rollback User", "rollback@test.com");
//
//      List<Row> beforeRollback = db.getRows("SELECT COUNT(*) as count FROM test_users");
//      int countBeforeRollback = beforeRollback.get(0).getInt("count").orElse(0);
//      System.out.println("✅ Users before rollback: " + countBeforeRollback);
//
//      db.rollbackTransaction();
//      System.out.println("✅ Transaction rolled back");
//
//      // Verify rollback worked
//      List<Row> afterRollback = db.getRows("SELECT COUNT(*) as count FROM test_users");
//      int countAfterRollback = afterRollback.get(0).getInt("count").orElse(0);
//      System.out.println("✅ Users after rollback: " + countAfterRollback);
//
//      if (countAfterRollback < countBeforeRollback) {
//        System.out.println("✅ Rollback worked correctly!");
//      } else {
//        System.err.println("❌ Rollback did not work as expected");
//      }
//
//    } catch (Exception e) {
//      System.err.println("❌ Rollback test failed: " + e.getMessage());
//      throw new RuntimeException(e);
//    }
//  }
//
//  @Test
//  public void testWriteQueuePerformance() {
//    System.out.println("=== Testing Write Queue Performance ===");
//
//    int numOperations = 100;
//    long startTime = System.currentTimeMillis();
//
//    // Perform batch writes
//    for (int i = 0; i < numOperations; i++) {
//      db.executeSql("INSERT INTO test_users (name, email) VALUES (?, ?)",
//          "PerfUser" + i, "perf" + i + "@test.com");
//    }
//
//    long endTime = System.currentTimeMillis();
//    long duration = endTime - startTime;
//
//    System.out.println("✅ Completed " + numOperations + " write operations in " + duration + "ms");
//    System.out.println("✅ Average: " + (duration / (double) numOperations) + "ms per operation");
//
//    // Verify all writes completed
//    List<Row> users = db.getRows(
//        "SELECT COUNT(*) as count FROM test_users WHERE name LIKE 'PerfUser%'");
//    int perfUserCount = users.get(0).getInt("count").orElse(0);
//    System.out.println("✅ Performance test users written: " + perfUserCount);
//
//    if (perfUserCount == numOperations) {
//      System.out.println("✅ All performance test writes completed successfully!");
//    } else {
//      System.err.println("❌ Expected " + numOperations + " users, but found " + perfUserCount);
//    }
//  }
//}
