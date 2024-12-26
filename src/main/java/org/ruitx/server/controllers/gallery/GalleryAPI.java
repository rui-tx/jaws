package org.ruitx.server.controllers.gallery;

import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.interfaces.AccessControl;
import org.ruitx.server.interfaces.Route;
import org.ruitx.server.utils.APIHandler;
import org.ruitx.server.utils.APIResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.ResponseCode.OK;
import static org.ruitx.server.strings.ResponseType.JSON;

public class GalleryAPI {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    // Just for testing purposes
    private static final List<Image> DB = Arrays.asList(
            new Image("8hj2ok",
                    "https://images.unsplash.com/photo-1731432245362-26f9c0f1ba2f?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                    "Malin Head, County Donegal, Irland"),
            new Image("sipm5t",
                    "https://images.unsplash.com/photo-1734597949864-0ee6637b0c3f?q=80&w=1567&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                    "New York, NY, USA"),
            new Image("jqzvtx",
                    "https://images.unsplash.com/photo-1726333629906-9a52575d4b78?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                    "Stunning view of Mt. Baker at sunset from the top of Sauk Mountain in Washington, I really love the cloud inversion between the two evergreen-topped mountain ridges."),
            new Image("m9gata",
                    "https://images.unsplash.com/photo-1668365187350-05c997d09eba?q=80&w=1470&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                    "Very early on the morning looking over the sea stacks at Ribeira da Janela on the Island of Madeira.")
    );

    public GalleryAPI() {
    }
    
    @Route(endpoint = API + "/gallery", method = GET, responseType = JSON)
    public void getImages(Yggdrasill.RequestHandler rh) throws IOException {
        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(true,
                        DB,
                        null))
        );
    }

    @AccessControl(login = true)
    @Route(endpoint = API + "/gallery/:id", method = GET, responseType = JSON)
    public void getImageById(Yggdrasill.RequestHandler rh) throws IOException {
        String imageId = rh.getPathParams().get("id");
        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        DB.stream().filter(image -> image.id().equals(imageId)).findFirst().orElse(null),
                        null))
        );
    }
}
