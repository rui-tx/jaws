package org.ruitx.server.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.strings.Messages;
import org.tinylog.Logger;

@Aspect
public class LoggingAspect {

    @Before("execution(* org.ruitx.server.components.Yggdrasill.start(..))")
    public void serverStart(JoinPoint joinPoint) {
        Logger.info("{} at port {} with www path: {}",
                Messages.SERVER_STARTED,
                Yggdrasill.currentPort,
                Yggdrasill.currentResourcesPath);
    }
}