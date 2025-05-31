package org.ruitx.jaws.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.exceptions.ProcessRequestException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.ValidationUtils;
import org.tinylog.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.ruitx.jaws.strings.DefaultHTML.*;
import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;
import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;

/**
 * Yggdrasill is the main HTTP server component
 * It integrates Jetty with JAWS' existing route system, middleware support,
 * and provides all request handling functionality.
 */
public class Yggdrasill {

    /**
     * The current number of active connections.
     */
    public static final AtomicInteger currentConnections = new AtomicInteger(0);

    /**
     * The port on which the server is running.
     */
    public static int currentPort;

    /**
     * The base path for static resources.
     */
    public static String currentResourcesPath;

    private final int port;
    private final String resourcesPath;
    private final List<Middleware> middlewares = new ArrayList<>();
    private Server server;

    /**
     * Constructs a new Yggdrasill instance.
     *
     * @param port          the port to run the server on.
     * @param resourcesPath the path to the server's static resources.
     */
    public Yggdrasill(int port, String resourcesPath) {
        this.port = port;
        this.resourcesPath = resourcesPath;
        currentPort = port;
        currentResourcesPath = resourcesPath;
    }

    /**
     * Adds a middleware to the server.
     * Middleware will be executed in order of their priority (getOrder() method).
     *
     * @param middleware the middleware to add
     */
    public void addMiddleware(Middleware middleware) {
        middlewares.add(middleware);
        // Sort middleware by order
        middlewares.sort(Comparator.comparingInt(Middleware::getOrder));
    }

    /**
     * Starts the server, setting up Jetty with custom servlet for route handling.
     */
    public void start() {
        try {
            server = new Server();
            
            // Create connector
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            connector.setIdleTimeout(ApplicationConfig.TIMEOUT);
            server.addConnector(connector);

            // Create servlet context
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            // Set up static file serving
            setupStaticFileServing(context);

            // Set up dynamic route handling
            setupDynamicRouteHandling(context);

            server.setHandler(context);
            server.start();

            Logger.info("Yggdrasill started on port {} with resources path: {}", port, resourcesPath);
            
        } catch (Exception e) {
            Logger.error("Yggdrasill encountered an error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start Yggdrasill", e);
        }
    }

    /**
     * Sets up static file serving using Jetty's DefaultServlet.
     */
    private void setupStaticFileServing(ServletContextHandler context) {
        try {
            // Create static resource handler
            Path resourcePath = Paths.get(resourcesPath).toAbsolutePath();
            if (!Files.exists(resourcePath)) {
                Logger.warn("Static resources path does not exist: {}", resourcePath);
                Files.createDirectories(resourcePath);
            }

            // Use DefaultServlet for static files
            ServletHolder staticServlet = new ServletHolder("static", DefaultServlet.class);
            staticServlet.setInitParameter("resourceBase", resourcePath.toString());
            staticServlet.setInitParameter("dirAllowed", "true");
            staticServlet.setInitParameter("pathInfoOnly", "true");
            staticServlet.setInitParameter("welcomeServlets", "false");
            
            // Add static servlet with lower priority (mapped to /static/*)
            context.addServlet(staticServlet, "/static/*");
            
            Logger.info("Static file serving configured for path: {}", resourcePath);
        } catch (Exception e) {
            Logger.error("Failed to setup static file serving: {}", e.getMessage());
        }
    }

    /**
     * Sets up dynamic route handling using a custom servlet.
     */
    private void setupDynamicRouteHandling(ServletContextHandler context) {
        // Create and add the main request handling servlet
        ServletHolder jawsServlet = new ServletHolder("jaws", new JawsServlet());
        context.addServlet(jawsServlet, "/*");
    }

    /**
     * Shuts down the server.
     */
    public void shutdown() {
        Logger.info("Yggdrasill shutdown initiated...");
        if (server != null) {
            try {
                Logger.info("Shutting down Yggdrasill...");
                server.stop();
                server.join();
            } catch (Exception e) {
                Logger.error("Error shutting down Yggdrasill: {}", e.getMessage());
            }
        }
    }

    /**
     * Custom servlet that handles all dynamic requests and integrates with the existing JAWS route system.
     */
    private class JawsServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            
            currentConnections.incrementAndGet();
            
