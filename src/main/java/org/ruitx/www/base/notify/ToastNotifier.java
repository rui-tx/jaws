package org.ruitx.www.base.notify;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.ruitx.jaws.components.Huginn;
import org.ruitx.jaws.components.hermod.Hermod;
import org.tinylog.Logger;

/**
 * ToastNotifier allows broadcasting toasts from arbitrary places in the codebase (e.g., background
 * jobs) by rendering the Thymeleaf toast fragment headlessly and publishing it via SSE (Huginn) to
 * the global channel.
 */
public final class ToastNotifier {

  private static final String TEMPLATE = "backoffice/components/toast/toast.html";
  private static final String FRAGMENT = "toast";

  private ToastNotifier() {
  }

  /**
   * Broadcast a generic toast to all subscribers of the channel.
   *
   * @param title       the toast title (defaults to "Notification" if blank)
   * @param description the toast description (empty if null)
   * @return true if rendered and broadcast successfully; false otherwise
   */
  public static boolean broadcastToast(String title, String description) {
    String html = renderToastHtml(title, description);
    if (html.isBlank()) {
      return false;
    }
    int sent = Huginn.getInstance()
        .broadcast(Huginn.GLOBAL_CHANNEL, Huginn.EventType.NOTIFICATION, html);
    return sent > 0;
  }

  /**
   * Broadcast a toast only to users with at least one of the provided roles.
   *
   * @param roles       set of role names; must not be empty
   * @param title       toast title
   * @param description toast description
   * @return true if at least one recipient received the toast; false otherwise
   */
  public static boolean broadcastToast(Set<String> roles, String title, String description) {
    if (roles == null || roles.isEmpty()) {
      return false;
    }
    String html = renderToastHtml(title, description);
    if (html.isBlank()) {
      return false;
    }
    int sent = Huginn.getInstance()
        .broadcastToRoles(Huginn.GLOBAL_CHANNEL, roles, Huginn.EventType.NOTIFICATION, html);
    return sent > 0;
  }

  /**
   * Broadcast a toast only to a specific user.
   *
   * @param userId      the user id (as encoded in your JWT)
   * @param title       toast title
   * @param description toast description
   * @return true if the toast was delivered to that user; false otherwise
   */
  public static boolean broadcastToast(String userId, String title, String description) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    String html = renderToastHtml(title, description);
    if (html.isBlank()) {
      return false;
    }
    int sent = Huginn.getInstance()
        .broadcastToUser(Huginn.GLOBAL_CHANNEL, userId, Huginn.EventType.NOTIFICATION, html);
    return sent > 0;
  }

  private static String renderToastHtml(String title, String description) {
    String safeTitle = (title == null || title.isBlank()) ? "Notification" : title;
    String safeDescription = (description == null) ? "" : description;

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("title", safeTitle);
    ctx.put("description", safeDescription);

    String html = Hermod.renderFragmentHeadless(TEMPLATE, FRAGMENT, ctx);
    if (html == null || html.isBlank()) {
      Logger.warn("ToastNotifier: empty HTML for toast '{}'. Not broadcasting.", safeTitle);
      return "";
    }
    return html;
  }
}
