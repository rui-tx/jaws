package org.ruitx.jaws.configs;

import org.ruitx.www.controller.*;

import java.util.List;

/**
 * Configuration class for dynamic routes in the application.
 * This class holds a list of route controllers that will be registered with the Yggdrasill framework.
 */
public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new AuthController(),
            new PasteitController(),
            new BackofficeController(),
            new APIController(),
            new JobController(),
            new ImageController(),
            new AdminController()
    );
}
