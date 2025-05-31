package org.ruitx.jaws.components;

import org.ruitx.jaws.strings.ResponseCode;

import java.io.IOException;
import java.util.Map;

/**
 * Bridge class that adapts JettyRequestHandler to the Yggdrasill.RequestHandler interface.
 * This maintains compatibility with existing Bragi controllers while using Jetty underneath.
 */
public class RequestHandlerBridge extends Yggdrasill.RequestHandler {

    private final JettyRequestHandler jettyHandler;

    public RequestHandlerBridge(JettyRequestHandler jettyHandler) {
        // We need to call the super constructor with dummy values since this is a bridge
        super(null, jettyHandler.getResourcesPath());
        this.jettyHandler = jettyHandler;
    }

    @Override
    public void sendHTMLResponse(ResponseCode responseCode, String body) throws IOException {
        jettyHandler.sendHTMLResponse(responseCode, body);
    }

    @Override
    public void sendJSONResponse(ResponseCode responseCode, String body) {
        jettyHandler.sendJSONResponse(responseCode, body);
    }

    @Override
    public void sendBinaryResponse(ResponseCode responseCode, String contentType, byte[] body) {
        jettyHandler.sendBinaryResponse(responseCode, contentType, body);
    }

    @Override
    public String getCurrentToken() {
        return jettyHandler.getCurrentToken();
    }

    @Override
    public Map<String, String> getQueryParams() {
        return jettyHandler.getQueryParams();
    }

    @Override
    public Map<String, String> getBodyParams() {
        return jettyHandler.getBodyParams();
    }

    @Override
    public Map<String, String> getPathParams() {
        return jettyHandler.getPathParams();
    }

    @Override
    public void addCustomHeader(String name, String value) {
        jettyHandler.addCustomHeader(name, value);
    }

    @Override
    public Map<String, String> getHeaders() {
        return jettyHandler.getHeaders();
    }

    @Override
    public String getClientIpAddress() {
        return jettyHandler.getClientIpAddress();
    }

    @Override
    public boolean isHTMX() {
        return jettyHandler.isHTMX();
    }

    @Override
    public boolean isConnectionClosed() {
        return jettyHandler.isConnectionClosed();
    }

    // Bridge method to access the underlying JettyRequestHandler
    public JettyRequestHandler getJettyHandler() {
        return jettyHandler;
    }
} 