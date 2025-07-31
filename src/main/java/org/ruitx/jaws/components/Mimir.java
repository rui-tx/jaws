package org.ruitx.jaws.components;

import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_PATH;
import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_SCHEMA_PATH;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.sql.DataSource;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.SqlFunction;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsUtils;
import org.sqlite.SQLiteDataSource;
import org.tinylog.Logger;

/**
 * Mimir is a database management component for Jaws framework. It provides methods to initialize,
 * manage transactions, execute SQL queries, and handle pagination.
 * <p>
 * Mimir uses SQLite as the underlying database engine and supports schema loading from a specified
 * file path.
 */
public class Mimir {

  // Per-key custom TTL in nanoseconds
  private static final ConcurrentHashMap<SqlCacheKey, Long> keyToTtl = new ConcurrentHashMap<>();
  // Reverse index lookup table -> cache keys
  private static final ConcurrentHashMap<String, Set<SqlCacheKey>> tableToKeys = new ConcurrentHashMap<>();
  // Thread-local storage for current tables and TTL
  private static final ThreadLocal<Set<String>> currentTables = new ThreadLocal<>();
  private static final ThreadLocal<Long> currentTtlMs = new ThreadLocal<>();
  // Thread-local storage for transaction modified tables
  private static final ThreadLocal<Set<String>> txModifiedTables = ThreadLocal.withInitial(
      HashSet::new);
  // Cache for SQL queries
  private static volatile Cache<SqlCacheKey, Object> queryCache;
  private static volatile ThreadLocal<Boolean> allowCacheFlag;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();
  private String schemaPath;
  private boolean shouldCreateDefaultUser;
  private DataSource dataSource;
  private File db;

  /**
   * Default constructor -  Uses the default database path and schema from ApplicationConfig.
   */
  public Mimir() {
    this(DATABASE_PATH, DATABASE_SCHEMA_PATH, true);
  }

  /**
   * Constructor for custom database without schema loading.
   *
   * @param databasePath Path to the database file
   */
  public Mimir(String databasePath) {
    this(databasePath, null, false);
  }

  /**
   * Constructor for custom database with optional schema.
   *
   * @param databasePath Path to the database file
   * @param schemaPath   Path to the schema file (null to skip schema loading)
   */
  public Mimir(String databasePath, String schemaPath) {
    this(databasePath, schemaPath, false);
  }

  /**
   * Full constructor with all options.
   *
   * @param databasePath            Path to the database file
   * @param schemaPath              Path to the schema file (null to skip schema loading)
   * @param shouldCreateDefaultUser Whether to create the default admin user
   */
  public Mimir(String databasePath, String schemaPath, boolean shouldCreateDefaultUser) {
    this.db = new File(databasePath);
    this.schemaPath = schemaPath;
    this.shouldCreateDefaultUser = shouldCreateDefaultUser;
    initializeDataSource();
  }

  /**
   * Ensure the cache is initialized. This method is called before any cache operations to ensure
   * the cache is ready.
   */
  private static synchronized void ensureCache() {
    if (queryCache == null) {
      allowCacheFlag = ThreadLocal.withInitial(() -> false);
      queryCache = Caffeine.newBuilder()
          .maximumSize(ApplicationConfig.MIMIR_CACHE_MAX_SIZE)
          .expireAfter(new Expiry<SqlCacheKey, Object>() {
            @Override
            public long expireAfterCreate(SqlCacheKey key, Object value, long currentTime) {
              return keyToTtl.getOrDefault(key, Long.MAX_VALUE);
            }

            @Override
            public long expireAfterUpdate(SqlCacheKey key, Object value, long currentTime,
                long currentDuration) {
              return keyToTtl.getOrDefault(key, currentDuration);
            }

            @Override
            public long expireAfterRead(SqlCacheKey key, Object value, long currentTime,
                long currentDuration) {
              return currentDuration;
            }
          })
          .build();
    }
  }

  public static void enableCacheForCurrentThread() {
    ensureCache();
    allowCacheFlag.set(true);
  }

  public static void disableCacheForCurrentThread() {
    ensureCache();
    allowCacheFlag.set(false);
  }

  public static boolean isCacheAllowedForCurrentThread() {
    ensureCache();
    return allowCacheFlag.get();
  }

