package org.ruitx.jaws.components.mimir;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface representing a function that takes a ResultSet and returns a value of type
 * T. This is typically used for mapping SQL query results to Java objects.
 *
 * @param <T> the type of the result
 */
@FunctionalInterface
public interface SqlFunction<T> {

  T apply(ResultSet resultSet) throws SQLException;
}