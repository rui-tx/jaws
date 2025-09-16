package org.ruitx.jaws.components.sse;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.utils.logger.JawsLogger;

/**
 * Huginn: SSE hub that manages subscriptions and broadcasting per channel. - Global channel
 * requires login (enforced by route and double-checked here). - Future channels can configure
 * allowed roles.
 */
public class Huginn {

  public static final String GLOBAL_CHANNEL = "global";
  private static final Huginn INSTANCE = new Huginn();

  private final Map<String, CopyOnWriteArrayList<Client>> channels = new ConcurrentHashMap<>();
  private final Map<String, ChannelPolicy> policies = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  private Huginn() {
    // Seed default policy: global requires login, any role
    policies.put(GLOBAL_CHANNEL, ChannelPolicy.requireLogin());

    // Heartbeats every 15s
    scheduler.scheduleAtFixedRate(this::heartbeatAll, 15, 15, TimeUnit.SECONDS);
  }

  public static Huginn getInstance() {
    return INSTANCE;
  }

  public Optional<ChannelPolicy> getPolicy(String channel) {
    return Optional.ofNullable(policies.get(channel));
  }

  public void setPolicy(String channel, ChannelPolicy policy) {
    Objects.requireNonNull(channel);
    Objects.requireNonNull(policy);
    policies.put(channel, policy);
  }

