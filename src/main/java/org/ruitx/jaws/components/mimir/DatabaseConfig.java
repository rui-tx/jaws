package org.ruitx.jaws.components.mimir;

import java.util.List;

public record DatabaseConfig(
    String databasePath,
    String schemaPath,
    int readerPoolSize,
    int busyTimeoutMs,
    boolean enableWal,
    String synchronousMode,
    Long leakDetectionThresholdMs,
    String writerPoolName,
    String readerPoolName,
    List<DatabaseSeeder> seeders) {

}
