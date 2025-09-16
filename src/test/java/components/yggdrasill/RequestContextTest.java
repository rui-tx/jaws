package components.yggdrasill;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.strings.ResponseCode;

public class RequestContextTest {

  private static Enumeration<String> enumerationOf(String... items) {
    return Collections.enumeration(List.of(items));
  }

  // Helper to construct RequestContext via reflection (constructor is private)
  private Yggdrasill.RequestContext newContext(HttpServletRequest req, HttpServletResponse resp)
      throws Exception {
    Constructor<Yggdrasill.RequestContext> ctor =
        Yggdrasill.RequestContext.class.getDeclaredConstructor(
            HttpServletRequest.class, HttpServletResponse.class, String.class);
    ctor.setAccessible(true);
    return ctor.newInstance(req, resp, "/static");
  }

  @Test
  @DisplayName("headers: extracts method-line and all headers")
  void headersExtraction() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/api/test");
    when(req.getQueryString()).thenReturn("a=1");

    when(req.getHeaderNames()).thenReturn(enumerationOf("Host", "HX-Request"));
    when(req.getHeader("Host")).thenReturn("example.test");
    when(req.getHeader("HX-Request")).thenReturn("true");

    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext ctx = newContext(req, resp);

    // First line uses method as key
    assertEquals("/api/test?a=1 HTTP/1.1", ctx.getHeader("GET"));
    assertEquals("example.test", ctx.getHeader("Host"));
    assertEquals("true", ctx.getHeader("HX-Request"));
  }

  @Test
  @DisplayName("params: extracts query and x-www-form-urlencoded body")
  void queryAndFormBodyParams() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/submit");
    when(req.getQueryString()).thenReturn("q=hello");
    when(req.getParameterMap()).thenReturn(Map.of("q", new String[]{"hello"}));

    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getContentType()).thenReturn("application/x-www-form-urlencoded");
    when(req.getReader()).thenReturn(
        new java.io.BufferedReader(new StringReader("a=1&b=two%20words")));

    Yggdrasill.RequestContext ctx = newContext(req, resp);

    assertEquals("hello", ctx.getQueryParams().get("q"));
    assertEquals("1", ctx.getBodyParams().get("a"));
    assertEquals("two words", ctx.getBodyParams().get("b"));
  }

  @Test
  @DisplayName("params: parses JSON body into map")
  void jsonBodyParams() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/json");
    when(req.getQueryString()).thenReturn(null);
    when(req.getParameterMap()).thenReturn(Map.of());

    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getContentType()).thenReturn("application/json");
    when(req.getReader()).thenReturn(
        new java.io.BufferedReader(new StringReader("{\"x\":1,\"y\":\"z\"}")));

    Yggdrasill.RequestContext ctx = newContext(req, resp);

    assertEquals("1", ctx.getBodyParams().get("x"));
    assertEquals("z", ctx.getBodyParams().get("y"));
  }

  @Test
  @DisplayName("multipart: files separated from form fields and body not read as text")
  void multipartHandling() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/upload");
    when(req.getQueryString()).thenReturn(null);
    when(req.getParameterMap()).thenReturn(Map.of());

    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getContentType()).thenReturn("multipart/form-data; boundary=abc");

    // Build parts: one file, one field
    Part filePart = mock(Part.class);
    when(filePart.getName()).thenReturn("file1");
    when(filePart.getSubmittedFileName()).thenReturn("hello.txt");

    Part fieldPart = mock(Part.class);
    when(fieldPart.getName()).thenReturn("desc");
    when(fieldPart.getSubmittedFileName()).thenReturn(null);
    when(fieldPart.getInputStream()).thenReturn(
        new ByteArrayInputStream("greeting".getBytes(StandardCharsets.UTF_8)));

    when(req.getParts()).thenReturn(List.of(filePart, fieldPart));

    Yggdrasill.RequestContext ctx = newContext(req, resp);

    assertTrue(ctx.getMultipartFiles().containsKey("file1"));
    assertEquals("hello.txt", ctx.getMultipartFiles().get("file1").getSubmittedFileName());
    assertEquals("greeting", ctx.getBodyParams().get("desc"));
    assertEquals("", ctx.getRequestBody(), "multipart should not read body as text");
  }

  @Test
  @DisplayName("token precedence: Authorization > cookie array > Cookie header > null")
  void tokenPrecedence() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/any");
    when(req.getQueryString()).thenReturn(null);
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("Authorization", "Cookie"));

    // Case 1: Authorization present
    when(req.getHeader("Authorization")).thenReturn("Bearer AAA");
    when(req.getCookies()).thenReturn(new Cookie[]{new Cookie("auth_token", "BBB")});
    when(req.getHeader("Cookie")).thenReturn("auth_token=CCC");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext ctx1 = newContext(req, resp);
    assertEquals("AAA", ctx1.getCurrentToken());

    // Case 2: No Authorization, cookie array used
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/any");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("Cookie"));
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getCookies()).thenReturn(new Cookie[]{new Cookie("auth_token", "BBB")});
    when(req.getHeader("Cookie")).thenReturn("auth_token=CCC");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext ctx2 = newContext(req, resp);
    assertEquals("BBB", ctx2.getCurrentToken());

    // Case 3: Only Cookie header
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/any");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("Cookie"));
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getCookies()).thenReturn(null);
    when(req.getHeader("Cookie")).thenReturn("foo=bar; auth_token=CCC; x=y");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext ctx3 = newContext(req, resp);
    assertEquals("CCC", ctx3.getCurrentToken());

    // Case 4: None
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/any");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getCookies()).thenReturn(null);
    when(req.getHeader("Cookie")).thenReturn(null);
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext ctx4 = newContext(req, resp);
    assertNull(ctx4.getCurrentToken());
  }

  @Test
  @DisplayName("ip resolution: X-Forwarded-For > X-Real-IP > remoteAddr")
  void ipResolutionOrder() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/ip");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("X-Forwarded-For", "X-Real-IP"));

    // Case 1
    when(req.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2");
    when(req.getHeader("X-Real-IP")).thenReturn("9.9.9.9");
    when(req.getRemoteAddr()).thenReturn("8.8.8.8");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext c1 = newContext(req, resp);
    assertEquals("1.1.1.1", c1.getClientIpAddress());

    // Case 2
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/ip");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("X-Real-IP"));
    when(req.getHeader("X-Forwarded-For")).thenReturn(null);
    when(req.getHeader("X-Real-IP")).thenReturn("9.9.9.9");
    when(req.getRemoteAddr()).thenReturn("8.8.8.8");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext c2 = newContext(req, resp);
    assertEquals("9.9.9.9", c2.getClientIpAddress());

    // Case 3
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/ip");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getHeader("X-Forwarded-For")).thenReturn(null);
    when(req.getHeader("X-Real-IP")).thenReturn(null);
    when(req.getRemoteAddr()).thenReturn("8.8.8.8");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    Yggdrasill.RequestContext c3 = newContext(req, resp);
    assertEquals("8.8.8.8", c3.getClientIpAddress());
  }

  @Test
  @DisplayName("htmx detection: HX-Request true or present, case-insensitive")
  void htmxDetection() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/hx");
    when(req.getParameterMap()).thenReturn(Map.of());

    // Variant 1: HX-Request: true
    when(req.getHeaderNames()).thenReturn(enumerationOf("HX-Request"));
    when(req.getHeader("HX-Request")).thenReturn("true");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));
    assertTrue(newContext(req, resp).isHTMX());

    // Variant 2: HX-Request present (any value)
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/hx");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("HX-Request"));
    when(req.getHeader("HX-Request")).thenReturn("1");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));
    assertTrue(newContext(req, resp).isHTMX());

    // Variant 3: lowercase header
    reset(req);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/hx");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf("hx-request"));
    when(req.getHeader("hx-request")).thenReturn("yes");
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));
    assertTrue(newContext(req, resp).isHTMX());
  }

  @Test
  @DisplayName("response helpers: JSON and binary senders set status, type, and body")
  void responseHelpers() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/resp");
    when(req.getParameterMap()).thenReturn(Map.of());
    when(req.getHeaderNames()).thenReturn(enumerationOf());
    when(req.getContentType()).thenReturn(null);
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    // Writer capture for JSON
    StringWriter sw = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(sw, true));

    // OutputStream capture for binary
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ServletOutputStream sos = new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
      }

      @Override
      public void write(int b) {
        baos.write(b);
      }
    };
    when(resp.getOutputStream()).thenReturn(sos);

    Yggdrasill.RequestContext ctx = newContext(req, resp);

    // JSON
    ctx.sendJSONResponse(ResponseCode.OK, "{\"ok\":true}");
    verify(resp).setStatus(ResponseCode.OK.getCode());
    verify(resp).setContentType("application/json; charset=UTF-8");
    assertTrue(sw.toString().contains("\"ok\":true"));

    // Binary
    byte[] bytes = "ABC".getBytes(StandardCharsets.UTF_8);
    ctx.sendBinaryResponse(ResponseCode.OK, "application/octet-stream", bytes);
    verify(resp, atLeastOnce()).setContentType("application/octet-stream");
    assertArrayEquals(bytes, baos.toByteArray());
  }
}