  /**
   * Returns an immutable snapshot of the current SQL query cache. Each entry contains the original
   * SQL, the bound parameters, the cached value, and the TTL remaining in nanoseconds. Intended
   * strictly for diagnostics and should be exposed only through secured debug endpoints.
   */
  public static List<Map<String, Object>> snapshotCache() {
    ensureCache();
    java.util.List<Map<String, Object>> entries = new ArrayList<>();
    queryCache.asMap().forEach((key, value) -> {
      java.util.Map<String, Object> entry = new HashMap<>();
      entry.put("sql", key.sql);
      entry.put("params", key.params);
      entry.put("value", value);
      entry.put("ttlNanos", keyToTtl.getOrDefault(key, Long.MAX_VALUE));
      entries.add(java.util.Map.copyOf(entry));
    });
    return List.copyOf(entries);
  }

  /**
   * Flushes the entire query cache and associated metadata.  Intended for admin/diagnostic use.
   */
  public static void flushCache() {
    ensureCache();
    queryCache.invalidateAll();
    keyToTtl.clear();
    tableToKeys.clear();
    Logger.debug("Mimir cache flushed via flushCache() helper");
  }

  public static void setCurrentTables(Set<String> tables) {
    currentTables.set(tables);
  }

  public static void clearCurrentTables() {
    currentTables.remove();
  }

  public static void setCurrentTtl(long ttlMs) {
    currentTtlMs.set(ttlMs);
  }

  public static void clearCurrentTtl() {
    currentTtlMs.remove();
  }


  /**
   * Initializes the JDBC data source for the embedded SQLite database.  This method is idempotent
   * and thread-safe, and is called automatically by the first call to
   * {@link #initializeDatabase(String)}.
   */
  private synchronized void initializeDataSource() {
    if (!initialized.get()) {
      SQLiteDataSource ds = new SQLiteDataSource();
      ds.setUrl("jdbc:sqlite:" + db.getAbsolutePath());
      dataSource = ds;
      initialized.set(true);
    }
  }

  /**
   * Initialize the database. This method is called automatically by the first call to
   * {@link #getRows(String)} or {@link #executeSql(String, Object[])}.
   *
   * <p>If a database path is provided, the database file will be created if it does not exist. If
   * a schema path is also provided and the database is empty, the schema will be loaded into the
   * database. Finally, the database is marked as initialized.
   *
   * @param databasePath The path to the database file (optional)
   */
  public void initializeDatabase(String databasePath) {
    if (databasePath != null && !databasePath.isEmpty()) {
      this.db = new File(databasePath);
      // Reset initialization to allow re-initialization with new path
      initialized.set(false);
      initializeDataSource();
    }

    createDatabaseFile();

    // If schema is specified and database was created empty, load the schema
    if (schemaPath != null && isDatabaseEmpty()) {
      loadSchema();
    }

    Logger.trace("Database is ready: {}", db.getAbsolutePath());
  }

  /**
   * Create the database file if it does not exist. This method is called during initialization to
   * ensure the database file is present.
   */
  private void createDatabaseFile() {
    if (db.exists()) {
      return;
    }
    try {
      if (db.createNewFile()) {
        Logger.info("Database created: " + db.getAbsolutePath());
      } else {
        throw new IOException("Could not create database file.");
      }
    } catch (IOException e) {
      Logger.error("Error creating database file: " + e.getMessage());
      throw new RuntimeException("Failed to create database file", e);
    }
  }

  /**
   * Check if the database is empty (no tables).
   *
   * @return true if database has no tables, false otherwise
   */
  private boolean isDatabaseEmpty() {
    try {
      List<Row> tables = getRows(
          "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");
      return tables.isEmpty();
    } catch (Exception e) {
      Logger.warn("Error checking if database is empty: {}", e.getMessage());
      return true; // Assume empty if we can't check
    }
  }

  /**
   * Force load the schema into the database. This can be used to load schema into an existing
   * database.
   */
  public void loadSchemaForce() {
    if (schemaPath != null) {
      loadSchema();
    } else {
      Logger.warn("No schema path configured for this Mimir instance");
    }
  }

  /**
   * Load the schema from the specified path into the database. This method reads the schema file
   * and executes its SQL statements.
   */
  private void loadSchema() {
    try (Connection conn = getConnection()) {
      String sql = Files.readString(Path.of(schemaPath));
      executeSqlStatements(conn, sql);
      // Only create default admin user if configured to do so
      if (shouldCreateDefaultUser) {
        createDefaultAdminUser();
      }
      Logger.info("Database initialized with schema: {}", schemaPath);
    } catch (SQLException | IOException e) {
      Logger.error("Error initializing database: " + e.getMessage());
      throw new RuntimeException("Failed to initialize database", e);
    }
  }

