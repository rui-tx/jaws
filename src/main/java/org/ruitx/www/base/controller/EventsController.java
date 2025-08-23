package org.ruitx.www.base.controller;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseType.HTML;

import java.util.Optional;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.components.sse.Huginn;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;

public class EventsController extends Bragi {

  @AccessControl(login = true)
  @Route(endpoint = "/events", method = GET, responseType = HTML)
  public void globalEvents() {
    Yggdrasill.RequestContext ctx = getRequestContext();
    Optional<Huginn.Client> client = Huginn.getInstance().subscribe(Huginn.GLOBAL_CHANNEL, ctx);

    if (client.isEmpty()) {
      // Unauthorized already set by subscribe; optionally send small body
      sendHTMLResponse(ResponseCode.UNAUTHORIZED, "Unauthorized");
    }
    // If subscribed, do nothing else; AsyncContext from Huginn keeps the connection open
  }
}
