package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.utils.JawsLogger;

/**
 * LoggingMiddleware logs incoming HTTP requests for debugging and monitoring.
 */
public class LoggingMiddleware implements Middleware {

    private int order = 10;

    public LoggingMiddleware(int order) {
        this.order = order;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
        try {
            JawsLogger.debug("LoggingMiddleware: Handling request");
            long startTime = System.currentTimeMillis();
            String method = context.getRequest().getMethod();
            String uri = context.getRequest().getRequestURI();
            String queryString = context.getRequest().getQueryString();
            String fullUrl = queryString != null ? uri + "?" + queryString : uri;
            String clientIp = context.getClientIpAddress();
            
            JawsLogger.info("HTTP {} {} from {}", method, fullUrl, clientIp);
            
            // Continue with the chain
            boolean result = chain.next();
            
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = context.getResponse().getStatus();
            
            JawsLogger.info("HTTP {} {} completed in {}ms with status {}", 
                method, fullUrl, duration, statusCode);
            
            return result;
            
        } catch (Exception e) {
            JawsLogger.error("Error in LoggingMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error
        }
    }

    @Override
    public int getOrder() {
        return order; // Execute very early in the chain
    }
} 