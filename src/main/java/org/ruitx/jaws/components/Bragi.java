package org.ruitx.jaws.components;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.tinylog.Logger;

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
 * The most important object is the requestHandler, that contains the request information.
 */
public abstract class Bragi {
    private static final ThreadLocal<Yggdrasill.RequestHandler> requestHandler = new ThreadLocal<>();
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
        return Hermod.getTemplateVariable(name);
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
     * Retrieves the current token by attempting to fetch it from cookies.
     *
     * @return the current token as a String if present in cookies,
     * or null if no token is available.
     */
    protected String getCurrentToken() {
        return getRequestHandler().getCurrentToken() == null ? "" : getRequestHandler().getCurrentToken();
    }

    /**
     * Send a JSON success response with no data
     *
     * @param code Response code
     */
    protected void sendSucessfulResponse(ResponseCode code) {
        try {
            boolean isSuccess = code.toString().startsWith("2"); // 2xx codes are success
            sendJSONResponse(isSuccess, code, null, null);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Send a JSON success response with data
     *
     * @param code Response code
     * @param data Response data (can be null)
     */
    protected void sendSucessfulResponse(ResponseCode code, Object data) {
        try {
            APIResponse<?> response = APIResponse.success(code.getCodeAndMessage(), data);
            requestHandler.get().sendJSONResponse(code, encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Sends a successful response with the provided response code and data.
     *
     * @param code The response code represented as a string.
     * @param data The data to be included in the response.
     */
    protected void sendSucessfulResponse(String code, Object data) {
        sendSucessfulResponse(ResponseCode.fromCodeAndMessage(code), null, data);
    }

    /**
     * Sends a successful response with the specified response code and message.
     *
     * @param code    the response code indicating the status of the operation
     * @param message the message providing additional information about the response
     */
    protected void sendSucessfulResponse(String code, String message) {
        sendSucessfulResponse(ResponseCode.fromCodeAndMessage(code), message, null);
    }

    /**
     * Send a JSON success response with data and info message
     *
     * @param code Response code
     * @param info Info message
     * @param data Response data (can be null)
     */
    protected void sendSucessfulResponse(ResponseCode code, String info, Object data) {
        try {
            APIResponse<?> response = APIResponse.success(code.getCodeAndMessage(), info, data);
            requestHandler.get().sendJSONResponse(code, encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Send a JSON error response
     *
     * @param code    Error code
     * @param message Error message
     */
    protected void sendErrorResponse(ResponseCode code, String message) {
        try {
            APIResponse<?> response = APIResponse.error(code.getCodeAndMessage(), message);
            requestHandler.get().sendJSONResponse(code, encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    /**
     * Send a JSON error response with a code and message
     * The code should be like this a string like this "200 OK"
     *
     * @param code    Error code
     * @param message Error message
     */
    protected void sendErrorResponse(String code, String message) {
        sendJSONResponse(false, ResponseCode.fromCodeAndMessage(code), message, null);
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
        try {
            APIResponse<Object> response = APIResponse.success(code.getCodeAndMessage(), info, data);
            requestHandler.get().sendJSONResponse(code, encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
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
            requestHandler.get().sendHTMLResponse(code, content);
        } catch (Exception e) {
            Logger.error("Failed to send HTML response: {}", e.getMessage());
            throw new SendRespondException("Failed to send HTML response", e);
        } finally {
            // Clean up template variables
            Hermod.clearTemplateVariables();
        }
    }

    protected void sendHTMLResponse(String code, String content) {
        try {
            requestHandler.get().sendHTMLResponse(ResponseCode.valueOf(code), content);
        } catch (Exception e) {
            Logger.error("Failed to send HTML response: {}", e.getMessage());
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
        return requestHandler.get().getPathParams().get(name);
    }

    /**
     * Get a query parameter from the request.
     *
     * @param name
     * @return
     */
    protected String getQueryParam(String name) {
        return requestHandler.get().getQueryParams() != null ? requestHandler.get().getQueryParams().get(name) : null;
    }

    /**
     * Get a body parameter from the request.
     *
     * @param name
     * @return
     */
    protected String getBodyParam(String name) {
        return requestHandler.get().getBodyParams() != null ? requestHandler.get().getBodyParams().get(name) : null;
    }

    /**
     * Check if the request is an HTMX request.
     *
     * @return
     */
    protected boolean isHTMX() {
        return requestHandler.get().isHTMX();
    }

    /**
     * Retrieves the IP address of the client making the request.
     *
     * @return the client's IP address as a String, or null if the address cannot be determined.
     */
    protected String getClientIpAddress() {
        return requestHandler.get().getClientIpAddress();
    }

    /**
     * Cleanup the request handler for the current thread.
     */
    protected void cleanup() {
        try {
            requestHandler.remove();
            Hermod.clearTemplateVariables(); // Also clean up template variables
        } catch (Exception e) {
            Logger.error("Error cleaning up request handler: {}", e.getMessage());
        }
    }


    /**
     * Get the request handler for the current thread.
     *
     * @return The request handler for the current thread
     */
    public Yggdrasill.RequestHandler getRequestHandler() {
        return requestHandler.get();
    }

    /**
     * Set the request handler and body path for the current thread.
     *
     * @param handler
     */
    public void setRequestHandler(Yggdrasill.RequestHandler handler) {
        requestHandler.set(handler);
        if (bodyHtmlPath != null) {
            setBodyPath(bodyHtmlPath);
        }
    }

    protected void addCustomHeader(String name, String value) {
        requestHandler.get().addCustomHeader(name, value);
    }

    /**
     * Render a template file with parameters.
     *
     * @param templatePath The path to the template file
     * @param params       The parameters to be used in the template
     * @return The rendered template with parameters replaced
     */
    protected String renderTemplate(String templatePath, Map<String, String> params) {
        try {
            String templateHtml = new String(Files.readAllBytes(Path.of(WWW_PATH + templatePath)));
            return Hermod.processTemplate(templateHtml, params, null);
        } catch (IOException e) {
            Logger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    public Map<String, String> getHeaders() {
        return requestHandler.get().getHeaders();
    }

//    public Optional<String> getCookieToken() {
//        String cookieHeader = getHeaders().entrySet().stream()
//                .filter(entry -> "Cookie".equalsIgnoreCase(entry.getKey()))
//                .map(Map.Entry::getValue)
//                .findFirst()
//                .orElse(null);
//
//        if (cookieHeader != null) {
//            return Optional.of(cookieHeader.split(";")[0].split("=")[1]);
//        }
//
//        return Optional.empty();
//    }

    /**
     * Render a template file without parameters.
     *
     * @param templatePath The path to the template file
     * @return The rendered template
     */
    protected String renderTemplate(String templatePath) {
        return renderTemplate(templatePath, new HashMap<>());
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
            return Hermod.assemblePage(baseTemplatePath, partialTemplatePath);
        } catch (IOException e) {
            Logger.error("Failed to assemble page: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page", e);
        }
    }

    /**
     * Assemble a full page by combining a base template with raw content.
     *
     * @param baseTemplatePath The path to the base template file
     * @param content          The raw content to insert
     * @return The assembled page
     */
    protected String assemblePageWithContent(String baseTemplatePath, String content) {
        try {
            return Hermod.assemblePageWithContent(baseTemplatePath, content);
        } catch (IOException e) {
            Logger.error("Failed to assemble page with content: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page with content", e);
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
            requestHandler.get().sendBinaryResponse(code, contentType, content);
        } catch (Exception e) {
            Logger.error("Failed to send binary response: {}", e.getMessage());
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
                Logger.error("API request failed with status code: {}", response.statusCode());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned error status: " + response.statusCode()
                );
            }

            String contentType = response.headers().firstValue(CONTENT_TYPE.getHeaderName()).orElse("");
            if (!contentType.contains("application/json")) {
                Logger.error("Unexpected content type: {}", contentType);
                Logger.error("Response body: {}", response.body());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned non-JSON response"
                );
            }
            return parseResponse(response.body(), responseType);

        } catch (IOException | InterruptedException e) {
            Logger.error("HTTP request failed: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to fetch data from JAWS"
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
            try {
                T directValue = getMapper().readValue(response, responseType);
                return APIResponse.success(ResponseCode.OK.getCodeAndMessage(), directValue);
            } catch (IOException e) {
                // If direct parsing fails, try to parse as APIResponse
                JavaType fullType = getMapper().getTypeFactory()
                        .constructParametricType(APIResponse.class, responseType);
                return getMapper().readValue(response, fullType);
            }
        } catch (JsonParseException e) {
            Logger.error("Failed to parse JSON (invalid format): {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Invalid JSON response from JAWS"
            );
        } catch (IOException e) {
            Logger.error("Failed to parse JSON response: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to process JAWS response"
            );
        }
    }
} 