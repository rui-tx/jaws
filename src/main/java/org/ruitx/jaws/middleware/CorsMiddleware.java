package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.JettyRequestHandler;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.tinylog.Logger;

import java.io.IOException;

/**
 * CorsMiddleware handles Cross-Origin Resource Sharing (CORS) headers.
 * This middleware adds appropriate CORS headers to responses and handles preflight requests.
 */
public class CorsMiddleware implements Middleware {

    private final String allowedOrigins;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final boolean allowCredentials;

    /**
     * Creates a CorsMiddleware with default permissive settings.
     */
    public CorsMiddleware() {
        this("*", "GET, POST, PUT, PATCH, DELETE, OPTIONS", "Content-Type, Authorization", true);
    }

    /**
     * Creates a CorsMiddleware with custom settings.
     */
    public CorsMiddleware(String allowedOrigins, String allowedMethods, String allowedHeaders, boolean allowCredentials) {
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.allowCredentials = allowCredentials;
    }

    @Override
    public boolean handle(JettyRequestHandler handler, MiddlewareChain chain) {
        try {
            // Add CORS headers to all responses
            handler.getResponse().setHeader("Access-Control-Allow-Origin", allowedOrigins);
            handler.getResponse().setHeader("Access-Control-Allow-Methods", allowedMethods);
            handler.getResponse().setHeader("Access-Control-Allow-Headers", allowedHeaders);
            
            if (allowCredentials) {
                handler.getResponse().setHeader("Access-Control-Allow-Credentials", "true");
            }

            // Handle preflight requests (OPTIONS)
            if ("OPTIONS".equalsIgnoreCase(handler.getRequest().getMethod())) {
                handler.getResponse().setStatus(200);
                handler.getResponse().setHeader("Access-Control-Max-Age", "3600");
                handler.getResponse().getWriter().flush();
                return false; // Stop processing for preflight requests
            }

            // Continue with the chain for non-preflight requests
            return chain.next();
            
        } catch (IOException e) {
            Logger.error("Error handling CORS in middleware: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int getOrder() {
        return 20; // Execute early, but after logging
    }
} 