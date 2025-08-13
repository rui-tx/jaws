package org.ruitx.jaws.components.mimir;

import org.ruitx.jaws.types.Row;

/**
 * Maps a database Row to a domain model instance. Keep mappings explicit and simple.
 */
@FunctionalInterface
public interface RowMapper<T> {

  T map(Row r) throws Exception;
}
