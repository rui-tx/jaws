package org.ruitx.jaws.middleware;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.DefaultHTML;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.strings.ResponseType;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.utils.JawsLogger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * AuthMiddleware handles JWT authentication for protected routes.
 * This middleware checks for valid authentication tokens before requests reach controllers.
 */
public class AuthMiddleware implements Middleware {

    private int order = 10;

    public AuthMiddleware(int order) {
        this.order = order;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
        try {
            JawsLogger.debug("AuthMiddleware: Handling request");
            String endPoint = context.getRequest().getRequestURI();
            String methodStr = context.getRequest().getMethod().toUpperCase();
            RequestType requestType = RequestType.fromString(methodStr);
            
            if (requestType == null) {
                JawsLogger.debug("AuthMiddleware: Invalid request type");
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
                JawsLogger.debug("AuthMiddleware: Found AccessControl - login: {}, role: '{}'", auth.login(), auth.role());
                
                if (auth.login()) {
                    // This route requires authentication
                    if (!isAuthenticated(context)) {
                        JawsLogger.debug("AuthMiddleware: User is not authenticated");
                        sendUnauthorizedResponse(context, routeMethod);
                        return false; // Stop the chain
                    }
                    
                    // Check role-based authorization if a specific role is required
                    if (!auth.role().isEmpty()) {
                        if (!isAuthorized(context, auth.role())) {
                            JawsLogger.debug("AuthMiddleware: User is not authorized");
                            sendUnauthorizedResponse(context, routeMethod);
                            return false; // Stop the chain
                        }
                    }
                }
            }

            // Continue to next middleware if authenticated or no auth required
            JawsLogger.debug("AuthMiddleware: Continuing to next middleware");
            return chain.next();
            
        } catch (Exception e) {
            JawsLogger.error("Error in AuthMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error to avoid breaking the chain
        }
    }

    /**
     * Find the route method for the given endpoint and request type.
     */
    private Method findRouteMethod(String endPoint, RequestType requestType) {
        JawsLogger.debug("AuthMiddleware: Finding route method for endpoint: {} and request type: {}", endPoint, requestType);
        // First check for direct route match
        Method routeMethod = Njord.getInstance().getRoute(endPoint, requestType);
        if (routeMethod != null) {
            JawsLogger.debug("AuthMiddleware: Direct route match found");
            return routeMethod;
        }

        // Check for dynamic routes
        for (Method route : Njord.getInstance().getAllRoutes()) {
            if (route.isAnnotationPresent(Route.class)) {
                Route routeAnnotation = route.getAnnotation(Route.class);
                if (routeAnnotation.method() == requestType && matchesRoutePattern(routeAnnotation.endpoint(), endPoint)) {
                    JawsLogger.debug("AuthMiddleware: Dynamic route match found");
                    return route;
                }
            }
        }

        JawsLogger.debug("AuthMiddleware: No route method found");
        return null;
    }

    /**
     * Simple pattern matching for dynamic routes.
     */
    private boolean matchesRoutePattern(String pattern, String path) {
        JawsLogger.debug("AuthMiddleware: Matching route pattern: {} for path: {}", pattern, path);
        // Handle exact matches
        if (pattern.equals(path)) {
            JawsLogger.debug("AuthMiddleware: Exact match found");
            return true;
        }

        // Handle dynamic parameters like /users/{id}
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        if (patternParts.length != pathParts.length) {
            JawsLogger.debug("AuthMiddleware: Length mismatch: pattern length: {}, path length: {}", patternParts.length, pathParts.length);
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            
            // Skip dynamic parts (enclosed in {})
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                JawsLogger.debug("AuthMiddleware: Dynamic part found: {}", patternPart);
                continue;
            }
            
            // Must match exactly for non-dynamic parts
            if (!patternPart.equals(pathPart)) {
                JawsLogger.debug("AuthMiddleware: Mismatch found: pattern part: {}, path part: {}", patternPart, pathPart);
                return false;
            }
        }

        JawsLogger.debug("AuthMiddleware: Route pattern matches path");
        return true;
    }

    /**
     * Check if the request is authenticated.
     */
    private boolean isAuthenticated(Yggdrasill.RequestContext context) {
        String token = context.getCurrentToken();
        
        JawsLogger.debug("AuthMiddleware: Checking authentication for endpoint: {}", context.getRequest().getRequestURI());
        
        if (token == null || token.trim().isEmpty()) {
            JawsLogger.debug("AuthMiddleware: No authentication token found");
            return false;
        }

        JawsLogger.debug("AuthMiddleware: Token found, validating...");
        if (!Tyr.isTokenValid(token)) {
            JawsLogger.debug("AuthMiddleware: Invalid authentication token");
            return false;
        }

        String userId = Tyr.getUserIdFromJWT(token);
        JawsLogger.debug("AuthMiddleware: Authentication successful for user: {}", userId);
        return true;
    }

