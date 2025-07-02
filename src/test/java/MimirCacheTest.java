package org.ruitx.jaws;

import org.junit.jupiter.api.*;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Row;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic sanity-check for Mimir′s query-level cache.
 * <p>
 * Scenario:
 * 1. Create a fresh in-memory database with one table.
 * 2. Enable cache → first SELECT should populate cache (MISS → PUT).
 * 3. Same SELECT again should be served from cache (HIT) – row count unchanged.
 * 4. Perform an INSERT (write) which targets the same table → cache entry must be invalidated automatically.
 * 5. Third SELECT must reflect the new row (cache repopulated)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MimirCacheTest {

    private static final String DB_PATH = "cachetest.db";
    private Mimir db;

    @BeforeAll
    void loadDriver() throws Exception {
        Class.forName("org.sqlite.JDBC");
    }

    @BeforeEach
    void setup() {
        // Fresh database each test
        new File(DB_PATH).delete();
        db = new Mimir(DB_PATH, null); // no schema – create manually
        db.executeSql("CREATE TABLE TEST (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
        Mimir.enableCacheForCurrentThread();
        Mimir.setCurrentTables(java.util.Set.of("TEST"));
    }

    @AfterEach
    void cleanup() {
        Mimir.clearCurrentTables();
        Mimir.clearCurrentTtl();
        Mimir.disableCacheForCurrentThread();
        db.close();
        new File(DB_PATH).delete();
    }

    @Test
    void cachePopulateHitAndInvalidate() {
        // Insert two rows
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "A");
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "B");

        // 1st read – populates cache
        List<Row> first = db.getRows("SELECT * FROM TEST ORDER BY id");
        assertEquals(2, first.size(), "Should see 2 rows initially");

        // 2nd read – should come from cache, still 2 rows
        List<Row> second = db.getRows("SELECT * FROM TEST ORDER BY id");
        assertSame(first.size(), second.size(), "Cache HIT should return same row count");

        // Write: add third row (this should invalidate cache for table TEST)
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "C");

        // 3rd read – cache was invalidated, row count should now be 3
        List<Row> third = db.getRows("SELECT * FROM TEST ORDER BY id");
        assertEquals(3, third.size(), "After invalidation should see 3 rows");
    }

    @Test
    void objectIdentityWhenCached() {
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "X");
        List<Row> first = db.getRows("SELECT * FROM TEST");
        List<Row> second = db.getRows("SELECT * FROM TEST");
        assertSame(first, second, "Second query should return the same cached object instance");
    }

    @Test
    void cacheBypassWhenDisabled() {
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "Y");
        List<Row> cached = db.getRows("SELECT * FROM TEST");
        Mimir.disableCacheForCurrentThread();
        List<Row> fresh = db.getRows("SELECT * FROM TEST");
        assertNotSame(cached, fresh, "Disabling cache should force a fresh query");
        Mimir.enableCacheForCurrentThread(); // restore for other tests
    }

    @Test
    void ttlExpirationEvictsEntry() throws InterruptedException {
        Mimir.setCurrentTtl(50); // 50 ms
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "A");
        List<Row> before = db.getRows("SELECT * FROM TEST");
        db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "B");
        Thread.sleep(120); // > ttl
        List<Row> after = db.getRows("SELECT * FROM TEST");
        assertEquals(2, after.size(), "Entry should have expired and been refreshed with new data");
    }

    @Test
    void cachedReadIsFaster() {
        for (int i = 0; i < 8000; i++) {
            db.executeInsert("INSERT INTO TEST (name) VALUES (?)", "row-" + i);
        }
        long t1 = System.nanoTime();
        db.getRows("SELECT * FROM TEST");
        long first = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        db.getRows("SELECT * FROM TEST");
        long second = System.nanoTime() - t2;

        System.out.printf("First read: %d µs, Cached read: %d µs%n", first / 1000, second / 1000);
        assertTrue(second < first, "Cached query should be faster than initial query");
    }
}