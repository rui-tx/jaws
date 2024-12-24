package org.ruitx.server.controllers.gallery;

import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.APIHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.ResponseCode.OK;

public class GalleryAPI {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    public GalleryAPI() {
    }

    @Route(endpoint = API + "/gallery", method = GET)
    public void getImages(Yggdrasill.RequestHandler rh) throws IOException {
        List<Image> images = Arrays.asList(
                new Image("https://images.unsplash.com/photo-1731432245362-26f9c0f1ba2f?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                        "Malin Head, County Donegal, Irland"),
                new Image("https://images.unsplash.com/photo-1734597949864-0ee6637b0c3f?q=80&w=1567&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                        "New York, NY, USA"),
                new Image("https://images.unsplash.com/photo-1726333629906-9a52575d4b78?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                        "Stunning view of Mt. Baker at sunset from the top of Sauk Mountain in Washington, I really love the cloud inversion between the two evergreen-topped mountain ridges."),
                new Image("https://images.unsplash.com/photo-1668365187350-05c997d09eba?q=80&w=1470&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                        "Very early on the morning looking over the sea stacks at Ribeira da Janela on the Island of Madeira.")
        );

        String json = APIHandler.getObjectMapper().writeValueAsString(images);
        rh.sendJSONResponse(OK, json);
    }
}