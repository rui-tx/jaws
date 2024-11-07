package integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ruitx.server.components.Mimir;
import org.ruitx.server.utils.Row;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.ruitx.server.configs.ApplicationConfig.DEFAULT_DATABASE_TESTS_PATH;

class MimirIntegrationTests {

    private Mimir db;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Mimir();
        db.initializeDatabase(DEFAULT_DATABASE_TESTS_PATH);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.deleteDatabase();
    }

    @Test
    void givenTodoTableExists_whenFetchAllTodos_thenReturnsExpectedTodos() throws SQLException {
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        assertEquals(3, todos.size(), "Expected 3 TODO items in the table.");
        assertTrue(todos.stream().anyMatch(row -> row.get("todo").equals("Buy milk")), "Expected 'Buy milk' to be in the table.");
        assertTrue(todos.stream().anyMatch(row -> row.get("todo").equals("Buy eggs")), "Expected 'Buy eggs' to be in the table.");
        assertTrue(todos.stream().anyMatch(row -> row.get("todo").equals("Buy bread")), "Expected 'Buy bread' to be in the table.");
    }

    @Test
    void givenTodoTableWithData_whenFetchingFirstTodo_thenReturnsFirstRow() throws SQLException {
        String querySql = "SELECT * FROM TODO";
        Row row = db.getRow(querySql);
        assertNotNull(row, "Expected to find a row in the result set.");
        assertEquals("Buy milk", row.get("todo"), "The first row's 'todo' column should be 'Buy milk'.");
    }


    @Test
    void givenNoTodosInDatabase_whenInsertNewTodo_thenTodoIsInserted() throws SQLException {
        String newTodo = "Go to gym";
        db.executeSql("INSERT INTO TODO (todo) VALUES (?)", newTodo);

        List<Row> todos = db.getRows("SELECT * FROM TODO WHERE todo = ?", newTodo);
        assertEquals(1, todos.size(), "Expected 1 row with the new TODO.");
        assertEquals(newTodo, todos.get(0).get("todo"), "The inserted TODO does not match the expected value.");
    }

    @Test
    void givenTodosInDatabase_whenDeleteTodo_thenTodoIsRemoved() throws SQLException {
        String todoToDelete = "Buy milk";
        db.executeSql("DELETE FROM TODO WHERE todo = ?", todoToDelete);
        List<Row> todos = db.getRows("SELECT * FROM TODO WHERE todo = ?", todoToDelete);
        assertTrue(todos.isEmpty(), "Expected the deleted TODO to be removed.");
    }

    @Test
    void givenTableWithTodos_whenUpdateTodo_thenTodoIsUpdated() throws SQLException {
        String oldTodo = "Buy milk";
        String newTodo = "Buy almond milk";
        db.executeSql("UPDATE TODO SET todo = ? WHERE todo = ?", newTodo, oldTodo);
        List<Row> todos = db.getRows("SELECT * FROM TODO WHERE todo = ?", newTodo);
        assertEquals(1, todos.size(), "Expected the updated TODO item to be in the table.");
        assertEquals(newTodo, todos.get(0).get("todo"), "The updated TODO does not match the expected value.");
    }

    @Test
    void givenNoDataInTable_whenFetchingRows_thenReturnsEmptyList() throws SQLException {
        db.executeSql("DELETE FROM TODO");
        List<Row> todos = db.getRows("SELECT * FROM TODO");
        assertTrue(todos.isEmpty(), "Expected no rows to be returned.");
    }

    @Test
    void givenValidTodo_whenExecuteSql_thenReturnsAffectedRowCount() {
        String newTodo = "Buy coffee";
        int affectedRows = db.executeSql("INSERT INTO TODO (todo) VALUES (?)", newTodo);
        assertEquals(1, affectedRows, "Expected 1 affected row after inserting a new TODO.");
    }

    @Test
    void givenValidTodo_whenExecuteSql_thenReturnsTrueForSuccess() {
        String todoToDelete = "Buy eggs";
        int isSuccess = db.executeSql("DELETE FROM TODO WHERE todo = ?", todoToDelete);
        assertEquals(1, isSuccess, "Expected 1 affected row after deleting a TODO.");
    }

    @Test
    void givenInvalidSqlQuery_whenExecuting_thenThrowsSQLException() {
        String invalidSql = "SELECT * FROM NonExistentTable";
        List<Row> rows = db.getRows(invalidSql);
        assertNull(rows, "Expected the query to fail and return null.");
    }

    @Test
    void givenCorruptedDatabase_whenCreatingConnection_thenThrowsSQLException() {
        String invalidPath = "invalid/path/to/database.db";
        db.initializeDatabase(invalidPath);
        String querySql = "SELECT * FROM TODO";
        List<Row> rows = db.getRows(querySql);
        assertNull(rows, "Expected the query to fail due to invalid database connection.");
    }

    @Test
    void givenNullValueForNonNullableColumn_whenExecutingPreparedStatement_thenThrowsSQLException() {
        String insertSql = "INSERT INTO TODO (todo) VALUES (?)";
        int affectedRows = db.executeSql(insertSql, (Object) null);
        assertEquals(0, affectedRows, "Expected no rows to be inserted due to constraint violation.");
    }

    @Test
    void givenInvalidSchemaFile_whenInitializingDatabase_thenThrowsSQLException() {
        String invalidSchemaPath = "invalid/schema/path.sql";
        db.initializeDatabase(invalidSchemaPath);
        List<Row> rows = db.getRows("SELECT * FROM TODO");
        assertNull(rows, "Expected failure when trying to load an invalid schema file.");
    }

    @Test
    void givenInvalidUpdateSql_whenExecuting_thenThrowsSQLException() {
        String invalidSql = "UPDATE NonExistentTable SET todo = 'Buy milk'";
        boolean result = db.executeSql(invalidSql);
        assertFalse(result, "Expected the SQL execution to fail due to invalid statement.");
    }
    
}