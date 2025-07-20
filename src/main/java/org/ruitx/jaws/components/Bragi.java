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
import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.types.ParamType;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;

/**
 * Base controller class for all controllers.
 * Contains methods for sending responses to the client.
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
     * @param bodyPath the path to the body HTML file
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

    /**
     * Send a JSON response to the client.
     *
     * @param success indicates if the response is successful
     * @param code    the response code
     * @param message the response message
     * @param data    the response data
     */
    protected void sendJSONResponse(boolean success, ResponseCode code, String message, Object data) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                APIResponse<Object> response;
                if (success) {
                    response = APIResponse.success(code, message, data);
                } else {
                    response = APIResponse.error(code, message);
                }
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
     * @param code    the response code
     * @param content the HTML content to send
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

    /**
     * Send an HTML response to the client with a specific code and content.
     *
     * @param code    the response code as a string
     * @param content the HTML content to send
     */
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

    // ===== NEW RESPONSE METHODS =====

    /**
     * Send a success response with data.
     *
     * @param data the response data
     */
    protected void sendSuccess(Object data) {
        sendJSONResponse(true, ResponseCode.OK, "Success", data);
    }

    /**
     * Send a success response with custom code and data.
     *
     * @param code the response code
     * @param data the response data
     */
    protected void sendSuccess(ResponseCode code, Object data) {
        sendJSONResponse(true, code, "Success", data);
    }

    /**
     * Send a success response with custom code, message, and data.
     *
     * @param code    the response code
     * @param message the success message
     * @param data    the response data
     */
    protected void sendSuccess(ResponseCode code, String message, Object data) {
        sendJSONResponse(true, code, message, data);
    }

    /**
     * Send a success response with string code and data.
     *
     * @param code the response code as a string
     * @param data the response data
     */
    protected void sendSuccess(String code, Object data) {
        ResponseCode responseCode = ResponseCode.fromCodeAndMessage(code);
        sendJSONResponse(true, responseCode, "Success", data);
    }

    /**
     * Send an error response with code and message.
     *
     * @param code    the response code
     * @param message the error message
     */
    public void sendFail(ResponseCode code, String message) {
        sendJSONResponse(false, code, message, null);
    }

    /**
     * Send an error response with string code and message.
     *
     * @param code    the response code as a string
     * @param message the error message
     */
    public void sendFail(String code, String message) {
        ResponseCode responseCode = ResponseCode.fromCodeAndMessage(code);
        sendJSONResponse(false, responseCode, message, null);
    }

    /**
     * Send an HTML response with code and content.
     *
     * @param code    the response code
     * @param content the HTML content to send
     */
    protected void sendHTML(ResponseCode code, String content) {
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

    /**
     * Send an HTML response with default OK status.
     *
     * @param content the HTML content to send
     */
    protected void sendHTML(String content) {
        sendHTML(ResponseCode.OK, content);
    }

    // ===== NEW PARAMETER METHODS =====

    /**
     * Get a parameter from the request (prioritizes: path > query > body).
     *
     * @param name the parameter name
     * @return the parameter value or null if not found
     */
    protected String get(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context == null) return null;

        // Check path parameters first
        String value = context.getPathParams().get(name);
        if (value != null) return value;

        // Check query parameters
        value = context.getQueryParams().get(name);
        if (value != null) return value;

        // Check body parameters
        return context.getBodyParams().get(name);
    }

    /**
     * Get a parameter from a specific source.
     *
     * @param name the parameter name
     * @param type the parameter type
     * @return the parameter value or null if not found
     */
    protected String get(String name, ParamType type) {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context == null) return null;

        return switch (type) {
            case PATH -> context.getPathParams().get(name);
            case QUERY -> context.getQueryParams().get(name);
            case BODY -> context.getBodyParams().get(name);
        };
    }

    /**
     * Get a multipart file from the request.
     *
     * @param name the name of the file input field
     * @return the Part object representing the uploaded file, or null if not found
     */
    protected Part file(String name) {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null && context.getMultipartFiles() != null ? context.getMultipartFiles().get(name) : null;
    }

    /**
     * Get all multipart files from the request.
     *
     * @return a map of field names to Part objects
     */
    protected Map<String, Part> files() {
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
     * @return true if the request is an HTMX request, false otherwise
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
     * @param context the request context to set
     */
    public void setRequestContext(Yggdrasill.RequestContext context) {
        requestContext.set(context);
        if (bodyHtmlPath != null) {
            setBodyPath(bodyHtmlPath);
        }
    }

    /**
     * Add a custom header to the response.
     *
     * @param name  the name of the header
     * @param value the value of the header
     */
    protected void addCustomHeader(String name, String value) {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context != null) {
            context.addCustomHeader(name, value);
        }
    }

    /**
     * Process a template with the given context.
     *
     * @param templatePath    The path to the template file
     * @param templateContext The context to use for processing the template
     * @return The processed template as a String
     */
    protected String processTemplate(String templatePath, Context templateContext) throws IOException {
        Yggdrasill.RequestContext context = requestContext.get();
        if (context != null) {
            return Hermod.render(
                    templatePath,
                    context.getQueryParams(),
                    context.getBodyParams(),
                    context.getRequest(),
                    context.getResponse(),
                    templateContext);
        } else {
            throw new IllegalStateException("No request context available");
        }
    }

    /**
     * Get the headers from the current request context.
     *
     * @return a map of header names to values
     */
    public Map<String, String> getHeaders() {
        Yggdrasill.RequestContext context = requestContext.get();
        return context != null ? context.getHeaders() : new HashMap<>();
    }

    /**
     * Render a template without context.
     *
     * @param templatePath The path to the template file
     * @return The rendered template
     */
    protected String render(String templatePath) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                return Hermod.render(templatePath, context.getRequest(), context.getResponse());
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (IOException e) {
            JawsLogger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    /**
     * Render a template with context.
     *
     * @param templatePath The path to the template file
     * @param context      The context to use for rendering
     * @return The rendered template
     */
    protected String render(String templatePath, Context context) {
        try {
            Yggdrasill.RequestContext rqContext = requestContext.get();
            if (requestContext != null) {
                return Hermod.render(
                        templatePath,
                        rqContext.getQueryParams(),
                        rqContext.getBodyParams(),
                        rqContext.getRequest(),
                        rqContext.getResponse(),
                        context);
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (IOException e) {
            JawsLogger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    /**
     * Compose a page by combining base and partial templates.
     *
     * @param baseTemplate    The path to the base template
     * @param partialTemplate The path to the partial template
     * @return The composed page
     */
    protected String compose(String baseTemplate, String partialTemplate) {
        try {
            Yggdrasill.RequestContext context = requestContext.get();
            if (context != null) {
                return Hermod.composePage(baseTemplate, partialTemplate,
                        context.getRequest(), context.getResponse());
            } else {
                throw new IllegalStateException("No request context available");
            }
        } catch (IOException e) {
            JawsLogger.error("Failed to compose page: {}", e.getMessage());
            throw new SendRespondException("Failed to compose page", e);
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
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call
     * @param method        the HTTP method
     * @param body          the request body object
     * @param responseClass the Class of the response
     * @param <T>           the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, RequestType method, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callInternal(endpoint, method, null, jsonBody, responseClass);
    }

    /**
     * Call an API endpoint with headers and an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call
     * @param method        the HTTP method
     * @param headers       the HTTP headers to include in the request
     * @param body          the request body object
     * @param responseClass the Class of the response
     * @param <T>           the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, RequestType method, Map<String, String> headers, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callInternal(endpoint, method, headers, jsonBody, responseClass);
    }

    /**
     * Call an API endpoint with a GET request and parse the response.
     *
     * @param endpoint      the API endpoint to call
     * @param responseClass the Class of the response
     * @param <T>           the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, Class<T> responseClass) {
        return call(endpoint, RequestType.GET, null, responseClass);
    }

    /**
     * Call an API endpoint with a GET request and parse the response using JavaType.
     *
     * @param endpoint     the API endpoint to call
     * @param responseType the JavaType of the response
     * @param <T>          the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, JavaType responseType) {
        return call(endpoint, RequestType.GET, null, responseType);
    }

    /**
     * Call an API endpoint with an object body and parse the response using JavaType.
     *
     * @param endpoint     the API endpoint to call
     * @param method       the HTTP method
     * @param body         the request body object
     * @param responseType the JavaType of the response
     * @param <T>          the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, RequestType method, Object body, JavaType responseType) {
        String jsonBody = body != null ? encode(body) : null;
        return callInternal(endpoint, method, null, jsonBody, responseType);
    }

    /**
     * Call an API endpoint with headers and an object body and parse the response using JavaType.
     *
     * @param endpoint     the API endpoint to call
     * @param method       the HTTP method
     * @param headers      the HTTP headers to include in the request
     * @param body         the request body object
     * @param responseType the JavaType of the response
     * @param <T>          the type of the response
     * @return the parsed API response
     */
    public <T> APIResponse<T> call(String endpoint, RequestType method, Map<String, String> headers, Object body, JavaType responseType) {
        String jsonBody = body != null ? encode(body) : null;
        return callInternal(endpoint, method, headers, jsonBody, responseType);
    }

    /**
     * Internal method to make HTTP calls and parse responses.
     *
     * @param endpoint      the API endpoint to call
     * @param method        the HTTP method
     * @param headers       the HTTP headers to include in the request
     * @param body          the request body
     * @param responseClass the Class of the response
     * @param <T>           the type of the response
     * @return the parsed API response
     */
    private <T> APIResponse<T> callInternal(String endpoint, RequestType method, Map<String, String> headers, String body, Class<T> responseClass) {
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
            return parseResponse(response.body(), responseClass);

        } catch (IOException | InterruptedException e) {
            JawsLogger.error("HTTP request failed: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to fetch data from API"
            );
        }
    }

    /**
     * Internal method to make HTTP calls and parse responses using JavaType.
     *
     * @param endpoint     the API endpoint to call
     * @param method       the HTTP method
     * @param headers      the HTTP headers to include in the request
     * @param body         the request body
     * @param responseType the JavaType of the response
     * @param <T>          the type of the response
     * @return the parsed API response
     */
    private <T> APIResponse<T> callInternal(String endpoint, RequestType method, Map<String, String> headers, String body, JavaType responseType) {
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
     * Parse the response from the API call.
     *
     * @param response      the response from the API call
     * @param responseClass the Class of the response
     * @param <T>           the type of the response
     * @return the parsed API response
     */
    private <T> APIResponse<T> parseResponse(String response, Class<T> responseClass) {
        try {
            T data = getMapper().readValue(response, responseClass);
            return APIResponse.success(ResponseCode.OK.getCodeAndMessage(), data);
        } catch (JsonParseException e) {
            JawsLogger.error("Failed to parse API response as JSON: {}", e.getMessage());
            return APIResponse.error(ResponseCode.BAD_REQUEST.getCodeAndMessage(), "Invalid JSON response");
        } catch (Exception e) {
            JawsLogger.error("Failed to parse API response: {}", e.getMessage());
            return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Failed to parse response");
        }
    }

    /**
     * Parse the response from the API call using JavaType.
     *
     * @param response     the response from the API call
     * @param responseType the JavaType of the response
     * @param <T>          the type of the response
     * @return the parsed API response
     */
    private <T> APIResponse<T> parseResponse(String response, JavaType responseType) {
        try {
            T data = getMapper().readValue(response, responseType);
            return APIResponse.success(ResponseCode.OK.getCodeAndMessage(), data);
        } catch (JsonParseException e) {
            JawsLogger.error("Failed to parse API response as JSON: {}", e.getMessage());
            return APIResponse.error(ResponseCode.BAD_REQUEST.getCodeAndMessage(), "Invalid JSON response");
        } catch (Exception e) {
            JawsLogger.error("Failed to parse API response: {}", e.getMessage());
            return APIResponse.error(ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(), "Failed to parse response");
        }
    }
} 