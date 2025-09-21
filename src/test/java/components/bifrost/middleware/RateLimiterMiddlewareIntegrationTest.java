package components.bifrost.middleware;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import components.bifrost.middleware.support.MiddlewareTestBase;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.njord.Njord;
import org.ruitx.jaws.components.njord.Route;
import org.ruitx.jaws.components.yggdrasill.Bragi;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.configs.bifrost.middleware.RateLimiterMiddleware;
import org.ruitx.jaws.enums.RequestType;
import org.ruitx.jaws.enums.ResponseType;

@DisplayName("RateLimiterMiddleware Integration Tests")
class RateLimiterMiddlewareIntegrationTest extends MiddlewareTestBase {

  @Override
  protected void registerRoutes() {
    Njord.getInstance().registerRoutes(new TestRateController());
  }

  @Override
  protected void addMiddlewares() {
    // Run RateLimiter early
    server.addMiddleware(new RateLimiterMiddleware(1));
  }

  @Test
  @DisplayName("Within limit: all requests return 200")
  void withinLimit_all200() {
    int max = ApplicationConfig.RATE_LIMIT_MAX_REQUESTS;

    for (int i = 0; i < max; i++) {
      given()
          .header("X-Forwarded-For", "10.0.0.1")
          .when()
          .get("/rate/ping")
          .then()
          .statusCode(200)
          .contentType(ContentType.JSON)
          .body("success", equalTo(true));
    }
  }

  @Test
  @DisplayName("Exceeding limit: returns 429 with rate limit headers")
  void exceedingLimit_returns429_withHeaders() {
    int max = ApplicationConfig.RATE_LIMIT_MAX_REQUESTS;

    // Reach the limit with 200s
    for (int i = 0; i < max; i++) {
      given()
          .header("X-Forwarded-For", "10.0.0.2")
          .when()
          .get("/rate/ping")
          .then()
          .statusCode(200);
    }

    // One more should be limited
    given()
        .header("X-Forwarded-For", "10.0.0.2")
        .when()
        .get("/rate/ping")
        .then()
        .statusCode(429)
        .contentType("application/json")
        .body(containsString("Rate limit exceeded"))
        .header("X-RateLimit-Limit", equalTo(String.valueOf(max)))
        .header("X-RateLimit-Reset", containsString(""))
        .header("Retry-After", containsString(""));

    // Subsequent immediate request still 429 (same window)
    given()
        .header("X-Forwarded-For", "10.0.0.2")
        .when()
        .get("/rate/ping")
        .then()
        .statusCode(429);
  }

  @Test
  @DisplayName("Different IP has its own bucket: not limited")
  void differentIp_notLimited() {
    // Exceed for IP A
    int max = ApplicationConfig.RATE_LIMIT_MAX_REQUESTS;
    for (int i = 0; i < max + 1; i++) {
      given()
          .header("X-Forwarded-For", "10.0.0.3")
          .when()
          .get("/rate/ping");
    }

    // IP B should still be fine
    given()
        .header("X-Forwarded-For", "10.0.0.4")
        .when()
        .get("/rate/ping")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true));
  }

  // --- Test Controller ---
  public static class TestRateController extends Bragi {

    @Route(endpoint = "/rate/ping", method = RequestType.GET, responseType = ResponseType.JSON)
    public void ping() {
      sendSuccess("pong");
    }
  }
}
