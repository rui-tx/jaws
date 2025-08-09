package org.ruitx.jaws.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

public interface DbConnector {
  void initialize() throws Exception;
  boolean isReady();

  Connection getReaderConnection() throws SQLException;
  Connection getWriterConnection() throws SQLException;

  <T> T withTransaction(Function<Connection, T> work) throws Exception;

  void close();
}
