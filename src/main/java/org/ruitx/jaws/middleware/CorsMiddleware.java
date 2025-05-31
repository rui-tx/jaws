package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.tinylog.Logger;

import jakarta.servlet.http.HttpServletResponse;

/**
 * CorsMiddleware handles Cross-Origin Resource Sharing (CORS) headers.
 * This middleware adds appropriate CORS headers to responses and handles preflight requests.
 */
public class CorsMiddleware implements Middleware {

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
        try {
            HttpServletResponse response = context.getResponse();
            
            // Add CORS headers
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
            response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, HX-Request, HX-Trigger, HX-Target, HX-Current-URL");
            response.setHeader("Access-Control-Max-Age", "3600");
            
            // Handle preflight OPTIONS requests
            if ("OPTIONS".equals(context.getRequest().getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                try {
                    response.getWriter().flush();
                } catch (Exception e) {
                    Logger.debug("Error flushing OPTIONS response: {}", e.getMessage());
                }
                return false; // Stop the chain for OPTIONS requests
            }
            
            return chain.next();
            
        } catch (Exception e) {
            Logger.error("Error in CorsMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error
        }
    }

    @Override
    public int getOrder() {
        return 10; // Execute early in the chain
    }
} 