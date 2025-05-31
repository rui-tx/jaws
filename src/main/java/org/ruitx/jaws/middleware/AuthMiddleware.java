package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.JettyRequestHandler;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.components.Bragi;
import org.tinylog.Logger;

import java.lang.reflect.Method;

/**
 * AuthMiddleware handles JWT authentication for protected routes.
 * This middleware checks for valid authentication tokens before requests reach controllers.
 */
public class AuthMiddleware implements Middleware {

    @Override
    public boolean handle(JettyRequestHandler handler, MiddlewareChain chain) {
        try {
            String endPoint = handler.getRequest().getRequestURI();
            String methodStr = handler.getRequest().getMethod().toUpperCase();
            RequestType requestType = RequestType.fromString(methodStr);
            
            if (requestType == null) {
                return chain.next(); // Let other middleware handle invalid methods
            }

            // Remove query parameters from endpoint for route matching
            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex != -1) {
                endPoint = endPoint.substring(0, questionMarkIndex);
            }

            // Find the route method to check if it requires authentication
            Method routeMethod = findRouteMethod(endPoint, requestType);
            
            if (routeMethod != null && routeMethod.isAnnotationPresent(AccessControl.class)) {
                AccessControl auth = routeMethod.getAnnotation(AccessControl.class);
                if (auth.login()) {
                    // This route requires authentication
                    if (!isAuthenticated(handler)) {
                        sendUnauthorizedResponse(handler, routeMethod);
                        return false; // Stop the chain
                    }
                }
            }

            // Continue to next middleware if authenticated or no auth required
            return chain.next();
            
        } catch (Exception e) {
            Logger.error("Error in AuthMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error to avoid breaking the chain
        }
    }

    /**
     * Find the route method for the given endpoint and request type.
     */
    private Method findRouteMethod(String endPoint, RequestType requestType) {
        // First check for direct route match
        Method routeMethod = Njord.getInstance().getRoute(endPoint, requestType);
        if (routeMethod != null) {
            return routeMethod;
        }

        // Check for dynamic routes
        for (Method route : Njord.getInstance().getAllRoutes()) {
            if (route.isAnnotationPresent(Route.class)) {
                Route routeAnnotation = route.getAnnotation(Route.class);
                if (routeAnnotation.method() == requestType && matchesRoutePattern(routeAnnotation.endpoint(), endPoint)) {
                    return route;
                }
            }
        }

        return null;
    }

    /**
     * Simple pattern matching for dynamic routes.
     */
    private boolean matchesRoutePattern(String pattern, String path) {
        // Handle exact matches
        if (pattern.equals(path)) {
            return true;
        }

        // Handle dynamic parameters like /users/{id}
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        if (patternParts.length != pathParts.length) {
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            
            // Skip dynamic parts (enclosed in {})
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                continue;
            }
            
            // Must match exactly for non-dynamic parts
            if (!patternPart.equals(pathPart)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if the request is authenticated.
     */
    private boolean isAuthenticated(JettyRequestHandler handler) {
        String token = handler.getCurrentToken();
        
        if (token == null || token.trim().isEmpty()) {
            Logger.debug("No authentication token found");
            return false;
        }

        if (!Tyr.isTokenValid(token)) {
            Logger.debug("Invalid authentication token");
            return false;
        }

        Logger.debug("Authentication successful for user: {}", Tyr.getUserIdFromJWT(token));
        return true;
    }

    /**
     * Send an unauthorized response based on the route's response type.
     */
    private void sendUnauthorizedResponse(JettyRequestHandler handler, Method routeMethod) {
        try {
            Route route = routeMethod.getAnnotation(Route.class);
            ResponseType responseType = route.responseType();

            switch (responseType) {
                case JSON -> {
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                            "You are not authorized to access this resource"
                    );
                    handler.sendJSONResponse(ResponseCode.UNAUTHORIZED, Bragi.encode(response));
                }
                case HTML -> {
                    handler.sendUnauthorizedHTMLResponse();
                }
                default -> {
                    // Default to JSON for unknown types
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                            "You are not authorized to access this resource"
                    );
                    handler.sendJSONResponse(ResponseCode.UNAUTHORIZED, Bragi.encode(response));
                }
            }
        } catch (Exception e) {
            Logger.error("Error sending unauthorized response: {}", e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() {
        return 30; // Execute after CORS and logging, but before most other middleware
    }
} 