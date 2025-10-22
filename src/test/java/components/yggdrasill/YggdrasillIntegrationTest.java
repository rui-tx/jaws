package components.yggdrasill;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import components.yggdrasill.support.TestController;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.utils.logger.JawsLogger;

@DisplayName("Yggdrasill Integration Tests")
public class YggdrasillIntegrationTest {

  private static Yggdrasill server;
  private static int port;
  private static Path staticRoot;
  private static Path mainDbPath;
  private static Path logsDbPath;

  @BeforeAll
  static void beforeAll() throws Exception {
    // Create temp static directory and a sample file
    staticRoot = Files.createTempDirectory("ygg-static-");
    Files.writeString(staticRoot.resolve("file.txt"), "hello static", StandardCharsets.UTF_8);
    // Register temporary 'db' and 'logs' databases with Odin so Freyr/JawsLogger can work without errors
    mainDbPath = Paths.get("target", "it-db-" + System.nanoTime() + ".db");
    logsDbPath = Paths.get("target", "it-logs-" + System.nanoTime() + ".db");

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

    // Bootstrap JawsLogger against Odin's logs DB (safe if called multiple times)
    if (Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));
    }
    // pick a random free port
    port = getFreePort();

    // Register routes once
    Njord.getInstance().registerRoutes(new TestController());

    // Start server
    server = new Yggdrasill(port, staticRoot.toAbsolutePath().toString());
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    server.start();
  }

  @AfterAll
  static void afterAll() {
    if (server != null) {
      server.shutdown();
    }
    try {
      Files.deleteIfExists(staticRoot.resolve("file.txt"));
    } catch (IOException ignored) {
    }
    try {
      Files.deleteIfExists(staticRoot);
    } catch (IOException ignored) {
    }
    try {
      if (logsDbPath != null) {
        Files.deleteIfExists(logsDbPath);
      }
      if (mainDbPath != null) {
        Files.deleteIfExists(mainDbPath);
      }
    } catch (IOException ignored) {
    }
  }

  private static int getFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }

  private static void awaitConnectionsToZero(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (Yggdrasill.getCurrentConnections() == 0) {
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignored) {
      }
    }
  }

  @BeforeEach
  void beforeEach() {
    // no-op for now
  }

  @AfterEach
  void afterEach() {
    // ensure connections return to 0 after each test
    // allow a short window for Jetty to finish
    awaitConnectionsToZero(1000);
  }

  @Test
  @DisplayName("Given server When GET /api/ping Then returns 200 JSON pong true")
  void givenServer_whenPing_thenOkJson() {
    given()
        .when().get("/api/ping")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(containsString("\"pong\":true"));
  }

  @Test
  @DisplayName("Given parameterized route When GET /users/123 Then returns JSON with id 123")
  void givenParamRoute_whenGetUser_thenJsonId() {
    given()
        .when().get("/users/123")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(containsString("\"id\":\"123\""))
        .body(containsString("\"success\":true"));
  }

  @Test
  @DisplayName("Given middlewares with orders When GET /api/ping Then order respected")
  void givenMiddlewares_whenHello_thenOrderRespected() {
    List<String> calls = new ArrayList<>();
    server.addMiddleware(new RecordingMiddleware("m2", calls, 2));
    server.addMiddleware(new RecordingMiddleware("m1", calls, 1));

    given().when().get("/api/ping").then().statusCode(200);

    // Expect m1 then m2
    org.junit.jupiter.api.Assertions.assertEquals(List.of("m1", "m2"), calls);
  }

  @Test
  @DisplayName("Given short-circuiting middleware When GET /short Then middleware response returned")
  void givenShortCircuit_whenShort_thenMiddlewareWins() throws IOException {
    // create route path file to avoid 404 from static; controller also has /cause/error but not /short
    server.addMiddleware(new ShortCircuitMiddleware("/short"));

    given().when().get("/short")
        .then()
        .statusCode(200)
        .contentType("text/plain; charset=UTF-8")
        .body(equalTo("short"));
  }

  @Test
  @DisplayName("Given static resource When GET /static/file.txt Then content served")
  void givenStatic_whenGetFile_thenContentServed() {
    given()
        .when().get("/file.txt")
        .then()
        .statusCode(200)
        .body(equalTo("hello static"));
  }

  @Test
  @DisplayName("Given concurrent requests When processed Then connection counter stabilizes to 0")
  void givenConcurrency_whenManyRequests_thenCounterStabilizes() throws InterruptedException {
    int threads = 8;
    int requestsPerThread = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      pool.submit(() -> {
        try {
          start.await(1, TimeUnit.SECONDS);
          for (int i = 0; i < requestsPerThread; i++) {
            given().when().get("/api/ping").then().statusCode(200);
          }
        } catch (InterruptedException ignored) {
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await(10, TimeUnit.SECONDS);
    pool.shutdownNow();

    awaitConnectionsToZero(5000);
    org.junit.jupiter.api.Assertions.assertEquals(0, Yggdrasill.getCurrentConnections());
  }

  @Test
  @DisplayName("Given missing route When GET /nope Then returns 404")
  void givenMissingRoute_whenGet_then404() {
    given()
        .when().get("/nope")
        .then()
        .statusCode(404);
  }

  @Test
  @DisplayName("Given controller throws When GET /cause/error Then server returns 500 JSON APIResponse")
  void givenControllerError_whenGet_then500Json() {
    given()
        .when().get("/cause/error")
        .then()
        .statusCode(500)
        .contentType(ContentType.JSON)
        .body(containsString("\"success\":false"));
  }

  // Recording middleware (order testing)
  private static class RecordingMiddleware implements Middleware {

    private final String name;
    private final List<String> sink;
    private final int order;

    RecordingMiddleware(String name, List<String> sink, int order) {
      this.name = name;
      this.sink = sink;
      this.order = order;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
      sink.add(name);
      return chain.next();
    }

    @Override
    public int getOrder() {
      return order;
    }
  }

  // Short-circuiting middleware for a specific path
  private static class ShortCircuitMiddleware implements Middleware {

    private final String path;

    ShortCircuitMiddleware(String path) {
      this.path = path;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
      String reqPath = context.getRequest().getRequestURI();
      if (reqPath.equals(path)) {
        context.sendBinaryResponse(
            ResponseCode.OK,
            "text/plain; charset=UTF-8",
            "short".getBytes(StandardCharsets.UTF_8)
        );
        return false;
      }
      return chain.next();
    }

    @Override
    public int getOrder() {
      return 1;
    }
  }
}
