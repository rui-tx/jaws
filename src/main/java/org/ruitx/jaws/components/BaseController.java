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
public abstract class BaseController {
    private static final ThreadLocal<Yggdrasill.RequestHandler> requestHandler = new ThreadLocal<>();

    /**
     * Set the request handler for the current thread.
     * @param handler
     */
    public void setRequestHandler(Yggdrasill.RequestHandler handler) {
        requestHandler.set(handler);
    }

    /**
     * Send a JSON response to the client.
     * @param code the response code
     * @param data the data to send
     */
    protected void sendJSONResponse(ResponseCode code, Object data) {
        try {
            requestHandler.get().sendJSONResponse(code, APIHandler.encode(
                    new APIResponse<>(
                            true,
                            code.getCodeAndMessage(),
                            "",
                            data))
            );
        } catch (Exception e) {
            Logger.error("Failed to send JSON response: {}", e.getMessage());
            throw new SendRespondException("Failed to send JSON response", e);
        }
    }

    /**
     * Send an HTML response to the client.
     * @param code the response code
     * @param content the HTML content to send
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
     * @param name the parameter name
     * @return the parameter value
     */
    protected String getPathParam(String name) {
        return requestHandler.get().getPathParams().get(name);
    }

    /**
     * Get a query parameter from the request.
     * @param name the parameter name
     * @return the parameter value
     */
    protected String getQueryParam(String name) {
        return requestHandler.get().getQueryParams().get(name);
    }

    /**
     * Get a body parameter from the request.
     * @param name the parameter name
     * @return the parameter value
     */
    protected String getBodyParam(String name) {
        return requestHandler.get().getBodyParams().get(name);
    }

    /**
     * Check if the request is from HTMX.
     * @return true if the request is from HTMX
     */
    protected boolean isHTMX() {
        return requestHandler.get().isHTMX();
    }

    /**
     * Clean up the request handler.
     */
    protected void cleanup() {
        requestHandler.remove();
    }

    /**
     * Add a custom header to the response.
     * @param name the header name
     * @param value the header value
     */
    protected void addCustomHeader(String name, String value) {
        requestHandler.get().addCustomHeader(name, value);
    }

    /**
     * Render a template with parameters.
     * @param templatePath the path to the template
     * @param params the parameters to render
     * @return the rendered template
     */
    protected String renderTemplate(String templatePath, Map<String, String> params) {
        try {
            Path path = Path.of(WWW_PATH, templatePath);
            String template = new String(Files.readAllBytes(path));
            for (Map.Entry<String, String> entry : params.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return template;
        } catch (Exception e) {
            Logger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    /**
     * Render a template without parameters.
     * @param templatePath the path to the template
     * @return the rendered template
     */
    protected String renderTemplate(String templatePath) {
        try {
            Path path = Path.of(WWW_PATH, templatePath);
            return new String(Files.readAllBytes(path));
        } catch (Exception e) {
            Logger.error("Failed to render template: {}", e.getMessage());
            throw new SendRespondException("Failed to render template", e);
        }
    }

    /**
     * Assemble a page from a base template and a partial template.
     * @param baseTemplatePath the path to the base template
     * @param partialTemplatePath the path to the partial template
     * @return the assembled page
     */
    protected String assemblePage(String baseTemplatePath, String partialTemplatePath) {
        try {
            String baseTemplate = renderTemplate(baseTemplatePath);
            String partialTemplate = renderTemplate(partialTemplatePath);
            return baseTemplate.replace("{{content}}", partialTemplate);
        } catch (Exception e) {
            Logger.error("Failed to assemble page: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page", e);
        }
    }

    /**
     * Assemble a page from a base template and content.
     * @param baseTemplatePath the path to the base template
     * @param content the content to insert
     * @return the assembled page
     */
    protected String assemblePageWithContent(String baseTemplatePath, String content) {
        try {
            String baseTemplate = renderTemplate(baseTemplatePath);
            return baseTemplate.replace("{{content}}", content);
        } catch (Exception e) {
            Logger.error("Failed to assemble page with content: {}", e.getMessage());
            throw new SendRespondException("Failed to assemble page with content", e);
        }
    }
} 