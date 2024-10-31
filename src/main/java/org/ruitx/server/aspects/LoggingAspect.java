package org.ruitx.server.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.ruitx.server.components.Yggdrasill;
import org.tinylog.Logger;

@Aspect
public class LoggingAspect {

    @Before("execution(* org.ruitx.server.components.Yggdrasill.start(..))")
    public void serverStart(JoinPoint joinPoint) {
        Logger.info("JAWS started at port {} with www path: {}",
                Yggdrasill.currentPort,
                Yggdrasill.currentResourcesPath);
    }
}