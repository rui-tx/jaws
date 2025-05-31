package org.ruitx.jaws.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.exceptions.APIException;
import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.exceptions.ConnectionException;
import org.ruitx.jaws.exceptions.SendRespondException;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.tinylog.Logger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketTimeoutException;

@Aspect
public class ExceptionAspect {

    // Pointcut to match any method in controllers
    @Pointcut("execution(* org.ruitx.www.controllers..*(..))")
    public void controllerMethods() {
    }

    // Pointcut to match any method in JettyServer.RequestContext
    @Pointcut("execution(* org.ruitx.jaws.components.JettyServer.RequestContext.*(..))")
    public void requestContextMethods() {
    }

    @Pointcut("execution(* org.ruitx.jaws.components.Bragi.*(..))")
    public void apiHandlerMethods() {
    }

    @Around("controllerMethods() || requestContextMethods() || apiHandlerMethods()")
    public Object handleException(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception ex) {
            handleException(ex, joinPoint);
            throw ex; // Re-throw the exception after handling
        }
    }

    private void handleException(Throwable ex, ProceedingJoinPoint joinPoint) {
        // Log the exception
        Logger.error("Exception occurred: {}", ex.getMessage(), ex);

        // Try to send error response if we have access to a controller or RequestContext
        try {
            sendErrorResponseFromController(ex, joinPoint);
        } catch (Exception e) {
            Logger.error("Failed to send error response: {}", e.getMessage());
        }
    }

    private void sendErrorResponseFromController(Throwable ex, ProceedingJoinPoint joinPoint) {
        // Check if the target is a Bragi controller (preferred approach)
        if (joinPoint.getTarget() instanceof Bragi controller) {
            sendErrorResponseThroughController(ex, controller);
            return;
        }

        // Try to get RequestContext directly
        Yggdrasill.RequestContext requestContext = getRequestContext(joinPoint);
        if (requestContext != null) {
            sendErrorResponseNew(ex, requestContext);
        }
    }

    private void sendErrorResponseThroughController(Throwable ex, Bragi controller) {
        try {
            ResponseCode responseCode = determineResponseCode(ex);
            String errorMessage = ex.getMessage();

            // Use the controller's sendErrorResponse method
            controller.sendErrorResponse(responseCode, errorMessage != null ? errorMessage : "An unexpected error occurred");
        } catch (Exception e) {
            Logger.error("Failed to send error response through controller: {}", e.getMessage());
        }
    }

    private Yggdrasill.RequestContext getRequestContext(ProceedingJoinPoint joinPoint) {
        // Check method arguments
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Yggdrasill.RequestContext) {
                return (Yggdrasill.RequestContext) arg;
            }
        }

        return null;
    }

    private void sendErrorResponseNew(Throwable ex, Yggdrasill.RequestContext requestContext) {
        try {
            ResponseCode responseCode = determineResponseCode(ex);
            String errorMessage = ex.getMessage();

            APIResponse<String> response = APIResponse.error(
                    responseCode.getCodeAndMessage(),
                    errorMessage != null ? errorMessage : "An unexpected error occurred"
            );

            // Only try to send response if connection is still alive
            if (!requestContext.isConnectionClosed()) {
                requestContext.sendJSONResponse(responseCode, Bragi.encode(response));
            }
        } catch (Exception e) {
            // Log but don't try to send more responses
            Logger.error("Failed to send error response: {}", e.getMessage());
        }
    }

    private ResponseCode determineResponseCode(Throwable ex) {
        //This happens because the controller method is being invoked through reflection
        // at invokeRouteMethod in JettyServer, so we get the actual cause from the thrown exception
        if (ex instanceof InvocationTargetException && ex.getCause() != null) {
            ex = ex.getCause();
        }

        if (ex instanceof SendRespondException) {
            return ResponseCode.INTERNAL_SERVER_ERROR;
        } else if (ex instanceof APIParsingException) {
            return ResponseCode.BAD_REQUEST;
        } else if (ex instanceof APIException) {
            return ResponseCode.SERVICE_UNAVAILABLE;
        } else if (ex instanceof ConnectionException || ex instanceof SocketTimeoutException) {
            return ResponseCode.GATEWAY_TIMEOUT;
        } else if (ex instanceof IOException) {
            return ResponseCode.INTERNAL_SERVER_ERROR;
        } else {
            return ResponseCode.INTERNAL_SERVER_ERROR;
        }
    }
}
