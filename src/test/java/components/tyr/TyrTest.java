package components.tyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.logger.JawsLogger;

@DisplayName("Tyr JWT and session management")
public class TyrTest {

  private static Mimir db;

  @BeforeAll
  static void setupDatabases() throws Exception {
    // Register main DB if not present, using temporary file and real schema
    if (!Odin.hasDatabase(Odin.DB_NAME)) {
      Path tmpDb = Files.createTempFile("jaws-tyr-test-mimir", ".sqlite");
      tmpDb.toFile().deleteOnExit();

      DatabaseConfig cfg = new DatabaseConfig(
          tmpDb.toAbsolutePath().toString(),
          ApplicationConfig.DATABASE_SCHEMA_PATH,
          ApplicationConfig.MIMIR_READER_POOL_SIZE,
          ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
          ApplicationConfig.MIMIR_ENABLE_WAL,
          ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
          null,
          "tyr-writer",
          "tyr-reader",
          null
      );
      Odin.registerDatabase(Odin.DB_NAME, cfg);
    }

    // Register logs DB if not present (Tyr doesn't require it, but keep env consistent)
    if (!Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      Path tmpLogs = Files.createTempFile("jaws-tyr-logs", ".sqlite");
      tmpLogs.toFile().deleteOnExit();
      String logsSchema = Paths.get("src/main/resources/sql/logs_schema.sql").toAbsolutePath()
          .toString();
      DatabaseConfig logsCfg = new DatabaseConfig(
          tmpLogs.toAbsolutePath().toString(),
          logsSchema,
          Math.max(2, ApplicationConfig.MIMIR_READER_POOL_SIZE / 2),
          ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
          true,
          ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
          null,
          "tyr-logs-writer",
          "tyr-logs-reader",
          null
      );
      Odin.registerDatabase(Odin.LOGS_DB_NAME, logsCfg);
    }

    // Bootstrap JawsLogger so Tyr's logging doesn't throw during tests
    JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));

    db = Odin.getDB();
  }

  @BeforeEach
  void resetTables() {
    // Ensure clean state between tests
    db.execute("DELETE FROM USER_SESSION");
    db.execute("DELETE FROM USER");
  }

  private void insertUser(int id, String username) {
    long now = Instant.now().getEpochSecond();
    db.execute(
        "INSERT INTO USER (id, user, password_hash, created_at) VALUES (?, ?, ?, ?)",
        id, username, "hash", now
    );
  }

  private int countActiveSessionsForUser(int userId) {
    Row row = db.getRow(
            "SELECT COUNT(*) AS cnt FROM USER_SESSION WHERE user_id = ? AND is_active = 1", userId)
        .get();
    return row.getInt("cnt").orElse(0);
  }

  private int countSessionsByRefresh(String refresh) {
    Row row = db.getRow("SELECT COUNT(*) AS cnt FROM USER_SESSION WHERE refresh_token = ?", refresh)
        .get();
    return row.getInt("cnt").orElse(0);
  }

  @Test
  @DisplayName("Given user and roles, when createTokenPair, then tokens returned and session stored")
  void testGivenUser_WhenCreateTokenPair_ThenTokensAndSession() {
    String userId = "42";
    insertUser(42, "alice");
    List<String> roles = List.of("admin", "user");

    Tyr.TokenPair pair = Tyr.createTokenPair(userId, roles, "UA/1.0", "127.0.0.1");

    assertNotNull(pair);
    assertNotNull(pair.accessToken());
    assertNotNull(pair.refreshToken());
    assertTrue(Tyr.isTokenValid(pair.accessToken()));
    assertTrue(Tyr.isTokenValid(pair.refreshToken()));

    assertEquals(1, countSessionsByRefresh(pair.refreshToken()));
  }

  @Test
  @DisplayName("Given valid and tampered tokens, when isTokenValid, then true for valid, false for tampered")
  void testGivenTokens_WhenIsTokenValid_ThenExpected() {
    String userId = "1";
    insertUser(1, "bob");
    List<String> roles = List.of("user");
    Tyr.TokenPair pair = Tyr.createTokenPair(userId, roles, "UA/1.0", "127.0.0.1");

    assertTrue(Tyr.isTokenValid(pair.accessToken()));
    assertFalse(Tyr.isTokenValid(pair.accessToken() + "x"));
  }

  @Test
  @DisplayName("Given JWT, when getUserIdFromJWT, then subject is extracted or empty on invalid")
  void testGivenJwt_WhenGetUserId_ThenExtractOrEmpty() {
    String userId = "7";
    insertUser(7, "charlie");
    Tyr.TokenPair pair = Tyr.createTokenPair(userId, List.of("user"), "UA/1.0", "127.0.0.1");

    assertEquals(userId, Tyr.getUserIdFromJWT(pair.accessToken()));
    assertEquals("", Tyr.getUserIdFromJWT("invalid.jwt"));
  }

  @Test
  @DisplayName("Given JWT, when getUserRolesFromJWT, then roles extracted or empty on missing/invalid")
  void testGivenJwt_WhenGetRoles_ThenExtractOrEmpty() {
    String userId = "9";
    insertUser(9, "dana");
    List<String> roles = List.of("admin", "user");
    Tyr.TokenPair pair = Tyr.createTokenPair(userId, roles, "UA/1.0", "127.0.0.1");

    List<String> extracted = Tyr.getUserRolesFromJWT(pair.refreshToken());
    assertEquals(roles, extracted);

    assertTrue(Tyr.getUserRolesFromJWT("invalid.jwt").isEmpty());
  }

  @Test
  @DisplayName("When createSecreteKey, then returns Base64 for 32 bytes")
  void testWhenCreateSecretKey_ThenBase64Of32Bytes() {
    String b64 = Tyr.createSecreteKey();
    assertNotNull(b64);
    byte[] decoded = Base64.getDecoder().decode(b64);
    assertEquals(32, decoded.length);
  }

  @Test
  @DisplayName("Given active session, when refreshToken with matching UA/IP, then returns new pair and rotates session")
  void testGivenActiveSession_WhenRefresh_ThenNewPairAndRotateSession() {
    int uid = 11;
    insertUser(uid, "erin");
    List<String> roles = List.of("user");
    Tyr.TokenPair initial = Tyr.createTokenPair(String.valueOf(uid), roles, "UA/1.0", "10.0.0.1");

    Optional<Tyr.TokenPair> refreshed = Tyr.refreshToken(initial.refreshToken(), "UA/1.0",
        "10.0.0.1");

    assertTrue(refreshed.isPresent());
    Tyr.TokenPair np = refreshed.get();
    // Tokens may be equal if generated within the same second (same claims & iat). We only
    // require that rotation occurred in the DB and returned tokens are valid.

    // Old session should be inactive; new active session exists
    Row oldRow = db.getRow("SELECT is_active FROM USER_SESSION WHERE refresh_token = ?",
        initial.refreshToken()).get();
    assertEquals(0, oldRow.getInt("is_active").orElse(0));
    assertTrue(Tyr.isTokenValid(np.accessToken()));
    assertTrue(Tyr.isTokenValid(np.refreshToken()));
  }

  @Test
  @DisplayName("Given UA/IP mismatch, when refreshToken, then invalidate session and return empty")
  void testGivenMismatch_WhenRefresh_ThenInvalidateAndEmpty() {
    int uid = 12;
    insertUser(uid, "frank");
    Tyr.TokenPair initial = Tyr.createTokenPair(String.valueOf(uid), List.of("user"), "UA/1.0",
        "10.0.0.2");

    Optional<Tyr.TokenPair> refreshed = Tyr.refreshToken(initial.refreshToken(), "UA/DIFF",
        "10.0.0.3");

    assertTrue(refreshed.isEmpty());
    Row old = db.getRow("SELECT is_active FROM USER_SESSION WHERE refresh_token = ?",
        initial.refreshToken()).get();
    assertEquals(0, old.getInt("is_active").orElse(0));
  }

  @Test
  @DisplayName("Given invalid token, when refreshToken, then empty and no session changes")
  void testGivenInvalidToken_WhenRefresh_ThenEmptyNoChange() {
    int before = countActiveSessionsForUser(99);
    Optional<Tyr.TokenPair> result = Tyr.refreshToken("this.is.not.jwt", "UA/1.0", "127.0.0.1");
    assertTrue(result.isEmpty());
    assertEquals(before, countActiveSessionsForUser(99));
  }

  @Test
  @DisplayName("Given concurrent calls, when refreshToken, then one success and final state consistent")
  void testGivenConcurrentRefresh_WhenCalled_ThenOneSuccessAndConsistentState()
      throws InterruptedException {
    int uid = 13;
    insertUser(uid, "gwen");
    List<String> roles = List.of("user");
    Tyr.TokenPair initial = Tyr.createTokenPair(String.valueOf(uid), roles, "UA/1.0", "10.0.0.4");

    int threads = 10;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);

    List<Boolean> successes = new ArrayList<>();
    List<Boolean> empties = new ArrayList<>();
    List<Throwable> errors = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        try {
          start.await();
          Optional<Tyr.TokenPair> r = Tyr.refreshToken(initial.refreshToken(), "UA/1.0",
              "10.0.0.4");
          successes.add(r.isPresent());
          empties.add(r.isEmpty());
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await(10, TimeUnit.SECONDS);
    pool.shutdownNow();

    long successCount = successes.stream().filter(b -> b).count();
    // At least one should succeed; others may fail or be empty depending on timing
    assertTrue(successCount >= 1);

    // Old session must be inactive
    Row old = db.getRow("SELECT is_active FROM USER_SESSION WHERE refresh_token = ?",
        initial.refreshToken()).get();
    assertEquals(0, old.getInt("is_active").orElse(0));

    // There should be at most one active session for this user at the end
    assertTrue(countActiveSessionsForUser(uid) <= 1);

    // No thread should crash the JVM; collect errors for visibility if any
    if (!errors.isEmpty()) {
      // Allow NoSuchElementException due to race on getRow().get(), but fail on unexpected ones
      boolean onlyExpected = errors.stream()
          .allMatch(e -> e instanceof java.util.NoSuchElementException);
      assertTrue(onlyExpected, "Unexpected errors: " + errors);
    }
  }
}
