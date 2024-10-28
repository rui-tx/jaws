package org.ruitx.server.components;

import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.*;

/**
 * Heimdall is a file watcher that watches for changes in a given path.
 */
public class Heimdall implements Runnable {
    private final Path path;

    public Heimdall(Path path) {
        this.path = path;
    }

    /**
     * Watches for changes in the www path.
     * Right now, it only watches for changes and does nothing with them.
     */
    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    Logger.info("Heimdall detected a file change: " + event.context());
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            Logger.error("Heimdall encountered an error: " + e.getMessage());
        }
    }
}
