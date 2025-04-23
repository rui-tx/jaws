package org.ruitx.www;

import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.interfaces.Route;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class API extends BaseController {

    private static final String API_ENDPOINT = "/api/v1/";

    public API() {
    }

    @Route(endpoint = API_ENDPOINT + "ping")
    public void ping() {
        sendJSONResponse(OK, OK.getCodeAndMessage());
    }
}
