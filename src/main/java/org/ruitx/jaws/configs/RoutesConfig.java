package org.ruitx.jaws.configs;

import org.ruitx.www.API;
import org.ruitx.www.Auth;

import java.util.List;

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new Auth(),
            new API()
    );
}
