package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.api.Post;
import org.ruitx.www.service.APIService;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;
import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.HTML;
import static org.ruitx.jaws.strings.ResponseType.JSON;
import static org.ruitx.jaws.types.TypeDefinition.LIST_POST;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.types.Context;

public class APIController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final APIService apiService;

    public APIController() {
        this.apiService = new APIService();
    }

    @Route(endpoint = API_ENDPOINT + "ping", responseType = JSON)
    public void ping() {
        sendSuccessfulResponse(OK, apiService.ping());
    }

    @Route(endpoint = API_ENDPOINT + "posts", responseType = JSON)
    public void testGetExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        APIResponse<List<Post>> response = callAPI(url, LIST_POST);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSuccessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = API_ENDPOINT + "posts", method = POST, responseType = JSON)
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

        sendSuccessfulResponse(response.code(), response.data());
    }

    @Route(endpoint = "/backoffice", method = GET, responseType = HTML)
    public void renderBackoffice() {

        Map<String, String> context = new HashMap<>();
        context.put("currentPage", "dashboard");
        setContext(context);

        sendHTMLResponse(OK, renderTemplate("backoffice/main.html"));
    }

    @Route(endpoint = "/backoffice/api/user-count", method = GET, responseType = HTML)
    public void getUserCount() {
        try {
            Thread.sleep(2000); // Simulate delay for skeleton loading
            int userCount = 1234; 

            Map<String, Object> statsData = Map.of(
                "icon", "icon-users",
                "label", "Total Users",
                "value", userCount
            );

            Context templateContext = Context.builder()
                .with("statsData", statsData)
                .build();

            String html = Hermod.processTemplate(
                "backoffice/components/card/stats-card-view.html", 
                getRequestContext().getRequest(), 
                getRequestContext().getResponse(), 
                templateContext
            );

            sendHTMLResponse(OK, html);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendErrorResponse(ResponseCode.INTERNAL_SERVER_ERROR, "Request interrupted");
        } catch (Exception e) {
            Logger.error("Error getting user count: {}", e.getMessage());
            sendErrorResponse(ResponseCode.INTERNAL_SERVER_ERROR, "Failed to get user count");
        }
    }
}
