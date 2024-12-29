package org.ruitx.jaws.aspects;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ruitx.jaws.exceptions.APIException;
import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.exceptions.ConnectionException;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;

@Aspect
public class ExceptionAspect {

    // Pointcut to match any method in RequestHandler
    @Pointcut("execution(* org.ruitx.jaws.components.Yggdrasill.RequestHandler.*(..))")
    public void requestHandlerMethods() {
    }

    @Pointcut("execution(* org.ruitx.jaws.utils.APIHandler.*(..))")
    public void apiHandlerMethods() {
    }

    @AfterThrowing(pointcut = "requestHandlerMethods() || apiHandlerMethods()", throwing = "ex")
    public void handleException(Throwable ex) {
        if (ex instanceof ConnectionException) {
            Logger.error("ConnectionException occurred: {}", ex.getMessage());
        } else if (ex instanceof SocketTimeoutException) {
            Logger.error("SocketTimeoutException occurred: {}", ex.getMessage());
        } else if (ex instanceof IOException) {
            Logger.error("IOException occurred: {}", ex.getMessage());
        } else if (ex instanceof APIParsingException) {
            Logger.error("API Parsing Error: {}", ex.getMessage());
        } else if (ex instanceof APIException) {
            Logger.error("API Error: {}", ex.getMessage());
        } else {
            Logger.error("An unexpected exception occurred: {}", ex.getMessage());
        }
    }

//    Disabled for now, as I need to test it
//    @Around("requestHandlerMethods() || apiHandlerMethods()")
//    public Object handleException(ProceedingJoinPoint joinPoint) throws Throwable {
//        Object result = null;
//        try {
//            result = joinPoint.proceed(); // Proceed with the method execution
//        } catch (Throwable ex) {
//            // Handle the exception
//            if (ex instanceof APIParsingException) {
//                Logger.error("API Parsing error: {}", ex.getMessage());
//                sendErrorResponse(ex, "Failed to parse JSON response", joinPoint);
//            } else if (ex instanceof APIException) {
//                Logger.error("API error: {}", ex.getMessage());
//                sendErrorResponse(ex, "API error", joinPoint);
//            } else {
//                Logger.error("Unexpected error: {}", ex.getMessage());
//            }
//        }
//        return result;
//    }
//
//    private void sendErrorResponse(Throwable ex, String reason, ProceedingJoinPoint joinPoint) {
//        // Extract RequestHandler (rh) from the method arguments (if it's the first argument)
//        for (Object arg : joinPoint.getArgs()) {
//            System.out.println(arg.getClass());
//        }
//        for (Object arg : joinPoint.getArgs()) {
//            if (arg instanceof Yggdrasill.RequestHandler rh) {
//                try {
//                    // Create the APIResponse and send it back to the client
//                    APIResponse<String> response = new APIResponse<>(false, null, reason + ": " + ex.getMessage());
//                    rh.sendJSONResponse(ResponseCode.INTERNAL_SERVER_ERROR, APIHandler.encode(response));
//                } catch (IOException e) {
//                    Logger.error("Error sending error response: {}", e.getMessage());
//                }
//                break;
//            }
//        }
//    }
}
