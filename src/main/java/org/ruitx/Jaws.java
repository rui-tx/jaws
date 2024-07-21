package org.ruitx;

import org.ruitx.server.Yggdrasill;
import org.ruitx.server.components.Heimdall;
import org.ruitx.server.configs.Constants;
import org.ruitx.server.configs.ProjectSystemSettings;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Jaws {
    public static void main(String[] args) {

        ProjectSystemSettings.setProperties();

        ExecutorService executor = Executors.newCachedThreadPool();

        int port = System.getenv("PORT") != null
                ? Integer.parseInt(System.getenv("PORT"))
                : Constants.DEFAULT_PORT;

        String wwwPath = System.getenv("WWWPATH") != null
                ? System.getenv("WWWPATH")
                : Constants.DEFAULT_RESOURCES_PATH;

        Thread serverThread = new Thread(() -> {
            try {
                new Yggdrasill(port, wwwPath).start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread heimdallThread = new Thread(() -> {
            new Heimdall(Paths.get(wwwPath)).run();
        });

        List<Thread> threads = Arrays.asList(
                serverThread,
                heimdallThread);

        for (Thread thread : threads) {
            executor.execute(thread);
        }
    }
}