    /**
     * Check if the request is authorized for the required role(s).
     * Supports comma-separated roles (ANY logic) like "admin,editor"
     */
    private boolean isAuthorized(Yggdrasill.RequestContext context, String requiredRoles) {
        String token = context.getCurrentToken();
        
        if (token == null || token.trim().isEmpty()) {
            JawsLogger.debug("AuthMiddleware: No token for role-based authorization");
            return false;
        }

        try {
            List<String> userRoles = Tyr.getUserRolesFromJWT(token);
            JawsLogger.debug("AuthMiddleware: Role check - required: '{}', user roles: {}", requiredRoles, userRoles);
            
            // Admin always has access to everything
            if (userRoles.contains("admin")) {
                JawsLogger.debug("AuthMiddleware: Admin access granted");
                return true;
            }
            
            // Parse required roles (support comma-separated)
            String[] requiredRoleArray = requiredRoles.split(",");
            
            // Check if user has ANY of the required roles
            boolean authorized = Arrays.stream(requiredRoleArray)
                    .map(String::trim)
                    .anyMatch(userRoles::contains);
                    
            JawsLogger.debug("AuthMiddleware: Role authorization result: {}", authorized);
            return authorized;
            
        } catch (Exception e) {
            JawsLogger.error("AuthMiddleware: Error checking role authorization: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send an unauthorized response based on the route's response type.
     */
    private void sendUnauthorizedResponse(Yggdrasill.RequestContext context, Method routeMethod) {
        try {
            Route route = routeMethod.getAnnotation(Route.class);
            ResponseType responseType = route.responseType();

            switch (responseType) {
                case JSON -> {
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                            "You are not authorized to access this resource"
                    );
                    JawsLogger.debug("AuthMiddleware: Sending JSON unauthorized response");
                    context.sendJSONResponse(ResponseCode.UNAUTHORIZED, Bragi.encode(response));
                }
                case HTML -> {
                    try {
                        JawsLogger.debug("AuthMiddleware: Sending HTML unauthorized response");
                        context.sendHTMLResponse(ResponseCode.UNAUTHORIZED, getUnauthorizedHTML(context));
                    } catch (Exception e) {
                        JawsLogger.error("Error sending HTML unauthorized response: {}", e.getMessage());
                        // Fallback to simple response
                        context.getResponse().setStatus(401);
                    }
                }
                default -> {
                    JawsLogger.debug("AuthMiddleware: Sending JSON unauthorized response for unknown response type");
                    // Default to JSON for unknown types
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.UNAUTHORIZED.getCodeAndMessage(),
                            "You are not authorized to access this resource"
                    );
                    context.sendJSONResponse(ResponseCode.UNAUTHORIZED, Bragi.encode(response));
                }
            }
        } catch (Exception e) {
            JawsLogger.error("Error sending unauthorized response: {}", e.getMessage(), e);
        }
    }

    /**
     * Get appropriate unauthorized HTML response.
     */
    private String getUnauthorizedHTML(Yggdrasill.RequestContext context) {
        if (context.isHTMX()) {
            return DefaultHTML.HTML_401_UNAUTHORIZED_HTMX;
        } else {
            // Check for custom 401 page
            String custom401Page = org.ruitx.jaws.configs.ApplicationConfig.CUSTOM_PAGE_PATH_401;
            if (custom401Page != null && !custom401Page.isEmpty()) {
                try {
                    // Extract only the filename since Hermod already has WWW_PATH as prefix
                    // Convert full path like "src/main/resources/www/401.html" to just "401.html"
                    String templateName = custom401Page;
                    if (custom401Page.contains("/")) {
                        templateName = custom401Page.substring(custom401Page.lastIndexOf("/") + 1);
                    }
                    
                    JawsLogger.debug("AuthMiddleware: Processing custom 401 template: {}", templateName);
                    return Hermod.processTemplate(
                        templateName,
                        context.getQueryParams(),
                        context.getBodyParams(),
                        context.getRequest(),
                        context.getResponse()
                    );
                } catch (Exception e) {
                    JawsLogger.error("Error processing custom 401 template: {}", e.getMessage());
                }
            }
            
            JawsLogger.debug("AuthMiddleware: No custom 401 page found, using default");
            // Default HTML response
            return DefaultHTML.HTML_401_UNAUTHORIZED;
        }
    }

    @Override
    public int getOrder() {
        return order; // Execute after CORS and logging, but before most other middleware
    }
} 