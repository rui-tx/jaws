package org.ruitx.jaws.configs;

import org.ruitx.www.examples.Auth;
import org.ruitx.www.examples.Todo;
import org.ruitx.www.examples.gallery.controller.GalleryAPIController;
import org.ruitx.www.examples.gallery.controller.GalleryController;

import java.util.List;

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new Todo(),
            new GalleryAPIController(),
            new GalleryController(),
            new Auth()
    );
}
