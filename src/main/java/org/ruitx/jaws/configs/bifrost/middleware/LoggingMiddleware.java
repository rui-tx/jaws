package org.ruitx.jaws.configs.bifrost.middleware;

import java.util.UUID;
import org.ruitx.jaws.components.bifrost.Middleware;
import org.ruitx.jaws.components.bifrost.MiddlewareChain;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.utils.logger.JawsLogger;

/**
 * LoggingMiddleware logs incoming HTTP requests for debugging and monitoring.
 */
public class LoggingMiddleware implements Middleware {

  private int order = 10;

  public LoggingMiddleware(int order) {
    this.order = order;
  }

  @Override
  public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
    try {
      String traceId = context.getTraceId();
      String method = context.getRequest().getMethod();
      String uri = context.getRequest().getRequestURI();
      String queryString = context.getRequest().getQueryString();
      String fullUrl = queryString != null ? uri + "?" + queryString : uri;

      JawsLogger.debug(
          UUID.fromString(traceId),
          "LoggingMiddleware: Handling request {} {}",
          method,
          fullUrl);

      return chain.next();

    } catch (Exception e) {
      JawsLogger.error("Error in LoggingMiddleware: {}", e.getMessage(), e);
      return chain.next(); // Continue on error
    }
  }

  @Override
  public int getOrder() {
    return order; // Execute very early in the chain
  }
} 