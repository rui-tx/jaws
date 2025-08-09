package org.ruitx.jaws.components;

import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.configs.MiddlewareConfig;
import org.ruitx.jaws.db.DatabaseConfig;
import org.ruitx.jaws.db.Verdandi;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.www.service.AuthService;
import org.tinylog.Logger;

/**
 * <p>Odin is the main class that starts the Jaws server.</p>
 * <p>It is responsible for starting the components of the server.</p>
 * <ul>
 * <li>Yggdrasill is the server that listens for incoming connections</li>
 * <li>Bifrost is the middleware that processes the requests</li>
 * <li>Heimdall is a file watcher that watches for changes in the www path</li>
 * <li>Mimir is a utility class/ORM that interfaces with the database</li>
 * <li>Njord is a dynamic router that routes requests to controllers</li>
 * <li>Norns is a cron job that runs scheduled tasks</li>
 * <li>Freyr is the job queue processing system</li>
 * <li>Hel is the shutdown hook that stops the server</li>
 * </ul>
 */
public final class Odin {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  // Registry of named DB connectors and Mimir instances
  private static final Map<String, Verdandi> CONNECTORS = new ConcurrentHashMap<>();
  private static final Map<String, Mimir> MIMIRS = new ConcurrentHashMap<>();
  private static Yggdrasill yggdrasill;

  // Ensure default databases are registered as soon as Odin is first referenced,
  // so that components with static initializers (e.g., JawsLogger) can resolve
  // the 'logs' database during their own class initialization.
  static {
    try {
      registerDefaultDatabases();
    } catch (Throwable t) {
      // Use Tinylog directly to avoid circular init through JawsLogger
      Logger.warn(
          "Odin static init: failed to register default databases: {}",
          t.getMessage());
    }
  }

  private Odin() {
  }

  /**
   * Get the ObjectMapper instance used for JSON serialization/deserialization.
   *
   * @return the ObjectMapper instance
   */
  public static ObjectMapper getMapper() {
    return objectMapper;
  }

  /**
   * Start the Jaws server. This method initializes all components and starts the server.
   */
  public static void start() {
    startComponents();
  }

  private static void startComponents() {
    ExecutorService executor = Executors.newCachedThreadPool();

    // In case classloading order prevented static init or tests reset state
    if (!hasDatabase("db")) {
      registerDefaultDatabases();
    }
    // If only primary DB got registered earlier and logs failed, ensure logs is registered now
    if (!hasDatabase("logs")) {
      try {
        registerDatabase("logs", buildLogsDbConfig());
        Logger.info("Odin: registered 'logs' database after startup guard");
      } catch (Exception e) {
        Logger.warn("Odin: failed to register 'logs' database in startup guard: {}", e.getMessage());
      }
    }
    createNjord();
    createFreyr();

    List<Thread> threads = Arrays.asList(
        createYggdrasill(),
        createHeimdall(),
        createNorns());

    for (Thread thread : threads) {
      executor.execute(thread);
    }

    createHel(executor);
  }

  // Register default databases: primary "db" and optional "logs"
  private static void registerDefaultDatabases() {
    // Primary application database registered as alias "db"
    DatabaseConfig appDb = new DatabaseConfig(
        ApplicationConfig.DATABASE_PATH,
        Optional.ofNullable(ApplicationConfig.DATABASE_SCHEMA_PATH),
        ApplicationConfig.MIMIR_READER_POOL_SIZE,
        ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
        ApplicationConfig.MIMIR_ENABLE_WAL,
        ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
        Optional.empty(),
        Optional.of("jaws-writer"),
        Optional.of("jaws-reader")
    );
    registerDatabase("db", appDb);

    // Logs database (use existing paths for now; can be moved to ApplicationConfig later)
    registerDatabase("logs", buildLogsDbConfig());
  }

  // Build logs database config with absolute paths to avoid CWD issues during early class init
  private static DatabaseConfig buildLogsDbConfig() {
    String logsDbPath = java.nio.file.Paths.get("src/main/resources/logs.db").toAbsolutePath().toString();
    String logsSchemaPath = java.nio.file.Paths.get("src/main/resources/sql/logs_schema.sql").toAbsolutePath().toString();
    return new org.ruitx.jaws.db.DatabaseConfig(
        logsDbPath,
        Optional.of(logsSchemaPath),
        Math.max(2, ApplicationConfig.MIMIR_READER_POOL_SIZE / 2),
        ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
        true,
        ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
        Optional.empty(),
        Optional.of("jaws-logs-writer"),
        Optional.of("jaws-logs-reader")
    );
  }

  // Public API: register a database by name
  public static synchronized void registerDatabase(String name, DatabaseConfig cfg) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Database name must not be blank");
    }
    if (CONNECTORS.containsKey(name) || MIMIRS.containsKey(name)) {
      throw new IllegalStateException("Database alias already registered: " + name);
    }
    try {
      Verdandi v = new Verdandi(cfg);
      v.initialize();
      CONNECTORS.put(name, v);
      MIMIRS.put(name, new Mimir(v));
    } catch (Exception e) {
      throw new RuntimeException("Failed to register database '" + name + "'", e);
    }
  }

  // Lookup helpers
  public static Optional<Mimir> findMimir(String name) {
    return Optional.ofNullable(MIMIRS.get(name));
  }

  public static boolean hasDatabase(String name) {
    return MIMIRS.containsKey(name);
  }

  public static Set<String> listDatabases() {
    return MIMIRS.keySet();
  }

  public static Mimir getMimir(String name) {
    Mimir db = MIMIRS.get(name);
    if (db == null) {
      throw new IllegalStateException("Database not registered: " + name);
    }
    return db;
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
    MiddlewareConfig.MIDDLEWARE.forEach(m -> {
      yggdrasill.addMiddleware(m);
      JawsLogger.info("Configured {} middleware", m.getClass().getSimpleName());
    });
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
    return new Thread(norns, "norns");
  }

  // Freyr, the job queue processing system
  private static void createFreyr() {
    Freyr freyr = Freyr.getInstance();
    freyr.start();
  }

  // Hel is the shutdown hook that gracefully stops all services
  private static void createHel(ExecutorService executor) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      JawsLogger.info("Shutdown hook triggered, stopping services...");

      // Stop Freyr gracefully
      Freyr jobQueue = Freyr.getInstance();
      jobQueue.shutdown();

      // Stop Yggdrasill gracefully
      if (yggdrasill != null) {
        yggdrasill.shutdown();
      }

      // Close all DB connectors
      CONNECTORS.values().forEach(v -> {
        try {
          v.close();
        } catch (Exception ignore) {
        }
      });

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

      JawsLogger.info("JAWS shutdown complete");
    }));
  }

  /**
   * Get the current Yggdrasill instance.
   *
   * @return the Yggdrasill instance, or null if not yet started
   */
  public static Yggdrasill getYggdrasill() {
    return yggdrasill;
  }

  /**
   * Access the primary Mimir instance (alias "db").
   */
  public static Mimir getMimir() {
    return getMimir("db");
  }
}
