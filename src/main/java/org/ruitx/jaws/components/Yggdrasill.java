package org.ruitx.jaws.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.DefaultHTML;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.JawsValidation;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
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

/**
 * Yggdrasill is the main HTTP server component
 * It integrates Jetty with JAWS' route system, middleware support,
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

            JawsLogger.info("Yggdrasill started on port {} with resources path: {}", port, resourcesPath);
            
        } catch (Exception e) {
            JawsLogger.error("Yggdrasill encountered an error: {}", e.getMessage(), e);
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
                JawsLogger.warn("Static resources path does not exist: {}", resourcePath);
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
            
            JawsLogger.info("Static file serving configured for path: {}", resourcePath);
        } catch (Exception e) {
            JawsLogger.error("Failed to setup static file serving: {}", e.getMessage());
        }
    }

    /**
     * Sets up dynamic route handling using a custom servlet.
     */
    private void setupDynamicRouteHandling(ServletContextHandler context) {
        // Create and add the main request handling servlet
        ServletHolder jawsServlet = new ServletHolder("jaws", new JawsServlet());
        
        // Configure multipart support for file uploads
        MultipartConfigElement multipartConfig = new MultipartConfigElement(
            "uploads/temp",  // temp file directory
            10 * 1024 * 1024,  // max file size (10MB)
            20 * 1024 * 1024,  // max request size (20MB)
            5 * 1024 * 1024    // file size threshold for writing to disk (5MB)
        );
        jawsServlet.getRegistration().setMultipartConfig(multipartConfig);
        
        context.addServlet(jawsServlet, "/*");
    }

    /**
     * Shuts down the server.
     */
    public void shutdown() {
        JawsLogger.info("Yggdrasill shutdown initiated...");
        if (server != null) {
            try {
                JawsLogger.info("Shutting down Yggdrasill...");
                server.stop();
                server.join();
            } catch (Exception e) {
                JawsLogger.error("Error shutting down Yggdrasill: {}", e.getMessage());
            }
        }
    }

    /**
     * Custom servlet that handles all dynamic requests and integrates with the JAWS route system.
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
                Bifrost middlewareChain = new Bifrost(middlewares, context);
                boolean continueProcessing = middlewareChain.execute();

                if (!continueProcessing) {
                    // Middleware stopped the request
                    return;
                }

                // Process the request using integrated JAWS logic
                processRequest(context);

            } catch (Exception e) {
                JawsLogger.error("Error processing request: {}", e.getMessage(), e);
                try {
                    if (!response.isCommitted()) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentType("text/html; charset=UTF-8");
                        response.getWriter().write("Internal Server Error");
                        response.getWriter().flush();
                    }
                } catch (IOException ioException) {
                    JawsLogger.error("Error sending error response: {}", ioException.getMessage());
                } finally {
                    currentConnections.decrementAndGet();
                }
            }
        }

        /**
         * Processes the request using JAWS' route system.
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
                JawsLogger.error("Invalid method: {}", method);
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
         * Uses a two-pass approach: first checks for exact static matches, then parameterized routes.
         * This ensures that static routes like "/api/endpoint/test" take precedence over 
         * parameterized routes like "/api/endpoint/:id".
         */
        private boolean findDynamicRouteFor(RequestContext context, String endPoint, RequestType method) {
            try {
                List<Method> allRoutes = Njord.getInstance().getAllRoutes();
                
                // PASS 1: Check for exact static matches (routes without parameters)
                for (Method routeMethod : allRoutes) {
                    if (routeMethod.isAnnotationPresent(Route.class)) {
                        Route route = routeMethod.getAnnotation(Route.class);
                        
                        // Check if the route method matches
                        if (!method.equals(route.method())) {
                            continue;
                        }
                        
                        // Only check routes with NO parameters (static routes)
                        if (!route.endpoint().contains(":")) {
                            if (route.endpoint().equals(endPoint)) {
                                JawsLogger.debug("Static route matched: {} {} -> {}.{}", 
                                    route.method(), route.endpoint(), 
                                    routeMethod.getDeclaringClass().getSimpleName(), routeMethod.getName());
                                
                                // No path parameters for static routes
                                context.pathParams = new LinkedHashMap<>();
                                
                                // Get controller instance and invoke the route method
                                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                                Object controllerInstance = Njord.getInstance().getControllerInstance(controllerName);
                                if (controllerInstance == null) {
                                    JawsLogger.error("Controller instance not found: {}", controllerName);
                                    return false;
                                }
                                
                                return invokeRouteMethod(context, routeMethod, controllerInstance);
                            }
                        }
                    }
                }
                
                // PASS 2: Check for parameterized routes (routes with parameters)
                for (Method routeMethod : allRoutes) {
                    if (routeMethod.isAnnotationPresent(Route.class)) {
                        Route route = routeMethod.getAnnotation(Route.class);
                        
                        // Check if the route method matches
                        if (!method.equals(route.method())) {
                            continue;
                        }
                        
                        // Only check routes WITH parameters (parameterized routes)
                        if (route.endpoint().contains(":")) {
                            Map<String, String> pathParams = matchRoutePattern(route.endpoint(), endPoint);
                            if (pathParams != null) {
                                JawsLogger.debug("Parameterized route matched: {} {} -> {}.{}", 
                                    route.method(), route.endpoint(), 
                                    routeMethod.getDeclaringClass().getSimpleName(), routeMethod.getName());
                                
                                // Store path parameters
                                context.pathParams = pathParams;
                                
                                // Get controller instance and invoke the route method
                                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                                Object controllerInstance = Njord.getInstance().getControllerInstance(controllerName);
                                if (controllerInstance == null) {
                                    JawsLogger.error("Controller instance not found: {}", controllerName);
                                    return false;
                                }
                                
                                return invokeRouteMethod(context, routeMethod, controllerInstance);
                            }
                        }
                    }
                }
                
                return false;
            } catch (Exception e) {
                JawsLogger.error("Error finding dynamic route: {}", e.getMessage(), e);
                return false;
            }
        }

        /**
         * Invokes the route method with proper parameter handling and validation.
         */
        private boolean invokeRouteMethod(RequestContext context, Method routeMethod, Object controllerInstance) {
            try {
                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                JawsLogger.debug("Invoking route method {} in controller {}", routeMethod.getName(), controllerName);

                // Set request context if the controller extends Bragi
                if (controllerInstance instanceof Bragi bragi) {
                    bragi.setRequestContext(context);
                    JawsLogger.debug("Set request context for controller {}", controllerName);
                }

                // Synchronize the controller instance to prevent concurrent access
                // This should not be needed anymore, Jetty and Bragi are thread safe (I think and hope)
                //synchronized (controllerInstance) {
                    try {
                        // Get method parameters
                        Class<?>[] parameterTypes = routeMethod.getParameterTypes();
                        Object[] parameters = new Object[parameterTypes.length];

                        // Handle request body parameter assignment
                        if (parameterTypes.length > 0) {
                            String contentType = context.getHeader(CONTENT_TYPE.getHeaderName());
                            boolean isMultipartRequest = contentType != null && contentType.contains("multipart/form-data");
                            
                            if (isMultipartRequest) {
                                // For multipart requests, we still need to handle deserialization here
                                // as the middleware skips multipart validation
                                String requestBodyTrimmed = context.requestBody != null ? context.requestBody.trim() : "";
                                if (!requestBodyTrimmed.isEmpty()) {
                                    try {
                                        ObjectMapper mapper = Odin.getMapper();
                                        parameters[0] = mapper.readValue(requestBodyTrimmed, parameterTypes[0]);
                                        
                                        // Validate the deserialized object using Jakarta Bean Validation
                                        APIResponse<String> validationError = JawsValidation.validate(parameters[0]);
                                        if (validationError != null) {
                                            sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(validationError));
                                            return true;
                                        }
                                    } catch (Exception e) {
                                        APIResponse<String> response = APIResponse.error(
                                                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                                "Error processing multipart request body: " + e.getMessage()
                                        );
                                        sendJSONResponse(context, ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                        return true;
                                    }
                                }
                            } else {
                                // For non-multipart requests, use the validated object from middleware
                                Object validatedRequestBody = context.getValidatedRequestBody();
                                if (validatedRequestBody != null) {
                                    parameters[0] = validatedRequestBody;
                                    JawsLogger.debug("Using validated request body from middleware for controller method");
                                }
                            }
                        }

                        // Invoke the method
                        routeMethod.invoke(controllerInstance, parameters);
                    } catch (Exception e) {
                        handleControllerException(context, e, controllerInstance, routeMethod.getName());
                        return true;
                    }
                //}

                // Cleanup if the controller extends Bragi
                if (controllerInstance instanceof Bragi) {
                    ((Bragi) controllerInstance).cleanup();
                }

                return true;
            } catch (Exception e) {
                JawsLogger.error("Failed to handle request: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                return false;
            }
        }

        /**
         * Handles exceptions that occur during controller execution.
         */
        private void handleControllerException(RequestContext context, Exception e, Object controllerInstance, String methodName) {
            String controllerName = controllerInstance.getClass().getSimpleName();
            JawsLogger.error("Controller execution failed in {}.{}: {} - {}",
                    controllerName, methodName, e.getClass().getSimpleName(), e.getMessage());

            if (e.getCause() != null) {
                JawsLogger.error("Caused by: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
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
                    JawsLogger.error("Failed to send error response: {}", ex.getMessage());
                }
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
                JawsLogger.error("Error sending JSON response: {}", e.getMessage());
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
            String processedHTML = Hermod.processTemplate(body, context.queryParams, context.bodyParams, context.request, context.response);
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
                JawsLogger.error("Error sending binary response: {}", e.getMessage());
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
                // get custom 404 page from application.properties if its set
                String custom404Page = ApplicationConfig.CUSTOM_PAGE_PATH_404;
                if (custom404Page != null && !custom404Page.isEmpty()) {
                    // Extract only the filename since Hermod already has WWW_PATH as prefix
                    // Convert full path like "src/main/resources/www/404.html" to just "404.html"
                    String templateName = custom404Page;
                    if (custom404Page.contains("/")) {
                        templateName = custom404Page.substring(custom404Page.lastIndexOf("/") + 1);
                    }
                    
                    String processedHTML = Hermod.processTemplate(
                        templateName, 
                        context.queryParams, 
                        context.bodyParams, 
                        context.request, 
                        context.response
                    );
                    context.response.getWriter().write(processedHTML);
                } else {
                    context.response.getWriter().write(DefaultHTML.HTML_404_NOT_FOUND);
                }
                context.response.getWriter().flush();
            } catch (IOException e) {
                JawsLogger.error("Error sending 404 response: {}", e.getMessage());
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
                    
                    // Check for custom 401 page
                    String custom401Page = ApplicationConfig.CUSTOM_PAGE_PATH_401;
                    if (custom401Page != null && !custom401Page.isEmpty()) {
                        try {
                            // Extract only the filename since Hermod already has WWW_PATH as prefix
                            // Convert full path like "src/main/resources/www/401.html" to just "401.html"
                            String templateName = custom401Page;
                            if (custom401Page.contains("/")) {
                                templateName = custom401Page.substring(custom401Page.lastIndexOf("/") + 1);
                            }
                            
                            String processedHTML = Hermod.processTemplate(
                                templateName,
                                context.queryParams,
                                context.bodyParams,
                                context.request,
                                context.response
                            );
                            context.response.getWriter().write(processedHTML);
                        } catch (Exception e) {
                            JawsLogger.error("Error processing custom 401 template: {}", e.getMessage());
                            // Fall back to default
                            context.response.getWriter().write(HTML_401_UNAUTHORIZED);
                        }
                    } else {
                        context.response.getWriter().write(HTML_401_UNAUTHORIZED);
                    }
                }
                
                context.response.getWriter().flush();
            } catch (Exception e) {
                JawsLogger.error("Error sending unauthorized response: {}", e.getMessage());
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
                // Get relative path from the resources directory for Thymeleaf
                Path resourcesPath = Paths.get(context.resourcesPath);
                String relativePath = resourcesPath.relativize(filePath).toString();
                
                // Process HTML templates using the relative file path
                String processedHTML = Hermod.processTemplate(
                    relativePath, 
                    context.queryParams, 
                    context.bodyParams,
                    context.request,
                    context.response
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
        private Map<String, Part> multipartFiles = new LinkedHashMap<>();
        private String currentToken;
        private String requestBody;
        private Object validatedRequestBody;

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
            
            // Read request body or multipart data
            readRequestData();
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
        private void readRequestData() {
            try {
                String contentType = request.getContentType();
                
                // For multipart requests, do NOT read the body as text
                // The multipart data should only be accessed through request.getParts()
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    requestBody = ""; // Set empty for multipart requests
                    multipartFiles = extractMultipartFiles(request);
                } else if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                    // For form-encoded requests, be more careful with stream reading
                    // This prevents the STREAMED error that occurs when the servlet container
                    // expects to handle form data but we consume the stream
                    try {
                        requestBody = request.getReader().lines().collect(Collectors.joining("\n"));
                        bodyParams = extractBodyParameters(requestBody);
                    } catch (Exception e) {
                        // If reading fails, set empty values to prevent further issues
                        JawsLogger.debug("Could not read form-encoded request body: {}", e.getMessage());
                        requestBody = "";
                        bodyParams = new LinkedHashMap<>();
                    }
                } else {
                    // For non-multipart, non-form requests (like JSON), read the body as text
                    requestBody = request.getReader().lines().collect(Collectors.joining("\n"));
                    
                    // Extract body parameters if it's JSON
                    if (contentType != null && contentType.contains("application/json")) {
                        bodyParams = parseJsonBody(requestBody);
                    }
                }
            } catch (IOException e) {
                JawsLogger.error("Error reading request body: {}", e.getMessage());
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
                JawsLogger.error("Failed to parse JSON body: {}", e.getMessage());
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

        /**
         * Extracts multipart files from the request.
         */
        private Map<String, Part> extractMultipartFiles(HttpServletRequest request) {
            Map<String, Part> files = new LinkedHashMap<>();
            try {
                if (request.getContentType() != null && request.getContentType().contains("multipart/form-data")) {
                    for (Part part : request.getParts()) {
                        if (part.getSubmittedFileName() != null) {
                            // This is a file upload
                            files.put(part.getName(), part);
                        } else {
                            // This is a form field - add to bodyParams
                            try (Scanner scanner = new Scanner(part.getInputStream(), StandardCharsets.UTF_8)) {
                                String value = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                                this.bodyParams.put(part.getName(), value);
                            }
                        }
                    }
                }
            } catch (IOException | ServletException e) {
                JawsLogger.error("Error extracting multipart files: {}", e.getMessage());
            }
            return files;
        }

        // Getter methods 
        public String getCurrentToken() { return currentToken; }
        public Map<String, String> getQueryParams() { return queryParams; }
        public Map<String, String> getBodyParams() { return bodyParams; }
        public Map<String, String> getPathParams() { return pathParams; }
        public Map<String, Part> getMultipartFiles() { return multipartFiles; }
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

        public Object getValidatedRequestBody() {
            return validatedRequestBody;
        }

        public void setValidatedRequestBody(Object validatedRequestBody) {
            this.validatedRequestBody = validatedRequestBody;
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
            String processedHTML = Hermod.processTemplate(body, queryParams, bodyParams, request, response);
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
                JawsLogger.error("Error sending JSON response: {}", e.getMessage());
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
                JawsLogger.error("Error sending binary response: {}", e.getMessage());
                throw new SendRespondException("Error sending binary response", e);
            }
        }
    }

    // Getter methods for compatibility
    public static int getCurrentConnections() {
        return currentConnections.get();
    }
} 