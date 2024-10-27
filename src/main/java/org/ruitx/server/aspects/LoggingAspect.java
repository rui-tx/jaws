package org.ruitx.server.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.ruitx.server.Yggdrasill;
import org.ruitx.server.strings.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Before("execution(* org.ruitx.server.Yggdrasill.start(..))")
    public void serverStart(JoinPoint joinPoint) {
        logger.info("{} at port {} with www path: {}",
                Messages.SERVER_STARTED,
                Yggdrasill.currentPort,
                Yggdrasill.currentResourcesPath);
    }


//    @Around("execution(* org.ruitx.server.Yggdrasill$RequestHandler.processRequest(..))")
//    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
//        long start = System.currentTimeMillis();
//        Object test = joinPoint.getArgs();
//        Object result = joinPoint.proceed();
//        long timeItTook = System.currentTimeMillis() - start;
//        logger.info("Request time: {} ms [{}]", timeItTook, test);
//        return result;
//    }
}