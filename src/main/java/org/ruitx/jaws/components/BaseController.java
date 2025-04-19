package org.ruitx.jaws.components;

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
     * @param code
     * @param data
     * @throws IOException
     */
    protected void sendJSONResponse(ResponseCode code, Object data) throws IOException {
        requestHandler.get().sendJSONResponse(code, APIHandler.encode(
                new APIResponse<>(
                        true,
                        code.getCodeAndMessage(),
                        "",
                        data))
        );
    }

    /**
     * Send an HTML response to the client.
     * @param code
     * @param content
     * @throws IOException
     */
    protected void sendHTMLResponse(ResponseCode code, String content) throws IOException {
        requestHandler.get().sendHTMLResponse(code, content);
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

    protected void addCustomHeader(String name, String value) {
        requestHandler.get().addCustomHeader(name, value);
    }

    /**
     * Render a partial HTML file with parameters.
     * @param partialPath The path to the partial HTML file
     * @param params The parameters to be used in the partial
     * @return The rendered HTML with parameters replaced
     * @throws IOException if there's an error reading the partial file
     */
    protected String renderPartial(String partialPath, Map<String, String> params) throws IOException {
        String partialHtml = new String(Files.readAllBytes(Path.of(WWW_PATH + partialPath)));
        return Hermes.parseHTML(partialHtml, params, null);
    }

    /**
     * Render a partial HTML file without parameters.
     * @param partialPath The path to the partial HTML file
     * @return The rendered HTML
     * @throws IOException if there's an error reading the partial file
     */
    protected String renderPartial(String partialPath) throws IOException {
        return renderPartial(partialPath, new HashMap<>());
    }
} 