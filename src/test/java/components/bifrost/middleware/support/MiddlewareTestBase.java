package components.bifrost.middleware.support;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.port;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.utils.logger.JawsLogger;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Middleware Integration Test Base")
public abstract class MiddlewareTestBase {

  protected Yggdrasill server;
  protected int httpPort;
  protected Path staticRoot;
  protected Path mainDbPath;
  protected Path logsDbPath;

  protected static int getFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }

  protected static void awaitConnectionsToZero(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (Yggdrasill.getCurrentConnections() == 0) {
        return;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(10);
      } catch (InterruptedException ignored) {
      }
    }
  }

  protected static String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  @BeforeAll
  protected void beforeAllBase() throws Exception {
    // Ensure clean slate across tests to avoid lingering pools/workers referencing old DB files
    Odin.shutdownAllForTests();

    // temp static dir
    staticRoot = Files.createTempDirectory("mw-it-static-");
    Files.writeString(staticRoot.resolve("ok.txt"), "ok", StandardCharsets.UTF_8);

    // temp DB files
    mainDbPath = Paths.get("target", "it-db-" + System.nanoTime() + ".db");
    logsDbPath = Paths.get("target", "it-logs-" + System.nanoTime() + ".db");

    // Register main DB
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

    // Register logs DB
    if (!Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      Odin.registerDatabase(Odin.LOGS_DB_NAME, new DatabaseConfig(
          logsDbPath.toAbsolutePath().toString(),
          Paths.get("src/main/resources/sql/logs_schema_v1.sql").toAbsolutePath().toString(),
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

    // Bootstrap logger
    if (Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));
    }

    // Start server
    httpPort = getFreePort();
    server = new Yggdrasill(httpPort, staticRoot.toAbsolutePath().toString());

    // Allow subclasses to register routes before server starts
    registerRoutes();

    // Allow subclasses to add middleware(s)
    addMiddlewares();

    // Configure RestAssured
    baseURI = "http://localhost";
    port = httpPort;
    server.start();
  }

  @AfterEach
  protected void afterEachBase() {
    awaitConnectionsToZero(1000);
  }

  @AfterAll
  protected void afterAllBase() {
    if (server != null) {
      server.shutdown();
    }
    // Ensure all background workers and DB pools are closed before file deletion
    Odin.shutdownAllForTests();

    try {
      Files.deleteIfExists(staticRoot.resolve("ok.txt"));
    } catch (IOException ignored) {
    }
    try {
      Files.deleteIfExists(staticRoot);
    } catch (IOException ignored) {
    }
    try {
      if (logsDbPath != null) {
        Files.deleteIfExists(logsDbPath);
        // delete sidecar files if WAL was enabled
        Path logsWal = Paths.get(logsDbPath.toString() + "-wal");
        Path logsShm = Paths.get(logsDbPath.toString() + "-shm");
        Files.deleteIfExists(logsWal);
        Files.deleteIfExists(logsShm);
      }
      if (mainDbPath != null) {
        Files.deleteIfExists(mainDbPath);
        Path mainWal = Paths.get(mainDbPath.toString() + "-wal");
        Path mainShm = Paths.get(mainDbPath.toString() + "-shm");
        Files.deleteIfExists(mainWal);
        Files.deleteIfExists(mainShm);
      }
    } catch (IOException ignored) {
    }
  }

  protected abstract void registerRoutes();

  protected void addMiddlewares() {
    // no-op by default
  }
}
