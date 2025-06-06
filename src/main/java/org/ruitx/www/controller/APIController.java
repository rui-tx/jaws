package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Loki;
import org.ruitx.jaws.components.Einherjar;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.Route;
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
import static org.ruitx.jaws.strings.ResponseType.JSON;
import static org.ruitx.jaws.types.TypeDefinition.LIST_POST;

public class APIController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final APIService apiService;

    public APIController() {
        this.apiService = new APIService();
    }

    @Route(endpoint = API_ENDPOINT + "ping", responseType = JSON)
    public void ping() {
        //Logger.info("ping");
        sendSucessfulResponse(OK, apiService.ping());
    }

    @Route(endpoint = API_ENDPOINT + "ping2", responseType = JSON)
    public void ping2() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        sendSucessfulResponse(OK, apiService.ping());
    }

    @Route(endpoint = API_ENDPOINT + "status", responseType = JSON, method = GET)
    public void getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Basic system info
        status.put("server", "JAWS");
        status.put("mode", ApplicationConfig.JAWS_MODE);
        status.put("version", "0.1.3-alpha");
        status.put("timestamp", System.currentTimeMillis());
        
        // Loki queue statistics
        Loki loki = Loki.getInstance();
        Loki.QueueStats queueStats = loki.getStats();
        Map<String, Object> lokiInfo = new HashMap<>();
        lokiInfo.put("mode", queueStats.getMode());
        lokiInfo.put("pendingRequests", queueStats.getPendingRequests());
        lokiInfo.put("pendingResponses", queueStats.getPendingResponses());
        status.put("loki", lokiInfo);
        
        // Einherjar worker statistics (if in worker mode)
        if (loki.isWorkerMode()) {
            Einherjar einherjar = Einherjar.getInstance();
            Einherjar.WorkerStats workerStats = einherjar.getStats();
            Map<String, Object> workerInfo = new HashMap<>();
            workerInfo.put("running", workerStats.isRunning());
            workerInfo.put("totalWorkers", workerStats.getTotalWorkers());
            workerInfo.put("activeWorkers", workerStats.getActiveWorkers());
            status.put("einherjar", workerInfo);
        }
        
        sendSucessfulResponse(OK, status);
    }

    @Route(endpoint = API_ENDPOINT + "posts", responseType = JSON)
    public void testGetExternalAPI() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        APIResponse<List<Post>> response = callAPI(url, LIST_POST);

        if (!response.success()) {
            sendErrorResponse(response.code(), response.info());
            return;
        }

        sendSucessfulResponse(response.code(), response.data());
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

        sendSucessfulResponse(response.code(), response.data());
    }
}
