package controller;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.ruitx.jaws.configs.ApplicationConfig.PORT;
import static org.ruitx.jaws.configs.ApplicationConfig.URL;
import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;

@Disabled
public class TodoControllerTest {

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
    public void givenValidToken_whenCreateTodo_thenReturnsCreatedTodoList() {
        String validToken = "valid-jwt-token"; // Use the valid JWT token for the test

        given()
                .port(PORT)
                .cookie("token", validToken)
                .contentType(ContentType.URLENC)
                .formParam("todo", "Test new todo")
                .when()
                .post(URL + "/todos")
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body(containsString("Test new todo"));  // Ensure the todo was added
    }


    @Test
    public void givenInvalidToken_whenCreateTodo_thenReturnsUnauthorizedError() {
        String invalidToken = "invalid-jwt-token";  // Simulate an invalid token

        given()
                .port(PORT)
                .auth().oauth2(invalidToken)
                .contentType(ContentType.JSON)
                .body("{\"todo\": \"Test new todo\"}")
                .when()
                .post(URL + "/todos")
                .then()
                .statusCode(401)  // Expecting Unauthorized response due to invalid token
                .body(containsString("Unauthorized"));
    }

    @Test
    public void givenMissingTodo_whenCreateTodo_thenReturnsBadRequest() {
        String validToken = "valid-jwt-token";

        given()
                .port(PORT)
                .auth().oauth2(validToken)
                .contentType(ContentType.JSON)
                .body("{}")  // Missing 'todo' field
                .when()
                .post(URL + "/todos")
                .then()
                .statusCode(400)  // Expecting Bad Request response
                .body(containsString("Missing todo parameter"));
    }
}

