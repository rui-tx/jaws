package org.ruitx;

import org.ruitx.server.components.Heimdall;
import org.ruitx.server.components.Njord;
import org.ruitx.server.components.Yggdrasill;
import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.controllers.Todo;
import org.tinylog.Logger;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Jaws {

    // Yggdrasill is the server that listens for incoming connections
    // Heimdall is a file watcher that watches for changes in the www path
    // Njord is a dynamic router that routes requests to controllers
    // Hel is the shutdown hook that stops the server

    static void createNjord() {
        Njord njord = Njord.getInstance();
        List<Object> routes = List.of(
                new Todo()
        );
        routes.forEach(njord::registerRoutes);
    }

    static Thread createYggdrasill() {
        return new Thread(() -> {
            new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH).start();
        });
    }

    static Thread createHeimdall() {
        return new Thread(() -> {
            new Heimdall(Paths.get(ApplicationConfig.WWW_PATH)).run();
        });
    }

    static void createHel(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down Jaws...");
            executor.shutdown();
        }));
    }

    static void runJaws() {
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

    public static void main(String[] args) {
        runJaws();
    }
}