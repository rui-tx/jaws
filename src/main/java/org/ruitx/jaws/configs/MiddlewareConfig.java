package org.ruitx.jaws.configs;

import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.middleware.AuthMiddleware;
import org.ruitx.jaws.middleware.CorsMiddleware;
import org.ruitx.jaws.middleware.LoggingMiddleware;
import org.ruitx.jaws.middleware.RateLimiterMiddleware;
import org.ruitx.jaws.middleware.RequestValidationMiddleware;

import java.util.List;

/**
 * Configuration class for middleware setup.
 * Add your middleware here to be automatically registered with the server.
 */
public class MiddlewareConfig {

    /**
     * List of middleware to be registered with the server.
     * Middleware will be executed in order of their priority (getOrder() method).
     */
    public static final List<Middleware> MIDDLEWARE = List.of(
            new LoggingMiddleware(1),
            new RateLimiterMiddleware(2),
            new CorsMiddleware(3),
            new AuthMiddleware(4),
            new RequestValidationMiddleware(5)
    );
} 