package org.ruitx.server.components;

import org.ruitx.server.configs.ApplicationConfig;
import org.tinylog.Logger;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.ruitx.server.configs.RoutesConfig.ROUTES;

/**
 * <p>Odin is the main class that starts the Jaws server.</p>
 * <p>It is responsible for starting the components of the server.</p>
 * <ul>
 * <li>Yggdrasill is the server that listens for incoming connections</li>
 * <li>Heimdall is a file watcher that watches for changes in the www path</li>
 * <li>Njord is a dynamic router that routes requests to controllers</li>
 * <li>Hel is the shutdown hook that stops the server</li>
 * </ul>
 */
public final class Odin {

    public static void start() {
        startComponents();
    }

    private static void startComponents() {
        ExecutorService executor = Executors.newCachedThreadPool();

        createNjord();
        List<Thread> threads = Arrays.asList(
                createYggdrasill(),
                createHeimdall());

        for (Thread thread : threads) {
            executor.execute(thread);
        }

        createHel(executor);
    }

    // Njord is a dynamic router that routes requests to controllers
    private static void createNjord() {
        Njord njord = Njord.getInstance();
        ROUTES.forEach(njord::registerRoutes);
    }

    // Yggdrasill is the server that listens for incoming connections
    private static Thread createYggdrasill() {
        return new Thread(() -> {
            new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH).start();
        });
    }

    // Heimdall is a file watcher that watches for changes in the www path
    private static Thread createHeimdall() {
        return new Thread(() -> {
            new Heimdall(Paths.get(ApplicationConfig.WWW_PATH)).run();
        });
    }

    // Hel is the shutdown hook that stops the server
    private static void createHel(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down Jaws...");
            executor.shutdown();
        }));
    }
}
