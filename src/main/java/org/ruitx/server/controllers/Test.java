package org.ruitx.server.controllers;

import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.strings.ResponseCode;

import java.io.IOException;
import java.util.Map;

public class Test {

    @Route(endpoint = "/test", method = "GET")
    public void getTest(Yggdrasill.RequestHandler requestHandler) throws IOException {
        Map<String, String> queryParams = requestHandler.getQueryParams();
        StringBuilder body = new StringBuilder();
        body.append("<p>Success! This content was loaded dynamically!</p>");

        if (queryParams != null) {
            body.append("<p>Query Params:</p>");
            body.append("<ul>");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                body.append("<li><strong>").append(entry.getKey()).append("</strong>: ")
                        .append(entry.getValue()).append("</li>");
            }

            body.append("</ul>");
        }
        requestHandler.sendHTMLResponse(ResponseCode.OK, body.toString());
    }
}