            try {
                // Create request context for this request
                RequestContext context = new RequestContext(request, response, resourcesPath);

                // Execute middleware chain
                MiddlewareChainImpl middlewareChain = new MiddlewareChainImpl(middlewares, context);
                boolean continueProcessing = middlewareChain.execute();

                if (!continueProcessing) {
                    // Middleware stopped the request
                    return;
                }

                // Process the request using integrated JAWS logic
                processRequest(context);

            } catch (Exception e) {
                Logger.error("Error processing request: {}", e.getMessage(), e);
                try {
                    if (!response.isCommitted()) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentType("text/html; charset=UTF-8");
                        response.getWriter().write("Internal Server Error");
                        response.getWriter().flush();
                    }
                } catch (IOException ioException) {
                    Logger.error("Error sending error response: {}", ioException.getMessage());
                } finally {
                    currentConnections.decrementAndGet();
                }
            }
        }

        /**
         * Processes the request using JAWS' existing route system.
         */
        private void processRequest(RequestContext context) throws IOException {
            String method = context.request.getMethod().toUpperCase();
            String endPoint = context.request.getRequestURI();

            // Remove query parameters from endpoint for route matching
            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex != -1) {
                endPoint = endPoint.substring(0, questionMarkIndex);
            }

            // Convert HTTP method to RequestType
            RequestType requestType = RequestType.fromString(method);
            if (requestType == null) {
                Logger.error("Invalid method: {}", method);
                context.response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                context.response.getWriter().write("405 - Method Not Allowed");
                return;
            }

            // Try to find and execute dynamic route
            boolean routeFound = findDynamicRouteFor(context, endPoint, requestType);

            if (!routeFound) {
                // Try to serve static file if no dynamic route found
                if (requestType == RequestType.GET) {
                    if (tryServeStaticFile(context, endPoint)) {
                        return;
                    }
                }
                
                // Send 404 if no route and no static file found
                sendNotFoundResponse(context);
            }
        }

        /**
         * Finds and executes a dynamic route for the given endpoint and method.
         */
        private boolean findDynamicRouteFor(RequestContext context, String endPoint, RequestType method) {
            try {
                // Get all registered controllers from Njord
                for (Method routeMethod : Njord.getInstance().getAllRoutes()) {
                    if (routeMethod.isAnnotationPresent(Route.class)) {
                        Route route = routeMethod.getAnnotation(Route.class);
                        
                        // Check if the route method matches
                        if (!method.equals(route.method())) {
                            continue;
                        }
                        
                        // Check if the route pattern matches
                        Map<String, String> pathParams = matchRoutePattern(route.endpoint(), endPoint);
                        if (pathParams != null) {
                            Logger.debug("Route matched: {} {} -> {}.{}", 
                                route.method(), route.endpoint(), 
                                routeMethod.getDeclaringClass().getSimpleName(), routeMethod.getName());
                            
                            // Store path parameters
                            context.pathParams = pathParams;
                            
                            // Get controller instance and invoke the route method
                            String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                            Object controllerInstance = Njord.getInstance().getControllerInstance(controllerName);
                            if (controllerInstance == null) {
                                Logger.error("Controller instance not found: {}", controllerName);
                                return false;
                            }
                            
                            return invokeRouteMethod(context, routeMethod, controllerInstance);
                        }
                    }
                }
                
                return false;
            } catch (Exception e) {
                Logger.error("Error finding dynamic route: {}", e.getMessage(), e);
                return false;
            }
        }

        /**
         * Invokes the route method with proper parameter handling and validation.
         */
        private boolean invokeRouteMethod(RequestContext context, Method routeMethod, Object controllerInstance) {
            try {
                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                Logger.debug("Invoking route method {} in controller {}", routeMethod.getName(), controllerName);

                // Set request context if the controller extends Bragi
                if (controllerInstance instanceof Bragi bragi) {
                    bragi.setRequestContext(context);
                    Logger.debug("Set request context for controller {}", controllerName);
                }

                // Synchronize the controller instance to prevent concurrent access
                synchronized (controllerInstance) {
                    try {
                        // Get method parameters
                        Class<?>[] parameterTypes = routeMethod.getParameterTypes();
                        Object[] parameters = new Object[parameterTypes.length];

                        // Handle request body deserialization if needed
                        if (parameterTypes.length > 0) {
                            String contentType = context.getHeader(CONTENT_TYPE.getHeaderName());
                            String requestBodyTrimmed = context.requestBody != null ? context.requestBody.trim() : "";
                            
                            // For POST/PUT/PATCH requests that expect a request body parameter,
                            // we need to ensure proper JSON content type and non-empty body
                            String httpMethod = context.request.getMethod().toUpperCase();
                            boolean expectsRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT") || httpMethod.equals("PATCH");
                            
                            if (expectsRequestBody) {
                                // Check for missing Content-Type header
                                if (contentType == null || !contentType.contains("application/json")) {
                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            "Content-Type header must be 'application/json' for " + httpMethod + " requests"
                                    );
                                    sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                }
                                
                                // Check for empty request body
                                if (requestBodyTrimmed.isEmpty()) {
                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            "Request body is required for " + httpMethod + " requests. Please provide a valid JSON body."
                                    );
                                    sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                }
                            }

                            // If we have a request body, process it
                            if (!requestBodyTrimmed.isEmpty()) {
                                try {
                                    ObjectMapper mapper = Odin.getMapper();

                                    // Deserialize the object
                                    parameters[0] = mapper.readValue(requestBodyTrimmed, parameterTypes[0]);
                                    
                                    // Validate the deserialized object using Jakarta Bean Validation
                                    APIResponse<String> validationError = ValidationUtils.validate(parameters[0]);
                                    if (validationError != null) {
                                        sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(validationError));
                                        return true;
                                    }
                                } catch (com.fasterxml.jackson.core.JsonParseException e) {
                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            "Invalid JSON format: " + e.getOriginalMessage()
                                    );
                                    sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
                                    // Handle unknown fields or mapping issues
                                    String originalMessage = e.getOriginalMessage();
                                    if (originalMessage != null && originalMessage.contains("Unrecognized field")) {
                                        // Extract the field name from the error message
                                        String fieldName = extractFieldNameFromError(originalMessage);
                                        APIResponse<String> response = APIResponse.error(
                                                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                                String.format("Unknown field '%s'. Accepted fields are: content, title, language, expiresInHours, isPrivate, password", fieldName)
                                        );
                                        sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                        return true;
                                    } else {
                                        APIResponse<String> response = APIResponse.error(
                                                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                                "JSON mapping error: " + (originalMessage != null ? originalMessage : e.getMessage())
                                        );
                                        sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                        return true;
                                    }
                                } catch (IllegalArgumentException e) {
                                    // This is thrown by validateRequestFields when validation fails
                                    // The error response is already sent in validateRequestFields
                                    return true;
                                } catch (Exception e) {
                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            "Error processing request body: " + e.getMessage()
                                    );
                                    sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                }
                            } else if (parameterTypes.length > 0 && expectsRequestBody) {
                                // If we reach here, it means we have parameters expected but no body was provided
                                // This shouldn't happen due to the checks above, but let's be safe
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Request body is required but was not provided"
                                );
                                sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }
                        }

                        // Invoke the method
                        routeMethod.invoke(controllerInstance, parameters);
                    } catch (Exception e) {
                        handleControllerException(context, e, controllerInstance, routeMethod.getName());
                        return true;
                    }
                }

                // Cleanup if the controller extends Bragi
                if (controllerInstance instanceof Bragi) {
                    ((Bragi) controllerInstance).cleanup();
                }

                return true;
            } catch (Exception e) {
                Logger.error("Failed to handle request: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                return false;
            }
        }

        /**
         * Handles exceptions that occur during controller execution.
         */
        private void handleControllerException(RequestContext context, Exception e, Object controllerInstance, String methodName) {
            String controllerName = controllerInstance.getClass().getSimpleName();
            Logger.error("Controller execution failed in {}.{}: {} - {}",
                    controllerName, methodName, e.getClass().getSimpleName(), e.getMessage());

            if (e.getCause() != null) {
                Logger.error("Caused by: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }

            // Try to send error response if possible
            if (controllerInstance instanceof Bragi controller) {
                try {
                    String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                            errorMessage != null ? errorMessage : "An unexpected error occurred"
                    );
                    sendJSONResponse(context, ResponseCode.INTERNAL_SERVER_ERROR, Bragi.encode(response));
                    controller.cleanup();
                } catch (Exception ex) {
                    Logger.error("Failed to send error response: {}", ex.getMessage());
                }
            }
        }

        /**
         * Extracts the field name from Jackson's error message for unknown fields.
         * Example: "Unrecognized field \"contednt\" (class ...)" -> "contednt"
         */
        private String extractFieldNameFromError(String errorMessage) {
            try {
                // Look for the pattern: "field \"fieldname\""
                int startQuote = errorMessage.indexOf('"');
                if (startQuote != -1) {
                    int endQuote = errorMessage.indexOf('"', startQuote + 1);
                    if (endQuote != -1) {
                        return errorMessage.substring(startQuote + 1, endQuote);
                    }
                }
                return "unknown";
            } catch (Exception e) {
                return "unknown";
            }
        }

        /**
         * Matches the route pattern to the endpoint and extracts path parameters if any.
         */
        private Map<String, String> matchRoutePattern(String routePattern, String endPoint) {
            Map<String, String> params = new LinkedHashMap<>();

            String[] routeParts = routePattern.split("/");
            String[] endpointParts = endPoint.split("/");

            if (routeParts.length != endpointParts.length) {
                return null;
            }

            for (int i = 0; i < routeParts.length; i++) {
                String routePart = routeParts[i];
                String endpointPart = endpointParts[i];

                if (routePart.startsWith(":")) {
                    String paramName = routePart.substring(1);
                    params.put(paramName, endpointPart);
                } else if (!routePart.equals(endpointPart)) {
                    return null;
                }
            }

            return params;
        }

        /**
         * Sends a JSON response to the client.
         */
        private void sendJSONResponse(RequestContext context, ResponseCode responseCode, String body) {
            try {
                context.response.setStatus(responseCode.getCode());
                context.response.setContentType("application/json; charset=UTF-8");
                
                // Add custom headers
                context.customResponseHeaders.forEach((name, value) -> 
                    context.response.setHeader(name, value));
                
                context.response.getWriter().write(body);
                context.response.getWriter().flush();
            } catch (IOException e) {
                Logger.error("Error sending JSON response: {}", e.getMessage());
                throw new SendRespondException("Error sending JSON response", e);
            }
        }

        /**
         * Sends an HTML response to the client.
         */
        private void sendHTMLResponse(RequestContext context, ResponseCode responseCode, String body) throws IOException {
            context.response.setStatus(responseCode.getCode());
            context.response.setContentType("text/html; charset=UTF-8");
            
            // Add custom headers
            context.customResponseHeaders.forEach((name, value) -> 
                context.response.setHeader(name, value));
            
            // Process templates through Hermod
            String processedHTML = Hermod.processTemplate(body, context.queryParams, context.bodyParams);
            processedHTML += "\n\n"; // Prevent truncation
            
            context.response.getWriter().write(processedHTML);
            context.response.getWriter().flush();
        }

        /**
         * Sends a binary response to the client.
         */
        private void sendBinaryResponse(RequestContext context, ResponseCode responseCode, String contentType, byte[] body) {
            try {
                context.response.setStatus(responseCode.getCode());
                context.response.setContentType(contentType);
                
                // Add custom headers
                context.customResponseHeaders.forEach((name, value) -> 
                    context.response.setHeader(name, value));
                
                context.response.getOutputStream().write(body);
                context.response.getOutputStream().flush();
            } catch (IOException e) {
                Logger.error("Error sending binary response: {}", e.getMessage());
                throw new SendRespondException("Error sending binary response", e);
            }
        }

        /**
         * Sends a 404 Not Found response.
         */
        private void sendNotFoundResponse(RequestContext context) {
            try {
                context.response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                context.response.setContentType("text/html; charset=UTF-8");
                context.response.getWriter().write(HTML_404_NOT_FOUND);
                context.response.getWriter().flush();
            } catch (IOException e) {
                Logger.error("Error sending 404 response: {}", e.getMessage());
            }
        }

        /**
         * Sends an unauthorized response.
         */
        private void sendUnauthorizedResponse(RequestContext context, ResponseType responseType) {
            try {
                context.response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                
                if (responseType == ResponseType.JSON) {
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                            "Access denied. Please provide valid authentication."
                    );
                    context.response.setContentType("application/json; charset=UTF-8");
                    context.response.getWriter().write(Bragi.encode(response));
                } else {
                    context.response.setContentType("text/html; charset=UTF-8");
                    context.response.getWriter().write(HTML_401_UNAUTHORIZED);
                }
                
                context.response.getWriter().flush();
            } catch (Exception e) {
                Logger.error("Error sending unauthorized response: {}", e.getMessage());
            }
        }

        /**
         * Attempts to serve a static file for the given endpoint.
         */
        private boolean tryServeStaticFile(RequestContext context, String endPoint) throws IOException {
            Path filePath = getResourcePath(endPoint);
            Path fileToServe = getFileToServe(filePath);
            
            if (fileToServe != null) {
                serveStaticFile(context, fileToServe);
                return true;
            }
            
            return false;
        }

        /**
         * Serves a static file with proper content type and template processing.
         */
        private void serveStaticFile(RequestContext context, Path filePath) throws IOException {
            byte[] content = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            if ("text/html".equals(contentType)) {
                // Process HTML templates
                String processedHTML = Hermod.processTemplate(
                    new String(content), 
                    context.queryParams, 
                    context.bodyParams
                );
                processedHTML += "\n\n"; // Prevent truncation
                
                context.response.setStatus(HttpServletResponse.SC_OK);
                context.response.setContentType(contentType);
                context.response.getWriter().write(processedHTML);
            } else {
                // Serve binary content
                context.response.setStatus(HttpServletResponse.SC_OK);
                context.response.setContentType(contentType);
                context.response.getOutputStream().write(content);
            }
        }

        /**
         * Gets the file to serve based on the path, checking for index.html in directories.
         */
        private Path getFileToServe(Path path) {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return path;
            }

            if (Files.isDirectory(path)) {
                Path indexPath = path.resolve("index.html");
                if (Files.exists(indexPath) && !Files.isDirectory(indexPath)) {
                    return indexPath;
                }
            }

            return null;
        }

        /**
         * Returns the path for the static resource corresponding to the endpoint.
         */
        private Path getResourcePath(String endPoint) {
            return endPoint.equals("/") 
                ? Paths.get(resourcesPath + "/index.html") 
                : Paths.get(resourcesPath + endPoint);
        }
    }

    /**
     * RequestContext encapsulates all request-related data and functionality.
     */
    public static class RequestContext {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final String resourcesPath;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> customResponseHeaders = new LinkedHashMap<>();
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private Map<String, String> bodyParams = new LinkedHashMap<>();
        private Map<String, String> pathParams = new LinkedHashMap<>();
        private String currentToken;
        private String requestBody;

        public RequestContext(HttpServletRequest request, HttpServletResponse response, String resourcesPath) {
            this.request = request;
            this.response = response;
            this.resourcesPath = resourcesPath;
            
            // Extract headers from Jetty request
            extractHeaders();
            
            // Extract query parameters
            extractQueryParameters();
            
            // Extract and store token
            extractAndStoreToken();
            
            // Read request body
            readRequestBody();
        }

        /**
         * Extracts headers from the Jetty request and stores them in the headers map.
         */
        private void extractHeaders() {
            // Add the HTTP method as the first header (for compatibility with existing system)
            String method = request.getMethod().toUpperCase();
            String requestURI = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullPath = queryString != null ? requestURI + "?" + queryString : requestURI;
            
            headers.put(method, fullPath + " HTTP/1.1");
            
            // Extract all other headers
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                String headerValue = request.getHeader(headerName);
                headers.put(headerName.replace(":", ""), headerValue);
            });
        }

        /**
         * Extracts query parameters from the Jetty request.
         */
        private void extractQueryParameters() {
            queryParams = request.getParameterMap().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().length > 0 ? entry.getValue()[0] : "",
                            (existing, replacement) -> existing,
                            LinkedHashMap::new
                    ));
        }

        /**
         * Reads the request body from the Jetty request.
         */
        private void readRequestBody() {
            try {
                requestBody = request.getReader().lines().collect(Collectors.joining("\n"));
                
                // Extract body parameters if it's form data
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                    bodyParams = extractBodyParameters(requestBody);
                } else if (contentType != null && contentType.contains("application/json")) {
                    bodyParams = parseJsonBody(requestBody);
                }
            } catch (IOException e) {
                Logger.error("Error reading request body: {}", e.getMessage());
                requestBody = "";
                bodyParams = new LinkedHashMap<>();
            }
        }

        /**
         * Extracts parameters from the body of a POST or PUT request.
         *
         * @param data the body data of the request.
         * @return a map of body parameters.
         */
        private Map<String, String> extractBodyParameters(String data) {
            Map<String, String> parsedData = new LinkedHashMap<>();
            if (data == null || data.isEmpty()) {
                return parsedData;
            }
            
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                parsedData.put(key, value);
            }
            return parsedData;
        }

        /**
         * Parses JSON body into a map of string key-value pairs.
         */
        private Map<String, String> parseJsonBody(String json) {
            if (json == null || json.trim().isEmpty()) {
                return new LinkedHashMap<>();
            }
            
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

        /**
         * Extracts the current token from the request headers and stores it.
         */
        private void extractAndStoreToken() {
            // First try Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                this.currentToken = authHeader.substring(7);
                return;
            }

            // Try to extract from cookies - specifically look for auth_token
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if ("auth_token".equals(cookie.getName())) {
                        this.currentToken = cookie.getValue();
                        return;
                    }
                }
            }

            // Fallback: try to parse from Cookie header like original Yggdrasill
            String cookieHeader = request.getHeader("Cookie");
            if (cookieHeader != null && cookieHeader.contains("auth_token=")) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith("auth_token=")) {
                        this.currentToken = cookie.substring("auth_token=".length());
                        return;
                    }
                }
            }

            this.currentToken = null;
        }

        // Getter methods for compatibility
        public String getCurrentToken() { return currentToken; }
        public Map<String, String> getQueryParams() { return queryParams; }
        public Map<String, String> getBodyParams() { return bodyParams; }
        public Map<String, String> getPathParams() { return pathParams; }
        public Map<String, String> getHeaders() { return headers; }
        public HttpServletRequest getRequest() { return request; }
        public HttpServletResponse getResponse() { return response; }
        public String getRequestBody() { return requestBody; }
        public String getResourcesPath() { return resourcesPath; }

        public void addCustomHeader(String name, String value) {
            customResponseHeaders.put(name, value);
        }

        public String getHeader(String name) {
            return headers.get(name);
        }

        public String getClientIpAddress() {
            // Try X-Forwarded-For header first (for proxy scenarios)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                return xForwardedFor.split(",")[0].trim();
            }

            // Try X-Real-IP header
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            // Fallback to remote address
            return request.getRemoteAddr();
        }

        public boolean isHTMX() {
            return "true".equals(request.getHeader("HX-Request")) || 
                   request.getHeader("HX-Request") != null || 
                   request.getHeader("hx-request") != null;
        }

        public boolean isConnectionClosed() {
            return response.isCommitted();
        }

        // Response sending methods for use by controllers
        public void sendHTMLResponse(ResponseCode responseCode, String body) throws IOException {
            response.setStatus(responseCode.getCode());
            response.setContentType("text/html; charset=UTF-8");
            
            // Add custom headers
            customResponseHeaders.forEach((name, value) -> response.setHeader(name, value));
            
            // Process templates through Hermod
            String processedHTML = Hermod.processTemplate(body, queryParams, bodyParams);
            processedHTML += "\n\n"; // Prevent truncation
            
            response.getWriter().write(processedHTML);
            response.getWriter().flush();
        }

        public void sendJSONResponse(ResponseCode responseCode, String body) {
            try {
                response.setStatus(responseCode.getCode());
                response.setContentType("application/json; charset=UTF-8");
                
                // Add custom headers
                customResponseHeaders.forEach((name, value) -> response.setHeader(name, value));
                
                response.getWriter().write(body);
                response.getWriter().flush();
            } catch (IOException e) {
                Logger.error("Error sending JSON response: {}", e.getMessage());
                throw new SendRespondException("Error sending JSON response", e);
            }
        }

        public void sendBinaryResponse(ResponseCode responseCode, String contentType, byte[] body) {
            try {
                response.setStatus(responseCode.getCode());
                response.setContentType(contentType);
                
                // Add custom headers
                customResponseHeaders.forEach((name, value) -> response.setHeader(name, value));
                
                response.getOutputStream().write(body);
                response.getOutputStream().flush();
            } catch (IOException e) {
                Logger.error("Error sending binary response: {}", e.getMessage());
                throw new SendRespondException("Error sending binary response", e);
            }
        }
    }

    // Getter methods for compatibility
    public static int getCurrentConnections() {
        return currentConnections.get();
    }
} 