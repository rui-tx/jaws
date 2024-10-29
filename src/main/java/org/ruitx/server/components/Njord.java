package org.ruitx.server.components;

import org.ruitx.server.interfaces.Route;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Njord {
    private static final Njord INSTANCE = new Njord();
    private final Map<String, Map<String, Method>> routes = new HashMap<>();
    private final Map<String, Object> controllers = new HashMap<>();

    private Njord() {
    }

    public static Njord getInstance() {
        return INSTANCE;
    }

    public void registerRoutes(Object controller) {
        String controllerName = controller.getClass().getSimpleName();
        controllers.put(controllerName, controller);

        for (Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class)) {
                Route route = method.getAnnotation(Route.class);
                routes
                        .computeIfAbsent(route.endpoint(), k -> new HashMap<>())
                        .put(route.method(), method);
            }
        }
    }

    public Method getRoute(String path, String method) {
        Map<String, Method> methodMap = routes.get(path);
        return methodMap != null ? methodMap.get(method) : null;
    }

    public Object getControllerInstance(String controllerName) {
        return controllers.get(controllerName);
    }
}
