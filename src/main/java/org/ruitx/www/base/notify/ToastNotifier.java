package org.ruitx.www.base.notify;

import java.util.HashMap;
import java.util.Map;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.components.sse.Huginn;
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
   * Broadcast a generic toast.
   *
   * @param title       the toast title (defaults to "Notification" if blank)
   * @param description the toast description (empty if null)
   * @return true if rendered and broadcast successfully; false otherwise
   */
  public static boolean broadcastToast(String title, String description) {
    String safeTitle = (title == null || title.isBlank()) ? "Notification" : title;
    String safeDescription = (description == null) ? "" : description;

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("title", safeTitle);
    ctx.put("description", safeDescription);

    String html = Hermod.renderFragmentHeadless(TEMPLATE, FRAGMENT, ctx);
    if (html == null || html.isBlank()) {
      Logger.warn("ToastNotifier: empty HTML for toast '{}'. Not broadcasting.", safeTitle);
      return false;
    }

    Huginn.getInstance().broadcast(Huginn.GLOBAL_CHANNEL, "notification", html);
    return true;
  }
}
