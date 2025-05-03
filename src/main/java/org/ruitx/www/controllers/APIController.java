package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.types.Post;
import org.ruitx.www.services.APIService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.types.TypeDefinition.LIST_POST;

public class APIController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final APIService apiService;

    public APIController() {
        this.apiService = new APIService();
    }

    @Route(endpoint = API_ENDPOINT + "ping")
    public void ping() {
        sendSucessfulResponse(OK, apiService.ping());
    }

    @Route(endpoint = API_ENDPOINT + "posts")
    public void testGetExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        APIResponse<List<Post>> response = callAPI(url, LIST_POST);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = API_ENDPOINT + "posts", method = POST)
    public void testPostExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        Post requestBody = new Post(1, null, "testTitle", "testBody");
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE.getHeaderName(), "application/json; charset=UTF-8");
        APIResponse<Post> response = callAPI(url, POST, headers, requestBody, Post.class);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
    }
}
