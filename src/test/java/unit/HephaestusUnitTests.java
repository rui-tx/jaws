package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Hephaestus;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.ruitx.server.configs.ApplicationConfig.APPLICATION_NAME;

public class HephaestusUnitTests {

    @Test
    public void givenOnlyResponseType_whenHeaderToStringIsCalled_thenReturnsCorrectHeader() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        String headerString = hephaestus.headerToString();
        String expectedHeaderStart = "HTTP/1.1 200 OK\r\n";
        String expectedHeaderEnd = "\r\n";

        assertTrue(headerString.startsWith(expectedHeaderStart), "Header does not start as expected");
        assertTrue(headerString.endsWith(expectedHeaderEnd), "Header does not end as expected");

        assertValidDefaultHeaders(headerString);
        assertValidDateHeader(headerString, 4);
    }

    @Test
    public void givenMultipleCustomHeaders_whenHeaderToStringIsCalled_thenReturnsCorrectHeader() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .contentType("text/html")
                .contentLength("1234")
                .server("MyServer")
                .date("Wed, 21 Oct 2015 07:28:00 GMT")
                .connection("keep-alive")
                .cacheControl("no-cache")
                .addCustomHeader("X-Custom-Header", "CustomValue")
                .addCustomHeader("X-Another-Header", "AnotherValue")
                .build();

        String expectedHeader = """
                HTTP/1.1 200 OK\r
                Cache-Control: no-cache\r
                Connection: keep-alive\r
                Content-Length: 1234\r
                Content-Type: text/html\r
                Date: Wed, 21 Oct 2015 07:28:00 GMT\r
                Server: MyServer\r
                X-Another-Header: AnotherValue\r
                X-Custom-Header: CustomValue\r
                \r
                """;

        assertEquals(expectedHeader, hephaestus.headerToString());
    }

    @Test
    public void givenCookie_whenAddCookieIsCalled_thenReturnsCorrectHeader() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .addCookie("SESSIONID", "12345", 3600)
                .build();

        String headerString = hephaestus.headerToString();
        String expectedHeaderStart = "HTTP/1.1 200 OK\r\n";
        String expectedHeaderEnd = "\r\n";

        assertTrue(headerString.startsWith(expectedHeaderStart), "Header does not start as expected");
        assertTrue(headerString.endsWith(expectedHeaderEnd), "Header does not end as expected");

        assertValidDefaultHeaders(headerString);
        assertValidDateHeader(headerString, 4);

        assertTrue(headerString.contains("Set-Cookie: SESSIONID=12345; Max-Age=3600; Path=/; HttpOnly; Secure"));

    }

    @Test
    public void givenValidResponse_whenHeaderToBytesIsCalled_thenReturnsCorrectByteArray() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("404 Not Found")
                .contentType("application/json")
                .date("Thu, 07 Nov 2024 09:45:43 WET")
                .build();

        byte[] expectedBytes = String.format("""
                HTTP/1.1 404 Not Found\r
                Cache-Control: no-cache\r
                Connection: keep-alive\r
                Content-Type: application/json\r
                Date: Thu, 07 Nov 2024 09:45:43 WET\r
                Server: %s\r
                \r
                """, APPLICATION_NAME).getBytes();

        assertArrayEquals(expectedBytes, hephaestus.headerToBytes());
    }


    @Test
    public void givenMultipleHeaders_whenBuilt_thenCorrectHeadersAdded() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .contentType("application/xml")
                .date("Thu, 07 Nov 2024 09:45:43 WET")
                .server("TestServer")
                .addCustomHeader("X-My-Header", "MyValue")
                .build();

        String expectedHeader = """
                HTTP/1.1 200 OK\r
                Cache-Control: no-cache\r
                Connection: keep-alive\r
                Content-Type: application/xml\r
                Date: Thu, 07 Nov 2024 09:45:43 WET\r
                Server: TestServer\r
                X-My-Header: MyValue\r
                \r
                """;
        assertEquals(expectedHeader, hephaestus.headerToString());
    }

    @Test
    public void givenNoContentType_whenBuilderIsUsed_thenDefaultsToTextHtml() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        String headerString = hephaestus.headerToString();
        String expectedHeaderStart = "HTTP/1.1 200 OK\r\n";
        String expectedHeaderEnd = "\r\n";

        assertTrue(headerString.startsWith(expectedHeaderStart), "Header does not start as expected");
        assertTrue(headerString.endsWith(expectedHeaderEnd), "Header does not end as expected");

        assertTrue(headerString.contains("Cache-Control: no-cache"));
        assertTrue(headerString.contains("Connection: keep-alive"));
        assertValidDateHeader(headerString, 4);
        assertTrue(headerString.contains("Server: " + APPLICATION_NAME));

        assertTrue(headerString.contains("Content-Type: text/html"));
    }

    @Test
    public void givenNoServer_whenBuilderIsUsed_thenDefaultsToMyServer() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        assertTrue(hephaestus.headerToString().contains("Server: " + APPLICATION_NAME));
    }

    @Test
    public void givenNoConnection_whenBuilderIsUsed_thenDefaultsToKeepAlive() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        assertTrue(hephaestus.headerToString().contains("Connection: keep-alive"));
    }

    @Test
    public void givenNoCacheControl_whenBuilderIsUsed_thenDefaultsToNoCache() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        assertTrue(hephaestus.headerToString().contains("Cache-Control: no-cache"));
    }

    @Test
    public void givenNoDate_whenBuilderIsUsed_thenDefaultsToCurrentDate() {
        Hephaestus hephaestus = new Hephaestus.Builder()
                .responseType("200 OK")
                .build();

        assertValidDateHeader(hephaestus.headerToString(), 4);
    }

    @Test
    public void givenNullHeaderValue_whenAddCustomHeaderIsUsed_thenThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Hephaestus.Builder().addCustomHeader("X-Null-Header", null);
        });
        assertEquals("Header value cannot be null or empty", exception.getMessage());
    }

    @Test
    public void givenNullCustomHeader_whenBuilderIsUsed_thenThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Hephaestus.Builder().responseType("200 OK").addCustomHeader("Test-Header", null);
        });
        assertEquals("Header value cannot be null or empty", exception.getMessage());
    }

    @Test
    public void givenNullCustomHeaderKey_whenBuilderIsUsed_thenThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Hephaestus.Builder().responseType("200 OK").addCustomHeader(null, "value");
        });
        assertEquals("Header name cannot be null or empty", exception.getMessage());
    }

    @Test
    public void givenEmptyResponseType_whenBuilderIsUsed_thenThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new Hephaestus.Builder().responseType("");
        });
        assertEquals("Response type cannot be null or empty", exception.getMessage());
    }

    private void assertValidDefaultHeaders(String headerString) {
        assertTrue(headerString.contains("Cache-Control: no-cache"));
        assertTrue(headerString.contains("Connection: keep-alive"));
        assertTrue(headerString.contains("Content-Type: text/html"));
        assertTrue(headerString.contains("Server: " + APPLICATION_NAME));
    }

    private void assertValidDateHeader(String headerString, int headerIndex) {
        String dateRegex = "^[a-zA-Z]{3}, \\d{2} [a-zA-Z]{3} \\d{4} \\d{02}:\\d{02}:\\d{02} [A-Za-z]{3}$";
        String headerLine = headerString.split("\r\n")[headerIndex];
        assertTrue(Pattern.matches(dateRegex, headerLine.substring(6)),
                "Date header does not match the expected format");
    }
}
