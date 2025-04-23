package integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.strings.ResponseCode;

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

        // Allow time for the jaws to start
        Thread.sleep(1000);
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
