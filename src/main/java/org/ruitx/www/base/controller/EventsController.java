package org.ruitx.www.base.controller;

import static org.ruitx.jaws.enums.RequestType.GET;
import static org.ruitx.jaws.enums.ResponseType.HTML;

import java.util.Optional;
import org.ruitx.jaws.components.Huginn;
import org.ruitx.jaws.components.njord.Route;
import org.ruitx.jaws.components.yggdrasill.AccessControl;
import org.ruitx.jaws.components.yggdrasill.Bragi;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.enums.ResponseCode;

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
