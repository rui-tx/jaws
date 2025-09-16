package components.bifrost.middleware;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import components.bifrost.middleware.support.MiddlewareTestBase;
import io.restassured.http.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.interfaces.Validatable;
import org.ruitx.jaws.middleware.RequestValidationMiddleware;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseType;

@DisplayName("RequestValidationMiddleware Integration Tests")
class RequestValidationMiddlewareIntegrationTest extends MiddlewareTestBase {

  @Override
  protected void registerRoutes() {
    Njord.getInstance().registerRoutes(new TestValidationController());
  }

  @Override
  protected void addMiddlewares() {
    // Run validation before route invocation
    server.addMiddleware(new RequestValidationMiddleware(5));
  }

  @Test
  @DisplayName("Valid JSON with correct Content-Type -> 200 and controller receives DTO")
  void validJson_returns200_andControllerExecutes() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"Alice\"}")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("ok"));
  }

  @Test
  @DisplayName("Missing Content-Type on POST -> 400 with helpful message")
  void missingContentType_returns400() {
    given()
        .body("{\"name\":\"Alice\"}")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("must be 'application/json'"));
  }

  @Test
  @DisplayName("Form URL Encoded on POST -> 400 requires JSON")
  void formUrlEncoded_returns400() {
    given()
        .contentType("application/x-www-form-urlencoded")
        .body("name=Alice")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("requires JSON"));
  }

  @Test
  @DisplayName("Non-JSON Content-Type on POST -> 400 must be application/json")
  void nonJsonContentType_returns400() {
    given()
        .contentType("text/plain")
        .body("name: Alice")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("must be 'application/json'"));
  }

  @Test
  @DisplayName("Empty body with JSON Content-Type -> 400 body required")
  void emptyBody_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("Request body is required"));
  }

  @Test
  @DisplayName("Invalid JSON -> 400 Invalid JSON format")
  void invalidJson_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("{")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("Invalid JSON format"));
  }

  @Test
  @DisplayName("Validatable custom error -> 400 with custom message")
  void customValidatableError_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"bad\"}")
        .when()
        .post("/validate/user")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("Name cannot be 'bad'"));
  }

  @Test
  @DisplayName("HTMX-only route without HX-Request -> 400")
  void htmxOnly_withoutHeader_returns400() {
    given()
        .when()
        .get("/validate/htmx")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("success", equalTo(false))
        .body(containsString("only accessible via HTMX"));
  }

  @Test
  @DisplayName("HTMX-only route with HX-Request -> 200")
  void htmxOnly_withHeader_returns200() {
    given()
        .header("HX-Request", "true")
        .when()
        .get("/validate/htmx")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("success", equalTo(true))
        .body(containsString("ok"));
  }

  // --- Test Controller ---
  public static class TestValidationController extends Bragi {

    @Route(endpoint = "/validate/user", method = RequestType.POST, responseType = ResponseType.JSON)
    public void create(UserDto dto) {
      // If we reached here, middleware parsed and validated the DTO; also passed to controller
      sendSuccess("ok");
    }

    @Route(endpoint = "/validate/htmx", method = RequestType.GET, responseType = ResponseType.JSON, htmx = true)
    public void htmxOnly() {
      sendSuccess("ok");
    }
  }

  // --- DTO used for validation ---
  public static class UserDto implements Validatable {

    @NotBlank
    @Size(min = 2, max = 50)
    public String name;

    public UserDto() {
    }

    @Override
    public java.util.Optional<String> isValid() {
      if ("bad".equalsIgnoreCase(name)) {
        return java.util.Optional.of("Name cannot be 'bad'");
      }
      return java.util.Optional.empty();
    }
  }
}
