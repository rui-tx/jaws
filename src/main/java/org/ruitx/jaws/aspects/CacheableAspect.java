package org.ruitx.jaws.aspects;

import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Cacheable;
import org.tinylog.Logger;

/**
 * AspectJ aspect that enables Mimir caching for methods annotated with @Cacheable.
 */
@Aspect
public class CacheableAspect {

  @Around("@annotation(cacheable)")
  public Object aroundCacheableMethod(ProceedingJoinPoint pjp, Cacheable cacheable)
      throws Throwable {
    Logger.trace("CacheableAspect: Enabling Mimir cache for method: {} with tables: {}",
        pjp.getSignature(), String.join(", ", cacheable.tables()));
    boolean previous = Mimir.isCacheAllowedForCurrentThread();
    try {
      Mimir.enableCacheForCurrentThread();
      Set<String> tbls = Set.of(cacheable.tables());
      Mimir.setCurrentTables(tbls);
      Mimir.setCurrentTtl(cacheable.ttl());
      return pjp.proceed();
    } finally {
      Mimir.clearCurrentTables();
      Mimir.clearCurrentTtl();
      // Restore previous state to avoid leaking across nested calls
      if (!previous) {
        Mimir.disableCacheForCurrentThread();
      }
    }
  }
}