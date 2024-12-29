package integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.ruitx.jaws.configs.ApplicationConfig.PORT;
import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;
import static org.ruitx.jaws.strings.ResponseCode.*;

public class YggdrasillIntegrationTests {

    @BeforeAll
    public static void setUp() throws Exception {
        Thread serverThread = new Thread(() -> {
            Yggdrasill server = new Yggdrasill(PORT, ApplicationConfig.WWW_PATH);
            server.start();
        });
        serverThread.start();

        Njord njord = Njord.getInstance();
        ROUTES.forEach(njord::registerRoutes);

        // Allow time for the jaws to start
        Thread.sleep(1000);
    }

    @Test
    public void givenRootEndpoint_whenRequested_thenReturns200() {
        given()
                .port(PORT)
                .when()
                .get("/")
                .then()
                .statusCode(OK.getCode())
                .contentType(ContentType.HTML);
    }

    @Test
    public void givenInvalidEndpoint_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidRequest_whenRequested_thenReturns409() {
        given()
                .port(PORT)
                .when()
                .request("INVALID", "/")
                .then()
                .statusCode(METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    public void givenValidEndpointWithDynamicHTML_whenRequested_thenReturns200() {
        given()
                .port(PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenMissingCredentials_whenGenerateToken_thenReturnsBadRequest() {
        // Missing user and password
        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{}")  // No "user" or "password"
                .when()
                .post("/auth/token/create")
                .then()
                .statusCode(BAD_REQUEST.getCode())  // 400 Bad Request
                .contentType(ContentType.JSON)
                .body("error", is("User / password is missing"));
    }

    @Test
    public void givenInvalidEndpointGET_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidEndpointPOST_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .post("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidEndpointPATCH_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .patch("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidEndpointPUT_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .put("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidEndpointDELETE_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .delete("/notfound")
                .then()
                .statusCode(NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidMethod_whenRequested_thenReturns404() {
        given()
                .port(PORT)
                .when()
                .request("BADMEHOTD", "/notfound")
                .then()
                .statusCode(METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    public void givenInvalidEndpointWithQueryParams_whenRequested_thenReturns404() {
        given()
                .port(PORT) // Use the correct port for your application
                .queryParam("param1", "value1")  // Add query parameter "param1=value1"
                .queryParam("param2", "value2")  // Add query parameter "param2=value2"
                .when()
                .get("/notfound")  // Send GET request to "/notfound"
                .then()
                .statusCode(NOT_FOUND.getCode()); // Expecting 404 Not Found
    }


    // TODO: Fix this test
    @Disabled
    @Test
    public void givenInvalidCredentials_whenGenerateToken_thenReturnsUnauthorized() {
        // Invalid user and password
        String invalidUser = "wrongUser";
        String invalidPassword = "wrongPassword";

        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{ \"user\": \"" + invalidUser + "\", \"password\": \"" + invalidPassword + "\" }")
                .when()
                .post("/auth/token/create")
                .then()
                .statusCode(UNAUTHORIZED.getCode())  // 401 Unauthorized
                .contentType(ContentType.JSON)
                .body("error", is("Credentials are invalid"));
    }

    // TODO: Fix this test
    @Disabled
    @Test
    public void givenValidCredentials_whenGenerateToken_thenReturnsToken() {
        // Assuming you already have a valid user in the database, e.g., "testuser" and "password123"
        String validUser = "testuser";
        String validPassword = "password123";

        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{ \"user\": \"" + validUser + "\", \"password\": \"" + validPassword + "\" }")
                .when()
                .post("/auth/token/create")
                .then()
                .statusCode(OK.getCode())  // 200 OK
                .contentType(ContentType.JSON)
                .body("token", is(notNullValue()));  // Verify that a token is returned
    }

    // TODO: Fix this test
    @Disabled
    @Test
    public void givenInvalidCredentials_whenLogin_thenReturnsUnauthorizedResponse() {
        // Example invalid login credentials
        String invalidUser = "nonexistentUser";
        String invalidPassword = "wrongPassword";

        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{ \"user\": \"" + invalidUser + "\", \"password\": \"" + invalidPassword + "\" }")
                .when()
                .post("/auth/user/login")  // Assuming this endpoint calls sendJSONResponse on invalid credentials
                .then()
                .statusCode(UNAUTHORIZED.getCode())  // 401 Unauthorized
                .contentType(ContentType.JSON)
                .body("error", is("Credentials are invalid"));
    }

    @Test
    public void givenMissingUser_whenLogin_thenReturnsBadRequestResponse() {
        // Example missing user in the login request
        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{ \"password\": \"somePassword\" }")  // Missing "user"
                .when()
                .post("/auth/user/login")  // Assuming this endpoint calls sendJSONResponse on missing user
                .then()
                .statusCode(BAD_REQUEST.getCode())  // 400 Bad Request
                .contentType(ContentType.JSON)
                .body("error", is("User / password is missing"));
    }

    // TODO: Fix this test
    @Disabled
    @Test
    public void givenValidUser_whenLogin_thenReturnsCreatedResponseWithToken() {
        // Example valid login credentials
        String validUser = "testuser";
        String validPassword = "correctPassword";

        given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body("{ \"user\": \"" + validUser + "\", \"password\": \"" + validPassword + "\" }")
                .when()
                .post("/auth/user/login")  // Assuming this endpoint calls sendJSONResponse with the token
                .then()
                .statusCode(CREATED.getCode())  // 201 Created
                .contentType(ContentType.JSON)
                .body("token", is(notNullValue()));  // Verify that a token is returned
    }
}

