package org.ruitx.www.controllers;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.types.ExternalTodo;
import org.ruitx.www.services.APIService;

import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.utils.APITypeDefinition.EXTERNALTODO_LIST;

public class APIController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final APIHandler apiHandler;
    private final APIService apiService;

    public APIController() {
        this.apiHandler = new APIHandler();
        this.apiService = new APIService();
    }

    @Route(endpoint = API_ENDPOINT + "ping")
    public void ping() {
        sendSucessfulResponse(OK, apiService.ping());
    }

    @Route(endpoint = API_ENDPOINT + "external")
    public void testGetExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        sendSucessfulResponse(OK, apiHandler.callAPI(url, EXTERNALTODO_LIST).data());
    }

    @Route(endpoint = API_ENDPOINT + "external", method = POST)
    public void testPostExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        ExternalTodo externalTodo = new ExternalTodo(1, null, "testTitle", "testBody");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        sendSucessfulResponse(OK, apiHandler.callAPI(url, POST, headers, externalTodo, ExternalTodo.class));
    }
}
