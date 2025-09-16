package org.ruitx.jaws.components;

import static org.ruitx.jaws.configs.RoutesConfig.ROUTES;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.ruitx.jaws.components.mimir.DatabaseConfig;
import org.ruitx.jaws.components.mimir.DatabaseSeeder;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Verdandi;
import org.ruitx.jaws.components.mimir.seeders.AdminBootstrapSeeder;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.configs.MiddlewareConfig;
import org.ruitx.jaws.utils.logger.JawsLogger;
import org.ruitx.www.base.service.AuthService;
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

  public static final String DB_NAME = "db";
  public static final String LOGS_DB_NAME = "logs";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Map<String, Verdandi> DB_CONNECTORS = new ConcurrentHashMap<>();
  private static final Map<String, Mimir> DBS = new ConcurrentHashMap<>();
  private static Yggdrasill YGGDRASILL;

  private Odin() {
  }

  /**
   * Get the ObjectMapper instance used for JSON serialization/deserialization.
   *
   * @return the ObjectMapper instance
   */
  public static ObjectMapper getMapper() {
    return OBJECT_MAPPER;
  }

  public static Mimir getDB() {
    return getDB(DB_NAME);
  }

  public static Optional<Mimir> findDb(String name) {
    return Optional.ofNullable(DBS.get(name));
  }

  public static boolean hasDatabase(String name) {
    return DBS.containsKey(name);
  }

  public static Set<String> listDatabases() {
    return DBS.keySet();
  }

  public static Mimir getDB(String name) {
    Mimir db = DBS.get(name);
    if (db == null) {
      throw new IllegalStateException("Database not registered: " + name);
    }
    return db;
  }

  /**
   * Start the Jaws server. This method initializes all components and starts the server.
   */
  public static void start() {
    startComponents();
  }

  private static void startComponents() {
    ExecutorService executor = Executors.newCachedThreadPool();

    // Enforce DB readiness with timeout before starting any other component
    try {
      long timeoutMs = ApplicationConfig.STARTUP_DB_TIMEOUT_MS;
      Logger.info("Odin: Initializing databases (timeout: {} ms)", timeoutMs);
      long startTs = System.currentTimeMillis();

      ExecutorService initExec = Executors.newSingleThreadExecutor(
          r -> new Thread(r, "jaws-mimir-init"));
      try {
        java.util.concurrent.Future<?> f = initExec.submit(() -> {
          Logger.info("Odin: Registering default databases ...");
          registerDefaultDatabases();
        });
        f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        long took = System.currentTimeMillis() - startTs;
        Logger.info("Odin: Databases initialized successfully in {} ms", took);
      } finally {
        initExec.shutdownNow();
      }
    } catch (java.util.concurrent.TimeoutException te) {
      Logger.error("Odin: Database initialization timed out. Shutting down.");
      // Hard exit as per policy
      System.exit(1);
      return;
    } catch (Exception e) {
      Logger.error("Odin: Database initialization failed: {}", e.getMessage());
      System.exit(1);
      return;
    }

    // At this point, both 'db' and 'logs' are registered and ready. Bootstrap logger.
    try {
      JawsLogger.bootstrap(getDB(LOGS_DB_NAME));
    } catch (Throwable t) {
      Logger.error(
          "Odin: Failed to bootstrap JawsLogger with {} DB: {}",
          LOGS_DB_NAME,
          t.getMessage());
      System.exit(1);
      return;
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

  // Njord is a dynamic router that routes requests to controllers
  private static void createNjord() {
    Njord njord = Njord.getInstance();
    ROUTES.forEach(njord::registerRoutes);
  }

  // Yggdrasill is the component that listens for incoming connections
  private static Thread createYggdrasill() {
    return new Thread(() -> {
      YGGDRASILL = new Yggdrasill(ApplicationConfig.PORT, ApplicationConfig.WWW_PATH);

      // Add middleware from configuration
      createBifrost(YGGDRASILL);

      YGGDRASILL.start();
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

  private static void registerDefaultDatabases() {
    Logger.info("Odin: Registering database alias {}", DB_NAME);
    List<DatabaseSeeder> dbSeeders = new ArrayList<>();
    dbSeeders.add(new AdminBootstrapSeeder());
    registerDatabase(DB_NAME, new DatabaseConfig(
        ApplicationConfig.DATABASE_PATH,
        ApplicationConfig.DATABASE_SCHEMA_PATH,
        ApplicationConfig.MIMIR_READER_POOL_SIZE,
        ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
        ApplicationConfig.MIMIR_ENABLE_WAL,
        ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
        null,
        "jaws-writer",
        "jaws-reader",
        dbSeeders));

    Logger.info("Odin: Registering database alias {}", LOGS_DB_NAME);
    registerDatabase(LOGS_DB_NAME, new DatabaseConfig(
        Paths.get("src/main/resources/logs.db").toAbsolutePath().toString(),
        Paths.get("src/main/resources/sql/logs_schema.sql").toAbsolutePath().toString(),
        Math.max(2, ApplicationConfig.MIMIR_READER_POOL_SIZE / 2),
        ApplicationConfig.MIMIR_BUSY_TIMEOUT_MS,
        true,
        ApplicationConfig.MIMIR_SYNCHRONOUS_MODE,
        null,
        "jaws-logs-writer",
        "jaws-logs-reader",
        null
    ));
  }

  public static synchronized void registerDatabase(String name, DatabaseConfig cfg) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Database name must not be blank");
    }
    if (DB_CONNECTORS.containsKey(name) || DBS.containsKey(name)) {
      throw new IllegalStateException("Database alias already registered: " + name);
    }
    try {
      Verdandi v = new Verdandi(cfg);
      Logger.info("Odin: Initializing database '{}' ...", name);
      v.initialize();

      // Ensure readiness flag is true after initialize()
      if (!v.isReady()) {
        throw new IllegalStateException("Database did not report ready after initialization");
      }

      // Create Mimir instance backed by this connector
      Mimir mimir = new Mimir(v);

      // Execute optional seeders synchronously (fail-fast)
      if (cfg.seeders() != null) {
        for (DatabaseSeeder seeder : cfg.seeders()) {
          long st = System.currentTimeMillis();
          Logger.info("Odin: Running seeder '{}' for database '{}' ...", seeder.name(), name);
          try {
            mimir.beginTransaction();
            try {
              seeder.run(mimir);
              mimir.commitTransaction();
            } catch (Exception se) {
              try {
                mimir.rollbackTransaction();
              } catch (Exception ignore) {
              }
              throw se;
            }
          } catch (Exception ex) {
            Logger.error("Odin: Seeder '{}' failed for database '{}': {}", seeder.name(), name,
                ex.getMessage());
            throw new RuntimeException(
                "Seeder '" + seeder.name() + "' failed for database '" + name + "'", ex);
          } finally {
            Logger.info("Odin: Seeder '{}' for database '{}' completed in {} ms", seeder.name(),
                name,
                (System.currentTimeMillis() - st));
          }
        }
      }

      DB_CONNECTORS.put(name, v);
      DBS.put(name, mimir);
      Logger.info("Odin: Database '{}' is ready", name);
    } catch (Exception e) {
      Logger.error("Odin: Failed to register database '{}': {}", name, e.getMessage());
      throw new RuntimeException("Failed to register database '" + name + "'", e);
    }
  }

  public static synchronized void unregisterDatabase(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    try {
      Verdandi v = DB_CONNECTORS.remove(name);
      if (v != null) {
        try {
          v.close();
        } catch (Exception ignore) {
        }
      }
    } finally {
      DBS.remove(name);
      Logger.info("Odin: Database '{}' unregistered", name);
    }
  }

  /**
   * Test-only helper to shutdown all background systems and close all DB connectors. Intended for
   * use in @AfterAll of integration tests to ensure files can be deleted safely.
   */
  public static synchronized void shutdownAllForTests() {
    // Stop Freyr if running
    try {
      // Fully reset singleton to avoid stale DB references across tests
      Freyr.resetForTests();
    } catch (Throwable ignore) {
    }

    // Close any server instance (if started in a test)
    try {
      if (YGGDRASILL != null) {
        YGGDRASILL.shutdown();
      }
    } catch (Throwable ignore) {
    }

    // Close all DB connectors and clear registries
    DB_CONNECTORS.forEach((n, v) -> {
      try {
        v.close();
      } catch (Exception ignore) {
      }
    });
    DB_CONNECTORS.clear();
    DBS.clear();
    Logger.info("Odin: shutdownAllForTests completed");
  }

  // Hel is the shutdown hook that gracefully stops all services
  private static void createHel(ExecutorService executor) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      JawsLogger.info("Shutdown hook triggered, stopping services...");

      // Stop Freyr gracefully
      Freyr jobQueue = Freyr.getInstance();
      jobQueue.shutdown();

      // Stop Yggdrasill gracefully
      if (YGGDRASILL != null) {
        YGGDRASILL.shutdown();
      }

      // Close all DB connectors
      DB_CONNECTORS.values().forEach(v -> {
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

}
