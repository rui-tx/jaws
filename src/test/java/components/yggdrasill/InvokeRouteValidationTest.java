package components.yggdrasill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.yggdrasill.Bragi;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.enums.ResponseCode;
import org.ruitx.jaws.utils.logger.JawsLogger;

public class InvokeRouteValidationTest {

  @BeforeAll
  static void initLogger() {
    // Ensure JawsLogger is bootstrapped to avoid IllegalStateException during tests
    if (!Odin.hasDatabase(Odin.LOGS_DB_NAME)) {
      Path logsDbPath = Paths.get("target", "unit-logs-" + System.nanoTime() + ".db");
      Odin.registerDatabase(Odin.LOGS_DB_NAME, new DatabaseConfig(
          logsDbPath.toAbsolutePath().toString(),
          Paths.get("src/main/resources/sql/logs_schema_v1.sql").toAbsolutePath().toString(),
          Math.max(2, ApplicationConfig.MIMIR_READER_POOL_SIZE / 2),
          ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
          true,
          ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
          null,
          "jaws-logs-writer",
          "jaws-logs-reader",
          null
      ));
    }
    JawsLogger.bootstrap(Odin.getDB(Odin.LOGS_DB_NAME));
  }

  private static Enumeration<String> enumerationOf(String... items) {
    return Collections.enumeration(java.util.List.of(items));
  }

  // Access private field on RequestContext
  private static void setPrivateField(Object target, String fieldName, Object value)
      throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  // Build RequestContext via reflection (constructor is private)
  private Yggdrasill.RequestContext newContext(HttpServletRequest req, HttpServletResponse resp)
      throws Exception {
    Constructor<Yggdrasill.RequestContext> ctor =
        Yggdrasill.RequestContext.class.getDeclaredConstructor(
            HttpServletRequest.class, HttpServletResponse.class, String.class);
    ctor.setAccessible(true);
    return ctor.newInstance(req, resp, "/static");
  }

  // Create an instance of the private inner class Yggdrasill.JawsServlet
  private Object newJawsServlet(Yggdrasill ygg) throws Exception {
    Class<?>[] classes = Yggdrasill.class.getDeclaredClasses();
    Class<?> jawsServletClass = null;
    for (Class<?> c : classes) {
      if (c.getSimpleName().equals("JawsServlet")) {
        jawsServletClass = c;
        break;
      }
    }
    if (jawsServletClass == null) {
      throw new IllegalStateException("JawsServlet not found");
    }
    Constructor<?> ctor = jawsServletClass.getDeclaredConstructor(Yggdrasill.class);
    ctor.setAccessible(true);
    return ctor.newInstance(ygg);
  }

  // Reflectively get the private invokeRouteMethod
  private Method getInvokeRouteMethod(Object jawsServlet) throws Exception {
    Method m = jawsServlet.getClass().getDeclaredMethod(
        "invokeRouteMethod",
        Yggdrasill.RequestContext.class,
        Method.class,
        Object.class);
    m.setAccessible(true);
    return m;
  }

  @Test
  @DisplayName("invokeRouteMethod: multipart invalid DTO -> 400 Bad Request")
  void invokeRoute_withInvalidMultipartDTO_returnsBadRequest() throws Exception {
    HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
    HttpServletResponse resp = org.mockito.Mockito.mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/users");
    when(req.getHeaderNames()).thenReturn(enumerationOf("Content-Type"));
    when(req.getHeader("Content-Type")).thenReturn("multipart/form-data; boundary=abc");
    when(req.getContentType()).thenReturn("multipart/form-data; boundary=abc");
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    StringWriter sw = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(sw, true));

    // Build context and force a body (so invokeRouteMethod will try to deserialize/validate)
    Yggdrasill.RequestContext ctx = newContext(req, resp);
    setPrivateField(ctx, "requestBody", "{\"name\":\"\"}"); // invalid: @NotBlank

    Yggdrasill ygg = new Yggdrasill(0, "/static");
    Object jawsServlet = newJawsServlet(ygg);
    Method invoke = getInvokeRouteMethod(jawsServlet);

    DtoController controller = new DtoController();
    Method routeMethod = DtoController.class.getMethod("create", CreateUserDTO.class);

    // Call private method
    Object result = invoke.invoke(jawsServlet, ctx, routeMethod, controller);

    // Should have sent 400 and JSON content type
    org.mockito.Mockito.verify(resp).setStatus(ResponseCode.BAD_REQUEST.getCode());
    org.mockito.Mockito.verify(resp).setContentType(ArgumentMatchers.contains("application/json"));
    assertEquals(true, (Boolean) result); // returns true after sending response
  }

  @Test
  @DisplayName("invokeRouteMethod: multipart valid DTO -> 200 OK and controller executed")
  void invokeRoute_withValidMultipartDTO_invokesController() throws Exception {
    HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
    HttpServletResponse resp = org.mockito.Mockito.mock(HttpServletResponse.class);

    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/users");
    when(req.getHeaderNames()).thenReturn(enumerationOf("Content-Type"));
    when(req.getHeader("Content-Type")).thenReturn("multipart/form-data; boundary=abc");
    when(req.getContentType()).thenReturn("multipart/form-data; boundary=abc");
    when(req.getReader()).thenReturn(new java.io.BufferedReader(new StringReader("")));

    StringWriter sw = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(sw, true));

    Yggdrasill.RequestContext ctx = newContext(req, resp);
    setPrivateField(ctx, "requestBody", "{\"name\":\"john\"}"); // valid

    Yggdrasill ygg = new Yggdrasill(0, "/static");
    Object jawsServlet = newJawsServlet(ygg);
    Method invoke = getInvokeRouteMethod(jawsServlet);

    DtoController controller = new DtoController();
    Method routeMethod = DtoController.class.getMethod("create", CreateUserDTO.class);

    Object result = invoke.invoke(jawsServlet, ctx, routeMethod, controller);

    // Controller should have sent success (default 200) and include the echoed name
    org.mockito.Mockito.verify(resp).setStatus(ResponseCode.OK.getCode());
    assertTrue(sw.toString().contains("john"));
    assertEquals(true, (Boolean) result);
  }

  // A simple DTO with javax validation annotations
  public static class CreateUserDTO {

    @jakarta.validation.constraints.NotBlank
    public String name;

    public CreateUserDTO() {
    }

    public CreateUserDTO(String name) {
      this.name = name;
    }
  }

  // Controller that consumes the DTO and writes a success JSON
  public static class DtoController extends Bragi {

    public void create(CreateUserDTO dto) {
      // Echo back to verify we received the validated/parsed DTO
      sendSuccess(java.util.Map.of("name", dto.name));
    }
  }
}
