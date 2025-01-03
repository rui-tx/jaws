package org.ruitx.www.examples.gallery.controller;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.examples.gallery.dto.Image;
import org.ruitx.www.examples.gallery.service.GalleryService;

import java.io.IOException;
import java.util.List;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.*;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class GalleryAPIController {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    public GalleryAPIController() {
    }

    @Route(endpoint = API + "/gallery", method = GET, responseType = JSON)
    public void getImages(Yggdrasill.RequestHandler rh) {
        GalleryService galleryService = new GalleryService();
        List<Image> images = galleryService.getAllImages();

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        OK.getCodeAndMessage(),
                        "",
                        images))
                );
    }

    @AccessControl(login = false)
    @Route(endpoint = API + "/gallery/:id", method = GET, responseType = JSON)
    public void getImageById(Yggdrasill.RequestHandler rh) {
        String imageId = rh.getPathParams().get("id");
        if (imageId == null || imageId.isEmpty()) {
            rh.sendJSONResponse(BAD_REQUEST, APIHandler.encode(
                    new APIResponse<>(
                            false,
                            BAD_REQUEST.getCodeAndMessage(),
                            "Image ID is invalid/empty",
                            null))
                    );
        }

        GalleryService galleryService = new GalleryService();
        Image image = galleryService.getImageById(imageId);
        if (image == null) {
            rh.sendJSONResponse(NOT_FOUND, APIHandler.encode(
                    new APIResponse<>(
                            true,
                            NOT_FOUND.getCodeAndMessage(),
                            "Image with ID " + imageId + " not found",
                            null))
                    );
            return;
        }

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        OK.getCodeAndMessage(),
                        "",
                        image))
                );
    }
}
