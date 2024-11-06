package org.ruitx.server.configs;

import org.ruitx.server.controllers.Auth;
import org.ruitx.server.controllers.Todo;

import java.util.List;

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new Todo(),
            new Auth()
    );
}
