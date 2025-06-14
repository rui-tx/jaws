package org.ruitx.jaws.components;

import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

public class Heimdall implements Runnable {
    private final Path path;
    private final List<String> ignoredExtensions = List.of(".temp", "~");
    private final List<String> ignoredDirectories = List.of(".");

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

            JawsLogger.info("Heimdall started");
            WatchService watchService = FileSystems.getDefault().newWatchService();
            registerAll(path, watchService);

            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = path.resolve((Path) event.context());

                    if (isIgnored(changed)) {
                        JawsLogger.info("Heimdall ignored file: " + changed);
                        continue;
                    }
                    JawsLogger.info("Heimdall detected a file change: " + changed);

                    // If the event indicates a directory was created, register it for watching
                    if (event.kind() == ENTRY_CREATE) {
                        registerAll(changed, watchService);
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            JawsLogger.error("Heimdall encountered an error: " + e);
        }
    }

    private boolean isIgnored(Path path) {
        String fileName = path.getFileName().toString();
        boolean isFileIgnoredByExtension = ignoredExtensions.stream().anyMatch(fileName::endsWith);
        boolean isDirectoryIgnored = ignoredDirectories.stream().anyMatch(fileName::equals);
        return isFileIgnoredByExtension || isDirectoryIgnored;
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> {
                        try {
                            if (isIgnored(path)) {
                                JawsLogger.info("Heimdall ignored directory: " + path);
                                return;
                            }
                            JawsLogger.trace("Heimdall is now watching: " + path);
                            path.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
                        } catch (IOException e) {
                            JawsLogger.error("Failed to register directory: " + path + " due to: " + e.getMessage());
                        }
                    });
        }
    }

}
