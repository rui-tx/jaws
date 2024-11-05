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

    public boolean executeSql(String sql) {
        boolean result = false;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            result = stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error("Error executing SQL: " + e.getMessage());
            return result;
        }
        return result;
    }

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

    public void executeSqlStatements(Connection conn, String sql) throws SQLException {
        for (String statement : sql.split(";")) {
            executeStatement(conn, statement.trim());
        }
    }

    private void executeStatement(Connection conn, String statement) throws SQLException {
        if (!statement.isEmpty()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            }
        }
    }

    public <T> T executeQuery(String sql, SqlFunction<T> action) {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return action.apply(rs);
        } catch (SQLException e) {
            Logger.error("Error executing SQL: " + e.getMessage());
            return null;
        }
    }

    // not tested
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

    public List<Row> list(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> result = listHelper(resultSet);
        return result.stream().map(Row::new).toList();
    }

    private List<Map<String, Object>> listHelper(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        int columnCount = resultSet.getMetaData().getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = resultSet.getMetaData().getColumnName(i);
                Object value = resultSet.getObject(i);
                row.put(columnName, value);
            }
            resultList.add(row);
        }
        return resultList;
    }

}
