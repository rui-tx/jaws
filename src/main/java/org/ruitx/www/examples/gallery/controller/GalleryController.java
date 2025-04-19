package org.ruitx.www.examples.gallery.controller;

import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.components.Hermes;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.examples.gallery.dto.Image;

import java.io.IOException;
import java.util.List;

import static org.ruitx.jaws.configs.ApplicationConfig.URL;
import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.utils.APITypeDefinition.IMAGE_LIST;

public class GalleryController extends BaseController {

    private static final String BASE_HTML_PATH = "examples/gallery/index.html";
    private static final String BODY_HTML_PATH = "examples/gallery/partials/_body.html";

    public GalleryController() {
    }

    @Route(endpoint = "/gallery", method = GET)
    public void renderIndex() throws IOException {
        String apiURL = URL + GalleryAPIController.URL + "/gallery";
        APIResponse<List<Image>> response = new APIHandler().callAPI(apiURL, IMAGE_LIST);

        StringBuilder bodyHTML = new StringBuilder("""
                <div class="block block-embossed">
                    <div id="gallery">
                """);

        if (response != null && response.success() && response.data() != null) {
            for (Image image : response.data()) {
                bodyHTML.append("""
                            <div class="responsive">
                                <div class="gallery">
                                    <a href="%s" target="_blank">
                                        <img alt="image" height="400" src="%s" width="600">
                                    </a>
                                    <div class="desc">ID: %s - %s</div>
                                </div>
                            </div>
                        """.formatted(image.url(), image.url(), image.id(), image.description()));
            }
        } else {
            bodyHTML.append("""
                        <div class="block block-embossed">
                            No images found
                        </div>
                    """);
        }

        bodyHTML.append("""
                        <div class="clearfix"></div>
                    </div>
                </div>
                """);

        sendHTMLResponse(OK, Hermes.makeFullPageWithHTML(BASE_HTML_PATH, bodyHTML.toString()));
    }
}
