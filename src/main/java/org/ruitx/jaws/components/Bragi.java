package org.ruitx.jaws.components;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Part;
import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;
import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;

/**
 * Base controller class for all controllers.
 * Contains methods for sending responses to the client.
 * The requestContext contains the request information and provides response capabilities.
 */
public abstract class Bragi {
    private static final ThreadLocal<Yggdrasill.RequestContext> requestContext = new ThreadLocal<>();
    protected String bodyHtmlPath;

    public static ObjectMapper getMapper() {
        return Odin.getMapper();
    }

    /**
     * Encode an object to JSON.
     *
     * @param obj the object to encode.
     * @return the encoded JSON string.
     */
    public static String encode(Object obj) {
        try {
            return getMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new APIParsingException("Failed to encode object to JSON", e);
        }
    }

    /**
     * Set the body path for the current thread.
     *
     * @param bodyPath
     */
    private void setBodyPath(String bodyPath) {
        Hermod.setBodyPath(bodyPath);
    }

    /**
     * Set a template variable for the current request context.
     *
     * @param name  the variable name
     * @param value the variable value
     */
    protected void setTemplateVariable(String name, String value) {
        Hermod.setTemplateVariable(name, value);
    }

    /**
     * Updates the template variables using the provided context map. Each entry in
     * the context map is set as a template variable.
     *
     * @param context a map containing key-value pairs where the key represents the
     *                template variable name and the value represents the associated
     *                variable value to be set.
     */
    protected void setContext(Map<String, String> context) {
        for (Map.Entry<String, String> entry : context.entrySet()) {
            Hermod.setTemplateVariable(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get a template variable from the current request context.
     *
     * @param name the variable name
     * @return the variable value or null if not found
     */
    protected String getTemplateVariable(String name) {
        Object value = Hermod.getTemplateVariable(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Remove a template variable from the current request context.
     *
     * @param name the variable name
     */
    protected void removeTemplateVariable(String name) {
        Hermod.removeTemplateVariable(name);
    }

    /**
     * Gets the current authentication token.
     *
     * @return the current token, or null if none is set
     */
    protected String getCurrentToken() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null ? context.getCurrentToken() : null;
    }

    // Success response methods
    protected void sendSucessfulResponse(ResponseCode code) {
        sendJSONResponse(true, code, "Success", null);
    }

    protected void sendSucessfulResponse(ResponseCode code, Object data) {
        sendJSONResponse(true, code, "Success", data);
    }

    protected void sendSucessfulResponse(ResponseCode code, String info, Object data) {
        sendJSONResponse(true, code, info, data);
    }

    protected void sendSucessfulResponse(String code, Object data) {
        sendJSONResponse(true, ResponseCode.fromCodeAndMessage(code), "Success", data);
    }

    // Error response methods
    public void sendErrorResponse(ResponseCode code, String message) {
        sendJSONResponse(false, code, message, null);
    }

    public void sendErrorResponse(String code, String message) {
        sendJSONResponse(false, ResponseCode.valueOf(code), message, null);
    }

    /**
     * Send a JSON error response with data
     *
     * @param code    Error code
     * @param message Error message
     * @param data    Response data (can be null)
     */
    protected void sendErrorResponse(ResponseCode code, String message, Object data) {
        sendJSONResponse(false, code, message, data);
    }

    /**
     * Internal method to send JSON response
     */
    private void sendJSONResponse(boolean success, ResponseCode code, String info, Object data) {
        APIResponse<Object> response;
        try {
            if (success) {
                response = APIResponse.success(code.getCodeAndMessage(), info, data);
            } else {
                response = APIResponse.error(code.getCodeAndMessage(), info);
            }
            
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                context.sendJSONResponse(code, encode(response));
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Send an HTML response to the client.
     *
     * @param code
     * @param content
     */
    protected void sendHTMLResponse(ResponseCode code, String content) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                context.sendHTMLResponse(code, content);
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to send HTML response: {}", e.getMessage());
            throw new SendRespondException("Failed to send HTML response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    protected void sendHTMLResponse(String code, String content) {
        try {
            ResponseCode responseCode = ResponseCode.valueOf(code);
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                context.sendHTMLResponse(responseCode, content);
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to send HTML response: {}", e.getMessage());
            throw new SendRespondException("Failed to send HTML response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Get a path parameter from the request.
     *
     * @param name
     * @return
     */
    protected String getPathParam(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null ? context.getPathParams().get(name) : null;
    }

    /**
     * Get a query parameter from the request.
     *
     * @param name
     * @return
     */
    protected String getQueryParam(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.getQueryParams() != null ? context.getQueryParams().get(name) : null;
    }

    /**
     * Get a body parameter from the request.
     *
     * @param name
     * @return
     */
    protected String getBodyParam(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.getBodyParams() != null ? context.getBodyParams().get(name) : null;
    }

    /**
     * Get a multipart file from the request.
     *
     * @param name the name of the file input field
     * @return the Part object representing the uploaded file, or null if not found
     */
    protected Part getMultipartFile(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.getMultipartFiles() != null ? context.getMultipartFiles().get(name) : null;
    }

    /**
     * Get all multipart files from the request.
     *
     * @return a map of field names to Part objects
     */
    protected Map<String, Part> getMultipartFiles() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.getMultipartFiles() != null ? context.getMultipartFiles() : new HashMap<>();
    }

    /**
     * Check if the request is a multipart form data request.
     *
     * @return true if the request contains multipart data
     */
    protected boolean isMultipartRequest() {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context == null) return false;
        
        String contentType = context.getHeader("Content-Type");
        return contentType != null && contentType.contains("multipart/form-data");
    }

    /**
     * Check if the request is an HTMX request.
     *
     * @return
     */
    protected boolean isHTMX() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.isHTMX();
    }

    /**
     * Retrieves the IP address of the client making the request.
     *
     * @return the client's IP address as a String, or null if the address cannot be determined.
     */
    protected String getClientIpAddress() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null ? context.getClientIpAddress() : null;
    }

    /**
     * Cleanup the request context for the current thread.
     */
    protected void cleanup() {
        try {
            requestContext.remove();
            Hermod.clearTemplateVariables(); // Also clean up template variables
        } catch (Exception e) {
            JawsLogger.error("Error cleaning up request context: {}", e.getMessage());
        }
    }

    /**
     * Get the request context for the current thread.
     *
     * @return The request context for the current thread
     */
    public Yggdrasill.RequestContext getRequestContext() {
        return requestContext.get();
    }

    /**
     * Set the request context for the current thread.
     *
     * @param context
     */
    public void setRequestContext(Yggdrasill.RequestContext context) {
        requestContext.set(context);
        if (bodyHtmlPath != null) {
            setBodyPath(bodyHtmlPath);
        }
    }

    protected void addCustomHeader(String name, String value) {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context != null) {
            context.addCustomHeader(name, value);
        }
    }

    /**
     * Render a template with parameters replaced.
     *
     * @param templatePath The path to the template file
     * @return The rendered template with parameters replaced
     */
    protected String renderTemplate(String templatePath) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                return Hermod.renderTemplate(templatePath, context.getRequest(), context.getResponse());
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (IOException e) {
            JawsLogger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    public Map<String, String> getHeaders() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null ? context.getHeaders() : new HashMap<>();
    }

    /**
     * Assemble a full page by combining a base template with a partial template.
     *
     * @param baseTemplatePath    The path to the base template file
     * @param partialTemplatePath The path to the partial template file
     * @return The assembled page
     */
    protected String assemblePage(String baseTemplatePath, String partialTemplatePath) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                return Hermod.assemblePage(baseTemplatePath, partialTemplatePath, 
                                         context.getRequest(), context.getResponse());
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (IOException e) {
            JawsLogger.error("Failed to assemble page: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page", e);
        }
    }

    /**
     * Send a binary response to the client.
     *
     * @param code        the response code
     * @param contentType the content type of the response
     * @param content     the binary content
     */
    protected void sendBinaryResponse(ResponseCode code, String contentType, byte[] content) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                context.sendBinaryResponse(code, contentType, content);
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (Exception e) {
            JawsLogger.error("Failed to send binary response: {}", e.getMessage());
            throw new SendRespondException("Failed to send binary response", e);
        }
    }

    /**
     * Call an API endpoint and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param method       the HTTP method (GET, POST, PUT, etc.)
     * @param body         the request body for POST/PUT requests (can be null for GET)
     * @param responseType the Java type of the response.
     * @param headers      the HTTP headers to include in the request (can be null)
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, String body, JavaType responseType) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpoint)).header("accept", "*/*");

        Map<String, String> allHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();
        for (Map.Entry<String, String> header : allHeaders.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        switch (method) {
            case POST, PUT, PATCH -> requestBuilder
                    .method(method.toString(), HttpRequest.BodyPublishers.ofString(body != null ? body : ""))
                    .header(CONTENT_TYPE.getHeaderName(), "application/json");
            case DELETE -> requestBuilder.DELETE();
            default -> requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                JawsLogger.error("API request failed with status code: {}", response.statusCode());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned error status: " + response.statusCode()
                );
            }

            String contentType = response.headers().firstValue(CONTENT_TYPE.getHeaderName()).orElse("");
            if (!contentType.contains("application/json")) {
                JawsLogger.error("Unexpected content type: {}", contentType);
                JawsLogger.error("Response body: {}", response.body());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned non-JSON response"
                );
            }
            return parseResponse(response.body(), responseType);

        } catch (IOException | InterruptedException e) {
            JawsLogger.error("HTTP request failed: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to fetch data from API"
            );
        }
    }

    /**
     * Call an API endpoint and parse the response using a Class type.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param body          the request body for POST/PUT requests (can be null for GET)
     * @param responseClass the Class of the response.
     * @param headers       the HTTP headers to include in the request (can be null)
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, String body, Class<T> responseClass) {
        JavaType responseType = getMapper().getTypeFactory().constructType(responseClass);
        return callAPI(endpoint, method, headers, body, responseType);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param body          the request body object for POST/PUT requests
     * @param responseClass the Class of the response.
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, null, jsonBody, responseClass);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param method       the HTTP method (GET, POST, PUT, etc.)
     * @param body         the request body object for POST/PUT requests
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Object body, JavaType responseType) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, null, jsonBody, responseType);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param headers       the HTTP headers to include in the request
     * @param body          the request body object for POST/PUT requests
     * @param responseClass the Class of the response.
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, headers, jsonBody, responseClass);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param method       the HTTP method (GET, POST, PUT, etc.)
     * @param headers      the HTTP headers to include in the request
     * @param body         the request body object for POST/PUT requests
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, Object body, JavaType responseType) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, headers, jsonBody, responseType);
    }

    public <T> APIResponse<T> callAPI(String endpoint, JavaType type) {
        return callAPI(endpoint, RequestType.GET, null, null, type);
    }

    /**
     * Parse the response from the API call.
     *
     * @param response     the response from the API call.
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    private <T> APIResponse<T> parseResponse(String response, JavaType responseType) {
        try {
            return getMapper().readValue(response, responseType);
        } catch (JsonParseException e) {
            JawsLogger.error("Failed to parse API response as JSON: {}", e.getMessage());
            return APIResponse.error(ResponseCode.BAD_REQUEST.getCodeAndMessage(), "Invalid JSON response");
        } catch (Exception e) {
            JawsLogger.error("Failed to parse API response: {}", e.getMessage());
            return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Failed to parse response");
        }
    }
} 