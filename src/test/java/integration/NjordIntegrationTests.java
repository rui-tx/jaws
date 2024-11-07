package integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Njord;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.controllers.Todo;
import org.ruitx.server.strings.ResponseCode;

import static io.restassured.RestAssured.given;

public class NjordIntegrationTests {

    private Njord njord;

    @BeforeEach
    void setUp() throws InterruptedException {
        njord = Njord.getInstance();

        Thread serverThread = new Thread(() -> {
            Yggdrasill server = new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH);
            server.start();
        });
        serverThread.start();

        // Allow time for the server to start
        Thread.sleep(1000);
    }

    @Test
    void givenDynamicEndpoint_whenRequested_thenReturns200() {
        Todo controller = new Todo();
        njord.registerRoutes(controller);

        given()
                .port(ApplicationConfig.PORT)
                .when()
                .get("/todos")
                .then()
                .statusCode(ResponseCode.OK.getCode());
    }

    @Test
    void givenDynamicEndpoint_whenRequested_thenReturns404() {
        given()
                .port(ApplicationConfig.PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(ResponseCode.NOT_FOUND.getCode());
    }

}
