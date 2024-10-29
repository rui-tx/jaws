package org.ruitx;

import org.ruitx.server.components.Heimdall;
import org.ruitx.server.components.Njord;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.controllers.Test;
import org.tinylog.Logger;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Jaws {
    public static void main(String[] args) {

        // Yggdrasill is the server that listens for incoming connections
        // Heimdall is a file watcher that watches for changes in the www path
        // Njord is a dynamic router that routes requests to controllers

        Njord njord = Njord.getInstance();
        List<Object> routes = List.of(
                new Test()
        );
        routes.forEach(njord::registerRoutes);

        ExecutorService executor = Executors.newCachedThreadPool();
        Thread serverThread = new Thread(() -> {
            new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH).start();
        });

        Thread fileWatcherThread = new Thread(() -> {
            new Heimdall(Paths.get(ApplicationConfig.WWW_PATH)).run();
        });

        List<Thread> threads = Arrays.asList(
                serverThread,
                fileWatcherThread);

        for (Thread thread : threads) {
            executor.execute(thread);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down Jaws...");
            executor.shutdown();
        }));
    }
}