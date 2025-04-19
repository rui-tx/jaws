package org.ruitx.www.examples.gallery.controller;

import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.examples.gallery.dto.Image;
import org.ruitx.www.examples.gallery.service.GalleryService;

import java.util.List;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.*;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class GalleryAPIController extends BaseController {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    public GalleryAPIController() {
    }

    @Route(endpoint = API + "/gallery", method = GET, responseType = JSON)
    public void getImages() {
        GalleryService galleryService = new GalleryService();
        List<Image> images = galleryService.getAllImages();
        sendJSONResponse(OK, images);
    }

    @AccessControl(login = false)
    @Route(endpoint = API + "/gallery/:id", method = GET, responseType = JSON)
    public void getImageById() {
        String imageId = getPathParam("id");
        if (imageId == null || imageId.isEmpty()) {
            sendJSONResponse(BAD_REQUEST, "Image ID is invalid/empty");
            return;
        }

        GalleryService galleryService = new GalleryService();
        Image image = galleryService.getImageById(imageId);
        if (image == null) {
            sendJSONResponse(NOT_FOUND, "Image with ID " + imageId + " not found");
            return;
        }

        sendJSONResponse(OK, image);
    }
}