  /**
   * Subscribe current request to channel via AsyncContext and set SSE headers.
   */
  public Optional<Client> subscribe(String channel, Yggdrasill.RequestContext ctx) {
    try {
      final String token = ctx.getCurrentToken();
      final List<String> roles = token != null && !token.isBlank()
          ? Tyr.getUserRolesFromJWT(token)
          : new ArrayList<>();
      final String userId = token != null && !token.isBlank() ? Tyr.getUserIdFromJWT(token) : "";

      ChannelPolicy policy = policies.getOrDefault(channel, ChannelPolicy.publicChannel());
      if (!policy.allows(roles)) {
        ctx.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Optional.empty();
      }

      // Prepare response for SSE
      HttpServletResponse resp = ctx.getResponse();
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setHeader("Content-Type", "text/event-stream; charset=utf-8");
      resp.setHeader("Cache-Control", "no-cache, no-transform");
      resp.setHeader("Connection", "keep-alive");

      // Start async
      AsyncContext async = ctx.getRequest().startAsync();
      async.setTimeout(0); // keep open

      PrintWriter writer = resp.getWriter();
      Client client = new Client(channel, userId, roles, async, writer);

      channels.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(client);

      // Initial comment to establish stream
      client.sendRaw(": connected\n\n");

      JawsLogger.trace("Huginn: client subscribed to channel {} (user={})", channel, userId);
      return Optional.of(client);

    } catch (IOException e) {
      JawsLogger.error("Huginn: subscribe failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Broadcast an event to all subscribers in the channel. Returns number of recipients.
   */
  private int broadcast(String channel, String event, String data) {
    List<Client> list = channels.getOrDefault(channel, new CopyOnWriteArrayList<>());
    if (list.isEmpty()) {
      return 0;
    }

    String payload = formatEvent(event, data);
    int sent = 0;
    for (Client c : list) {
      boolean ok = c.sendRaw(payload);
      if (!ok) {
        // Remove broken client
        close(c);
      } else {
        sent++;
      }
    }
    return sent;
  }

  // Overload using EventType
  public int broadcast(String channel, EventType event, String data) {
    Objects.requireNonNull(event);
    return broadcast(channel, event.eventName(), data);
  }

  // Filtered broadcast: only to users with at least one of the given roles.
  public int broadcastToRoles(String channel, Set<String> roles, EventType event, String data) {
    Objects.requireNonNull(event);
    if (roles == null || roles.isEmpty()) {
      return 0;
    }
    List<Client> list = channels.getOrDefault(channel, new CopyOnWriteArrayList<>());
    if (list.isEmpty()) {
      return 0;
    }
    String payload = formatEvent(event.eventName(), data);
    int sent = 0;
    for (Client c : list) {
      if (hasAnyRole(c.roles, roles)) {
        boolean ok = c.sendRaw(payload);
        if (!ok) {
          close(c);
        } else {
          sent++;
        }
      }
    }
    return sent;
  }

  // Filtered broadcast: only to a specific userId
  public int broadcastToUser(String channel, String userId, EventType event, String data) {
    Objects.requireNonNull(event);
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    List<Client> list = channels.getOrDefault(channel, new CopyOnWriteArrayList<>());
    if (list.isEmpty()) {
      return 0;
    }
    String payload = formatEvent(event.eventName(), data);
    int sent = 0;
    for (Client c : list) {
      if (userId.equals(c.userId)) {
        boolean ok = c.sendRaw(payload);
        if (!ok) {
          close(c);
        } else {
          sent++;
        }
      }
    }
    return sent;
  }

  private boolean hasAnyRole(List<String> userRoles, Set<String> requiredRoles) {
    if (userRoles == null || userRoles.isEmpty()) {
      return false;
    }
    for (String r : userRoles) {
      if (requiredRoles.contains(r)) {
        return true;
      }
    }
    return false;
  }

  private String formatEvent(String event, String data) {
    StringBuilder sb = new StringBuilder();
    if (event != null && !event.isBlank()) {
      sb.append("event: ").append(event).append('\n');
    }
    if (data != null) {
      for (String line : data.split("\n")) {
        sb.append("data: ").append(line).append('\n');
      }
    } else {
      sb.append("data: ").append("{}").append('\n');
    }
    sb.append('\n');
    return sb.toString();
  }

  /**
   * Send heartbeats to all clients.
   */
  private void heartbeatAll() {
    try {
      for (Map.Entry<String, CopyOnWriteArrayList<Client>> e : channels.entrySet()) {
        for (Client c : e.getValue()) {
          boolean ok = c.sendRaw(": keep-alive\n\n");
          if (!ok) {
            close(c);
          }
        }
      }
    } catch (Exception ex) {
      JawsLogger.debug("Huginn: heartbeat error: {}", ex.getMessage());
    }
  }

  /**
   * Close and cleanup the client subscription.
   */
  public void close(Client client) {
    try {
      CopyOnWriteArrayList<Client> list = channels.get(client.channel);
      if (list != null) {
        list.remove(client);
      }
      client.async.complete();
    } catch (Exception ignored) {
    }
  }

  // Event types supported by SSE. Add more as needed.
  public enum EventType {
    NOTIFICATION("notification"),
    CONNECTION("connection");

    private final String eventName;

    EventType(String eventName) {
      this.eventName = eventName;
    }

    public String eventName() {
      return eventName;
    }
  }

  /**
   * Represents a subscribed client.
   */
  public static class Client {

    public final String channel;
    public final String userId;
    public final List<String> roles;
    public final AsyncContext async;
    private final PrintWriter writer;
    private volatile long lastWriteEpochSec = Instant.now().getEpochSecond();

    private Client(
        String channel,
        String userId,
        List<String> roles,
        AsyncContext async,
        PrintWriter writer) {
      this.channel = channel;
      this.userId = userId;
      this.roles = roles != null ? List.copyOf(roles) : List.of();
      this.async = async;
      this.writer = writer;
    }

    public synchronized boolean sendRaw(String raw) {
      try {
        writer.write(raw);
        writer.flush();
        lastWriteEpochSec = Instant.now().getEpochSecond();
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    public long lastWriteEpochSec() {
      return lastWriteEpochSec;
    }
  }

  /**
   * Policy for channel access.
   */
  public static class ChannelPolicy {

    private final boolean requireLogin;
    private final Set<String> allowedRoles; // empty = any authenticated role

    private ChannelPolicy(boolean requireLogin, Set<String> allowedRoles) {
      this.requireLogin = requireLogin;
      this.allowedRoles = allowedRoles == null ? Collections.emptySet() : Set.copyOf(allowedRoles);
    }

    public static ChannelPolicy publicChannel() {
      return new ChannelPolicy(false, Collections.emptySet());
    }

    public static ChannelPolicy requireLogin() {
      return new ChannelPolicy(true, Collections.emptySet());
    }

    public static ChannelPolicy requireRoles(Set<String> roles) {
      return new ChannelPolicy(true, roles);
    }

    public boolean allows(List<String> userRoles) {
      if (!requireLogin) {
        return true;
      }
      if (userRoles == null || userRoles.isEmpty()) {
        return false;
      }
      if (userRoles.contains("admin")) {
        return true;
      }
      if (allowedRoles.isEmpty()) {
        return true; // any authenticated role
      }
      for (String r : userRoles) {
        if (allowedRoles.contains(r)) {
          return true;
        }
      }
      return false;
    }
  }
}
