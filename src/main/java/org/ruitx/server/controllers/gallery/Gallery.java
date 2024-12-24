package org.ruitx.server.controllers.gallery;

import com.fasterxml.jackson.core.type.TypeReference;
import org.ruitx.server.components.Hermes;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.APIHandler;

import java.io.IOException;
import java.util.List;

import static org.ruitx.server.configs.ApplicationConfig.URL;
import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.ResponseCode.OK;

public class Gallery {

    private static final String BASE_HTML_PATH = "examples/gallery/index.html";
    private static final String BODY_HTML_PATH = "examples/gallery/partials/_body.html";

    public Gallery() {
    }

    @Route(endpoint = "/gallery", method = GET)
    public void renderIndex(Yggdrasill.RequestHandler rh) throws IOException {

        String apiURL = URL + GalleryAPI.URL + "/gallery";
        List<Image> images = new APIHandler().callAPI(apiURL, new TypeReference<>() {
        });

        StringBuilder bodyHTML = new StringBuilder();
        bodyHTML.append("<div class=\"block block-embossed\">\n");
        bodyHTML.append("    <div id=\"gallery\">\n");

        int n = 0;
        for (Image image : images) {
            bodyHTML.append("        <div class=\"responsive\">\n");
            bodyHTML.append("            <div class=\"gallery\">\n");
            bodyHTML.append("                <a href=\"").append(image.url()).append("\" target=\"_blank\">\n");
            bodyHTML.append("                    <img alt=\"image\" height=\"400\" src=\"").append(image.url()).append("\" width=\"600\">\n");
            bodyHTML.append("                </a>\n");
            bodyHTML.append("                <div class=\"desc\">Image ").append(++n).append(": ").append(image.description()).append(" </div>");
            bodyHTML.append("            </div>\n");
            bodyHTML.append("        </div>\n");
        }

        bodyHTML.append("        <div class=\"clearfix\"></div>\n");
        bodyHTML.append("    </div>\n");
        bodyHTML.append("</div>\n");

        rh.sendHTMLResponse(OK, Hermes.makeFullPageWithHTML(BASE_HTML_PATH, bodyHTML.toString()));
        //rh.sendHTMLResponse(OK, Hermes.makeFullPage(BASE_HTML_PATH, BODY_HTML_PATH));
    }
}
