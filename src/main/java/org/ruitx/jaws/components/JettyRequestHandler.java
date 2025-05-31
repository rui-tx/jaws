package org.ruitx.jaws.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.exceptions.ProcessRequestException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.tinylog.Logger;
import org.ruitx.jaws.utils.ValidationUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.ruitx.jaws.strings.DefaultHTML.*;
import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;
import static org.ruitx.jaws.strings.RequestType.*;
import static org.ruitx.jaws.strings.ResponseCode.*;

/**
 * JettyRequestHandler bridges Jetty's servlet API with JAWS' existing request handling system.
 * This class maintains compatibility with the existing Bragi controller system while
 * using Jetty as the underlying HTTP server.
 */
public class JettyRequestHandler {

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

    /**
     * Constructs a JettyRequestHandler for the specified Jetty request/response and resource path.
     *
     * @param request        the Jetty HTTP request
     * @param response       the Jetty HTTP response
     * @param resourcesPath  the path to static resources
     */
    public JettyRequestHandler(HttpServletRequest request, HttpServletResponse response, String resourcesPath) {
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
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            try {
                // Look for auth_token in the cookie header
                if (cookieHeader.contains("auth_token=")) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        cookie = cookie.trim();
                        if (cookie.startsWith("auth_token=")) {
                            String[] parts = cookie.split("=", 2);
                            if (parts.length == 2) {
                                this.currentToken = parts[1];
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logger.debug("Error parsing cookie header: {}", e.getMessage());
            }
        }
    }

    /**
     * Finds a dynamic route for the given endpoint and HTTP method.
     *
     * @param endPoint the endpoint to match.
     * @param method   the HTTP method (GET, POST, etc.).
     * @return true if a route is found and invoked, false otherwise.
     */
    public boolean findDynamicRouteFor(String endPoint, RequestType method) {
        // Check if we have a static route match first
        Method routeMethod = Njord.getInstance().getRoute(endPoint, method);
        
        if (routeMethod != null) {
            return invokeRouteMethod(routeMethod);
        }

        // If no direct match, search through all routes for dynamic matches
        for (Method route : Njord.getInstance().getAllRoutes()) {
            if (route.isAnnotationPresent(Route.class)) {
                Route routeAnnotation = route.getAnnotation(Route.class);
                String routePattern = routeAnnotation.endpoint();
                
                // Check if the route pattern matches the endpoint, allowing dynamic parameters
                pathParams = matchRoutePattern(routePattern, endPoint);
                if (pathParams != null && routeAnnotation.method() == method) {
                    return invokeRouteMethod(route);
                }
            }
        }

        return false;
    }

    /**
     * Invokes the method corresponding to a dynamic route.
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
                // Create a compatible RequestHandler wrapper
                Yggdrasill.RequestHandler compatibleHandler = createCompatibleRequestHandler();
                bragi.setRequestHandler(compatibleHandler);
                Logger.debug("Set request handler for controller {}", controllerName);
            }

            // Synchronize the controller instance to prevent concurrent access
            synchronized (controllerInstance) {
                try {
                    // Get method parameters
                    Class<?>[] parameterTypes = routeMethod.getParameterTypes();
                    Object[] parameters = new Object[parameterTypes.length];

                    // Handle request body deserialization if needed
                    if (parameterTypes.length > 0) {
                        String contentType = headers.get(CONTENT_TYPE.getHeaderName());
                        String requestBodyTrimmed = requestBody != null ? requestBody.trim() : "";
                        
                        // For POST/PUT/PATCH requests that expect a request body parameter,
                        // we need to ensure proper JSON content type and non-empty body
                        String httpMethod = request.getMethod().toUpperCase();
                        boolean expectsRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT") || httpMethod.equals("PATCH");
                        
                        if (expectsRequestBody) {
                            // Check for missing Content-Type header
                            if (contentType == null || !contentType.contains("application/json")) {
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Content-Type header must be 'application/json' for " + httpMethod + " requests"
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }
                            
                            // Check for empty request body
                            if (requestBodyTrimmed.isEmpty()) {
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Request body is required for " + httpMethod + " requests. Please provide a valid JSON body."
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
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
                                    sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(validationError));
                                    return true;
                                }
                            } catch (com.fasterxml.jackson.core.JsonParseException e) {
                                APIResponse<String> response = APIResponse.error(
                                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                        "Invalid JSON format: " + e.getOriginalMessage()
                                );
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
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
                                    sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                    return true;
                                } else {
                                    APIResponse<String> response = APIResponse.error(
                                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                            "JSON mapping error: " + (originalMessage != null ? originalMessage : e.getMessage())
                                    );
                                    sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
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
                                sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                                return true;
                            }
                        } else if (parameterTypes.length > 0 && expectsRequestBody) {
                            // If we reach here, it means we have parameters expected but no body was provided
                            // This shouldn't happen due to the checks above, but let's be safe
                            APIResponse<String> response = APIResponse.error(
                                    ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                    "Request body is required but was not provided"
                            );
                            sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
                            return true;
                        }
                    }

                    // Invoke the method
                    routeMethod.invoke(controllerInstance, parameters);
                } catch (Exception e) {
                    handleControllerException(e, controllerInstance, routeMethod.getName());
                    return true;
                }
            }

            // Cleanup if the controller extends BaseController
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
     * Validates request fields for JSON body parameters.
     */
    private void validateRequestFields(Method routeMethod, JsonNode root, Class<?> paramType) throws Exception {
        // For now, we'll use a simplified validation that just checks if the JSON is structurally valid
        // and let the business logic in the service layer handle specific field validation
        
        // Only check for completely unexpected fields that don't exist in the class
        List<String> expectedFields = new ArrayList<>();
        for (Field field : paramType.getDeclaredFields()) {
            expectedFields.add(field.getName());
        }

        List<String> extraFields = new ArrayList<>();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!expectedFields.contains(field)) {
                extraFields.add(field);
            }
        }

