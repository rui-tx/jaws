package org.ruitx.jaws.components;

import org.ruitx.jaws.configs.ApplicationConfig;
import org.tinylog.Logger;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_PATH;
import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;

/**
 * <p>Odin is the main class that starts the Jaws jaws.</p>
 * <p>It is responsible for starting the components of the jaws.</p>
 * <ul>
 * <li>Yggdrasill is the jaws that listens for incoming connections</li>
 * <li>Heimdall is a file watcher that watches for changes in the www path</li>
 * <li>Njord is a dynamic router that routes requests to controllers</li>
 * <li>Hel is the shutdown hook that stops the jaws</li>
 * </ul>
 */
public final class Odin {

    public static void start() {
        startComponents();
    }

    private static void startComponents() {
        ExecutorService executor = Executors.newCachedThreadPool();

        createMimir();
        createNjord();
        List<Thread> threads = Arrays.asList(
                createYggdrasill(),
                createHeimdall());

        for (Thread thread : threads) {
            executor.execute(thread);
        }

        createHel(executor);
    }

    // Mimir is a utility class that initializes the database
    private static void createMimir() {
        Mimir mimir = new Mimir();
        mimir.initializeDatabase(DATABASE_PATH);
    }

    // Njord is a dynamic router that routes requests to controllers
    private static void createNjord() {
        Njord njord = Njord.getInstance();
        ROUTES.forEach(njord::registerRoutes);
    }

    // Yggdrasill is the jaws that listens for incoming connections
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

    // Hel is the shutdown hook that stops the jaws
    private static void createHel(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down Jaws...");
            executor.shutdown();
        }));
    }

}
