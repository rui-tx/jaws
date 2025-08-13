package org.ruitx.jaws.components.mimir;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;

/**
 * BaseRepo provides small, explicit helpers around Mimir for typed mapping and common ops. Keep it
 * simple and SQLite-first for now.
 */
public abstract class Repository {

  protected final Mimir db;

  protected Repository(Mimir db) {
    this.db = db;
  }

  // Helpers
  private static <T> T mapperSafe(RowMapper<T> mapper, Row r) {
    try {
      return mapper.map(r);
    } catch (Exception e) {
      return null;
    }
  }

  // Reads
  protected <T> Optional<T> getOne(String sql, RowMapper<T> mapper, Object... params) {
    try {
      Optional<Row> row = db.getRow(sql, params);
      if (row.isEmpty()) {
        return Optional.empty();
      }
      T mapped = mapper.map(row.get());
      return Optional.ofNullable(mapped);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  protected <T> List<T> getAll(String sql, RowMapper<T> mapper, Object... params) {
    List<T> out = new ArrayList<>();
    try {
      List<Row> rows = db.getRows(sql, params);
      for (Row r : rows) {
        T mapped = mapperSafe(mapper, r);
        if (mapped != null) {
          out.add(mapped);
        }
      }
    } catch (Exception e) {
      // return empty list on error to match current repo behavior
    }
    return out;
  }

  protected <T> Page<T> getPage(String sql, PageRequest pageRequest, RowMapper<T> mapper,
      Object... params) {
    try {
      return db.getPage(sql, pageRequest, r -> mapperSafe(mapper, r), params);
    } catch (Exception e) {
      return Page.empty(pageRequest);
    }
  }

  // Writes
  protected int execute(String sql, Object... params) {
    return db.execute(sql, params);
  }

  protected long insert(String sql, Object... params) {
    return db.insert(sql, params);
  }

  protected <R> R inTransaction(SqlSupplier<R> body) throws Exception {
    try {
      db.beginTransaction();
      R result = body.get();
      db.commitTransaction();
      return result;
    } catch (Exception e) {
      db.rollbackTransaction();
      throw e;
    }
  }

  protected void inTransaction(SqlRunnable body) throws Exception {
    inTransaction(() -> {
      body.run();
      return null;
    });
  }

  // Transactions
  @FunctionalInterface
  public interface SqlSupplier<R> {

    R get() throws Exception;
  }

  @FunctionalInterface
  public interface SqlRunnable {

    void run() throws Exception;
  }
}
