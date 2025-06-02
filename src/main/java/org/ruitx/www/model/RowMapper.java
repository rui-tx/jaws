package org.ruitx.www.model;

import org.ruitx.jaws.types.Row;
import java.util.Optional;
import java.util.function.Function;

/**
 * Utility class for mapping database rows to model objects.
 */
public final class RowMapper {
    private RowMapper() {
        // Prevent instantiation
    }

    /**
     * Functional interface for mapping database rows to model objects
     */
    @FunctionalInterface
    public interface Mapper<T> {
        Optional<T> map(Row row);
    }

    /**
     * Safely gets a value from a Row, returning null if not present.
     *
     * @param row the database row
     * @param column the column name
     * @param getter the getter function to use
     * @return the value or null if not present
     */
    public static <T> T getOrNull(Row row, String column, Function<String, Optional<T>> getter) {
        return getter.apply(column).orElse(null);
    }

    /**
     * Checks if a required field is present in the row.
     *
     * @param row the database row
     * @param column the column name
     * @param getter the getter function to use
     * @return true if the field is present, false otherwise
     */
    public static <T> boolean hasRequiredField(Row row, String column, Function<String, Optional<T>> getter) {
        return getter.apply(column).isPresent();
    }

    /**
     * Gets a value with a default if not present.
     *
     * @param row the database row
     * @param column the column name
     * @param getter the getter function to use
     * @param defaultValue the default value to use if not present
     * @return the value or the default if not present
     */
    public static <T> T getOrDefault(Row row, String column, Function<String, Optional<T>> getter, T defaultValue) {
        return getter.apply(column).orElse(defaultValue);
    }
} 