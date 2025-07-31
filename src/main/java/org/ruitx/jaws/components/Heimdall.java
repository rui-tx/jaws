package org.ruitx.jaws.components;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.ruitx.jaws.utils.JawsLogger;

/**
 * Heimdall is a file watcher that monitors changes in the www path. It ignores certain file
 * extensions and directories. Currently, it only logs detected changes without taking further
 * action.
 */
public class Heimdall implements Runnable {

  private final Path path;
  private final List<String> ignoredExtensions = List.of(".temp", "~");
  private final List<String> ignoredDirectories = List.of(".");
  private final AtomicBoolean running = new AtomicBoolean(true);
  private WatchService watchService;

  public Heimdall(Path path) {
    this.path = path;
  }

  /**
   * Watches for changes in the www path. Right now, it only watches for changes and does nothing
   * with them.
   */
  @Override
  public void run() {
    try {
      JawsLogger.info("Heimdall started");
      watchService = FileSystems.getDefault().newWatchService();
      registerAll(path, watchService);

      WatchKey key;
      while (running.get() && (key = watchService.take()) != null) {
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
    } catch (InterruptedException e) {
      JawsLogger.info("Heimdall is shutting down");
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      JawsLogger.error("Heimdall encountered an error: " + e);
    } finally {
      if (watchService != null) {
        try {
          watchService.close();
        } catch (IOException e) {
          JawsLogger.error("Error closing watch service: " + e);
        }
      }
    }
  }

  /**
   * Shuts down the file watcher
   */
  public void shutdown() {
    running.set(false);
    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException e) {
        JawsLogger.error("Error closing watch service during shutdown: " + e);
      }
    }
  }

  /**
   * Checks if the given path should be ignored based on its file extension or directory name.
   *
   * @param path The path to check.
   * @return true if the path is ignored, false otherwise.
   */
  private boolean isIgnored(Path path) {
    String fileName = path.getFileName().toString();
    boolean isFileIgnoredByExtension = ignoredExtensions.stream().anyMatch(fileName::endsWith);
    boolean isDirectoryIgnored = ignoredDirectories.stream().anyMatch(fileName::equals);
    return isFileIgnoredByExtension || isDirectoryIgnored;
  }

  /**
   * Registers all directories under the given path to the watch service.
   *
   * @param start        The starting path to register.
   * @param watchService The watch service to register directories with.
   * @throws IOException If an I/O error occurs.
   */
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
              JawsLogger.error(
                  "Failed to register directory: " + path + " due to: " + e.getMessage());
            }
          });
    }
  }
}
