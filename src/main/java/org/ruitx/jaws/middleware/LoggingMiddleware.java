package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.JettyRequestHandler;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.tinylog.Logger;

/**
 * LoggingMiddleware logs incoming requests for monitoring and debugging purposes.
 * This middleware runs early in the chain (order 10) to capture all requests.
 */
public class LoggingMiddleware implements Middleware {

    @Override
    public boolean handle(JettyRequestHandler handler, MiddlewareChain chain) {
        long startTime = System.currentTimeMillis();
        
        // Log incoming request
        String method = handler.getRequest().getMethod();
        String uri = handler.getRequest().getRequestURI();
        String queryString = handler.getRequest().getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = handler.getClientIpAddress();
        
        //Logger.info("Incoming request: {} {} from {}", method, fullUrl, clientIp);
        
        // Continue with the chain
        boolean result = chain.next();
        
        // Log completion time
        long duration = System.currentTimeMillis() - startTime;
        //Logger.debug("Request completed in {}ms", duration);
        
        return result;
    }

    @Override
    public int getOrder() {
        return 10; // Execute early
    }
} 