        // Only reject requests with completely unexpected fields
        if (!extraFields.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Unexpected fields found: ").append(String.join(", ", extraFields));
            errorMessage.append(". Accepted fields are: ").append(String.join(", ", expectedFields));

            APIResponse<String> response = APIResponse.error(
                    ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                    errorMessage.toString()
            );
            sendJSONResponse(ResponseCode.BAD_REQUEST, Bragi.encode(response));
            throw new IllegalArgumentException("Validation failed");
        }
        
        // Let Jackson deserialize and the service layer will handle business validation
        // This allows for proper null handling and business-specific required field validation
    }

    /**
     * Handles exceptions that occur during controller execution.
     */
    private void handleControllerException(Exception e, Object controllerInstance, String methodName) {
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
                sendJSONResponse(ResponseCode.INTERNAL_SERVER_ERROR, Bragi.encode(response));
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
     * Creates a compatible RequestHandler wrapper for existing Bragi controllers.
     */
    private Yggdrasill.RequestHandler createCompatibleRequestHandler() {
        // This is a bridge pattern - we need to create a wrapper that implements
        // the same interface as the original RequestHandler but delegates to JettyRequestHandler
        return new RequestHandlerBridge(this);
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

    // Response sending methods - delegate to Jetty response

    /**
     * Sends an HTML response with the specified response code and body content.
     */
    public void sendHTMLResponse(ResponseCode responseCode, String body) throws IOException {
        String parsedHTML = Hermod.processTemplate(body);
        parsedHTML += "\n\n";
        
        response.setStatus(responseCode.getCode());
        response.setContentType("text/html");
        addCustomHeaders();
        response.getWriter().write(parsedHTML);
        response.getWriter().flush();
    }

    /**
     * Sends a JSON response with the specified response code and body content.
     */
    public void sendJSONResponse(ResponseCode responseCode, String body) {
        try {
            response.setStatus(responseCode.getCode());
            response.setContentType("application/json");
            addCustomHeaders();
            response.getWriter().write(body);
            response.getWriter().flush();
        } catch (IOException e) {
            throw new SendRespondException("Error sending JSON response", e);
        }
    }

    /**
     * Sends a binary response with the specified response code and body content.
     */
    public void sendBinaryResponse(ResponseCode responseCode, String contentType, byte[] body) {
        try {
            response.setStatus(responseCode.getCode());
            response.setContentType(contentType);
            addCustomHeaders();
            response.getOutputStream().write(body);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new SendRespondException("Error sending binary response", e);
        }
    }

    /**
     * Sends a 404 Not Found response.
     */
    public void sendNotFoundResponse() {
        try {
            byte[] content;
            Path path = Paths.get(ApplicationConfig.CUSTOM_PAGE_PATH_404);
            if (Files.exists(path)) {
                content = Files.readAllBytes(path);
            } else {
                Logger.warn("Could not find 404 page, using hard coded default");
                content = HTML_404_NOT_FOUND.getBytes();
            }
            
            response.setStatus(NOT_FOUND.getCode());
            response.setContentType("text/html");
            addCustomHeaders();
            response.getOutputStream().write(content);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new ProcessRequestException("Error sending 404 response", e);
        }
    }

    /**
     * Sends a 401 Unauthorized response.
     */
    private void sendUnauthorizedResponse(ResponseType responseType) {
        sendUnauthorizedHTMLResponse();
    }

    /**
     * Sends a 401 Unauthorized HTML response.
     */
    public void sendUnauthorizedHTMLResponse() {
        try {
            String content;
            if (isHTMX()) {
                response.setStatus(OK.getCode());
                response.setContentType("text/html; charset=UTF-8");
                addCustomHeaders();
                response.getWriter().write(HTML_401_UNAUTHORIZED_HTMX);
                response.getWriter().flush();
                return;
            }

            Path path = Paths.get(ApplicationConfig.CUSTOM_PAGE_PATH_401);
            if (Files.exists(path)) {
                content = Files.readString(path);
            } else {
                Logger.warn("Could not find 401 page, using hard coded default");
                content = HTML_401_UNAUTHORIZED;
            }

            response.setStatus(UNAUTHORIZED.getCode());
            response.setContentType("text/html; charset=UTF-8");
            addCustomHeaders();
            response.getWriter().write(content);
            response.getWriter().flush();
        } catch (IOException e) {
            throw new ProcessRequestException("Error sending 401 response", e);
        }
    }

    /**
     * Adds custom headers to the response.
     */
    private void addCustomHeaders() {
        for (Map.Entry<String, String> entry : customResponseHeaders.entrySet()) {
            response.setHeader(entry.getKey(), entry.getValue());
        }
    }

    // Getter methods for compatibility with existing system

    public String getCurrentToken() {
        return this.currentToken;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getBodyParams() {
        return bodyParams;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public void addCustomHeader(String name, String value) {
        customResponseHeaders.put(name, value);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getClientIpAddress() {
        // Check various headers for client IP when behind proxy
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (proxyClientIp != null && !proxyClientIp.isEmpty()) {
            return proxyClientIp;
        }

        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty()) {
            return wlProxyClientIp;
        }

        return request.getRemoteAddr();
    }

    public boolean isHTMX() {
        return request.getHeader("HX-Request") != null || request.getHeader("hx-request") != null;
    }

    public boolean isConnectionClosed() {
        // In Jetty context, we can't directly check if connection is closed
        // This is handled by Jetty itself
        return false;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getResourcesPath() {
        return resourcesPath;
    }
} 