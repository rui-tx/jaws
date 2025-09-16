package org.ruitx.jaws.components.mimir;

/**
 * A post-DB-ready initialization task. Implementations must be idempotent. Any exception aborts
 * startup.
 */
public interface DatabaseSeeder {

  /**
   * A human-friendly name used in logs.
   */
  String name();

  /**
   * Execute the seeder logic. Implementations should be idempotent and can assume the DB schema is
   * present. Throwing an exception will abort startup.
   */
  void run(Mimir db) throws Exception;
}
