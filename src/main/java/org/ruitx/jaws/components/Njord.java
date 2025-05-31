package org.ruitx.jaws.components;

import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.tinylog.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Njord {
    private static final Njord INSTANCE = new Njord();
    private final Map<String, Map<RequestType, Method>> routes = new HashMap<>();
    private final Map<String, Object> controllers = new HashMap<>();

    private Njord() {
    }
    
    public static Njord getInstance() {
        return INSTANCE;
    }

    public void registerRoutes(Object controller) {
        String controllerName = controller.getClass().getSimpleName();
        controllers.put(controllerName, controller);

        Logger.info("Registering routes for controller: {}", controllerName);

        for (Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class)) {
                Route route = method.getAnnotation(Route.class);
                routes
                        .computeIfAbsent(route.endpoint(), k -> new HashMap<>())
                        .put(route.method(), method);
                
                Logger.info("Registered route: {} {} -> {}.{}", 
                        route.method(), route.endpoint(), controllerName, method.getName());
            }
        }
    }

    /**
     * Retrieves a method associated with a specific path and HTTP method type.
     *
     * @param path   the URL path of the route
     * @param method the HTTP method (GET, POST, etc.)
     * @return the method corresponding to the path and HTTP method, or null if not found
     */
    public Method getRoute(String path, RequestType method) {
        Map<RequestType, Method> methodMap = routes.get(path);
        return methodMap != null ? methodMap.get(method) : null;
    }

    /**
     * Retrieves all registered routes (methods) across all controllers.
     *
     * @return a list of all registered route handler methods
     */
    public List<Method> getAllRoutes() {
        List<Method> allRoutes = new ArrayList<>();

        for (Map<RequestType, Method> methodMap : routes.values()) {
            allRoutes.addAll(methodMap.values());
        }

        return allRoutes;
    }

    /**
     * Retrieves the controller instance by its name.
     *
     * @param controllerName the name of the controller
     * @return the instance of the controller or null if not found
     */
    public Object getControllerInstance(String controllerName) {
        return controllers.get(controllerName);
    }
}
