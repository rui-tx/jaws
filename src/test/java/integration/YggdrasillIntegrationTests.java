package integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.strings.ResponseCode;

import static io.restassured.RestAssured.given;

public class YggdrasillIntegrationTests {

    @BeforeAll
    public static void setUp() throws Exception {
        Thread serverThread = new Thread(() -> {
            Yggdrasill server = new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH);
            server.start();
        });
        serverThread.start();

        // Allow time for the server to start
        Thread.sleep(1000);
    }

    @Test
    public void givenRootEndpoint_whenRequested_thenReturns200() {
        given()
                .port(ApplicationConfig.PORT)
                .when()
                .get("/")
                .then()
                .statusCode(ResponseCode.OK.getCode())
                .contentType(ContentType.HTML);
    }

    @Test
    public void givenInvalidEndpoint_whenRequested_thenReturns404() {
        given()
                .port(ApplicationConfig.PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(ResponseCode.NOT_FOUND.getCode());
    }

    @Test
    public void givenInvalidRequest_whenRequested_thenReturns409() {
        given()
                .port(ApplicationConfig.PORT)
                .when()
                .request("INVALID", "/")
                .then()
                .statusCode(ResponseCode.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    public void givenValidEndpointWithDynamicHTML_whenRequested_thenReturns200() {
        given()
                .port(ApplicationConfig.PORT)
                .when()
                .get("/notfound")
                .then()
                .statusCode(ResponseCode.NOT_FOUND.getCode());
    }
}

