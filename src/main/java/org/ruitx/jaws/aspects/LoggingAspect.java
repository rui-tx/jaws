package org.ruitx.jaws.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.utils.JawsLogger;

@Aspect
public class LoggingAspect {

    @Before("execution(* org.ruitx.jaws.components.JettyServer.start(..))")
    public void serverStart(JoinPoint joinPoint) {
        JawsLogger.info("JAWS started at port {} with www path: {}",
                Yggdrasill.currentPort,
                Yggdrasill.currentResourcesPath);
    }
}