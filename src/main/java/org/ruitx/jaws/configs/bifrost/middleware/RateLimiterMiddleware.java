package org.ruitx.jaws.configs.bifrost.middleware;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.ruitx.jaws.components.bifrost.Middleware;
import org.ruitx.jaws.components.bifrost.MiddlewareChain;
import org.ruitx.jaws.components.yggdrasill.Yggdrasill;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.utils.logger.JawsLogger;

/**
 * RateLimiterMiddleware prevents clients from making too many requests in a short time period.
 */
public class RateLimiterMiddleware implements Middleware {

  // Store request counts by IP
  private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

  private final int maxRequestsPerWindow = ApplicationConfig.RATE_LIMIT_MAX_REQUESTS;
  private final int windowSizeMs = ApplicationConfig.RATE_LIMIT_WINDOW_MS;

  private int order = 10;

  public RateLimiterMiddleware(int order) {
    this.order = order;
  }

  @Override
  public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
    String clientIp = context.getClientIpAddress();

    JawsLogger.debug(
        UUID.fromString(context.getTraceId()),
        "RateLimiterMiddleware: Checking rate limit for IP {}",
        clientIp);

    // Get or create counter for this IP
    RequestCounter counter = requestCounts.computeIfAbsent(
        clientIp, ip -> new RequestCounter(windowSizeMs)
    );

    // Check if rate limit is exceeded
    if (counter.incrementAndCheck() > maxRequestsPerWindow) {
      JawsLogger.warn(
          UUID.fromString(context.getTraceId()),
          "Rate limit exceeded for IP: {}",
          clientIp);

      // Calculate seconds until window reset
      long secondsUntilReset = (counter.getNextResetTimeMs() - System.currentTimeMillis()) / 1000;
      secondsUntilReset = Math.max(1, secondsUntilReset); // Ensure at least 1 second

      // Set rate limit headers
      context.getResponse().setHeader(
          "X-RateLimit-Limit",
          String.valueOf(maxRequestsPerWindow));
      context.getResponse()
          .setHeader(
              "X-RateLimit-Reset",
              String.valueOf(counter.getNextResetTimeMs() / 1000));
      context.getResponse().setHeader("Retry-After", String.valueOf(secondsUntilReset));
      context.getResponse().setStatus(429);
      context.getResponse().setContentType("application/json");
      try {
        context.getResponse().getWriter().write(
            "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests, please try again later\"}");
      } catch (Exception e) {
        JawsLogger.error(
            UUID.fromString(context.getTraceId()),
            e.getCause(),
            "Error sending rate limit response: {}",
            e.getMessage());
      }

      return false;
    }

    return chain.next();
  }

  @Override
  public int getOrder() {
    return order;
  }

  /**
   * Helper class to track request counts within a time window
   */
  private static class RequestCounter {

    private final long windowSizeMs;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private long windowStartTimeMs;

    public RequestCounter(long windowSizeMs) {
      this.windowSizeMs = windowSizeMs;
      this.windowStartTimeMs = System.currentTimeMillis();
    }

    public int incrementAndCheck() {
      long currentTime = System.currentTimeMillis();
      if (currentTime - windowStartTimeMs > windowSizeMs) {
        // Reset window if it has expired
        requestCount.set(1);
        windowStartTimeMs = currentTime;
        return 1;
      }

      return requestCount.incrementAndGet();
    }

    public long getNextResetTimeMs() {
      return windowStartTimeMs + windowSizeMs;
    }
  }
}