  /**
   * Create a default admin user if the database is initialized with schema. This method is called
   * after loading the schema to ensure the admin user exists.
   */
  private void createDefaultAdminUser() {
    Optional<String> password = JawsUtils.newPassword();
    String email = "admin@jaws.local";
    String firstName = "John";
    String lastName = "Doe";
    String hashedPassword =
        BCrypt.withDefaults().hashToString(12, password.orElse("Lee7Pa$$w00rd").toCharArray());

    // Insert the admin user
    executeSql(
        "INSERT INTO USER (user, password_hash, email, first_name, last_name, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        "admin",
        hashedPassword,
        email,
        firstName,
        lastName,
        Date.from(Instant.now())
    );

    // Assign admin role to the newly created admin user
    // Get the admin role ID and user ID, then create the assignment
    Row adminUser = getRow("SELECT id FROM USER WHERE user = 'admin'");
    Row adminRole = getRow("SELECT id FROM ROLE WHERE name = 'admin'");

    if (adminUser != null && adminRole != null) {
      executeSql("INSERT INTO USER_ROLE (user_id, role_id, assigned_at) VALUES (?, ?, ?)",
          adminUser.getInt("id").orElse(0),
          adminRole.getInt("id").orElse(0),
          Instant.now().getEpochSecond()
      );
      Logger.info("Admin role assigned to default admin user");
    } else {
      Logger.warn("Failed to assign admin role to default admin user - role or user not found");
    }

    Logger.info("A new admin user has been created with username 'admin' and password '"
        + (password.orElse("Lee7Pa$$w00rd")) + "'");
    Logger.info("Please save this in a safe place, it will not be shown again.");
  }

  /**
   * Get a connection from the data source. If we're in a transaction, return the transaction
   * connection. Otherwise, get a new connection from the data source.
   *
   * @return A connection to the database
   * @throws SQLException If the connection fails
   */
  public Connection getConnection() throws SQLException {
    // If we're in a transaction, return the transaction connection
    Connection conn = transactionConnection.get();
    if (conn != null) {
      return conn;
    }

    // Otherwise get a new connection
    if (dataSource == null) {
      throw new SQLException("DataSource not initialized");
    }
    return dataSource.getConnection();
  }

  /**
   * Begin a transaction.
   *
   * @throws SQLException If the transaction fails
   */
  public void beginTransaction() throws SQLException {
    if (transactionConnection.get() != null) {
      throw new SQLException("Transaction already in progress");
    }
    Connection conn = dataSource.getConnection();
    conn.setAutoCommit(false);
    transactionConnection.set(conn);
    // Track tables touched inside this transaction
    txModifiedTables.set(new HashSet<>());
  }

  /**
   * Commit a transaction.
   *
   * @throws SQLException If the transaction fails
   */
  public void commitTransaction() throws SQLException {
    Connection conn = transactionConnection.get();
    if (conn == null) {
      throw new SQLException("No transaction in progress");
    }
    try {
      conn.commit();
      // Selective invalidation based on tables actually modified in this transaction
      invalidateTables(txModifiedTables.get());
    } finally {
      conn.close();
      transactionConnection.remove();
      txModifiedTables.remove();
    }
  }

  /**
   * Rollback a transaction.
   *
   * @throws SQLException If the transaction fails
   */
  public void rollbackTransaction() throws SQLException {
    Connection conn = transactionConnection.get();
    if (conn == null) {
      throw new SQLException("No transaction in progress");
    }
    try {
      conn.rollback();
      // Still invalidate modified tables to avoid serving stale data
      invalidateTables(txModifiedTables.get());
    } finally {
      conn.close();
      transactionConnection.remove();
      txModifiedTables.remove();
    }
  }

  /**
   * Execute a SQL query and return the first row.
   *
   * @param sql SQL query string
   * @return First row result or null if no rows
   */
  public Row getRow(String sql) {
    List<Row> rows = getRows(sql);
    return (rows != null && !rows.isEmpty()) ? rows.get(0) : null;
  }

  /**
   * Execute a SQL query and return the first row.
   *
   * @param sql    SQL query string
   * @param params Parameters for the prepared statement
   * @return First row result or null if no rows
   */
  public Row getRow(String sql, Object... params) {
    List<Row> rows = getRows(sql, params);
    return (rows != null && !rows.isEmpty()) ? rows.get(0) : null;
  }

  /**
   * Execute a SQL query and return a list of rows.
   *
   * @param sql SQL query string
   * @return List of Row objects
   */
  public List<Row> getRows(String sql) {
    return executeQuery(sql, this::list);
  }

  /**
   * Execute a SQL query and return a list of rows.
   *
   * @param sql    SQL query string
   * @param params Parameters for the prepared statement
   * @return List of Row objects
   */
  public List<Row> getRows(String sql, Object... params) {
    return executeQuery(sql, this::list, params);
  }

