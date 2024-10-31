package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Hephaestus;

import static org.junit.jupiter.api.Assertions.*;

public class HephaestusUnitTests {

    @Test
    public void givenValidInputs_whenHeaderToStringIsCalled_thenReturnsCorrectHeader() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .contentType("text/html")
                .contentLength("1234")
                .server("MyServer")
                .date("Wed, 21 Oct 2015 07:28:00 GMT")
                .connection("keep-alive")
                .cacheControl("no-cache")
                .endResponse()
                .build();

        String expectedHeader = """
                HTTP/1.1 200 OK\r
                Content-Type: text/html\r
                Content-Length: 1234\r
                Server: MyServer\r
                Date: Wed, 21 Oct 2015 07:28:00 GMT\r
                Connection: keep-alive\r
                Cache-Control: no-cache\r
                \r
                """;

        assertEquals(expectedHeader, hephaestus.headerToString());
    }

    @Test
    public void givenValidInputs_whenHeaderToBytesIsCalled_thenReturnsCorrectByteArray() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("404 Not Found")
                .contentType("application/json")
                .endResponse()
                .build();

        byte[] expectedBytes = """
                HTTP/1.1 404 Not Found\r
                Content-Type: application/json\r
                \r
                """.getBytes();

        assertArrayEquals(expectedBytes, hephaestus.headerToBytes());
    }

    @Test
    public void givenNullResponseType_whenBuilderIsUsed_thenThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Hephaestus.Builder().responseType(null);
        });
        assertEquals("Request type cannot be null", exception.getMessage());
    }

    @Test
    public void givenBuilder_whenBuildCalled_thenCreatesHephaestusInstance() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("500 Internal Server Error")
                .endResponse()
                .build();

        assertNotNull(hephaestus);
        assertEquals("HTTP/1.1 500 Internal Server Error\r\n\r\n", hephaestus.headerToString());
    }
}
