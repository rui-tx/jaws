package org.ruitx.jaws.aspects;

import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.exceptions.AuthenticationException;
import org.ruitx.jaws.exceptions.OperationException;
import org.ruitx.jaws.exceptions.ValidationException;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.logger.JawsLogger;

@Aspect
public class ExceptionAspect {

  // Pointcut to match any method in controllers
  @Pointcut("execution(* org.ruitx.www..controller..*(..))")
  public void controllerMethods() {
  }

  // Pointcut to match any method in Yggdrasill.RequestContext
  @Pointcut("execution(* org.ruitx.jaws.components.Yggdrasill.RequestContext.*(..))")
  public void requestContextMethods() {
  }

  // Pointcut to match any method in Bragi
  @Pointcut("execution(* org.ruitx.jaws.components.Bragi.*(..))")
  public void apiHandlerMethods() {
  }

  @Around("controllerMethods() || requestContextMethods() || apiHandlerMethods()")
  public Object handleException(ProceedingJoinPoint joinPoint) {
    try {
      return joinPoint.proceed();
    } catch (Throwable ex) {
      handleException(ex, joinPoint);
      return null; // Prevent further processing
    }
  }

  private void handleException(Throwable ex, ProceedingJoinPoint joinPoint) {
    // Log the exception with stack trace for debugging
    JawsLogger.error("Exception occurred: {}", ex.getMessage(), ex);

    try {
      if (joinPoint.getTarget() instanceof Bragi controller) {
        handleControllerException(ex, controller);
      } else {
        Yggdrasill.RequestContext context = getRequestContext(joinPoint);
        if (context != null) {
          handleRequestContextException(ex, context);
        }
      }
    } catch (Exception e) {
      JawsLogger.error("Failed to handle exception: {}", e.getMessage(), e);
    }
  }

  private void handleControllerException(Throwable ex, Bragi controller) {
    if (ex instanceof OperationException oe) {
      handleOperationException(oe, controller);
    } else if (ex instanceof ValidationException ve) {
      handleValidationException(ve, controller);
    } else if (ex instanceof AuthenticationException ae) {
      handleAuthenticationException(ae, controller);
    } else if (ex.getCause() instanceof OperationException oe) {
      handleOperationException(oe, controller);
    } else if (ex.getCause() instanceof AuthenticationException ae) {
      handleAuthenticationException(ae, controller);
    } else if (ex.getCause() instanceof ValidationException ve) {
      handleValidationException(ve, controller);
    } else {
      handleUnexpectedException(ex, controller);
    }
  }

  private void handleRequestContextException(Throwable ex, Yggdrasill.RequestContext context) {
    // Handle exceptions when we only have access to RequestContext
    ResponseCode code = ResponseCode.INTERNAL_SERVER_ERROR;
    String message = "An unexpected error occurred";
    Map<String, Object> errorDetails = new HashMap<>();

    if (ex instanceof OperationException oe) {
      code = oe.getError().getResponseCode();
      message = oe.getMessage();
    } else if (ex.getCause() instanceof OperationException oe) {
      code = oe.getError().getResponseCode();
      message = oe.getMessage();
    }

    sendErrorResponse(context, code, message, errorDetails);
  }

  private void handleOperationException(OperationException ex, Bragi controller) {
    controller.sendFail(ex.getError().getResponseCode(), ex.getMessage());
  }

  private void handleValidationException(ValidationException ex, Bragi controller) {
    Map<String, Object> errorDetails = new HashMap<>();
    errorDetails.put("field", ex.getField());
    errorDetails.put("rejectedValue", ex.getRejectedValue());

    controller.sendFail(
        false, // success
        ex.getError().getResponseCode(), // response code
        ex.getMessage(), // error message
        errorDetails // error details
    );
  }

  private void handleAuthenticationException(AuthenticationException ex, Bragi controller) {
    controller.sendFail(ex.getError().getResponseCode(), ex.getMessage());
  }

  private void handleUnexpectedException(Throwable ex, Bragi controller) {
    JawsLogger.error("Unexpected error: {}", ex.getMessage(), ex);
    controller.sendFail(
        ResponseCode.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred: " + ex.getMessage()
    );
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

  private void sendErrorResponse(Yggdrasill.RequestContext requestContext,
      ResponseCode code,
      String message,
      Map<String, Object> details) {
    try {
      APIResponse<Map<String, Object>> response;

      if (details != null && !details.isEmpty()) {
        response = new APIResponse<>(
            false,
            code.getCodeAndMessage(),
            System.currentTimeMillis() / 1000L,
            message != null ? message : "An unexpected error occurred",
            details
        );
      } else {
        response = APIResponse.error(
            code,
            message != null ? message : "An unexpected error occurred"
        );
      }

      requestContext.sendJSONResponse(
          code,
          Bragi.encode(response)
      );
    } catch (Exception e) {
      JawsLogger.error("Failed to send error response: {}", e.getMessage(), e);
    }
  }
}
