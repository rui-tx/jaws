package org.ruitx.www.examples.gallery.controller;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.examples.gallery.dto.Image;
import org.ruitx.www.examples.gallery.service.GalleryService;

import java.io.IOException;
import java.util.List;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class GalleryAPIController {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    public GalleryAPIController() {
    }

    @Route(endpoint = API + "/gallery", method = GET, responseType = JSON)
    public void getImages(Yggdrasill.RequestHandler rh) throws IOException {
        GalleryService galleryService = new GalleryService();
        List<Image> images = galleryService.getAllImages();

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        images,
                        null))
        );
    }

    @AccessControl(login = false)
    @Route(endpoint = API + "/gallery/:id", method = GET, responseType = JSON)
    public void getImageById(Yggdrasill.RequestHandler rh) throws IOException {
        String imageId = rh.getPathParams().get("id");
        if (imageId == null || imageId.isEmpty()) {
            rh.sendJSONResponse(BAD_REQUEST, APIHandler.encode(
                    new APIResponse<>(
                            false,
                            null,
                            "Image ID is empty"))
            );
        }

        GalleryService galleryService = new GalleryService();
        Image image = galleryService.getImageById(imageId);

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        image,
                        null))
        );
    }
}
