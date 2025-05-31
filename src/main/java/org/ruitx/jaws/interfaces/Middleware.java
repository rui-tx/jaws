package org.ruitx.jaws.interfaces;

import org.ruitx.jaws.components.JettyRequestHandler;

/**
 * Middleware interface for processing HTTP requests and responses in a chain pattern.
 * Middleware can modify requests, handle authentication, logging, or other cross-cutting concerns.
 */
public interface Middleware {
    
    /**
     * Process the request/response and decide whether to continue the chain.
     * 
     * @param handler The request handler containing request/response information
     * @param chain The middleware chain to continue processing
     * @return true if processing should continue, false to stop the chain
     */
    boolean handle(JettyRequestHandler handler, MiddlewareChain chain);
    
    /**
     * Get the order/priority of this middleware. Lower numbers execute first.
     * Default implementation returns 100.
     * 
     * @return the order priority
     */
    default int getOrder() {
        return 100;
    }
} 