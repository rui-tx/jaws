package auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.njord.Njord;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.configs.bifrost.middleware.AuthMiddleware;
import org.ruitx.jaws.configs.bifrost.middleware.RequestValidationMiddleware;
import org.ruitx.jaws.utils.logger.JawsLogger;
import org.ruitx.www.base.controller.AuthController;
import org.ruitx.www.base.dto.auth.UserCreateRequest;
import org.ruitx.www.base.service.AuthService;

@DisplayName("AuthController Integration Tests")
public class AuthControllerIntegrationTest {

  private static final String IT_FIST_NAME = "Bob";
  private static final String IT_LAST_NAME = "Ross";
  private static final String IT_USER = "it_user";
  private static final String IT_PASS = "Password123!";
  private static final String IT_USER_AGENT = "IT-Agent/1.0";
  private static Yggdrasill server;
  private static int port;
  private static Path staticRoot;
  private static Path mainDbPath;
  private static Path logsDbPath;

  @BeforeAll
  static void beforeAll() throws Exception {
    // Ensure clean slate across tests to avoid lingering pools/workers referencing old DB files
    Odin.shutdownAllForTests();

    // temp static dir
    staticRoot = Files.createTempDirectory("auth-it-static-");
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

    // start server
    port = getFreePort();

    // register routes
    Njord.getInstance().registerRoutes(new AuthController());

    server = new Yggdrasill(port, staticRoot.toAbsolutePath().toString());

    // Register middlewares required for JSON body parsing/validation and auth
    server.addMiddleware(new AuthMiddleware(10));
    server.addMiddleware(new RequestValidationMiddleware(20));

    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    server.start();

    // seed test user after DB ready
    new AuthService().createUser(new UserCreateRequest(
        IT_USER,
        IT_FIST_NAME,
        IT_LAST_NAME,
        IT_PASS,
        IT_PASS
    ));
  }

  @AfterAll
  static void afterAll() {
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

  private static int getFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }

  // ------------------- Positive cases -------------------

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

  @AfterEach
  void afterEach() {
    awaitConnectionsToZero(1000);
  }

  @Test
  @DisplayName("Given valid user When POST /api/v1/auth/login Then 200 and token pair returned")
  void givenValidUser_whenLogin_thenReturnsTokens() {
    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"user\":\"" + IT_USER + "\"," +
            "\"password\":\"" + IT_PASS + "\"" +
            "}")
        .when()
        .post("/api/v1/auth/login")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body("data.access_token", notNullValue())
        .body("data.refresh_token", notNullValue())
        .body("data.token_type", equalTo("Bearer"))
        .body("data.expires_in", notNullValue());
  }

  // ------------------- Negative cases -------------------

  @Test
  @DisplayName("Given valid refresh token When POST /api/v1/auth/refresh Then 200 and new tokens")
  void givenValidRefreshToken_whenRefresh_thenReturnsNewTokens() {
    // login first
    String refresh =
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", IT_USER_AGENT)
            .body("{" +
                "\"user\":\"" + IT_USER + "\"," +
                "\"password\":\"" + IT_PASS + "\"" +
                "}")
            .when()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract().path("data.refresh_token");

    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"refresh_token\":\"" + refresh + "\"" +
            "}")
        .when()
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body("data.access_token", notNullValue())
        .body("data.refresh_token", notNullValue())
        .body("data.token_type", equalTo("Bearer"))
        .body("data.expires_in", notNullValue());
  }

  @Test
  @DisplayName("Given valid tokens When POST /api/v1/auth/logout Then 200")
  void givenValidTokens_whenLogout_then200() {
    // login first
    var loginResp =
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", IT_USER_AGENT)
            .body("{" +
                "\"user\":\"" + IT_USER + "\"," +
                "\"password\":\"" + IT_PASS + "\"" +
                "}")
            .when()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    String access = loginResp.path("data.access_token");
    String refresh = loginResp.path("data.refresh_token");

    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .header("Authorization", "Bearer " + access)
        .body("{" +
            "\"refreshToken\":\"" + refresh + "\"" +
            "}")
        .when()
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true));
  }

  @Test
  @DisplayName("Given invalid credentials When POST /api/v1/auth/login Then 401")
  void givenInvalidCredentials_whenLogin_then401() {
    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"user\":\"" + IT_USER + "\"," +
            "\"password\":\"wrong-pass\"" +
            "}")
        .when()
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false));
  }

  @Test
  @DisplayName("Given empty credentials When POST /api/v1/auth/login Then 400")
  void givenEmptyCredentials_whenLogin_then400() {
    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"user\":\"\"," +
            "\"password\":\"\"" +
            "}")
        .when()
        .post("/api/v1/auth/login")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false));
  }

  // ------------------- helpers -------------------

  @Test
  @DisplayName("Given invalid refresh token When POST /api/v1/auth/refresh Then 401")
  void givenInvalidRefreshToken_whenRefresh_then401() {
    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"refresh_token\":\"nope\"" +
            "}")
        .when()
        .post("/api/v1/auth/refresh")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false));
  }

  @Test
  @DisplayName("Given missing Authorization When POST /api/v1/auth/logout Then 401")
  void givenMissingAuthorization_whenLogout_then401() {
    given()
        .contentType(ContentType.JSON)
        .header("User-Agent", IT_USER_AGENT)
        .body("{" +
            "\"refreshToken\":\"anything\"" +
            "}")
        .when()
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false));
  }
}
