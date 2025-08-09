package org.ruitx.jaws.db;

import java.util.Optional;

public class DatabaseConfig {
  public final String databasePath;
  public final Optional<String> schemaPath;
  public final int readerPoolSize;
  public final int busyTimeoutMs;
  public final boolean enableWal;
  public final String synchronousMode;
  public final Optional<Long> leakDetectionThresholdMs;
  public final Optional<String> writerPoolName;
  public final Optional<String> readerPoolName;

  public DatabaseConfig(
      String databasePath,
      Optional<String> schemaPath,
      int readerPoolSize,
      int busyTimeoutMs,
      boolean enableWal,
      String synchronousMode,
      Optional<Long> leakDetectionThresholdMs,
      Optional<String> writerPoolName,
      Optional<String> readerPoolName) {
    this.databasePath = databasePath;
    this.schemaPath = schemaPath;
    this.readerPoolSize = readerPoolSize;
    this.busyTimeoutMs = busyTimeoutMs;
    this.enableWal = enableWal;
    this.synchronousMode = synchronousMode;
    this.leakDetectionThresholdMs = leakDetectionThresholdMs;
    this.writerPoolName = writerPoolName;
    this.readerPoolName = readerPoolName;
  }
}
