package org.ruitx.jaws.middleware;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.interfaces.Validatable;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.jaws.utils.JawsValidation;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.ruitx.jaws.strings.HttpHeaders.CONTENT_TYPE;

/**
 * RequestValidationMiddleware handles request body validation for all routes.
 * This middleware validates Content-Type headers, parses JSON request bodies,
 * performs deserialization and Jakarta Bean Validation before requests reach controllers.

 */
public class RequestValidationMiddleware implements Middleware {

    private final int order;

    public RequestValidationMiddleware(int order) {
        this.order = order;
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
        try {
            String endPoint = context.getRequest().getRequestURI();
            RequestType requestType = RequestType.fromString(context.getRequest().getMethod());
            
            // Skip validation for certain endpoints
            // if (endPoint.equals("/") || endPoint.equals("/api/v1/ping") || endPoint.startsWith("/api/admin")) {
            //     return chain.next();
            // }

            String contentType = context.getHeader(CONTENT_TYPE.getHeaderName());
            String httpMethod = context.getRequest().getMethod().toUpperCase();
            boolean expectsRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT") || httpMethod.equals("PATCH");
            
            if (expectsRequestBody && contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                try {
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                            "This endpoint requires JSON data. Please set Content-Type to 'application/json' and send JSON in the request body"
                    );
                    sendErrorResponse(context, response);
                    return false; // Stop the chain
                } catch (Exception e) {
                    return false;
                }
            }

            // Find the route method to determine if validation is needed
            Method routeMethod = findRouteMethod(endPoint, requestType);
            
            if (routeMethod != null) {
                // Get method parameters to determine if we need to validate request body
                Class<?>[] parameterTypes = routeMethod.getParameterTypes();
                
                if (parameterTypes.length > 0) {
                    // Perform request validation and body parsing
                    ValidationResult validationResult = validateAndParseRequestBody(context, parameterTypes[0]);
                    
                    if (!validationResult.success) {
                        // Validation failed, error response already sent
                        return false;
                    }
                    
                    // Store the validated object in context for the controller to use
                    if (validationResult.validatedObject != null) {
                        context.setValidatedRequestBody(validationResult.validatedObject);
                    }
                }
            }

            // Continue to next middleware/route processing
            return chain.next();
            
        } catch (Exception e) {
            JawsLogger.error("Error in RequestValidationMiddleware: {}", e.getMessage(), e);
            return chain.next(); // Continue on error to avoid breaking the chain
        }
    }

    /**
     * Validates and parses the request body for the given target type.
     */
    private ValidationResult validateAndParseRequestBody(Yggdrasill.RequestContext context, Class<?> targetType) {
        String contentType = context.getHeader(CONTENT_TYPE.getHeaderName());
        String requestBodyTrimmed = context.getRequestBody() != null ? context.getRequestBody().trim() : "";
        
        // Determine if this request expects a body
        String httpMethod = context.getRequest().getMethod().toUpperCase();
        boolean expectsRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT") || httpMethod.equals("PATCH");
        boolean isMultipartRequest = contentType != null && contentType.contains("multipart/form-data");
        
        // Skip validation for multipart requests - they're handled differently in the route method
        if (isMultipartRequest) {
            JawsLogger.debug("RequestValidationMiddleware: Skipping validation for multipart request");
            return ValidationResult.success(null);
        }
        
        if (expectsRequestBody) {
            // Check for missing Content-Type header or wrong Content-Type
            if (contentType == null) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Content-Type header is required for " + httpMethod + " requests. Please set Content-Type to 'application/json'"
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
            }
            
            // Check for wrong Content-Type (form-encoded instead of JSON)
            if (contentType.contains("application/x-www-form-urlencoded")) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "This endpoint requires JSON data. Please set Content-Type to 'application/json' and send JSON in the request body"
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
            }
            
            // Check for other invalid content types
            if (!contentType.contains("application/json")) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Content-Type header must be 'application/json' for " + httpMethod + " requests"
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
            }
            
            // Check for empty request body
            if (requestBodyTrimmed.isEmpty()) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Request body is required for " + httpMethod + " requests. Please provide a valid JSON body."
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
            }
        }

        // If we have a request body, process it
        if (!requestBodyTrimmed.isEmpty()) {
            try {
                ObjectMapper mapper = Odin.getMapper();

                // Deserialize the object
                Object deserializedObject = mapper.readValue(requestBodyTrimmed, targetType);
                
                // Validate the deserialized object using Jakarta Bean Validation
                APIResponse<String> validationError = JawsValidation.validate(deserializedObject);
                if (validationError != null) {
                    sendErrorResponse(context, validationError);
                    return ValidationResult.failure();
                }
                
                // Custom validation for DTOs that implement Validatable
                if (deserializedObject instanceof Validatable validatable) {
                    Optional<String> customValidationError = validatable.isValid();
                    if (customValidationError.isPresent()) {
                        APIResponse<String> customError = APIResponse.error(
                                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                                customValidationError.get()
                        );
                        sendErrorResponse(context, customError);
                        return ValidationResult.failure();
                    }
                }
                
                return ValidationResult.success(deserializedObject);
                
            } catch (JsonParseException e) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Invalid JSON format: " + e.getOriginalMessage()
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
                
            } catch (JsonMappingException e) {
                // Handle unknown fields or mapping issues
                String originalMessage = e.getOriginalMessage();
                if (originalMessage != null && originalMessage.contains("Unrecognized field")) {
                    // Extract the field name from the error message
                    String fieldName = extractFieldNameFromError(originalMessage);
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                            String.format("Unknown field '%s'. Please check the API documentation for accepted fields.", fieldName)
                    );
                    sendErrorResponse(context, response);
                    return ValidationResult.failure();
                } else {
                    APIResponse<String> response = APIResponse.error(
                            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                            "JSON mapping error: " + (originalMessage != null ? originalMessage : e.getMessage())
                    );
                    sendErrorResponse(context, response);
                    return ValidationResult.failure();
                }
                
            } catch (Exception e) {
                APIResponse<String> response = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Error processing request body: " + e.getMessage()
                );
                sendErrorResponse(context, response);
                return ValidationResult.failure();
            }
        } else if (expectsRequestBody) {
            // If we reach here, it means we have parameters expected but no body was provided
            APIResponse<String> response = APIResponse.error(
                    ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                    "Request body is required but was not provided"
            );
            sendErrorResponse(context, response);
            return ValidationResult.failure();
        }
        
        // No request body needed or provided
        return ValidationResult.success(null);
    }

    /**
     * Find the route method for the given endpoint and request type.
     * This uses the same logic as AuthMiddleware for consistency.
     */
    private Method findRouteMethod(String endPoint, RequestType requestType) {
        JawsLogger.debug("RequestValidationMiddleware: Finding route method for endpoint: {} and request type: {}", endPoint, requestType);
        
        // First check for direct route match
        Method routeMethod = Njord.getInstance().getRoute(endPoint, requestType);
        if (routeMethod != null) {
            JawsLogger.debug("RequestValidationMiddleware: Direct route match found");
            return routeMethod;
        }

        // Check for dynamic routes
        for (Method route : Njord.getInstance().getAllRoutes()) {
            if (route.isAnnotationPresent(Route.class)) {
                Route routeAnnotation = route.getAnnotation(Route.class);
                if (routeAnnotation.method() == requestType && matchesRoutePattern(routeAnnotation.endpoint(), endPoint)) {
                    JawsLogger.debug("RequestValidationMiddleware: Dynamic route match found");
                    return route;
                }
            }
        }

        JawsLogger.debug("RequestValidationMiddleware: No route method found");
        return null;
    }

    /**
     * Simple pattern matching for dynamic routes.
     * This matches the logic used in AuthMiddleware.
     */
    private boolean matchesRoutePattern(String pattern, String path) {
        JawsLogger.debug("RequestValidationMiddleware: Matching route pattern: {} for path: {}", pattern, path);
        
        // Handle exact matches
        if (pattern.equals(path)) {
            JawsLogger.debug("RequestValidationMiddleware: Exact match found");
            return true;
        }

        // Handle dynamic parameters like /users/:id
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        if (patternParts.length != pathParts.length) {
            JawsLogger.debug("RequestValidationMiddleware: Length mismatch: pattern length: {}, path length: {}", patternParts.length, pathParts.length);
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            
            // Skip dynamic parts (parameters starting with :)
            if (patternPart.startsWith(":")) {
                JawsLogger.debug("RequestValidationMiddleware: Dynamic part found: {}", patternPart);
                continue;
            }
            
            // Must match exactly for non-dynamic parts
            if (!patternPart.equals(pathPart)) {
                JawsLogger.debug("RequestValidationMiddleware: Mismatch found: pattern part: {}, path part: {}", patternPart, pathPart);
                return false;
            }
        }

        JawsLogger.debug("RequestValidationMiddleware: Route pattern matches path");
        return true;
    }

    /**
     * Extracts the field name from Jackson's error message for unknown fields.
     * Example: "Unrecognized field \"content\" (class ...)" -> "content"
     */
    private String extractFieldNameFromError(String errorMessage) {
        try {
            // Look for the pattern: "field \"fieldname\""
            int startQuote = errorMessage.indexOf('"');
            if (startQuote != -1) {
                int endQuote = errorMessage.indexOf('"', startQuote + 1);
                if (endQuote != -1) {
                    return errorMessage.substring(startQuote + 1, endQuote);
                }
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Sends an error response using the context's JSON response method.
     */
    private boolean sendErrorResponse(Yggdrasill.RequestContext context, APIResponse<String> response) {
        try {
            // Extract the response code from the APIResponse instead of hardcoding BAD_REQUEST
            ResponseCode responseCode = ResponseCode.fromCodeAndMessage(response.code());
            context.sendJSONResponse(responseCode, Bragi.encode(response));
            return true; // Successfully sent error response
        } catch (Exception e) {
            JawsLogger.error("RequestValidationMiddleware: Error sending validation error response: {}", e.getMessage());
            // Try to send a simple error response as fallback
            try {
                APIResponse<String> fallbackResponse = APIResponse.error(
                        ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                        "Request validation failed"
                );
                context.getResponse().setStatus(400);
                context.getResponse().setContentType("application/json; charset=UTF-8");
                context.getResponse().getWriter().write(Bragi.encode(fallbackResponse));
                context.getResponse().getWriter().flush();
                return true;
            } catch (Exception fallbackException) {
                JawsLogger.error("RequestValidationMiddleware: Failed to send fallback error response: {}", fallbackException.getMessage());
                return false; // Complete failure
            }
        }
    }

    @Override
    public int getOrder() {
        return order; // Execute after Auth but before route processing
    }

    /**
     * Helper class to encapsulate validation results.
     */
    private static class ValidationResult {
        final boolean success;
        final Object validatedObject;

        private ValidationResult(boolean success, Object validatedObject) {
            this.success = success;
            this.validatedObject = validatedObject;
        }

        static ValidationResult success(Object validatedObject) {
            return new ValidationResult(true, validatedObject);
        }

        static ValidationResult failure() {
            return new ValidationResult(false, null);
        }
    }
} 