package org.ruitx.jaws.configs.bifrost;

import java.util.List;
import org.ruitx.jaws.components.bifrost.Middleware;
import org.ruitx.jaws.configs.bifrost.middleware.AuthMiddleware;
import org.ruitx.jaws.configs.bifrost.middleware.CorsMiddleware;
import org.ruitx.jaws.configs.bifrost.middleware.LoggingMiddleware;
import org.ruitx.jaws.configs.bifrost.middleware.RateLimiterMiddleware;
import org.ruitx.jaws.configs.bifrost.middleware.RequestValidationMiddleware;

/**
 * Configuration class for middleware setup. Add your middleware here to be automatically registered
 * with the server.
 */
public class BifrostConfig {

  /**
   * List of middleware to be registered with the server. Middleware will be executed in order of
   * their priority (getOrder() method).
   */
  public static final List<Middleware> MIDDLEWARE = List.of(
      new LoggingMiddleware(1),
      new RateLimiterMiddleware(2),
      new CorsMiddleware(3),
      new AuthMiddleware(4),
      new RequestValidationMiddleware(5)
  );
} 