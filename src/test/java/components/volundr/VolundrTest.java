package components.volundr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Volundr;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.enums.HttpHeaders;

@DisplayName("Volundr Tests")
public class VolundrTest {

  @Test
  @DisplayName("Given minimal builder When build Then defaults applied and status line correct")
  void givenMinimal_whenBuild_thenDefaultsAndStatusLine() {
    Volundr v = new Volundr.Builder()
        .responseType("200 OK")
        .build();

    String header = v.headerToString();

    assertTrue(header.startsWith("HTTP/1.1 200 OK\r\n"));
    // Has trailing blank line
    assertTrue(header.endsWith("\r\n\r\n"));

    // Defaults exist
    assertTrue(header.contains(HttpHeaders.CACHE_CONTROL.getHeaderName() + ": no-cache"));
    assertTrue(header.contains(HttpHeaders.CONNECTION.getHeaderName() + ": keep-alive"));
    assertTrue(header.contains(HttpHeaders.CONTENT_TYPE.getHeaderName() + ": text/html"));
    assertTrue(header.contains(
        HttpHeaders.SERVER.getHeaderName() + ": " + ApplicationConfig.APPLICATION_NAME));

    // Date present and non-empty (avoid strict locale/timezone-specific regex)
    String datePrefix = HttpHeaders.DATE.getHeaderName() + ": ";
    int idx = header.indexOf(datePrefix);
    assertTrue(idx >= 0, "Date header should be present");
    int lineEnd = header.indexOf("\r\n", idx);
    assertTrue(lineEnd > idx + datePrefix.length(), "Date header value should be non-empty");
  }

  @Test
  @DisplayName("Given overrides When build Then custom headers override defaults")
  void givenOverrides_whenBuild_thenOverridesApplied() {
    String customDate = "Mon, 01 Jan 2024 00:00:00 GMT";
    Volundr v = new Volundr.Builder()
        .responseType("404 Not Found")
        .server("TestServer/1.0")
        .connection("close")
        .cacheControl("max-age=60")
        .contentType("application/json")
        .date(customDate)
        .contentLength("123")
        .build();

    String h = v.headerToString();

    assertTrue(h.startsWith("HTTP/1.1 404 Not Found\r\n"));
    assertTrue(h.contains("Server: TestServer/1.0\r\n"));
    assertTrue(h.contains("Connection: close\r\n"));
    assertTrue(h.contains("Cache-Control: max-age=60\r\n"));
    assertTrue(h.contains("Content-Type: application/json\r\n"));
    assertTrue(h.contains("Date: " + customDate + "\r\n"));
    assertTrue(h.contains("Content-Length: 123\r\n"));
  }

  @Test
  @DisplayName("Given built header When toString/bytes Then CRLF used and bytes match string")
  void givenHeader_whenSerializing_thenCrLfAndBytesMatch() {
    Volundr v = new Volundr.Builder().responseType("200 OK").build();
    String s = v.headerToString();

    // Each header line should end with CRLF, and header ends with CRLF CRLF
    String[] parts = s.split("\r\n");
    assertTrue(s.endsWith("\r\n\r\n"));
    // There will be an empty string after splitting due to trailing CRLFs, ensure we had lines
    assertTrue(parts.length > 2);

    // headerToBytes equals getBytes of headerToString
    byte[] expected = s.getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, v.headerToBytes());
  }

  @Test
  @DisplayName("Given cookie When addCookie Then Set-Cookie formatted with attributes")
  void givenCookie_whenAdd_thenFormatted() {
    Volundr v = new Volundr.Builder()
        .responseType("200 OK")
        .addCookie("SID", "abc", 3600)
        .build();

    String h = v.headerToString();
    assertTrue(h.contains("Set-Cookie: SID=abc; Max-Age=3600; Path=/; HttpOnly; Secure\r\n"));
  }

  @Test
  @DisplayName("Given multiple cookies When added Then last wins due to single header map")
  void givenMultipleCookies_whenAdd_thenLastWins() {
    Volundr v = new Volundr.Builder()
        .responseType("200 OK")
        .addCookie("A", "1", 10)
        .addCookie("B", "2", 20)
        .build();

    String h = v.headerToString();
    // Expect only the last cookie present because headers are stored as a map by name
    assertFalse(h.contains("Set-Cookie: A=1"));
    assertTrue(h.contains("Set-Cookie: B=2; Max-Age=20; Path=/; HttpOnly; Secure\r\n"));
  }

  @Test
  @DisplayName("Given invalid inputs When setting Then IllegalArgumentException thrown")
  void givenInvalidInputs_whenSetting_thenThrows() {
    // responseType null/empty
    assertThrows(IllegalArgumentException.class, () -> new Volundr.Builder().responseType(null));
    assertThrows(IllegalArgumentException.class, () -> new Volundr.Builder().responseType(" "));

    // addCustomHeader null/empty name/value
    Volundr.Builder b = new Volundr.Builder().responseType("200 OK");
    assertThrows(IllegalArgumentException.class, () -> b.addCustomHeader(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> b.addCustomHeader(" ", "x"));
    assertThrows(IllegalArgumentException.class, () -> b.addCustomHeader("X", null));
    assertThrows(IllegalArgumentException.class, () -> b.addCustomHeader("X", " "));
  }

  @Test
  @DisplayName("Given null/empty optionals When provided Then ignored and defaults remain")
  void givenNullOrEmpty_whenOptionalHeaders_thenIgnored() {
    Volundr v = new Volundr.Builder()
        .responseType("200 OK")
        .contentType("")
        .contentLength(null)
        .server(" ")
        .date(null)
        .connection("")
        .cacheControl(null)
        .build();

    String h = v.headerToString();

    // Defaults should still be present since no valid overrides were provided
    assertTrue(h.contains("Content-Type: text/html\r\n"));
    assertTrue(h.contains("Cache-Control: no-cache\r\n"));
    assertTrue(h.contains("Connection: keep-alive\r\n"));
    assertTrue(h.contains("Server: " + ApplicationConfig.APPLICATION_NAME + "\r\n"));

    // Content-Length not set because we passed null, so it should not appear at all
    assertFalse(h.contains("Content-Length:"));
  }
}
