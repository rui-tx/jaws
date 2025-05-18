package org.ruitx.jaws.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.exceptions.ConnectionException;
import org.ruitx.jaws.exceptions.ProcessRequestException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.tinylog.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.ruitx.jaws.strings.DefaultHTML.*;
import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;
import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;

/**
 * Yggdrasill represents the main HTTP jaws, handling incoming requests and dispatching them
 * to the appropriate request handlers based on the HTTP method (GET, POST, PUT, PATCH, DELETE).
 * It manages client connections and responses.
 */
public class Yggdrasill {

    /**
     * The current number of active connections.
     */
    public static Integer currentConnections = 0;

    /**
     * The port on which the jaws is running.
     */
    public static int currentPort;

    /**
     * The base path for static resources.
     */
    public static String currentResourcesPath;

    private final int port;
    private final String resourcesPath;
    private ServerSocket serverSocket;

    /**
     * Constructs a new Yggdrasill jaws instance.
     *
     * @param port          the port to run the jaws on.
     * @param resourcesPath the path to the jaws's static resources.
     */
    public Yggdrasill(int port, String resourcesPath) {
        this.port = port;
        this.resourcesPath = resourcesPath;
        currentPort = port;
        currentResourcesPath = resourcesPath;
    }

    /**
     * Starts the jaws, accepting incoming connections and delegating request processing to a thread pool.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            ExecutorService threadPool = Executors.newCachedThreadPool();
            acceptConnections(serverSocket, threadPool);
        } catch (IOException | ConnectionException e) {
            Logger.error("Yggdrasill encountered an error: %s", e.getMessage());
        }
    }

    /**
     * Shuts down the jaws by closing the jaws socket.
     */
    public void shutdown() {
        Logger.info("Yggdrasill shutdown initiated...");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Logger.info("Shutting down Yggdrasill...");
                serverSocket.close();
            } catch (IOException e) {
                Logger.error("Error closing jaws socket: %s", e.getMessage());
            }
        }
    }

    /**
     * Accepts incoming connections and submits them to a thread pool for processing.
     *
     * @param serverSocket the jaws socket to accept connections on.
     * @param threadPool   the thread pool to submit connection tasks.
     * @throws ConnectionException if an error occurs while accepting a connection.
     */
    private void acceptConnections(ServerSocket serverSocket, ExecutorService threadPool) throws ConnectionException {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout((int) ApplicationConfig.TIMEOUT);
                threadPool.submit(new RequestHandler(clientSocket, resourcesPath));
                ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool;
                synchronized (Yggdrasill.class) {
                    currentConnections = executor.getActiveCount();
                }
            } catch (SocketTimeoutException e) {
                throw new ConnectionException("Socket timeout while waiting for connection", e);
            } catch (IllegalBlockingModeException e) {
                throw new ConnectionException("Illegal blocking mode: " + e.getMessage(), e);
            } catch (SecurityException e) {
                throw new ConnectionException("Security exception: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new ConnectionException("Error accepting connection: " + e.getMessage(), e);
            }
        }
    }

    /**
     * RequestHandler processes individual HTTP requests from clients.
     * It reads the request, determines the appropriate route, and sends a response.
     */
    public static class RequestHandler implements Runnable {

        private final Socket socket;
        private final String resourcesPath;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> customResponseHeaders = new LinkedHashMap<>();
        private final StringBuilder body = new StringBuilder();
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private Map<String, String> bodyParams = new LinkedHashMap<>();
        private Map<String, String> pathParams = new LinkedHashMap<>();
        private String currentToken;
        private BufferedReader in;
        private DataOutputStream out;

        /**
         * Constructs a RequestHandler for the specified socket and resource path.
         *
         * @param socket        the socket to handle.
         * @param resourcesPath the path to static resources.
         */
        public RequestHandler(Socket socket, String resourcesPath) {
            this.socket = socket;
            this.resourcesPath = resourcesPath;
        }

        /**
         * Runs the request handling process, including reading headers, body, and sending a response.
         */
        @Override
        public void run() {
            processRequest();
        }

        /**
         * Initializes input/output streams and processes the request.
         * Reads the request's headers, body, and sends the appropriate response.
         */
        private void processRequest() {
            initializeStreams();
            readHeaders();
            readBody();
            checkRequestAndSendResponse();
            closeSocket();
        }

        /**
         * Initializes the input and output streams for the socket.
         */
        private void initializeStreams() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                throw new ProcessRequestException("Error initializing streams", e);
            }
        }

        /**
         * Reads the HTTP request headers and stores them in the {@link #headers} map.
         */
        private void readHeaders() {
            String headerLine;
            try {
                while (!(headerLine = in.readLine()).isEmpty()) {
                    String[] parts = headerLine.split(" ", 2);
                    headers.put(parts[0].replace(":", ""), parts[1]);
                }
            } catch (IOException e) {
                throw new ProcessRequestException("Error reading headers", e);
            }
            extractAndStoreToken(headers);
        }

        /**
         * Reads the body of the HTTP request (if any).
         */
        private void readBody() {
            try {
                while (in.ready()) {
                    body.append((char) in.read());
                }
            } catch (IOException e) {
                throw new ProcessRequestException("Error reading body", e);
            }
        }

        /**
         * Determines the type of HTTP request (e.g., GET, POST, etc.) and sends a response accordingly.
         */
        private void checkRequestAndSendResponse() {
            String requestType = getRequestType();
            if (requestType.equals("INVALID")) {
                Logger.error("Invalid method: " + requestType);
                byte[] content = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>405 - Method Not Allowed</title>
                        </head>
                        <body>
                            <h1>405 - Method not allowed</h1>
                        </body>
                        </html>
                        """.getBytes();
                sendResponseHeaders(METHOD_NOT_ALLOWED, "text/html", content.length);
                sendResponseBody(content);
                return;
            }

            sendResponse(requestType, body.toString());
        }

        /**
         * Sends an appropriate response based on the HTTP request type (GET, POST, PUT, PATCH, DELETE).
         *
         * @param requestType the type of the HTTP request.
         * @param body        the body of the HTTP request.
         */
        private void sendResponse(String requestType, String body) {
            String endPoint = headers.get(requestType).split(" ")[0];
            queryParams = extractQueryParameters(endPoint);

            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex != -1) {
                endPoint = endPoint.substring(0, questionMarkIndex);
            }

            switch (RequestType.fromString(requestType)) {
                case GET -> processGET(endPoint);
                case POST -> processPOST(endPoint, body);
                case PUT -> processPUT(endPoint, body);
                case PATCH -> processPATCH(endPoint, body);
                case DELETE -> processDELETE(endPoint, body);
                default -> Logger.error("Request type not recognized [" + requestType + "], ignored");
            }
        }

        // I know there is some code duplication here, but I don't want to overload sendResponse method
        // I think it's easier to read this way, and in the future, I think adding authentication and authorization
        // should be easier

        /**
         * Processes GET requests, serving static files or invoking dynamic routes.
         *
         * @param endPoint the endpoint of the request.
         */
        private void processGET(String endPoint) {

            if (findDynamicRouteFor(endPoint, GET)) {
                return;
            }

            // Try to get the file or index.html if it's a directory
            Path path = getResourcePath(endPoint);
            Path fileToServe = getFileToServe(path);
            if (fileToServe != null) {
                sendFileResponse(fileToServe);
                return;
            }

            sendNotFoundResponse();
        }

        /**
         * Processes POST requests, extracting body parameters and dispatching to dynamic routes.
         *
         * @param endPoint the endpoint of the request.
         * @param body     the body of the request.
         */
        private void processPOST(String endPoint, String body) {
            bodyParams = extractBodyParameters(body);

            if (findDynamicRouteFor(endPoint, POST)) {
                return;
            }
            sendNotFoundResponse();
        }

        /**
         * Processes PUT requests, extracting body parameters and dispatching to dynamic routes.
         *
         * @param endPoint the endpoint of the request.
         * @param body     the body of the request.
         */
        private void processPUT(String endPoint, String body) {
            bodyParams = extractBodyParameters(body);

            if (findDynamicRouteFor(endPoint, PUT)) {
                return;
            }
            sendNotFoundResponse();
        }

        /**
         * Processes PATCH requests, extracting body parameters and dispatching to dynamic routes.
         *
         * @param endPoint the endpoint of the request.
         * @param body     the body of the request.
         */
        private void processPATCH(String endPoint, String body) {
            bodyParams = extractBodyParameters(body);

            if (findDynamicRouteFor(endPoint, PATCH)) {
                return;
            }
            sendNotFoundResponse();
        }

        /**
         * Processes DELETE requests, extracting body parameters and dispatching to dynamic routes.
         *
         * @param endPoint the endpoint of the request.
         * @param body     the body of the request.
         * @throws IOException if an error occurs while processing the DELETE request.
         */
        private void processDELETE(String endPoint, String body) {
            bodyParams = extractBodyParameters(body);

            if (findDynamicRouteFor(endPoint, DELETE)) {
                return;
            }
            sendNotFoundResponse();
        }

        /**
         * Sends an HTML response with the specified response code and body content.
         *
         * @param responseCode the HTTP response code.
         * @param body         the body content of the response.
         * @throws IOException if an error occurs while sending the response.
         */
        public void sendHTMLResponse(ResponseCode responseCode, String body) throws IOException {
            byte[] content = body.getBytes();
            String contentType = "text/html";

            String parsedHTML = Hermod.processTemplate(new String(content));
            parsedHTML += "\n\n";
            sendResponseHeaders(responseCode, contentType, parsedHTML.length());
            sendResponseBody(parsedHTML.getBytes());
        }

        /**
         * Sends a JSON response with the specified response code and body content.
         *
         * @param responseCode the HTTP response code.
         * @param body         the body content of the response.
         */
        public void sendJSONResponse(ResponseCode responseCode, String body) {
            byte[] content = body.getBytes();
            String contentType = "application/json";

            sendResponseHeaders(responseCode, contentType, content.length);
            sendResponseBody(content);
        }

        /**
         * Sends a binary response with the specified response code and body content.
         *
         * @param responseCode the HTTP response code.
         * @param contentType  the content type of the response.
         * @param body         the body content of the response.
         */
        public void sendBinaryResponse(ResponseCode responseCode, String contentType, byte[] body) {
            sendResponseHeaders(responseCode, contentType, body.length);
            sendResponseBody(body);
        }

        /**
         * Sends a file response for the specified path.
         *
         * @param path the path to the file.
         */
        private void sendFileResponse(Path path) {
            if (isConnectionClosed()) {
                return;
            }
            try {
                byte[] content = Files.readAllBytes(path);
                String contentType = Files.probeContentType(path);

                if (contentType.equals("text/html")) {
                    String parsedHTML = Hermod.processTemplate(new String(content), queryParams, bodyParams);
                    parsedHTML += "\n\n"; // prevents truncation of the last line
                    sendResponseHeaders(OK, contentType, parsedHTML.length());
                    out.write(parsedHTML.getBytes(StandardCharsets.UTF_8));
                } else {
                    sendResponseHeaders(OK, contentType, content.length);
                    out.write(content);
                }
                out.flush();
            } catch (IOException e) {
                throw new ProcessRequestException("Error sending file response", e);
            }

        }

        /**
         * Sends a 404 Not Found response.
         */
        private void sendNotFoundResponse() {
            try {
                byte[] content;
                Path path = Paths.get(ApplicationConfig.CUSTOM_PAGE_PATH_404);
                if (Files.exists(path)) {
                    content = Files.readAllBytes(path);
                } else {
                    Logger.warn("Could not find 404 page, using hard coded default");
                    content = HTML_404_NOT_FOUND.getBytes();
                }
                sendResponseHeaders(NOT_FOUND, "text/html", content.length);
                sendResponseBody(content);
            } catch (IOException e) {
                throw new ProcessRequestException("Error sending 404 response", e);
            }
        }

        /**
         * Sends a 401 Not Found response.
         */
        private void sendUnauthorizedResponse(ResponseType responseType) {
            byte[] content;
            switch (responseType) {
                case HTML -> {
                    Logger.warn("Could not find 401 page, using hard coded default");
                    if (isHTMX()) {
                        sendResponseHeaders(OK, "text/html", HTML_401_UNAUTHORIZED_HTMX.getBytes().length);
                        sendResponseBody(HTML_401_UNAUTHORIZED_HTMX.getBytes());
                        return;
                    }

                    Path path = Paths.get(ApplicationConfig.CUSTOM_PAGE_PATH_401);
                    if (Files.exists(path)) {
                        try {
                            content = Files.readAllBytes(path);
                        } catch (IOException e) {
                            throw new ProcessRequestException("Error sending 401 response", e);
                        }
                    } else {
                        Logger.warn("Could not find 401 page, using hard coded default");
                        content = HTML_401_UNAUTHORIZED.getBytes();
                    }

                    sendResponseHeaders(UNAUTHORIZED, "text/html", content.length);
                    sendResponseBody(content);
                }

                case JSON -> {
                    APIResponse<String> res = APIResponse.error(
                            UNAUTHORIZED.getCodeAndMessage(),
                            "You are not authorized to access this resource"
                    );
                    content = Bragi.encode(res).getBytes();
                    sendResponseHeaders(UNAUTHORIZED, "application/json", content.length);
                    sendResponseBody(content);
                }

                case INVALID -> {
                    Logger.error("Invalid response type");
                    APIResponse<String> res = APIResponse.error(
                            METHOD_NOT_ALLOWED.getCodeAndMessage(),
                            "The requested response type is invalid"
                    );
                    content = Bragi.encode(res).getBytes();
                    sendResponseHeaders(UNAUTHORIZED, "application/json", content.length);
                    sendResponseBody(content);
                }
            }
        }

        /**
         * Sends response headers to the client.
         *
         * @param responseCode  the HTTP response code.
         * @param contentType   the content type of the response.
         * @param contentLength the length of the content.
         */
        private void sendResponseHeaders(ResponseCode responseCode, String contentType, int contentLength) {
            if (isConnectionClosed()) {
                return;
            }

            Volundr.Builder builder = new Volundr.Builder()
                    .responseType(responseCode.toString())
                    .contentType(contentType)
                    .contentLength(String.valueOf(contentLength));

            if (!customResponseHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : customResponseHeaders.entrySet()) {
                    builder.addCustomHeader(entry.getKey(), entry.getValue());
                }
            }
            builder.build();

            Volundr responseHeader = builder.build();
            try {
                out.write(responseHeader.headerToBytes());
                out.flush();
            } catch (IOException e) {
                throw new SendRespondException("Error sending response headers", e);
            }
        }

        /**
         * Sends the body of the response to the client.
         *
         * @param body the body content of the response.
         */
        private void sendResponseBody(byte[] body) {
            if (isConnectionClosed()) {
                return;
            }

            try {
                out.write(body);
                out.flush();
            } catch (IOException e) {
                throw new SendRespondException("Error sending response body", e);
            }
        }

        /**
         * Retrieves the type of HTTP request (GET, POST, PUT, PATCH, DELETE).
         *
         * @return the HTTP request type, or "INVALID" if no valid request type is found.
         */
        private String getRequestType() {
            return headers.keySet().stream()
                    .filter(this::isValidRequestType)
                    .findFirst()
                    .orElse("INVALID");
        }

        /**
         * Checks if the provided key is a valid HTTP request type (GET, POST, PUT, PATCH, DELETE).
         *
         * @param key the header key to check.
         * @return true if the key is a valid request type, false otherwise.
         */
        private boolean isValidRequestType(String key) {
            return key.equals(GET.name())
                    || key.equals(POST.name())
                    || key.equals(PUT.name())
                    || key.equals(PATCH.name())
                    || key.equals(DELETE.name());
        }

        /**
         * Extracts parameters from the body of a POST or PUT request.
         *
         * @param data the body data of the request.
         * @return a map of body parameters.
         */
        private Map<String, String> extractBodyParameters(String data) {
            // Check if the content type is JSON
            String contentType = headers.get(CONTENT_TYPE.getHeaderName());
            if (contentType != null && contentType.contains("application/json")) {
                return parseJsonBody(data);
            }

            // Fall back to form data parsing
            Map<String, String> parsedData = new LinkedHashMap<>();
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                parsedData.put(key, value);
            }
            return parsedData;
        }

        private Map<String, String> parseJsonBody(String json) {
            try {
                ObjectMapper mapper = Odin.getMapper();
                Map<String, String> result = new LinkedHashMap<>();
                JsonNode root = mapper.readTree(json);

                // Convert all fields to strings
                root.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    result.put(entry.getKey(), value.isTextual() ? value.asText() : value.toString());
                });

                return result;
            } catch (Exception e) {
                Logger.error("Failed to parse JSON body: {}", e.getMessage());
                return new LinkedHashMap<>();
            }
        }

        // TODO: Refactor this method
        // what if ? is present but is not a query parameter
        // if query parameter is not present, then it should ignore extractQueryParameters

        /**
         * Extracts query parameters from the URL.
         *
         * @param endPoint the endpoint (URL) of the request.
         * @return a map of query parameters, or null if no query parameters exist.
         */
        private Map<String, String> extractQueryParameters(String endPoint) {
            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex == -1) {
                return null;
            }

            Map<String, String> queryParams = new LinkedHashMap<>();
            String queryString = endPoint.substring(questionMarkIndex + 1);
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                queryParams.put(key, value);
            }
            return queryParams;
        }

        /**
         * Extracts the current token from the request headers and stores it in a thread-local variable.
         *
         * @param headers the request headers.
         */
        private void extractAndStoreToken(Map<String, String> headers) {
            if (headers == null) return;

            String authHeader = headers.entrySet().stream()
                    .filter(entry -> "Authorization".equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);

            if (authHeader != null) {
                this.currentToken = authHeader.substring(7);
                return;
            }

            // TODO: Refactor this if statement, it shouldn't be just "Cookie", but "Cookie token=" or something
            // tries to extract the token from the Cookie header
            String cookieHeader = headers.entrySet().stream()
                    .filter(entry -> "Cookie".equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);

            if (cookieHeader != null) {
                this.currentToken = cookieHeader.split(";")[0].split("=")[1];
            }
        }

        /**
         * Finds a dynamic route for the given endpoint and HTTP method.
         *
         * @param endPoint the endpoint to match.
         * @param method   the HTTP method (GET, POST, etc.).
         * @return true if a route is found and invoked, false otherwise.
         */
        private boolean findDynamicRouteFor(String endPoint, RequestType method) {
            // Check if we have a static route match first
            Method routeMethod = Njord.getInstance().getRoute(endPoint, method);
            if (routeMethod != null) {

                // Check if the route requires authorization
                if (isAuthorized(routeMethod)) {
                    return invokeRouteMethod(routeMethod);
                } else {
                    sendUnauthorizedResponse(routeMethod.getAnnotation(Route.class).responseType());
                    return false;
                }
            }

            // If no direct match, search through all routes for dynamic matches
            for (Method route : Njord.getInstance().getAllRoutes()) {
                if (route.isAnnotationPresent(Route.class)) {
                    Route routeAnnotation = route.getAnnotation(Route.class);
                    String routePattern = routeAnnotation.endpoint();
                    // Check if the route pattern matches the endpoint, allowing dynamic parameters
                    pathParams = matchRoutePattern(routePattern, endPoint);
                    if (pathParams != null && routeAnnotation.method() == method) {
                        // Check if the route requires authorization
                        if (isAuthorized(route)) {
                            return invokeRouteMethod(route);
                        } else {
                            sendUnauthorizedResponse(route.getAnnotation(Route.class).responseType());
                            return false;
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Checks if the current user is authorized and has the required role to access the route.
         *
         * @param routeMethod the route method to check.
         * @return true if the user is authorized, false otherwise.
         */
        private boolean isAuthorized(Method routeMethod) {
            if (routeMethod.isAnnotationPresent(AccessControl.class)) {
                AccessControl auth = routeMethod.getAnnotation(AccessControl.class);
                if (auth.login()) {
                    if (currentToken == null) {
                        if (getHeaders().get("Cookie") == null && getHeaders().get("cookie") == null) {
                            return false;
                        }
                    } else if (!Tyr.isTokenValid(currentToken)) {
                        return false;
                    }
                    String userId = Tyr.getUserIdFromJWT(currentToken);
                    String userRole = Tyr.getUserRoleFromJWT(currentToken);

                    // Check if the user's role is sufficient for this endpoint
                    // Roles does nothing at the moment
                    // Logger.info("User ID: " + userId);
                    // Logger.info("User Role: " + userRole);
                }
            }

            return true; // Authorized if no issues
        }

        /**
         * Invokes the method corresponding to a dynamic route.
         *
         * @param routeMethod the route method to invoke.
         * @return true if the method was invoked successfully, false otherwise.
         */
        private boolean invokeRouteMethod(Method routeMethod) {
            try {
                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                Logger.debug("Invoking route method {} in controller {}", routeMethod.getName(), controllerName);

                Object controllerInstance = Njord.getInstance().getControllerInstance(controllerName);
                if (controllerInstance == null) {
                    Logger.error("Failed to get controller instance for {}", controllerName);
                    throw new IllegalStateException("Controller instance not found: " + controllerName);
                }

                // Set request handler if the controller extends BaseController
                if (controllerInstance instanceof Bragi bragi) {
                    bragi.setRequestHandler(this);
                    Logger.debug("Set request handler for controller {}", controllerName);
                }

                // Synchronize the controller instance to prevent concurrent access
                synchronized (controllerInstance) {
                    try {
                        // Get method parameters
                        Class<?>[] parameterTypes = routeMethod.getParameterTypes();
                        Object[] parameters = new Object[parameterTypes.length];

                        // If there's a parameter and it's a request object, try to deserialize it
                        if (parameterTypes.length > 0) {
                            String contentType = headers.get(CONTENT_TYPE.getHeaderName());
                            String requestBody = body.toString().trim();

                            // Check if Content-Type is missing or not JSON
                            if (contentType == null || !contentType.contains("application/json")) {
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Request body is required. Check the API documentation for the correct request body"
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }

                            if (requestBody.isEmpty()) {
                                // Send a proper error response for empty JSON body
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Request body is required. Check the API documentation for the correct request body"
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }

                            try {
                                ObjectMapper mapper = Odin.getMapper();
                                JsonNode root = mapper.readTree(requestBody);

                                // Get the expected fields from the parameter type
                                Class<?> paramType = parameterTypes[0];
                                List<String> requiredFields = new ArrayList<>();
                                for (Field field : paramType.getDeclaredFields()) {
                                    requiredFields.add(field.getName());
                                }

                                // Check for missing or extra fields
                                List<String> missingFields = new ArrayList<>();
                                List<String> extraFields = new ArrayList<>();

                                for (String field : requiredFields) {
                                    if (!root.has(field)) {
                                        missingFields.add(field);
                                    }
                                }

                                Iterator<String> fieldNames = root.fieldNames();
                                while (fieldNames.hasNext()) {
                                    String field = fieldNames.next();
                                    if (!requiredFields.contains(field)) {
                                        extraFields.add(field);
                                    }
                                }

                                // Build error message if there are issues
                                if (!missingFields.isEmpty() || !extraFields.isEmpty()) {
                                    StringBuilder errorMessage = new StringBuilder("Invalid request body: ");
                                    if (!missingFields.isEmpty()) {
                                        errorMessage.append("Missing required fields: ").append(String.join(", ", missingFields));
                                    }
                                    if (!extraFields.isEmpty()) {
                                        if (!missingFields.isEmpty()) {
                                            errorMessage.append(". ");
                                        }
                                        errorMessage.append("Unexpected fields: ").append(String.join(", ", extraFields));
                                    }

                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            errorMessage.toString()
                                    );
                                    sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                }

                                // If validation passes, deserialize the object
                                parameters[0] = mapper.readValue(requestBody, parameterTypes[0]);
                            } catch (Exception e) {
                                // Send a proper error response for invalid JSON
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Invalid JSON format. Check the API documentation for the correct request body"
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }
                        }

                        // Invoke the method
                        routeMethod.invoke(controllerInstance, parameters);
                    } catch (Exception e) {
                        Logger.error("Controller execution failed in {}.{}: {} - {}",
                                controllerName,
                                routeMethod.getName(),
                                e.getClass().getSimpleName(),
                                e.getMessage());

                        if (e.getCause() != null) {
                            Logger.error("Caused by: {} - {}",
                                    e.getCause().getClass().getSimpleName(),
                                    e.getCause().getMessage());
                        }

                        // Try to send error response if possible
                        if (controllerInstance instanceof Bragi controller) {
                            Yggdrasill.RequestHandler handler = controller.getRequestHandler();
                            if (handler != null) {
                                // Create error response with detailed message
                                String errorMessage = e.getCause() != null ?
                                        e.getCause().getMessage() : e.getMessage();

                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                                        errorMessage != null ? errorMessage : "An unexpected error occurred"
                                );

                                // Send error response
                                handler.sendJSONResponse(
                                        ResponseCode.INTERNAL_SERVER_ERROR,
                                        Bragi.encode(response)
                                );

                                // Cleanup after sending response
                                controller.cleanup();
                                return true;
                            }
                        }
                        throw e; // Re-throw if we couldn't handle it
                    }
                }

                // Cleanup if the controller extends BaseController
                if (controllerInstance instanceof Bragi) {
                    ((Bragi) controllerInstance).cleanup();
                }

                return true;
            } catch (Exception e) {
                Logger.error("Failed to handle request: {} - {}",
                        e.getClass().getSimpleName(),
                        e.getMessage());
                if (e.getCause() != null) {
                    Logger.error("Caused by: {} - {}",
                            e.getCause().getClass().getSimpleName(),
                            e.getCause().getMessage());
                }
                closeSocket();
                return false;
            }
        }

        /**
         * Matches the route pattern to the endpoint and extracts path parameters if any.
         *
         * @param routePattern the route pattern to match.
         * @param endPoint     the endpoint to check against the pattern.
         * @return a map of path parameters if the pattern matches, null otherwise.
         */
        private Map<String, String> matchRoutePattern(String routePattern, String endPoint) {
            Map<String, String> params = new LinkedHashMap<>();

            // Split the route pattern and the endpoint into parts (e.g., "/user/:id" becomes ["user", ":id"])
            String[] routeParts = routePattern.split("/");
            String[] endpointParts = endPoint.split("/");

            // If the number of parts doesn't match, it's not a valid route
            if (routeParts.length != endpointParts.length) {
                return null;
            }

            // Loop through the route parts and check if they match the endpoint
            for (int i = 0; i < routeParts.length; i++) {
                String routePart = routeParts[i];
                String endpointPart = endpointParts[i];

                if (routePart.startsWith(":")) {
                    // Dynamic segment, extract the value and add it to the params map
                    String paramName = routePart.substring(1);  // Remove the ":"
                    params.put(paramName, endpointPart);
                } else if (!routePart.equals(endpointPart)) {
                    // Static segment doesn't match, return null
                    return null;
                }
            }

            // Return the map of parameters (if any)
            return params;
        }

        /**
         * Gets the file to serve based on the path.
         * If the path is a directory, it will check for an index.html file.
         *
         * @param path the path to the file
         * @return the file to serve, or null if no valid file is found
         */
        private Path getFileToServe(Path path) {
            // If the path exists and is a file, return it
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return path;
            }

            // If the path is a directory, check for index.html
            if (Files.isDirectory(path)) {
                Path indexPath = path.resolve("index.html");
                if (Files.exists(indexPath) && !Files.isDirectory(indexPath)) {
                    return indexPath;
                }
            }

            // Return null if no valid file is found
            return null;
        }

        /**
         * Retrieves the client's IP address by checking various headers commonly used
         * when a client is behind a proxy, or falls back to the remote address of the socket.
         * The order of preference for determining the IP address is:
         * - "X-Forwarded-For" header
         * - "Proxy-Client-IP" header
         * - "WL-Proxy-Client-IP" header
         * - Remote address of the socket
         * If none of these are available, it returns "unknown".
         *
         * @return the client's IP address as a String, or "unknown" if the IP cannot be determined
         */
        public String getClientIpAddress() {
            // First check the X-Forwarded-For header (for clients behind a proxy)
            String forwardedFor = headers.get("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                // Get the first IP if there are multiple
                return forwardedFor.split(",")[0].trim();
            }

            // Then check the Proxy-Client-IP header
            String proxyClientIp = headers.get("Proxy-Client-IP");
            if (proxyClientIp != null && !proxyClientIp.isEmpty()) {
                return proxyClientIp;
            }

            // Then check the WL-Proxy-Client-IP header
            String wlProxyClientIp = headers.get("WL-Proxy-Client-IP");
            if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty()) {
                return wlProxyClientIp;
            }

            // Finally, get the remote address from the socket
            if (socket != null && socket.getInetAddress() != null) {
                return socket.getInetAddress().getHostAddress();
            }

            return "unknown";
        }

        /**
         * Check if the current request is made by HTMX.
         *
         * @return true if the request is made by HTMX, false otherwise.
         */
        public boolean isHTMX() {
            return headers.containsKey("HX-Request") || headers.containsKey("hx-request");
        }

        /**
         * Checks whether the socket connection is closed.
         *
         * @return true if the socket is null, closed, or not connected; false otherwise.
         */
        public boolean isConnectionClosed() {
            return socket == null || socket.isClosed() || !socket.isConnected();
        }

        /**
         * Closes the socket connection.
         */
        private void closeSocket() {
            synchronized (Yggdrasill.class) {
                currentConnections--;
            }
            //currentToken.remove();
            try {
                socket.close();
            } catch (IOException e) {
                throw new ProcessRequestException("Error closing socket", e);
            }
        }

        /**
         * Returns the path for the static resource corresponding to the endpoint.
         *
         * @param endPoint the endpoint of the request.
         * @return the path to the corresponding static resource.
         */
        private Path getResourcePath(String endPoint) {
            return endPoint.equals("/") ? Paths.get(resourcesPath + "/index.html") : Paths.get(resourcesPath + endPoint);
        }

        /**
         * Retrieves the current token from the request headers.
         *
         * @return the current token, or null if no token is found.
         */
        public String getCurrentToken() {
            return this.currentToken;
        }

        /**
         * Retrieves the query parameters from the request.
         *
         * @return a map of query parameters.
         */
        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        /**
         * Retrieves the body parameters from the request.
         *
         * @return a map of body parameters.
         */
        public Map<String, String> getBodyParams() {
            return bodyParams;
        }

        /**
         * Retrieves the path parameters from the request.
         *
         * @return a map of path parameters.
         */
        public Map<String, String> getPathParams() {
            return pathParams;
        }

        /**
         * Add a custom response header to the response.
         *
         * @param name  the name of the header.
         * @param value the value of the header.
         */
        public void addCustomHeader(String name, String value) {
            customResponseHeaders.put(name, value);
        }

        /**
         * Retrieves the headers as a map of key-value pairs.
         *
         * @return a map containing the headers, where keys are the header names and values are the corresponding header values
         */
        public Map<String, String> getHeaders() {
            return headers;
        }
    }
}
