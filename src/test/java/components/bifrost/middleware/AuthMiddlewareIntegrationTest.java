package components.bifrost.middleware;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import components.bifrost.middleware.support.MiddlewareTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.middleware.AuthMiddleware;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;

@DisplayName("AuthMiddleware Integration Tests")
class AuthMiddlewareIntegrationTest extends MiddlewareTestBase {

  private String userToken;
  private String adminToken;
  private String editorToken;

  // ---------- Helpers ----------
  private static String makeToken(String userId, List<String> roles) {
    SecretKey key = Keys.hmacShaKeyFor(ApplicationConfig.JWT_SECRET.getBytes());
    long now = Instant.now().getEpochSecond();
    return Jwts.builder()
        .issuer(ApplicationConfig.APPLICATION_NAME)
        .subject(userId)
        .claim("roles", roles)
        .issuedAt(Date.from(Instant.ofEpochSecond(now)))
        .expiration(Date.from(Instant.ofEpochSecond(now + 3600)))
        .signWith(key)
        .compact();
  }

  @Override
  protected void registerRoutes() {
    // Register test controller routes
    Njord.getInstance().registerRoutes(new TestAuthController());

    // Pre-generate some tokens for tests (user id arbitrary for tests)
    userToken = makeToken("1", List.of("user"));
    adminToken = makeToken("2", List.of("admin"));
    editorToken = makeToken("3", List.of("editor"));
  }

  // ---------- Tests ----------

  @Override
  protected void addMiddlewares() {
    server.addMiddleware(new AuthMiddleware(10));
  }

  @Test
  @DisplayName("Given no AccessControl When GET /public Then 200")
  void givenNoAccessControl_whenGetPublic_then200() {
    given()
        .when().get("/public")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("public"));
  }

  @Test
  @DisplayName("Given login required and no token When GET /json/protected Then 401 JSON")
  void givenLoginRequired_noToken_json_then401() {
    given()
        .when().get("/json/protected")
        .then()
        .statusCode(ResponseCode.UNAUTHORIZED.getCode())
        .contentType(ContentType.JSON)
        .body(containsString("success"))
        .body(containsString("You are not authorized"));
  }

  @Test
  @DisplayName("Given login required and valid token When GET /json/protected Then 200")
  void givenLoginRequired_validToken_then200() {
    given()
        .header("Authorization", bearer(userToken))
        .when().get("/json/protected")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("protected"));
  }

  @Test
  @DisplayName("Given role required and missing role When GET /json/role-user Then 401")
  void givenRoleRequired_missingRole_then401() {
    given()
        .header("Authorization", bearer(editorToken)) // editor lacks 'user'
        .when().get("/json/role-user")
        .then()
        .statusCode(ResponseCode.UNAUTHORIZED.getCode());
  }

  @Test
  @DisplayName("Given role required and admin token When GET /json/role-user Then 200 (admin override)")
  void givenRoleRequired_adminToken_then200() {
    given()
        .header("Authorization", bearer(adminToken))
        .when().get("/json/role-user")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true));
  }

  @Test
  @DisplayName("Given CSV role requirement When one matches Then 200")
  void givenCsvRoles_anyMatch_then200() {
    given()
        .header("Authorization", bearer(editorToken)) // matches 'editor' among 'editor,contrib'
        .when().get("/json/role-editor-or-contrib")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true));
  }

  @Test
  @DisplayName("Given protected route With no token Then 401")
  void givenProtectedRoute_noToken_then401() {
    given()
        .when().get("/secure/profile")
        .then()
        .statusCode(ResponseCode.UNAUTHORIZED.getCode());
  }

  @Test
  @DisplayName("Given protected route With valid token Then 200")
  void givenProtectedRoute_validToken_then200() {
    given()
        .header("Authorization", bearer(userToken))
        .when().get("/secure/profile")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("profile"));
  }

  @Test
  @DisplayName("Given HTML responseType When unauthorized Then 401 text/html")
  void givenHtmlResponseType_whenUnauthorized_then401() {
    given()
        .when().get("/html/protected")
        .then()
        .statusCode(ResponseCode.UNAUTHORIZED.getCode())
        .contentType("text/html; charset=UTF-8");
  }

  // ---------- Test Controller ----------
  public static class TestAuthController extends Bragi {

    @Route(endpoint = "/public", method = RequestType.GET, responseType = ResponseType.JSON)
    public void publicEndpoint() {
      sendSuccess("public");
    }

    @Route(endpoint = "/json/protected", method = RequestType.GET, responseType = ResponseType.JSON)
    @AccessControl(login = true)
    public void jsonProtected() {
      sendSuccess("protected");
    }

    @Route(endpoint = "/json/role-user", method = RequestType.GET, responseType = ResponseType.JSON)
    @AccessControl(login = true, role = "user")
    public void jsonRoleUser() {
      sendSuccess("role-user");
    }

    @Route(endpoint = "/json/role-editor-or-contrib", method = RequestType.GET, responseType = ResponseType.JSON)
    @AccessControl(login = true, role = "editor,contrib")
    public void jsonRoleEditorOrContrib() {
      sendSuccess("role-editor-or-contrib");
    }

    @Route(endpoint = "/secure/profile", method = RequestType.GET, responseType = ResponseType.JSON)
    @AccessControl(login = true)
    public void secureProfile() {
      sendSuccess("profile");
    }

    @Route(endpoint = "/html/protected", method = RequestType.GET, responseType = ResponseType.HTML)
    @AccessControl(login = true)
    public void htmlProtected() {
      sendHTMLResponse(ResponseCode.OK, "<html><body>ok</body></html>");
    }
  }
}
