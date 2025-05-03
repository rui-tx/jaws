package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.jaws.types.Row;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RowUnitTest {

    @Test
    void givenValidData_whenGetCalled_thenReturnsCorrectValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "Test User");
        Row row = new Row(data);

        Object idValue = row.get("id");
        Object nameValue = row.get("name");

        assertEquals(1, idValue, "Expected 'id' to be 1.");
        assertEquals("Test User", nameValue, "Expected 'name' to be 'Test User'.");
    }

    @Test
    void givenNonExistentColumn_whenGetCalled_thenReturnsNull() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        Row row = new Row(data);

        Object nonExistentValue = row.get("nonExistentColumn");
        assertNull(nonExistentValue, "Expected value to be null for non-existent column.");
    }

    @Test
    void givenValidData_whenGetStringCalled_thenReturnsCorrectString() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Test User");
        Row row = new Row(data);

        String nameValue = row.getString("name").get();
        assertEquals("Test User", nameValue, "Expected 'name' to be 'Test User'.");
    }

//    @Test
//    void givenNonStringColumn_whenGetStringCalled_thenThrowsClassCastException() {
//        Map<String, Object> data = new HashMap<>();
//        data.put("id", 1);
//        Row row = new Row(data);
//
//        assertThrows(ClassCastException.class, () -> row.getString("id"), "Expected ClassCastException when casting non-string column to String.");
//    }

    @Test
    void givenValidData_whenGetIntCalled_thenReturnsCorrectInteger() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        Row row = new Row(data);

        Integer idValue = row.getInt("id").get();
        assertEquals(1, idValue, "Expected 'id' to be 1.");
    }

//    @Test
//    void givenNonIntegerColumn_whenGetIntCalled_thenThrowsClassCastException() {
//        Map<String, Object> data = new HashMap<>();
//        data.put("name", "Test User");
//        Row row = new Row(data);
//
//        assertThrows(ClassCastException.class, () -> row.getInt("name"), "Expected ClassCastException when casting non-integer column to Integer.");
//    }

    @Test
    void givenValidData_whenContainsColumnCalled_thenReturnsTrueForExistingColumn() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        Row row = new Row(data);

        boolean containsId = row.containsColumn("id");
        boolean containsName = row.containsColumn("name");

        assertTrue(containsId, "Expected row to contain 'id' column.");
        assertFalse(containsName, "Expected row not to contain 'name' column.");
    }

    @Test
    void givenEmptyData_whenContainsColumnCalled_thenReturnsFalseForNonExistentColumn() {
        Map<String, Object> data = new HashMap<>();
        Row row = new Row(data);
        assertFalse(row.containsColumn("id"), "Expected row not to contain 'id' column.");
    }
}
