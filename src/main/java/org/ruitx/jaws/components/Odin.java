package org.ruitx.jaws.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.configs.MiddlewareConfig;
import org.ruitx.jaws.jobs.JobQueue;
import org.ruitx.www.service.AuthService;
import org.ruitx.www.service.PasteService;
import org.tinylog.Logger;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_PATH;
import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;

/**
 * <p>Odin is the main class that starts the Jaws server.</p>
 * <p>It is responsible for starting the components of the server.</p>
 * <ul>
 * <li>Yggdrasill is the server that listens for incoming connections</li>
 * <li>Bifrost is the middleware that processes the requests</li>
 * <li>Heimdall is a file watcher that watches for changes in the www path</li>
 * <li>Njord is a dynamic router that routes requests to controllers</li>
 * <li>Norns is a cron job that runs scheduled tasks</li>
 * <li>Hel is the shutdown hook that stops the server</li>
 * </ul>
 */
public final class Odin {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Yggdrasill yggdrasill;

    private Odin() {
    }

    public static ObjectMapper getMapper() {
        return objectMapper;
    }

    public static void start() {
        startComponents();
    }

    private static void startComponents() {
        ExecutorService executor = Executors.newCachedThreadPool();

        createMimir();
        createNjord();
        startJobQueue();
        List<Thread> threads = Arrays.asList(
                createYggdrasill(),
                createHeimdall(),
                createNorns());

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

    // Yggdrasill is the component that listens for incoming connections
    private static Thread createYggdrasill() {
        return new Thread(() -> {
            yggdrasill = new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH);
            
            // Add middleware from configuration
            createBifrost(yggdrasill);
            
            yggdrasill.start();
        });
    }

    // Bifrost is the middleware that processes the requests
    private static void createBifrost(Yggdrasill yggdrasill) {
        MiddlewareConfig.MIDDLEWARE.forEach(yggdrasill::addMiddleware);
        
        Logger.info("Configured {} middleware(s): {}", 
                MiddlewareConfig.MIDDLEWARE.size(),
                MiddlewareConfig.MIDDLEWARE.stream()
                        .map(m -> m.getClass().getSimpleName())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none"));
    }

    // Heimdall is a file watcher that watches for changes in the www path
    private static Thread createHeimdall() {
        return new Thread(() -> {
            new Heimdall(Paths.get(ApplicationConfig.WWW_PATH)).run();
        });
    }

    // Norns manages scheduled tasks
    private static Thread createNorns() {
        Norns norns = Norns.getInstance();
        norns.registerTask(
                "clean-old-sessions",
                () -> new AuthService().cleanOldSessions(),
                30,
                TimeUnit.MINUTES
        );
        norns.registerTask(
                "clean-expired-pastes",
                () -> new PasteService().cleanExpiredPastes(),
                10,
                TimeUnit.MINUTES
        );
        return new Thread(norns, "norns");
    }

    // Hel is the shutdown hook that gracefully stops all services
    private static void createHel(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutdown hook triggered, stopping services...");
            
            // Stop JobQueue gracefully
            JobQueue jobQueue = JobQueue.getInstance();
            jobQueue.shutdown();
            
            // Stop Yggdrasill gracefully
            if (yggdrasill != null) {
                yggdrasill.shutdown();
            }
            
            // Stop other services
            try {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            Logger.info("JAWS shutdown complete");
        }));
    }

    // Start the job queue processing system
    private static void startJobQueue() {
        JobQueue jobQueue = JobQueue.getInstance();
        jobQueue.start();
        Logger.info("JobQueue started successfully");
    }

    /**
     * Get the current Yggdrasill instance.
     * 
     * @return the Yggdrasill instance, or null if not yet started
     */
    public static Yggdrasill getYggdrasill() {
        return yggdrasill;
    }
}
