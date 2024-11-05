package org.ruitx.server.components;

import org.ruitx.server.interfaces.SqlFunction;
import org.ruitx.server.utils.Row;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.server.configs.ApplicationConfig.DATABASE_PATH;
import static org.ruitx.server.configs.ApplicationConfig.DATABASE_SCHEMA_PATH;

public class Mimir {
    private final File db;

    public Mimir() {
        this.db = new File(DATABASE_PATH);
    }

    public void initializeDatabase() {
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
        }
    }

    private void loadSchema() {
        try (Connection conn = getConnection()) {
            String sql = Files.readString(Path.of(DATABASE_SCHEMA_PATH));
            executeSqlStatements(conn, sql);
            Logger.info("Database initialized with " + DATABASE_SCHEMA_PATH);
        } catch (SQLException | IOException e) {
            Logger.error("Error initializing database: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
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
     * Execute a SQL query and return a list of rows.
     *
     * @param sql SQL query string
     * @return List of Row objects
     */
    public List<Row> getRows(String sql) {
        return executeQuery(sql, this::list);
    }

    /**
     * Execute a SQL statement that doesn't return data (like UPDATE, DELETE, etc.).
     *
     * @param sql SQL statement string
     * @return true if executed successfully, false otherwise
     */
    public boolean executeSql(String sql) {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            return stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error("Error executing SQL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute a SQL statement with parameters (like INSERT, UPDATE, DELETE).
     *
     * @param sql    SQL statement string
     * @param params Parameters for the prepared statement
     * @return The number of affected rows
     */
    public int executeSql(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            return stmt.executeUpdate(); // affected rows
        } catch (SQLException e) {
            Logger.error("Error executing prepared update: " + e.getMessage());
            return 0;
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
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return action.apply(rs);
        } catch (SQLException e) {
            Logger.error("Error executing SQL: " + e.getMessage());
            return null;
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
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return action.apply(rs);
            }
        } catch (SQLException e) {
            Logger.error("Error executing prepared query: " + e.getMessage());
            return null;
        }
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
}
