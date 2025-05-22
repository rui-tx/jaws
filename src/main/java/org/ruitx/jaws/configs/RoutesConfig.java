package org.ruitx.jaws.configs;

import java.util.List;

import org.ruitx.www.controller.PasteitController;
import org.ruitx.www.controller.AuthController;
import org.ruitx.www.controller.BackofficeController;

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new AuthController(),
            new PasteitController(),
            new BackofficeController()
    );
}
