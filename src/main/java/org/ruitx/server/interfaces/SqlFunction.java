package org.ruitx.server.interfaces;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlFunction<T> {
    T apply(ResultSet resultSet) throws SQLException;
}