package org.ruitx.jaws.components;

import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.tinylog.Logger;

import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;

/**
 * Base controller class for all controllers.
 * Contains methods for sending responses to the client.
 * The most important object is the requestHandler, that contains the request information.
 */
public abstract class Bragi {
    private static final ThreadLocal<Yggdrasill.RequestHandler> requestHandler = new ThreadLocal<>();
    protected String bodyHtmlPath;

    /**
     * Set the request handler and body path for the current thread.
     * @param handler
     */
    public void setRequestHandler(Yggdrasill.RequestHandler handler) {
        requestHandler.set(handler);
        if (bodyHtmlPath != null) {
            setBodyPath(bodyHtmlPath);
        }
    }

    /**
     * Set the body path for the current thread.
     * @param bodyPath
     */
    private void setBodyPath(String bodyPath) {
        Hermes.setBodyPath(bodyPath);
    }

    /**
     * Send a JSON success response with no data
     * @param code Response code
     */
    protected void sendSucessfulResponse(ResponseCode code) {
        boolean isSuccess = code.toString().startsWith("2"); // 2xx codes are success
        sendJSONResponse(isSuccess, code, null, null);
    }

    /**
     * Send a JSON success response with data
     * @param code Response code
     * @param data Response data (can be null)
     */
    protected void sendSucessfulResponse(ResponseCode code, Object data) {
        try {
            APIResponse<?> response = APIResponse.success(code.getCodeAndMessage(), data);
            requestHandler.get().sendJSONResponse(code, APIHandler.encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        }
    }

    /**
     * Send a JSON success response with data and info message
     * @param code Response code
     * @param info Info message
     * @param data Response data (can be null)
     */
    protected void sendSucessfulResponse(ResponseCode code, String info, Object data) {
        try {
            APIResponse<?> response = APIResponse.success(code.getCodeAndMessage(), info, data);
            requestHandler.get().sendJSONResponse(code, APIHandler.encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        }
    }

    /**
     * Send a JSON error response
     * @param code Error code
     * @param message Error message
     */
    protected void sendErrorResponse(ResponseCode code, String message) {
        try {
            APIResponse<?> response = APIResponse.error(code.getCodeAndMessage(), message);
            requestHandler.get().sendJSONResponse(code, APIHandler.encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        }
    }

    /**
     * Send a JSON error response with data
     * @param code Error code
     * @param message Error message
     * @param data Response data (can be null)
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
            requestHandler.get().sendJSONResponse(code, APIHandler.encode(response));
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        }
    }

    /**
     * Send an HTML response to the client.
     * @param code
     * @param content
     */
    protected void sendHTMLResponse(ResponseCode code, String content) {
        try {
            requestHandler.get().sendHTMLResponse(code, content);
        } catch (Exception e) {
            Logger.error("Failed to send HTML response: {}", e.getMessage());
            throw new SendRespondException("Failed to send HTML response", e);
        }
    }

    /**
     * Get a path parameter from the request.
     * @param name
     * @return
     */
    protected String getPathParam(String name) {
        return requestHandler.get().getPathParams().get(name);
    }

    /**
     * Get a query parameter from the request.
     * @param name
     * @return
     */
    protected String getQueryParam(String name) {
        return requestHandler.get().getQueryParams() != null ? requestHandler.get().getQueryParams().get(name) : null;
    }

    /**
     * Get a body parameter from the request.
     * @param name
     * @return
     */
    protected String getBodyParam(String name) {
        return requestHandler.get().getBodyParams() != null ? requestHandler.get().getBodyParams().get(name) : null;
    }

    /**
     * Check if the request is an HTMX request.
     * @return
     */ 
    protected boolean isHTMX() {
        return requestHandler.get().isHTMX();
    }

    /**
     * Cleanup the request handler for the current thread.
     */
    protected void cleanup() {
        try {
            requestHandler.remove();
        } catch (Exception e) {
            Logger.error("Error cleaning up request handler: {}", e.getMessage());
        }
    }

    /**
     * Get the request handler for the current thread.
     * @return The request handler for the current thread
     */
    public Yggdrasill.RequestHandler getRequestHandler() {
        return requestHandler.get();
    }

    protected void addCustomHeader(String name, String value) {
        requestHandler.get().addCustomHeader(name, value);
    }

    /**
     * Render a template file with parameters.
     * @param templatePath The path to the template file
     * @param params The parameters to be used in the template
     * @return The rendered template with parameters replaced
     */
    protected String renderTemplate(String templatePath, Map<String, String> params) {
        try {
            String templateHtml = new String(Files.readAllBytes(Path.of(WWW_PATH + templatePath)));
            return Hermes.processTemplate(templateHtml, params, null);
        } catch (IOException e) {
            Logger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    /**
     * Render a template file without parameters.
     * @param templatePath The path to the template file
     * @return The rendered template
     */
    protected String renderTemplate(String templatePath) {
        return renderTemplate(templatePath, new HashMap<>());
    }

    /**
     * Assemble a full page by combining a base template with a partial template.
     * @param baseTemplatePath The path to the base template file
     * @param partialTemplatePath The path to the partial template file
     * @return The assembled page
     */
    protected String assemblePage(String baseTemplatePath, String partialTemplatePath) {
        try {
            return Hermes.assemblePage(baseTemplatePath, partialTemplatePath);
        } catch (IOException e) {
            Logger.error("Failed to assemble page: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page", e);
        }
    }

    /**
     * Assemble a full page by combining a base template with raw content.
     * @param baseTemplatePath The path to the base template file
     * @param content The raw content to insert
     * @return The assembled page
     */
    protected String assemblePageWithContent(String baseTemplatePath, String content) {
        try {
            return Hermes.assemblePageWithContent(baseTemplatePath, content);
        } catch (IOException e) {
            Logger.error("Failed to assemble page with content: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page with content", e);
        }
    }

    /**
     * Send a binary response to the client.
     * @param code the response code
     * @param contentType the content type of the response
     * @param content the binary content
     */
    protected void sendBinaryResponse(ResponseCode code, String contentType, byte[] content) {
        try {
            requestHandler.get().sendBinaryResponse(code, contentType, content);
        } catch (Exception e) {
            Logger.error("Failed to send binary response: {}", e.getMessage());
            throw new SendRespondException("Failed to send binary response", e);
        }
    }
} 