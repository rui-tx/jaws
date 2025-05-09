package org.ruitx.jaws.components;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.ruitx.jaws.interfaces.SqlFunction;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsUtils;
import org.sqlite.SQLiteDataSource;
import org.tinylog.Logger;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_PATH;
import static org.ruitx.jaws.configs.ApplicationConfig.DATABASE_SCHEMA_PATH;

public class Mimir {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();
    private static DataSource dataSource;
    private File db;

    public Mimir() {
        this.db = new File(DATABASE_PATH);
        initializeDataSource();
    }

    private synchronized void initializeDataSource() {
        if (!initialized.get()) {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + db.getAbsolutePath());
            dataSource = ds;
            initialized.set(true);
        }
    }

    public void initializeDatabase(String databasePath) {
        if (databasePath != null && !databasePath.isEmpty()) {
            this.db = new File(databasePath);
        }

        createDatabaseFile();
        Logger.info("Database is ready.");
    }

    private void createDatabaseFile() {
        if (db.exists()) return;
        try {
            if (db.createNewFile()) {
                Logger.info("Database created: " + db.getAbsolutePath());
                loadSchema();
            } else {
                throw new IOException("Could not create database file.");
            }
        } catch (IOException e) {
            Logger.error("Error creating database file: " + e.getMessage());
            throw new RuntimeException("Failed to create database file", e);
        }
    }

    private void loadSchema() {
        try (Connection conn = getConnection()) {
            String sql = Files.readString(Path.of(DATABASE_SCHEMA_PATH));
            executeSqlStatements(conn, sql);
            createDefaultAdminUser();
            Logger.info("Database initialized with " + DATABASE_SCHEMA_PATH);
        } catch (SQLException | IOException e) {
            Logger.error("Error initializing database: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    private void createDefaultAdminUser() {
        Optional<String> password = JawsUtils.newPassword();
        String hashedPassword =
                BCrypt.withDefaults().hashToString(12, password.orElse("Lee7Pa$$w00rd").toCharArray());
        executeSql("INSERT INTO USER (user, password_hash, created_at) VALUES (?, ?, ?)",
                "admin", hashedPassword, Date.from(Instant.now()));
        Logger.info("A new admin user has been created with username 'admin' and password '"
                + (password.orElse("Lee7Pa$$w00rd")) + "'");
        Logger.info("Please save this in a safe place, it will not be shown again.");
    }

    /**
     * Get a connection from the data source.
     * If we're in a transaction, return the transaction connection.
     * Otherwise, get a new connection from the data source.
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
        } finally {
            conn.close();
            transactionConnection.remove();
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
        } finally {
            conn.close();
            transactionConnection.remove();
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
                stmt = conn.prepareStatement(sql);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                return stmt.executeUpdate();
            } finally {
                if (stmt != null) try {
                    stmt.close();
                } catch (SQLException e) {
                    Logger.error("Error closing statement: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            Logger.error("Error executing prepared update: {}", e.getMessage());
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
                Logger.info("SQL executed: {}, result: {}", sql, result);
                return result;
            } finally {
                if (stmt != null) try {
                    stmt.close();
                } catch (SQLException e) {
                    Logger.error("Error closing statement: " + e.getMessage());
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
                return action.apply(rs);
            } finally {
                // Close resources in reverse order
                if (rs != null) try {
                    rs.close();
                } catch (SQLException e) {
                    Logger.error("Error closing result set: " + e.getMessage());
                }
                if (stmt != null) try {
                    stmt.close();
                } catch (SQLException e) {
                    Logger.error("Error closing statement: " + e.getMessage());
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
                    return action.apply(rs);
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
     * Extracts the table name from an SQL INSERT statement.
     * Only handles simple INSERT INTO statements without schema qualifiers.
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

    // Private helper method to execute statements (only used internally).
    private void executeSqlStatements(Connection conn, String sql) throws SQLException {
        for (String statement : sql.split(";")) {
            executeStatement(conn, statement.trim());
        }
    }

    // Private helper method to execute a single statement (only used internally).
    private void executeStatement(Connection conn, String statement) throws SQLException {
        if (!statement.isEmpty()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            }
        }
    }

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
}
