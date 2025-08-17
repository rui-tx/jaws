package components.yggdrasill.support;

import java.util.Map;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;

public class TestController extends Bragi {

  @Route(endpoint = "/hello", method = RequestType.GET)
  public void hello() {
    sendHTMLResponse(ResponseCode.OK, "hello");
  }

  @Route(endpoint = "/api/ping", method = RequestType.GET)
  public void apiPing() {
    sendSuccess(Map.of("pong", true));
  }

  @Route(endpoint = "/users/:id", method = RequestType.GET)
  public void getUserById() {
    String id = get("id");
    sendSuccess(Map.of("id", id));
  }

  @Route(endpoint = "/cause/error", method = RequestType.GET)
  public void causeError() {
    throw new RuntimeException("boom");
  }
}
