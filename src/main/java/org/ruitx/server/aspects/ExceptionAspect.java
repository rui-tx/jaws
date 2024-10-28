package org.ruitx.server.aspects;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ruitx.server.exceptions.ConnectionException;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;

@Aspect
public class ExceptionAspect {

    // Pointcut to match any method in RequestHandler
    @Pointcut("execution(* org.ruitx.server.components.Yggdrasill.RequestHandler.*(..))")
    public void requestHandlerMethods() {
    }

    @AfterThrowing(pointcut = "requestHandlerMethods()", throwing = "ex")
    public void handleException(Throwable ex) {
        if (ex instanceof ConnectionException) {
            Logger.error("ConnectionException occurred: {}", ex.getMessage());
        } else if (ex instanceof SocketTimeoutException) {
            Logger.error("SocketTimeoutException occurred: {}", ex.getMessage());
        } else if (ex instanceof IOException) {
            Logger.error("IOException occurred: {}", ex.getMessage());
        } else {
            Logger.error("An unexpected exception occurred: {}", ex.getMessage());
        }
    }
}
