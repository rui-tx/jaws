package org.ruitx.jaws.configs;

import java.util.List;
import org.ruitx.www.base.controller.AuthController;
import org.ruitx.www.base.controller.BackofficeController;
import org.ruitx.www.base.controller.CacheController;
import org.ruitx.www.base.controller.EventsController;
import org.ruitx.www.controller.APIController;

/**
 * Configuration class for dynamic routes in the application. This class holds a list of route
 * controllers that will be registered with the Yggdrasill framework.
 */
public class RoutesConfig {

  // All the dynamic routes that will be registered
  // File paths are not needed here, as they are handled by Yggdrasill

  public static final List<Object> ROUTES = List.of(
      new AuthController(),
      new APIController(),
      new BackofficeController(),
      new CacheController(),
      new EventsController()
  );
}
