package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.utils.JawsLogger;

import jakarta.servlet.http.HttpServletResponse;

/**
 * CorsMiddleware handles Cross-Origin Resource Sharing (CORS) headers.
 * This middleware adds appropriate CORS headers to responses and handles preflight requests.
 */
public class CorsMiddleware implements Middleware {

    private int order = 10;

    public CorsMiddleware(int order) {
        this.order = order;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
        try {
            JawsLogger.debug("CorsMiddleware: Handling request");
            HttpServletResponse response = context.getResponse();
            JawsLogger.debug("CorsMiddleware: Request: {}", context.getRequest());
                        
            // Add CORS headers
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
            response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, HX-Request, HX-Trigger, HX-Target, HX-Current-URL");
            response.setHeader("Access-Control-Max-Age", "3600");
            JawsLogger.debug("CorsMiddleware: Response: {}", response);

            
            // Handle preflight OPTIONS requests
            if ("OPTIONS".equals(context.getRequest().getMethod())) {
                JawsLogger.debug("CorsMiddleware: Handling OPTIONS request");
                response.setStatus(HttpServletResponse.SC_OK);
                try {
                    response.getWriter().flush();
                } catch (Exception e) {
                    JawsLogger.debug("Error flushing OPTIONS response: {}", e.getMessage());
                }
                return false; // Stop the chain for OPTIONS requests
            }
            
            JawsLogger.debug("CorsMiddleware: Continuing to next middleware");
            return chain.next();
            
        } catch (Exception e) {
            JawsLogger.error("Error in CorsMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error
        }
    }

    @Override
    public int getOrder() {
        return order; // Execute early in the chain
    }
} 