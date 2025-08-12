package org.ruitx.jaws.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.tinylog.Logger;

public class Verdandi implements DbConnector {

  private final DatabaseConfig cfg;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ThreadLocal<Connection> txnConn = new ThreadLocal<>();
  private volatile HikariDataSource writerDs;
  private volatile HikariDataSource readerDs;

  public Verdandi(DatabaseConfig cfg) {
    this.cfg = cfg;
  }

  @Override
  public void initialize() throws Exception {
    if (ready.get()) {
      return;
    }

    ensureDbFileExists();

    // writer pool
    HikariConfig w = new HikariConfig();
    String abs = new File(cfg.databasePath()).getAbsolutePath();
    w.setJdbcUrl("jdbc:sqlite:file:" + abs + "?uri=true");
    w.setPoolName(cfg.writerPoolName() != null ? cfg.writerPoolName() : "jaws-writer");
    w.setMaximumPoolSize(1);
    w.setMinimumIdle(1);
    // PRAGMAs; WAL + sync on writer
    StringBuilder init = new StringBuilder();
    init.append("PRAGMA foreign_keys=ON; ");
    init.append("PRAGMA busy_timeout=").append(cfg.busyTimeoutMs()).append("; ");
    if (cfg.enableWal()) {
      init.append("PRAGMA journal_mode=WAL; ");
      init.append("PRAGMA wal_autocheckpoint=1000; ");
    }
    init.append("PRAGMA synchronous=").append(cfg.synchronousMode()).append(";");
    w.setConnectionInitSql(init.toString());
    if (cfg.leakDetectionThresholdMs() != null) {
      w.setLeakDetectionThreshold(cfg.leakDetectionThresholdMs());
    }
    writerDs = new HikariDataSource(w);

    // reader pool (read-only URL; do not call setReadOnly(true))
    HikariConfig r = new HikariConfig();
    r.setJdbcUrl("jdbc:sqlite:file:" + abs + "?mode=ro&uri=true");
    r.setPoolName(cfg.readerPoolName() != null ? cfg.readerPoolName() : "jaws-reader");
    r.setMaximumPoolSize(cfg.readerPoolSize());
    r.setMinimumIdle(Math.max(1, cfg.readerPoolSize() / 2));
    r.setConnectionInitSql(
        "PRAGMA foreign_keys=ON; PRAGMA busy_timeout=" + cfg.busyTimeoutMs() + ";");
    if (cfg.leakDetectionThresholdMs() != null) {
      r.setLeakDetectionThreshold(cfg.leakDetectionThresholdMs());
    }
    readerDs = new HikariDataSource(r);

    // schema
    if (cfg.schemaPath() != null && isDatabaseEmptyInternal()) {
      applySchema(cfg.schemaPath());
    }

    ready.set(true);
  }

  @Override
  public boolean isReady() {
    return ready.get();
  }

  @Override
  public Connection getReaderConnection() throws SQLException {
    Connection tx = txnConn.get();
    if (tx != null) {
      return tx; // inside transaction, use writer
    }
    ensureReady();
    return readerDs.getConnection();
  }

  @Override
  public Connection getWriterConnection() throws SQLException {
    Connection tx = txnConn.get();
    if (tx != null) {
      return tx;
    }
    ensureReady();
    return writerDs.getConnection();
  }

  @Override
  public <T> T withTransaction(Function<Connection, T> work) throws Exception {
    ensureReady();
    try (Connection c = writerDs.getConnection()) {
      boolean prevAuto = c.getAutoCommit();
      c.setAutoCommit(false);
      txnConn.set(c);
      try {
        T result = work.apply(c);
        c.commit();
        return result;
      } catch (Exception e) {
        try {
          c.rollback();
        } catch (Exception ignore) {
        }
        throw e;
      } finally {
        txnConn.remove();
        try {
          c.setAutoCommit(prevAuto);
        } catch (Exception ignore) {
        }
      }
    }
  }

  @Override
  public void close() {
    ready.set(false);
    txnConn.remove();
    if (readerDs != null) {
      try {
        readerDs.close();
      } catch (Exception ignore) {
      }
      readerDs = null;
    }
    if (writerDs != null) {
      try {
        writerDs.close();
      } catch (Exception ignore) {
      }
      writerDs = null;
    }
  }

  private void ensureDbFileExists() throws Exception {
    File f = new File(cfg.databasePath());
    File parent = f.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs() && !parent.exists()) {
        throw new IllegalStateException("Failed to create DB directory: " + parent);
      }
    }
    if (!f.exists()) {
      Files.createFile(Paths.get(f.getAbsolutePath()));
    }
  }

  private boolean isDatabaseEmptyInternal() {
    try (Connection conn = writerDs.getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");
        ResultSet rs = ps.executeQuery()) {
      return !rs.next();
    } catch (Exception e) {
      Logger.warn("Error checking if database is empty: {}", e.getMessage());
      return true;
    }
  }

  private void applySchema(String schemaPath) throws Exception {
    String sql = Files.readString(Paths.get(schemaPath));
    try (Connection conn = writerDs.getConnection()) {
      for (String stmt : sql.split(";")) {
        String s = stmt.trim();
        if (s.isEmpty()) {
          continue;
        }
        try (PreparedStatement ps = conn.prepareStatement(s)) {
          ps.execute();
        }
      }
    }
  }

  private void ensureReady() {
    if (!ready.get()) {
      throw new IllegalStateException("Database not ready");
    }
  }
}
