package org.ruitx.server.aspects;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.ruitx.server.exceptions.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Aspect
public class ExceptionAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionAspect.class);

    // Pointcut to match any method in RequestHandler
    @Pointcut("execution(* org.ruitx.server.Yggdrasill.RequestHandler.*(..))")
    public void requestHandlerMethods() {
    }

    @AfterThrowing(pointcut = "requestHandlerMethods()", throwing = "ex")
    public void handleException(Throwable ex) {
        if (ex instanceof ConnectionException) {
            logger.error("ConnectionException occurred: {}", ex.getMessage());
        } else if (ex instanceof IOException) {
            logger.error("IOException occurred: {}", ex.getMessage());
        } else {
            logger.error("An unexpected exception occurred: {}", ex.getMessage());
        }
    }
}