  /**
   * Execute a SQL statement with parameters (like INSERT, UPDATE, DELETE).
   *
   * @param sql    SQL statement string
   * @param params Parameters for the prepared statement
   * @return The number of affected rows
   */
  public int executeSql(String sql, Object... params) {
    Connection conn = null;
    boolean isTransactionConnection = false;

    try {
      conn = getConnection();
      // Check if this is a transaction-managed connection
      isTransactionConnection = (transactionConnection.get() == conn);

      PreparedStatement stmt = null;

      try {
        // Parameter count check (simple heuristic)
        int expectedParams = sql.length() - sql.replace("?", "").length();
        if (expectedParams != params.length) {
          Logger.warn("Parameter count mismatch! SQL: {} expects {} params, but got {}", sql,
              expectedParams, params.length);
        }

        Logger.trace("Executing SQL: {}\nParams: {}", sql, Arrays.toString(params));

        stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
          stmt.setObject(i + 1, params[i]);
        }

        int affected = stmt.executeUpdate();

        // Track modified tables if inside a transaction; else invalidate immediately
        Set<String> modified = extractTablesFromWrite(sql);
        if (isTransactionConnection) {
          txModifiedTables.get().addAll(modified);
        } else {
          invalidateTables(modified);
        }
        return affected;
      } finally {
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {
            Logger.error("Error closing statement: " + e.getMessage());
          }
        }
      }
    } catch (SQLException e) {
      Logger.error("Error executing prepared update: {}\nSQL: {}\nParams: {}",
          e.getMessage(), sql, Arrays.toString(params));
      Logger.error("Stack trace: {}", e.getStackTrace());
      throw new RuntimeException("Database update failed", e);
    } finally {
      // Only close if not a transaction connection
      if (conn != null && !isTransactionConnection) {
        try {
          conn.close();
        } catch (SQLException e) {
          Logger.error("Error closing connection: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Execute a SQL statement that doesn't return data (like UPDATE, DELETE, etc.).
   *
   * @param sql SQL statement string
   * @return true if executed successfully, false otherwise
   */
  public boolean executeSql(String sql) {
    Connection conn = null;
    boolean isTransactionConnection = false;

    try {
      conn = getConnection();
      // Check if this is a transaction-managed connection
      isTransactionConnection = (transactionConnection.get() == conn);

      Statement stmt = null;

      try {
        stmt = conn.createStatement();
        boolean result = stmt.execute(sql);

        // Track modified tables if inside a transaction; else invalidate immediately
        Set<String> modified = extractTablesFromWrite(sql);
        if (isTransactionConnection) {
          txModifiedTables.get().addAll(modified);
        } else {
          invalidateTables(modified);
        }
        Logger.trace("SQL executed: {}, result: {}", sql, result);
        return result;
      } finally {
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {
            Logger.error("Error closing statement: " + e.getMessage());
          }
        }
      }
    } catch (SQLException e) {
      Logger.error("Error executing SQL: {}", e.getMessage());
      throw new RuntimeException("Database update failed", e);
    } finally {
      // Only close if not a transaction connection
      if (conn != null && !isTransactionConnection) {
        try {
          conn.close();
        } catch (SQLException e) {
          Logger.error("Error closing connection: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Executes a query and applies a transformation function on the result set.
   *
   * @param sql    SQL query string
   * @param action Transformation function to apply on the ResultSet
   * @param <T>    The return type of the transformation
   * @return Transformed result from the query
   */
  public <T> T executeQuery(String sql, SqlFunction<T> action) {
    ensureCache();
    boolean skipCache = !ApplicationConfig.MIMIR_CACHE_ENABLED
        || transactionConnection.get() != null
        || !isCacheAllowedForCurrentThread();
    if (skipCache) {
      Logger.trace("Mimir cache BYPASS for SQL: {}", sql);
    }
    SqlCacheKey cacheKey = null;
    if (!skipCache) {
      cacheKey = new SqlCacheKey(sql);
      @SuppressWarnings("unchecked")
      T cached = (T) queryCache.getIfPresent(cacheKey);
      if (cached != null) {
        Logger.debug("Mimir cache HIT for SQL: {}", sql);
        return cached;
      }
      Logger.debug("Mimir cache MISS for SQL: {}", sql);
    }

    Connection conn = null;
    boolean isTransactionConnection = false;

    try {
      conn = getConnection();
      // Check if this is a transaction-managed connection
      isTransactionConnection = (transactionConnection.get() == conn);

      Statement stmt = null;
      ResultSet rs = null;

      try {
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sql);
        T result = action.apply(rs);
        if (!skipCache) {
          long ttlMs = Optional.ofNullable(currentTtlMs.get()).orElse(-1L);
          if (ttlMs > 0) {
            keyToTtl.put(cacheKey, TimeUnit.MILLISECONDS.toNanos(ttlMs));
          }
          queryCache.put(cacheKey, result);
          // Register key under current tables for selective invalidation
          Set<String> tbls = currentTables.get();
          if (tbls != null && !tbls.isEmpty()) {
            for (String tbl : tbls) {
              tableToKeys.computeIfAbsent(tbl.toUpperCase(), k -> ConcurrentHashMap.newKeySet())
                  .add(cacheKey);
            }
          }
          Logger.debug("Mimir cache PUT for SQL: {} (ttlMs={})", sql, ttlMs);
        }
        return result;
      } finally {
        // Close resources in reverse order
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {
            Logger.error("Error closing result set: " + e.getMessage());
          }
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {
            Logger.error("Error closing statement: " + e.getMessage());
          }
        }
      }
    } catch (SQLException e) {
      Logger.error("Error executing SQL: " + e.getMessage());
      throw new RuntimeException("Database query failed", e);
    } finally {
      // Only close if not a transaction connection
      if (conn != null && !isTransactionConnection) {
        try {
          conn.close();
        } catch (SQLException e) {
          Logger.error("Error closing connection: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Executes a query with parameters and applies a transformation function on the result set.
   *
   * @param sql    SQL query string
   * @param action Transformation function to apply on the ResultSet
   * @param params Parameters for the prepared statement
   * @param <T>    The return type of the transformation
   * @return Transformed result from the query
   */
  public <T> T executeQuery(String sql, SqlFunction<T> action, Object... params) {
    ensureCache();
    boolean skipCache = !ApplicationConfig.MIMIR_CACHE_ENABLED
        || transactionConnection.get() != null
        || !isCacheAllowedForCurrentThread();

    if (skipCache) {
      Logger.trace("Mimir cache BYPASS for SQL: {}", sql);
    }
    SqlCacheKey cacheKey = null;
    if (!skipCache) {
      cacheKey = new SqlCacheKey(sql, params);
      @SuppressWarnings("unchecked")
      T cached = (T) queryCache.getIfPresent(cacheKey);
      if (cached != null) {
        Logger.debug("Mimir cache HIT for SQL: {}", sql);
        return cached;
      }
      Logger.debug("Mimir cache MISS for SQL: {}", sql);
    }
    Connection conn = null;
    boolean isTransactionConnection = false;

    try {
      conn = getConnection();
      // Check if this connection is from a transaction
      isTransactionConnection = (transactionConnection.get() == conn);

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        for (int i = 0; i < params.length; i++) {
          stmt.setObject(i + 1, params[i]);
        }

        try (ResultSet rs = stmt.executeQuery()) {
          T result = action.apply(rs);
          if (!skipCache) {
            long ttlMs = Optional.ofNullable(currentTtlMs.get()).orElse(-1L);
            if (ttlMs > 0) {
              keyToTtl.put(cacheKey, TimeUnit.MILLISECONDS.toNanos(ttlMs));
            }
            queryCache.put(cacheKey, result);

            // Register key under current tables for selective invalidation
            Set<String> tbls = currentTables.get();
            if (tbls != null && !tbls.isEmpty()) {
              for (String tbl : tbls) {
                tableToKeys.computeIfAbsent(tbl.toUpperCase(), k -> ConcurrentHashMap.newKeySet())
                    .add(cacheKey);
              }
            }
            Logger.debug("Mimir cache PUT for SQL: {} (ttlMs={})", sql, ttlMs);
          }
          return result;
        }
      }
    } catch (SQLException e) {
      Logger.error("Error executing prepared query: " + e.getMessage());
      throw new RuntimeException("Database query failed", e);
    } finally {
      // Only close the connection if it's not managed by a transaction
      if (conn != null && !isTransactionConnection) {
        try {
          conn.close();
        } catch (SQLException e) {
          Logger.error("Error closing connection: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Execute an INSERT statement and return the inserted row(s).
   *
   * @param sql    SQL statement string
   * @param params Parameters for the prepared statement
   * @return List of inserted Row objects
   */
  public List<Row> executeInsert(String sql, Object... params) {
    Connection conn = null;
    boolean isTransactionConnection = false;

    try {
      conn = getConnection();
      // Check if this is a transaction-managed connection
      isTransactionConnection = (transactionConnection.get() == conn);

      try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        for (int i = 0; i < params.length; i++) {
          stmt.setObject(i + 1, params[i]);
        }

        int affectedRows = stmt.executeUpdate();
        java.util.Set<String> modified = extractTablesFromWrite(sql);
        if (isTransactionConnection) {
          txModifiedTables.get().addAll(modified);
        } else {
          invalidateTables(modified);
        }

        if (affectedRows == 0) {
          return List.of();
        }

        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
          if (generatedKeys.next()) {
            long id = generatedKeys.getLong(1);
            Optional<String> tableName = extractInsertTableName(sql);
            if (tableName.isEmpty()) {
              Logger.error("Failed to extract table name from INSERT statement: {}", sql);
              return List.of();
            }
            return getRows("SELECT * FROM " + tableName.get() + " WHERE rowid = ?", id);
          }
        }
        return List.of();
      }
    } catch (SQLException e) {
      Logger.error("Error executing insert: {}", e.getMessage());
      throw new RuntimeException("Database insert failed", e);
    } finally {
      // Only close if not a transaction connection
      if (conn != null && !isTransactionConnection) {
        try {
          conn.close();
        } catch (SQLException e) {
          Logger.error("Error closing connection: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Extracts the table name from an SQL INSERT statement. Only handles simple INSERT INTO
   * statements without schema qualifiers.
   *
   * @param sql the SQL INSERT statement
   * @return Optional containing the table name, or empty if not found or invalid SQL
   * @throws NullPointerException if sql is null
   */
  private Optional<String> extractInsertTableName(String sql) {
    Objects.requireNonNull(sql, "SQL statement cannot be null");
    String insertIntoKeyword = "INSERT INTO ";
    String upperCaseSql = sql.toUpperCase();
    int insertKeywordIndex = upperCaseSql.indexOf(insertIntoKeyword);

    if (insertKeywordIndex == -1) {
      return Optional.empty();
    }

    int tableNameStart = insertKeywordIndex + insertIntoKeyword.length();
    int tableNameEnd = upperCaseSql.indexOf(" ", tableNameStart);

    if (tableNameEnd == -1) {
      return Optional.empty();
    }

    return Optional.of(sql.substring(tableNameStart, tableNameEnd));
  }

  /**
   * Converts the ResultSet into a list of Row objects.
   *
   * @param resultSet The ResultSet from the SQL query
   * @return List of Row objects
   * @throws SQLException If an error occurs while processing the ResultSet
   */
  private List<Row> list(ResultSet resultSet) throws SQLException {
    List<Map<String, Object>> result = new ArrayList<>();
    int columnCount = resultSet.getMetaData().getColumnCount();

    while (resultSet.next()) {
      Map<String, Object> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = resultSet.getMetaData().getColumnName(i);
        Object value = resultSet.getObject(i);
        row.put(columnName, value);
      }
      result.add(row);
    }

    // Transform each Map into a Row
    return result.stream().map(Row::new).toList();
  }

  /**
   * Execute multiple SQL statements from a single string. This method splits the string by
   * semicolons and executes each statement.
   *
   * @param conn Connection to the database
   * @param sql  SQL statements separated by semicolons
   * @throws SQLException If an error occurs while executing the statements
   */
  private void executeSqlStatements(Connection conn, String sql) throws SQLException {
    for (String statement : sql.split(";")) {
      executeStatement(conn, statement.trim());
    }
  }

  /**
   * Execute a single SQL statement. This method is used internally to execute each statement from a
   * batch.
   *
   * @param conn      Connection to the database
   * @param statement The SQL statement to execute
   * @throws SQLException If an error occurs while executing the statement
   */
  private void executeStatement(Connection conn, String statement) throws SQLException {
    if (!statement.isEmpty()) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(statement);
      }
    }
  }

  /**
   * Execute a paginated query and return a Page of Row objects. This method automatically adds
   * LIMIT, OFFSET, and ORDER BY clauses to the SQL.
   *
   * @param sql         Base SQL query (without LIMIT/OFFSET)
   * @param pageRequest Pagination parameters
   * @param params      Parameters for the prepared statement
   * @return Page containing Row objects and pagination metadata
   */
  public Page<Row> getPage(String sql, PageRequest pageRequest, Object... params) {
    return getPage(sql, pageRequest, row -> row, params);
  }

  /**
   * Execute a paginated query with a custom mapper function. This method automatically adds LIMIT,
   * OFFSET, and ORDER BY clauses to the SQL.
   *
   * @param <T>         The type to map each row to
   * @param sql         Base SQL query (without LIMIT/OFFSET)
   * @param pageRequest Pagination parameters
   * @param mapper      Function to transform each Row to type T
   * @param params      Parameters for the prepared statement
   * @return Page containing mapped objects and pagination metadata
   */
  public <T> Page<T> getPage(String sql, PageRequest pageRequest, Function<Row, T> mapper,
      Object... params) {
    // Get total count first
    long totalElements = getCountFromQuery(sql, params);

    if (totalElements == 0) {
      return Page.empty(pageRequest);
    }

    String paginatedSql = buildPaginatedSql(sql, pageRequest);
    List<Row> rows = getRows(paginatedSql, params);
    List<T> content = rows.stream()
        .map(mapper)
        .toList();

    return new Page<>(content, pageRequest, totalElements);
  }

  /**
   * Get count of total elements for pagination. Converts a SELECT query to a COUNT query.
   *
   * @param sql    Original SQL query
   * @param params Parameters for the query
   * @return Total count of elements
   */
  public long getCount(String sql, Object... params) {
    return getCountFromQuery(sql, params);
  }

  /**
   * Helper method to build paginated SQL with LIMIT, OFFSET and optional ORDER BY.
   *
   * @param baseSql     The base SQL query
   * @param pageRequest Pagination parameters
   * @return SQL with pagination clauses added
   */
  private String buildPaginatedSql(String baseSql, PageRequest pageRequest) {
    StringBuilder sql = new StringBuilder(baseSql.trim());

    // Add ORDER BY if specified and not already present
    if (pageRequest.hasSorting() && !containsOrderBy(baseSql)) {
      sql.append(" ORDER BY ")
          .append(sanitizeColumnName(pageRequest.sortBy().get()))
          .append(" ")
          .append(pageRequest.getEffectiveDirection().getSqlKeyword());
    }

    // Add LIMIT and OFFSET
    sql.append(" LIMIT ").append(pageRequest.size())
        .append(" OFFSET ").append(pageRequest.getOffset());

    return sql.toString();
  }

  /**
   * Convert a SELECT query to a COUNT query for pagination.
   *
   * @param sql    Original SELECT query
   * @param params Query parameters
   * @return Total count
   */
  private long getCountFromQuery(String sql, Object... params) {
    String countSql = convertToCountQuery(sql);
    Row countRow = getRow(countSql, params);
    return countRow != null ? countRow.getLong("count").orElse(0L) : 0L;
  }

  /**
   * Convert a SELECT statement to a COUNT statement. Handles simple SELECT queries - for complex
   * queries, consider providing a custom count query.
   *
   * @param sql Original SQL query
   * @return COUNT query
   */
  private String convertToCountQuery(String sql) {
    String upperSql = sql.toUpperCase().trim();

    // Find SELECT and FROM positions
    int selectPos = upperSql.indexOf("SELECT");
    int fromPos = upperSql.indexOf("FROM");

    if (selectPos == -1 || fromPos == -1) {
      throw new IllegalArgumentException("Invalid SQL: Cannot convert to COUNT query - " + sql);
    }

    // Extract everything from FROM onwards (excluding ORDER BY)
    String fromClause = sql.substring(fromPos);

    // Remove ORDER BY clause if present (case insensitive)
    int orderByPos = fromClause.toUpperCase().lastIndexOf("ORDER BY");
    if (orderByPos != -1) {
      fromClause = fromClause.substring(0, orderByPos).trim();
    }

    return "SELECT COUNT(*) as count " + fromClause;
  }

  /**
   * Check if SQL already contains ORDER BY clause.
   *
   * @param sql SQL query to check
   * @return true if ORDER BY is present
   */
  private boolean containsOrderBy(String sql) {
    return sql.toUpperCase().contains("ORDER BY");
  }

  /**
   * Basic SQL column name sanitization to prevent injection. Only allows alphanumeric characters,
   * underscores, and dots.
   *
   * @param columnName Column name to sanitize
   * @return Sanitized column name
   * @throws IllegalArgumentException if column name is invalid
   */
  private String sanitizeColumnName(String columnName) {
    if (columnName == null || columnName.trim().isEmpty()) {
      throw new IllegalArgumentException("Column name cannot be null or empty");
    }

    String sanitized = columnName.trim();

    // Allow alphanumeric, underscore, and dot (for table.column notation)
    if (!sanitized.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$")) {
      throw new IllegalArgumentException("Invalid column name: " + columnName +
          ". Only alphanumeric characters, underscores, and dots are allowed.");
    }

    return sanitized;
  }

  /**
   * Delete the test database file. This method is useful for cleaning up after tests or when the
   * database is no longer needed.
   */
  public void deleteDatabase() {
    if (db.exists()) {
      boolean deleted = db.delete();
      if (deleted) {
        Logger.info("Test database deleted: " + db.getAbsolutePath());
      } else {
        Logger.error("Failed to delete test database: " + db.getAbsolutePath());
      }
    }
  }

  /**
   * Cleanup resources when this Mimir instance is no longer needed. This is optional as SQLite
   * handles cleanup automatically on JVM exit, but good practice for long-running applications with
   * dynamic database usage.
   */
  public void close() {
    try {
      // Clean up any ongoing transactions for this instance
      Connection txConn = transactionConnection.get();
      if (txConn != null) {
        try {
          txConn.rollback(); // Rollback any uncommitted transaction
        } catch (SQLException e) {
          Logger.warn("Error rolling back transaction during cleanup: {}", e.getMessage());
        }
        txConn.close();
        transactionConnection.remove();
      }

      // SQLiteDataSource doesn't need explicit cleanup, but reset state
      initialized.set(false);
      Logger.debug("Mimir instance cleanup completed for: {}", db.getAbsolutePath());
    } catch (SQLException e) {
      Logger.warn("Error during Mimir cleanup: {}", e.getMessage());
    }
  }

  /**
   * Get the database file path for this Mimir instance.
   *
   * @return The database file path
   */
  public String getDatabasePath() {
    return db.getAbsolutePath();
  }

  /**
   * Check if this Mimir instance is initialized.
   *
   * @return true if initialized, false otherwise
   */
  public boolean isInitialized() {
    return initialized.get();
  }

  /**
   * Invalidate all cache entries. Called after write operations.
   */
  private void invalidateCache() {
    ensureCache();
    if (queryCache.estimatedSize() > 0) {
      queryCache.invalidateAll();
      Logger.debug("Mimir cache INVALIDATED (all)");
    }
  }

  private void invalidateTables(Set<String> tables) {
    ensureCache();
    if (tables == null || tables.isEmpty()) {
      invalidateCache();
      return;
    }
    for (String table : tables) {
      Set<SqlCacheKey> keys = tableToKeys.remove(table.toUpperCase());
      if (keys != null) {
        queryCache.invalidateAll(keys);
      }
    }
    Logger.debug("Mimir cache INVALIDATED for tables: {}", tables);
  }

  private Set<String> extractTablesFromWrite(String sql) {
    // 1) Try JSqlParser for correctness
    try {
      String cleaned = preprocessForParser(sql);
      net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(cleaned);
      if (stmt instanceof Insert insert) {
        return Set.of(insert.getTable().getName());
      }
      if (stmt instanceof Update update) {
        return Set.of(update.getTable().getName());
      }
      if (stmt instanceof Delete deleteStmt) {
        return Set.of(deleteStmt.getTable().getName());
      }
    } catch (Exception ex) {
      Logger.debug("extractTablesFromWrite: JSqlParser failed – falling back. Err={}",
          ex.getMessage());
      // fall through to heuristic below
    }

    // 2) Heuristic fallback (previous behaviour)
    try {
      String upper = sql.toUpperCase();
      String table = null;
      if (upper.startsWith("INSERT INTO")) {
        table = upper.split("\\s+")[2];
      } else if (upper.startsWith("UPDATE")) {
        table = upper.split("\\s+")[1];
      } else if (upper.startsWith("DELETE FROM")) {
        table = upper.split("\\s+")[2];
      }
      if (table != null) {
        table = table.replace("`", "").replace("\"", "");
        return Set.of(table);
      }
    } catch (Exception ignore) {
      // ignore
    }
    return Set.of();
  }

  /**
   * Pre-process SQL text to increase the chance JSqlParser can parse it. – Normalises INSERT OR
   * REPLACE / IGNORE → INSERT INTO – Strips trailing LIMIT/OFFSET clauses on UPDATE/DELETE which
   * the parser doesn't understand.
   */
  private String preprocessForParser(String sql) {
    String s = sql.trim();
    // normalise INSERT OR …
    s = s.replaceFirst("(?i)INSERT\\s+OR\\s+(REPLACE|IGNORE)", "INSERT INTO");
    // strip LIMIT / OFFSET at end
    s = s.replaceAll("(?i)\\s+LIMIT\\s+\\d+(\\s+OFFSET\\s+\\d+)?\\s*$", "");
    return s;
  }

  /**
   * Key used for caching query results in Caffeine.
   */
  private static class SqlCacheKey {

    private final String sql;
    private final List<Object> params;

    SqlCacheKey(String sql, Object... params) {
      this.sql = sql == null ? "" : sql.trim();
      this.params = params == null ? List.of() : List.of(params.clone());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SqlCacheKey other)) {
        return false;
      }
      return sql.equals(other.sql) && params.equals(other.params);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sql, params);
    }
  }
}