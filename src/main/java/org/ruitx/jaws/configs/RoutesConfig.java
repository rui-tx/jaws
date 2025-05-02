package org.ruitx.jaws.configs;

import org.ruitx.www.controllers.AuthController;
import org.ruitx.www.controllers.APIController;
import org.ruitx.www.controllers.TodoController;

import java.util.List;

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new AuthController(),
            new APIController(),
            new TodoController()
    );
}
