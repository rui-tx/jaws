package components.bifrost.middleware;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import components.bifrost.middleware.support.MiddlewareTestBase;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.middleware.CorsMiddleware;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseType;

@DisplayName("CorsMiddleware Integration Tests")
class CorsMiddlewareIntegrationTest extends MiddlewareTestBase {

  @Override
  protected void registerRoutes() {
    Njord.getInstance().registerRoutes(new TestCorsController());
  }

  @Override
  protected void addMiddlewares() {
    // Run CORS very early so headers are present regardless of route
    server.addMiddleware(new CorsMiddleware(1));
  }

  @Test
  @DisplayName("Given OPTIONS preflight When request Then 200 and CORS headers present")
  void givenOptionsPreflight_then200_andHeaders() {
    given()
        .when()
        .options("/cors/ping")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", equalTo("*"))
        .header("Access-Control-Allow-Methods",
            containsString("GET"))
        .header("Access-Control-Allow-Methods",
            containsString("OPTIONS"))
        .header("Access-Control-Allow-Headers",
            containsString("Authorization"))
        .header("Access-Control-Allow-Headers",
            containsString("HX-Request"))
        .header("Access-Control-Max-Age", equalTo("3600"));
  }

  @Test
  @DisplayName("Given normal GET When request Then route executes and CORS headers present")
  void givenGet_thenRouteExecutes_andHeadersPresent() {
    given()
        .when()
        .get("/cors/ping")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("pong"))
        .header("Access-Control-Allow-Origin", equalTo("*"))
        .header("Access-Control-Allow-Methods",
            containsString("GET"))
        .header("Access-Control-Allow-Methods",
            containsString("OPTIONS"))
        .header("Access-Control-Allow-Headers",
            containsString("Authorization"))
        .header("Access-Control-Allow-Headers",
            containsString("HX-Request"))
        .header("Access-Control-Max-Age", equalTo("3600"));
  }

  // --- Test Controller ---
  public static class TestCorsController extends Bragi {

    @Route(endpoint = "/cors/ping", method = RequestType.GET, responseType = ResponseType.JSON)
    public void ping() {
      sendSuccess("pong");
    }
  }
}
