package org.ruitx.www.base.controller;

import static org.ruitx.jaws.enums.RequestType.GET;
import static org.ruitx.jaws.enums.ResponseCode.OK;
import static org.ruitx.jaws.enums.ResponseType.JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.njord.Route;
import org.ruitx.jaws.components.yggdrasill.AccessControl;
import org.ruitx.jaws.components.yggdrasill.Bragi;

/**
 * Debug controller to inspect the current Mimir query cache.  EXPOSE ONLY IN DEVELOPMENT!
 */
public class CacheController extends Bragi {

  private static final String API_ENDPOINT = "/api/v1/cache/";

  /**
   * Returns a snapshot of the current SQL query cache managed by {@link Mimir}.
   * <p>
   * Example response:
   * <pre>
   * {
   *   "size": 3,
   *   "entries": [
   *     {
   *       "sql": "select * from user where id = ?",
   *       "params": [1],
   *       "value": {"id":1,"user":"admin", ...},
   *       "ttlNanos": 123456789
   *     },
   *     ...
   *   ]
   * }
   * </pre>
   */
  @AccessControl(login = true, role = "admin")
  @Route(endpoint = API_ENDPOINT + "dump", method = GET, responseType = JSON)
  public void dumpCache() {
    List<Map<String, Object>> entries = Mimir.snapshotCache();
    Map<String, Object> payload = new HashMap<>();
    payload.put("size", entries.size());
    payload.put("entries", entries);
    sendSuccess(OK, payload);
  }
} 