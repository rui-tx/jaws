package org.ruitx.jaws.db;

import java.util.List;

/**
 * @param schemaPath               nullable
 * @param leakDetectionThresholdMs nullable
 * @param writerPoolName           nullable
 * @param readerPoolName           nullable
 * @param seeders                  Optional, ordered seeders to run after DB is ready nullable
 */
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
