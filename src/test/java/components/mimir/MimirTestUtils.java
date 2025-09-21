package components.mimir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Row;

/**
 * Test utilities for Mimir-based database tests. - Creates unique temporary DB files under target/
 * - Tracks and closes all created Mimir instances - Deletes all created DB files on teardown
 */
public final class MimirTestUtils {

  private static final List<Mimir> INSTANCES = new CopyOnWriteArrayList<>();
  private static final List<Path> DB_PATHS = new CopyOnWriteArrayList<>();

  static {
    // Ensure SQLite driver is available in test runtime
    try {
      Class.forName("org.sqlite.JDBC");
      DriverManager.registerDriver(new org.sqlite.JDBC());
    } catch (Exception ignored) {
    }
    ensureTargetDir();
  }

  private MimirTestUtils() {
  }

  public static Path newTempDb(String prefix) {
    ensureTargetDir();
    Path path = Paths.get("target", prefix + System.nanoTime() + ".mimir");
    DB_PATHS.add(path);
    return path;
  }

  public static Mimir create(String dbPath) {
    Mimir m = new Mimir(dbPath);
    INSTANCES.add(m);
    return m;
  }

  public static Mimir create(String dbPath, String schemaPath) {
    Mimir m = new Mimir(dbPath, schemaPath);
    INSTANCES.add(m);
    return m;
  }

  public static void init(Mimir mimir, String dbPath) {
    mimir.initializeDatabase(dbPath);
  }

  public static void closeAll() {
    for (Mimir m : INSTANCES) {
      try {
        m.close();
      } catch (Exception ignored) {
      }
    }
    INSTANCES.clear();
  }

  public static void deleteAllDbs() {
    for (Path p : DB_PATHS) {
      try {
        Files.deleteIfExists(p);
      } catch (IOException ignored) {
      }
    }
    DB_PATHS.clear();
  }

  public static int scalarCount(Mimir m, String sql, Object... params) {
    List<Row> rows = m.getRows(sql, params);
    if (rows.isEmpty()) {
      return 0;
    }
    return rows.get(0).getInt("count").orElse(0);
  }

  /**
   * Create a Mimir instance using a schema loaded from the classpath resource. The resource is
   * copied to target/ as a temporary .sql file.
   */
  public static Mimir createWithSchemaResource(String dbPath, String cpResource) {
    String schemaFilePath = writeSchemaResourceToTemp(cpResource);
    return create(dbPath, schemaFilePath);
  }

  /**
   * Copy a classpath SQL resource to a temporary file under target/ and return the file system
   * path.
   */
  public static String writeSchemaResourceToTemp(String cpResource) {
    ensureTargetDir();
    String normalized = cpResource.startsWith("/") ? cpResource : "/" + cpResource;
    try (java.io.InputStream in = MimirTestUtils.class.getResourceAsStream(normalized)) {
      if (in == null) {
        throw new IllegalArgumentException("Schema resource not found on classpath: " + cpResource);
      }
      Path out = Paths.get("target", "schema-" + System.nanoTime() + ".sql");
      Files.copy(in, out);
      return out.toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed to copy schema resource: " + cpResource, e);
    }
  }

  /**
   * Check if a given table exists in the database.
   */
  public static boolean tableExists(Mimir m, String tableName) {
    List<Row> rows = m.getRows(
        "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
        tableName
    );
    return !rows.isEmpty();
  }

  /**
   * Check if a given table does not exist in the database.
   */
  public static boolean tableNotExists(Mimir m, String tableName) {
    return !tableExists(m, tableName);
  }

  private static void ensureTargetDir() {
    try {
      Files.createDirectories(Paths.get("target"));
    } catch (IOException ignored) {
    }
  }
}
